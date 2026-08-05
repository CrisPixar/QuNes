import { verifyToken } from "../utils/jwt.js";
import { getDB } from "../db/index.js";
import { generateId } from "../crypto/index.js";
import { checkWsRateLimit } from "../middleware/rateLimit.js";
import { MAX_HEADER_SIZE, MAX_MESSAGE_PAYLOAD } from "../constants.js";

interface WsData {
  userId: string;
  username: string;
  role: string;
  ip: string;
  sessionId: string;
}

const connections = new Map<string, Set<any>>();

export function broadcastToUser(userId: string, message: object): void {
  const payload = JSON.stringify(message);
  for (const socket of connections.get(userId) ?? []) {
    try { socket.send(payload); } catch {}
  }
}

function isMember(chatId: string, userId: string): boolean {
  return Boolean(getDB().query(
    "SELECT 1 FROM chat_members WHERE chat_id = ? AND user_id = ?",
  ).get(chatId, userId));
}

function broadcastToChat(chatId: string, message: object, excludeUserId?: string): void {
  const members = getDB().query("SELECT user_id FROM chat_members WHERE chat_id = ?").all(chatId) as any[];
  for (const member of members) {
    if (member.user_id !== excludeUserId) broadcastToUser(member.user_id, message);
  }
}

export const websocketHandler = {
  open(ws: any) {
    ws.authTimeout = setTimeout(() => {
      if (!ws.data?.userId) ws.close(4001, "Authentication timeout");
    }, 10_000);
    ws.send(JSON.stringify({ type: "connected" }));
  },

  message(ws: any, raw: string | Buffer) {
    try {
      const message = JSON.parse(raw.toString()) as Record<string, any>;
      const data = ws.data as WsData | undefined;
      if (!data?.userId) {
        if (message.type !== "auth") {
          ws.send(JSON.stringify({ type: "error", message: "Send auth first" }));
          return;
        }
        handleAuth(ws, message);
        return;
      }
      if (!checkWsRateLimit(data.userId)) {
        ws.send(JSON.stringify({ type: "error", message: "Rate limit exceeded" }));
        return;
      }
      switch (message.type) {
        case "message":
          handleMessage(ws, data, message);
          break;
        case "typing":
          if (typeof message.chatId === "string" && isMember(message.chatId, data.userId)) {
            broadcastToChat(message.chatId, { type: "typing", chatId: message.chatId, userId: data.userId }, data.userId);
          }
          break;
        case "read":
          handleRead(data, message);
          break;
        case "ping":
          ws.send(JSON.stringify({ type: "pong", timestamp: Date.now() }));
          break;
        default:
          ws.send(JSON.stringify({ type: "error", message: "Unknown type" }));
      }
    } catch {
      ws.send(JSON.stringify({ type: "error", message: "Invalid message" }));
    }
  },

  close(ws: any) {
    clearTimeout(ws.authTimeout);
    const data = ws.data as WsData | undefined;
    if (!data?.userId) return;
    const userConnections = connections.get(data.userId);
    if (userConnections) {
      userConnections.delete(ws);
      if (!userConnections.size) {
        connections.delete(data.userId);
        broadcastStatus(data.userId, false);
      }
    }
    getDB().run("DELETE FROM ws_sessions WHERE id = ?", [data.sessionId]);
  },

  error(_ws: any, error: Error) {
    console.error("[WS]", error.message);
  },
};

