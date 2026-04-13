import { getDB }             from "../db/index.js";
import { generateId }        from "../crypto/index.js";
import { requireAuth, json } from "../middleware/auth.js";

export function handleGetChats(req: Request): Response {
  const auth = requireAuth(req); if (auth instanceof Response) return auth;
  const uid = (auth as any).userId; const db = getDB();
  const chats = db.query(`
    SELECT c.id, c.type, c.name, c.created_at,
      (SELECT COUNT(*) FROM messages m WHERE m.chat_id = c.id AND m.deleted = 0) as message_count,
      (SELECT m.created_at FROM messages m WHERE m.chat_id = c.id AND m.deleted = 0 ORDER BY m.created_at DESC LIMIT 1) as last_message_at
    FROM chats c
    JOIN chat_members cm ON cm.chat_id = c.id
    WHERE cm.user_id = ?
    ORDER BY last_message_at DESC NULLS LAST
  `).all(uid) as any[];
  return json(chats.map(c => {
    if (c.type === "direct") {
      const other = db.query(
        "SELECT u.id, u.username, u.last_seen, u.is_scam FROM users u JOIN chat_members cm ON cm.user_id = u.id WHERE cm.chat_id = ? AND u.id != ? LIMIT 1"
      ).get(c.id, uid);
      return { ...c, otherUser: other };
    }
    return c;
  }));
}

export async function handleCreateChat(req: Request): Promise<Response> {
  const auth = requireAuth(req); if (auth instanceof Response) return auth;
  const uid = (auth as any).userId;
  let body: any;
  try { body = await req.json(); } catch { return json({ error: "Invalid JSON" }, 400); }
  const { type, memberIds, name } = body;
  if (!type || !["direct","group"].includes(type)) return json({ error: "type: 'direct' or 'group'" }, 400);
  if (!Array.isArray(memberIds) || memberIds.length === 0) return json({ error: "memberIds required" }, 400);
  const db = getDB();
  if (type === "direct") {
    const oid = memberIds[0];
    const ex = db.query(`
      SELECT c.id FROM chats c
      JOIN chat_members a ON a.chat_id = c.id AND a.user_id = ?
      JOIN chat_members b ON b.chat_id = c.id AND b.user_id = ?
      WHERE c.type = 'direct' LIMIT 1
    `).get(uid, oid) as any;
    if (ex) return json({ chatId: ex.id, existing: true });
  }
  const chatId = generateId(); const now = Date.now();
  db.run("INSERT INTO chats (id,type,name,created_by,created_at) VALUES (?,?,?,?,?)", [chatId, type, name ?? null, uid, now]);
  for (const mid of [uid, ...memberIds.filter((id: string) => id !== uid)]) {
    if (db.query("SELECT id FROM users WHERE id = ?").get(mid))
      db.run("INSERT OR IGNORE INTO chat_members (chat_id,user_id,joined_at) VALUES (?,?,?)", [chatId, mid, now]);
  }
  return json({ chatId, type, name: name ?? null }, 201);
}

export function handleGetMessages(req: Request, chatId: string): Response {
  const auth = requireAuth(req); if (auth instanceof Response) return auth;
  const uid = (auth as any).userId; const db = getDB();
  if (!db.query("SELECT 1 FROM chat_members WHERE chat_id = ? AND user_id = ?").get(chatId, uid))
    return json({ error: "Not a member of this chat" }, 403);
  const url = new URL(req.url);
  const before = url.searchParams.get("before");
  const limit = Math.min(parseInt(url.searchParams.get("limit") ?? "50"), 100);
  const q = `SELECT id, sender_id, encrypted_payload, ratchet_header, signature, payload_size, created_at, delivered, read
             FROM messages WHERE chat_id = ? AND deleted = 0`;
  const msgs = before
    ? db.query(q + " AND created_at < ? ORDER BY created_at DESC LIMIT ?").all(chatId, parseInt(before), limit)
    : db.query(q + " ORDER BY created_at DESC LIMIT ?").all(chatId, limit);
  return json((msgs as any[]).reverse());
}
