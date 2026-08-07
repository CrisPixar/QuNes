import { getDB } from "../db/index.js";
import { generateId } from "../crypto/index.js";
import { requireAuth, json } from "../middleware/auth.js";
import { MESSAGES_PAGE_SIZE } from "../constants.js";
import { positiveInteger, textWithin } from "../utils/validation.js";

function validMemberIds(value: unknown): string[] | null {
  if (!Array.isArray(value) || value.length === 0 || value.length > 50) return null;
  const ids = [...new Set(value.filter((id): id is string => typeof id === "string" && id.length > 0))];
  return ids.length === value.length ? ids : null;
}

export function handleGetChats(req: Request): Response {
  const auth = requireAuth(req);
  if (auth instanceof Response) return auth;
  const db = getDB();
  const chats = db.query(`
    SELECT c.id, c.type, c.name, c.created_at,
      (SELECT COUNT(*) FROM messages m WHERE m.chat_id = c.id AND m.deleted = 0) as message_count,
      (SELECT m.created_at FROM messages m WHERE m.chat_id = c.id AND m.deleted = 0 ORDER BY m.created_at DESC LIMIT 1) as last_message_at
    FROM chats c
    JOIN chat_members cm ON cm.chat_id = c.id
    WHERE cm.user_id = ?
    ORDER BY last_message_at DESC NULLS LAST, c.created_at DESC
  `).all(auth.userId) as any[];

  return json(chats.map((chat) => {
    if (chat.type !== "direct") return chat;
    const other = db.query(
      "SELECT u.id, u.username, u.last_seen, u.is_scam, u.scam_reason, u.is_verified FROM users u JOIN chat_members cm ON cm.user_id = u.id WHERE cm.chat_id = ? AND u.id != ? LIMIT 1",
    ).get(chat.id, auth.userId) as any;
    return {
      ...chat,
      otherUser: other ? {
        id: other.id,
        username: other.username,
        lastSeen: other.last_seen,
        isScam: other.is_scam === 1,
        scamReason: other.scam_reason ?? null,
        isVerified: other.is_verified === 1,
      } : null,
    };
  }));
}

export async function handleCreateChat(req: Request): Promise<Response> {
  const auth = requireAuth(req);
  if (auth instanceof Response) return auth;
  const body = await req.json().catch(() => null);
  if (!body || !["direct", "group"].includes(body.type)) return json({ error: "type must be direct or group" }, 400);

  const memberIds = validMemberIds(body.memberIds);
  if (!memberIds) return json({ error: "memberIds must contain 1 to 50 unique user IDs" }, 400);
  const otherIds = memberIds.filter((id) => id !== auth.userId);
  if (body.type === "direct" && otherIds.length !== 1) return json({ error: "A direct chat needs exactly one other user" }, 400);
  if (body.type === "group" && otherIds.length === 0) return json({ error: "A group needs another member" }, 400);

  const db = getDB();
  const placeholders = otherIds.map(() => "?").join(",");
  const existingUsers = placeholders
    ? db.query(`SELECT id FROM users WHERE id IN (${placeholders})`).all(...otherIds) as any[]
    : [];
  if (existingUsers.length !== otherIds.length) return json({ error: "One or more users do not exist" }, 404);

  const directKey = body.type === "direct" ? [auth.userId, otherIds[0]].sort().join(":") : null;
  if (directKey) {
    // Защита от гонок: сначала пытаемся найти существующий direct-чат, и только
    // если его нет — создаём. INSERT также защищён UNIQUE-индексом по direct_key,
    // поэтому даже при параллельных запросах получится максимум один чат.
    const existing = db.query("SELECT id FROM chats WHERE direct_key = ?").get(directKey) as any;
    if (existing) return json({ chatId: existing.id, existing: true });
  }

  const chatId = generateId();
  const now = Date.now();
  const name = body.type === "group" ? textWithin(body.name, 128) : null;
  try {
    db.run(
      "INSERT INTO chats (id,type,name,direct_key,created_by,created_at) VALUES (?,?,?,?,?,?)",
      [chatId, body.type, name, directKey, auth.userId, now],
    );
  } catch (error) {
    if (directKey) {
      // Параллельная вставка могла обогнать — берём уже существующий чат.
      const existing = db.query("SELECT id FROM chats WHERE direct_key = ?").get(directKey) as any;
      if (existing) return json({ chatId: existing.id, existing: true });
    }
    throw error;
  }
  for (const memberId of [auth.userId, ...otherIds]) {
    db.run(
      "INSERT INTO chat_members (chat_id,user_id,joined_at) VALUES (?,?,?)",
      [chatId, memberId, now],
    );
  }
  return json({ chatId, type: body.type, name }, 201);
}

export function handleGetMessages(req: Request, chatId: string): Response {
  const auth = requireAuth(req);
  if (auth instanceof Response) return auth;
  const db = getDB();
  if (!db.query("SELECT 1 FROM chat_members WHERE chat_id = ? AND user_id = ?").get(chatId, auth.userId)) {
    return json({ error: "Not a member of this chat" }, 403);
  }

  const params = new URL(req.url).searchParams;
  const limit = positiveInteger(params.get("limit"), MESSAGES_PAGE_SIZE, 100);
  const beforeRaw = params.get("before");
  const before = beforeRaw && /^\d+$/.test(beforeRaw) ? Number(beforeRaw) : null;
  const base = `
    SELECT id, chat_id as chatId, sender_id as senderId, client_message_id as clientMessageId,
      encrypted_payload as encryptedPayload, ratchet_header as ratchetHeader, signature,
      protocol_version as protocolVersion, created_at as createdAt, delivered, read
    FROM messages WHERE chat_id = ? AND deleted = 0`;
  const rows = before
    ? db.query(`${base} AND created_at < ? ORDER BY created_at DESC LIMIT ?`).all(chatId, before, limit)
    : db.query(`${base} ORDER BY created_at DESC LIMIT ?`).all(chatId, limit);
  return json((rows as any[]).reverse().map((row) => ({
    ...row,
    delivered: Boolean(row.delivered),
    read: Boolean(row.read),
  })));
}