function handleAuth(ws: any, message: Record<string, any>): void {
  if (typeof message.token !== "string" || message.token.length > 4096) {
    ws.close(4001, "Invalid token");
    return;
  }
  try {
    const payload = verifyToken(message.token, "access");
    const user = getDB().query("SELECT id, username, role FROM users WHERE id = ?").get(payload.sub) as any;
    if (!user) {
      ws.close(4001, "User not found");
      return;
    }
    clearTimeout(ws.authTimeout);
    const sessionId = generateId();
    const data: WsData = {
      userId: user.id,
      username: user.username,
      role: user.role,
      ip: ws.remoteAddress ?? "unknown",
      sessionId,
    };
    ws.data = data;
    if (!connections.has(data.userId)) connections.set(data.userId, new Set());
    connections.get(data.userId)!.add(ws);
    getDB().run(
      "INSERT INTO ws_sessions (id,user_id,ip_address,user_agent,connected_at) VALUES (?,?,?,?,?)",
      [sessionId, data.userId, data.ip, "", Date.now()],
    );
    ws.send(JSON.stringify({ type: "auth_ok", userId: data.userId }));
    broadcastStatus(data.userId, true);
  } catch {
    ws.close(4001, "Invalid token");
  }
}

function handleMessage(ws: any, data: WsData, message: Record<string, any>): void {
  const chatId = typeof message.chatId === "string" ? message.chatId : "";
  const encryptedPayload = typeof message.encryptedPayload === "string" ? message.encryptedPayload : "";
  const ratchetHeader = typeof message.ratchetHeader === "string" ? message.ratchetHeader : null;
  const signature = typeof message.signature === "string" ? message.signature : null;
  const nonce = typeof message.nonce === "string" ? message.nonce : null;
  if (!chatId || !encryptedPayload) {
    ws.send(JSON.stringify({ type: "error", message: "chatId and encryptedPayload required" }));
    return;
  }
  if (encryptedPayload.length > MAX_MESSAGE_PAYLOAD || (ratchetHeader && ratchetHeader.length > MAX_HEADER_SIZE) || (signature && signature.length > MAX_HEADER_SIZE) || (nonce && nonce.length > MAX_HEADER_SIZE)) {
    ws.send(JSON.stringify({ type: "error", message: "Message is too large" }));
    return;
  }
  if (!isMember(chatId, data.userId)) {
    ws.send(JSON.stringify({ type: "error", message: "Not a member" }));
    return;
  }

  const messageId = generateId();
  const createdAt = Date.now();
  getDB().run(
    "INSERT INTO messages (id,chat_id,sender_id,encrypted_payload,ratchet_header,signature,server_nonce,payload_size,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
    [messageId, chatId, data.userId, encryptedPayload, ratchetHeader, signature, nonce, encryptedPayload.length, createdAt],
  );
  const event = {
    type: "message",
    id: messageId,
    messageId,
    chatId,
    senderId: data.userId,
    encryptedPayload,
    ratchetHeader,
    signature,
    nonce,
    createdAt,
    delivered: false,
    read: false,
  };
  broadcastToChat(chatId, event, data.userId);
  ws.send(JSON.stringify({ type: "message_sent", messageId, chatId, createdAt }));
}

function handleRead(data: WsData, message: Record<string, any>): void {
  const chatId = typeof message.chatId === "string" ? message.chatId : "";
  const messageId = typeof message.messageId === "string" ? message.messageId : "";
  if (!chatId || !messageId || !isMember(chatId, data.userId)) return;
  getDB().run("UPDATE messages SET read = 1 WHERE id = ? AND chat_id = ?", [messageId, chatId]);
  broadcastToChat(chatId, { type: "read_receipt", chatId, messageId, userId: data.userId }, data.userId);
}

function broadcastStatus(userId: string, online: boolean): void {
  const db = getDB();
  if (!online) db.run("UPDATE users SET last_seen = ? WHERE id = ?", [Date.now(), userId]);
  const contacts = db.query(`
    SELECT DISTINCT other.user_id
    FROM chat_members mine
    JOIN chat_members other ON mine.chat_id = other.chat_id
    WHERE mine.user_id = ? AND other.user_id != ?
  `).all(userId, userId) as any[];
  const event = online
    ? { type: "user_online", userId }
    : { type: "user_offline", userId, lastSeen: Date.now() };
  for (const contact of contacts) broadcastToUser(contact.user_id, event);
}
