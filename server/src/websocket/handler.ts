// Rate: 100 WS сообщений/мин на пользователя
import { verifyToken }        from "../utils/jwt.js";
import { getDB }              from "../db/index.js";
import { generateId }         from "../crypto/index.js";
import { checkWsRateLimit }   from "../middleware/rateLimit.js";

interface WsData { userId: string; username: string; role: string; ip: string; sessionId: string; }
const connections = new Map<string, Set<any>>();

export function broadcastToUser(userId: string, msg: object): void {
  const p = JSON.stringify(msg);
  connections.get(userId)?.forEach(ws => { try { ws.send(p); } catch {} });
}

function broadcastToChat(chatId: string, msg: object, excludeId?: string): void {
  const members = getDB().query("SELECT user_id FROM chat_members WHERE chat_id = ?").all(chatId) as any[];
  for (const { user_id } of members) if (user_id !== excludeId) broadcastToUser(user_id, msg);
}

export const websocketHandler = {
  open(ws: any) {
    ws.send(JSON.stringify({ type: "connected" }));
    ws.authTimeout = setTimeout(() => { if (!ws.data?.userId) ws.close(4001, "Auth timeout"); }, 10_000);
  },
  message(ws: any, raw: string | Buffer) {
    try {
      const data: WsData = ws.data;
      let msg: any;
      try { msg = JSON.parse(raw.toString()); }
      catch { ws.send(JSON.stringify({ type: "error", message: "Invalid JSON" })); return; }

      if (!data?.userId) {
        if (msg.type !== "auth") { ws.send(JSON.stringify({ type: "error", message: "Send auth first" })); return; }
        handleAuth(ws, msg); return;
      }
      if (!checkWsRateLimit(data.userId)) {
        ws.send(JSON.stringify({ type: "error", message: "Rate limit exceeded" })); return;
      }
      switch (msg.type) {
        case "message": handleMessage(ws, data, msg); break;
        case "typing":
          if (msg.chatId) broadcastToChat(msg.chatId, { type:"typing", chatId:msg.chatId, userId:data.userId }, data.userId);
          break;
        case "read":    handleRead(data, msg);    break;
        case "ping":    ws.send(JSON.stringify({ type: "pong" })); break;
        default:        ws.send(JSON.stringify({ type: "error", message: "Unknown type" }));
      }
    } catch (e) { console.error("[WS] Error:", e); }
  },
  close(ws: any) {
    clearTimeout(ws.authTimeout);
    const data: WsData = ws.data;
    if (!data?.userId) return;
    const set = connections.get(data.userId);
    if (set) { set.delete(ws); if (set.size === 0) { connections.delete(data.userId); broadcastStatus(data.userId, false); } }
    getDB().run("DELETE FROM ws_sessions WHERE id = ?", [data.sessionId]);
    console.log(`[WS] Disconnected: ${data.username}`);
  },
  error(ws: any, e: Error) { console.error("[WS] Error:", e.message); },
};

function handleAuth(ws: any, msg: any): void {
  if (!msg.token) { ws.close(4001, "No token"); return; }
  try {
    const p = verifyToken(msg.token);
    clearTimeout(ws.authTimeout);
    const sessionId = generateId();
    const ip = ws.remoteAddress ?? "unknown";
    ws.data = { userId: p.sub, username: p.username, role: p.role, ip, sessionId };
    if (!connections.has(p.sub)) connections.set(p.sub, new Set());
    connections.get(p.sub)!.add(ws);
    getDB().run("INSERT INTO ws_sessions (id,user_id,ip_address,user_agent,connected_at) VALUES (?,?,?,?,?)",
      [sessionId, p.sub, ip, "", Date.now()]);
    ws.send(JSON.stringify({ type: "auth_ok", userId: p.sub }));
    broadcastStatus(p.sub, true);
    console.log(`[WS] Authenticated: ${p.username} from ${ip}`);
  } catch { ws.close(4001, "Invalid token"); }
}

function handleMessage(ws: any, data: WsData, msg: any): void {
  const { chatId, encryptedPayload, signature, nonce, ratchetHeader } = msg;
  if (!chatId || !encryptedPayload) {
    ws.send(JSON.stringify({ type: "error", message: "chatId and encryptedPayload required" })); return;
  }
  const db = getDB();
  if (!db.query("SELECT 1 FROM chat_members WHERE chat_id = ? AND user_id = ?").get(chatId, data.userId)) {
    ws.send(JSON.stringify({ type: "error", message: "Not a member" })); return;
  }
  const messageId = generateId(); const now = Date.now();
  db.run(
    "INSERT INTO messages (id,chat_id,sender_id,encrypted_payload,ratchet_header,signature,payload_size,created_at) VALUES (?,?,?,?,?,?,?,?)",
    [messageId, chatId, data.userId, encryptedPayload, ratchetHeader ?? null, signature ?? null, String(encryptedPayload).length, now]
  );
  broadcastToChat(chatId, { type:"message", messageId, fromUserId:data.userId, chatId, encryptedPayload, ratchetHeader:ratchetHeader??null, signature:signature??null, nonce:nonce??null, createdAt:now }, data.userId);
  ws.send(JSON.stringify({ type: "message_sent", messageId, chatId, createdAt: now }));
}

function handleRead(data: WsData, msg: any): void {
  const { chatId, messageId } = msg;
  if (!chatId || !messageId) return;
  getDB().run("UPDATE messages SET read = 1 WHERE id = ? AND chat_id = ?", [messageId, chatId]);
  broadcastToChat(chatId, { type:"read_receipt", chatId, messageId, userId:data.userId }, data.userId);
}

function broadcastStatus(userId: string, online: boolean): void {
  const db = getDB();
  if (!online) db.run("UPDATE users SET last_seen = ? WHERE id = ?", [Date.now(), userId]);
  const contacts = db.query(`
    SELECT DISTINCT cm2.user_id FROM chat_members cm1
    JOIN chat_members cm2 ON cm1.chat_id = cm2.chat_id
    WHERE cm1.user_id = ? AND cm2.user_id != ?
  `).all(userId, userId) as any[];
  const event = online
    ? { type: "user_online", userId }
    : { type: "user_offline", userId, lastSeen: Date.now() };
  for (const { user_id } of contacts) broadcastToUser(user_id, event);
}
