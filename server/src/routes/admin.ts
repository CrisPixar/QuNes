// ================================================================
//  ADMIN API
//  Все эндпоинты защищены requireAdmin (role = 'admin').
//  Функции: просмотр IP/сессий, SCAM флаг, удаление аккаунтов/
//           сообщений, отзыв сессий, статистика.
// ================================================================

import { getDB }              from "../db/index.js";
import { requireAdmin, json } from "../middleware/auth.js";
import { hashPassword }       from "../crypto/index.js";

export function handleAdminStats(req: Request): Response {
  const auth = requireAdmin(req); if (auth instanceof Response) return auth;
  const db = getDB();
  const q = (sql: string, ...p: any[]) => (db.query(sql).get(...p) as any)?.c ?? 0;
  return json({
    totalUsers:    q("SELECT COUNT(*) as c FROM users"),
    totalMessages: q("SELECT COUNT(*) as c FROM messages WHERE deleted = 0"),
    totalChats:    q("SELECT COUNT(*) as c FROM chats"),
    scamUsers:     q("SELECT COUNT(*) as c FROM users WHERE is_scam = 1"),
    activeSessions:q("SELECT COUNT(*) as c FROM refresh_tokens WHERE revoked = 0 AND expires_at > ?", Date.now()),
    activeWs:      q("SELECT COUNT(*) as c FROM ws_sessions"),
    uptime:        process.uptime(),
    timestamp:     Date.now(),
  });
}

export function handleAdminGetUsers(req: Request): Response {
  const auth = requireAdmin(req); if (auth instanceof Response) return auth;
  const url  = new URL(req.url);
  const q     = url.searchParams.get("q") ?? "";
  const page  = Math.max(1, parseInt(url.searchParams.get("page")  ?? "1"));
  const limit = Math.min(100, parseInt(url.searchParams.get("limit") ?? "50"));
  const db = getDB();
  const users = db.query(`
    SELECT u.id, u.username, u.role, u.is_scam, u.scam_reason, u.last_ip, u.last_seen, u.created_at,
      (SELECT COUNT(*) FROM refresh_tokens rt WHERE rt.user_id = u.id AND rt.revoked = 0 AND rt.expires_at > ?) as active_sessions,
      (SELECT COUNT(*) FROM messages m WHERE m.sender_id = u.id AND m.deleted = 0) as message_count
    FROM users u
    WHERE u.username LIKE ?
    ORDER BY u.created_at DESC
    LIMIT ? OFFSET ?
  `).all(Date.now(), `%${q}%`, limit, (page - 1) * limit) as any[];
  const total = (db.query("SELECT COUNT(*) as c FROM users WHERE username LIKE ?").get(`%${q}%`) as any)?.c ?? 0;
  return json({ users, total, page, limit });
}

export function handleAdminGetUser(req: Request, userId: string): Response {
  const auth = requireAdmin(req); if (auth instanceof Response) return auth;
  const db = getDB();
  const user = db.query(
    "SELECT id, username, role, is_scam, scam_reason, last_ip, last_seen, created_at FROM users WHERE id = ?"
  ).get(userId) as any;
  if (!user) return json({ error: "User not found" }, 404);
  const sessions = db.query(
    "SELECT id, ip_address, user_agent, created_at, expires_at, revoked FROM refresh_tokens WHERE user_id = ? ORDER BY created_at DESC LIMIT 50"
  ).all(userId);
  const wsConns = db.query(
    "SELECT id, ip_address, user_agent, connected_at FROM ws_sessions WHERE user_id = ?"
  ).all(userId);
  return json({ user, sessions, wsConnections: wsConns });
}

export async function handleAdminUpdateUser(req: Request, userId: string): Promise<Response> {
  const auth = requireAdmin(req); if (auth instanceof Response) return auth;
  let body: any;
  try { body = await req.json(); } catch { return json({ error: "Invalid JSON" }, 400); }
  const db = getDB();
  if (!db.query("SELECT id FROM users WHERE id = ?").get(userId)) return json({ error: "User not found" }, 404);
  const updates: string[] = []; const values: any[] = [];
  if (typeof body.username === "string" && body.username.length >= 3) {
    if (db.query("SELECT id FROM users WHERE username = ? AND id != ?").get(body.username, userId))
      return json({ error: "Username already taken" }, 409);
    updates.push("username = ?"); values.push(body.username);
  }
  if (typeof body.password === "string" && body.password.length >= 8) {
    updates.push("password_hash = ?"); values.push(await hashPassword(body.password));
  }
  if (typeof body.role === "string" && ["user","admin"].includes(body.role)) {
    updates.push("role = ?"); values.push(body.role);
  }
  if (updates.length === 0) return json({ error: "Nothing to update" }, 400);
  values.push(userId);
  db.run(`UPDATE users SET ${updates.join(", ")} WHERE id = ?`, values);
  console.log(`[ADMIN] Updated user: ${userId} by ${(auth as any).userId}`);
  return json({ message: "User updated" });
}

export function handleAdminDeleteUser(req: Request, userId: string): Response {
  const auth = requireAdmin(req); if (auth instanceof Response) return auth;
  const db = getDB();
  const u = db.query("SELECT id, username FROM users WHERE id = ?").get(userId) as any;
  if (!u) return json({ error: "User not found" }, 404);
  db.run("DELETE FROM users WHERE id = ?", [userId]); // каскадное удаление через FK
  console.log(`[ADMIN] Deleted user: ${u.username} by ${(auth as any).userId}`);
  return json({ message: "User deleted" });
}

export async function handleAdminSetScam(req: Request, userId: string): Promise<Response> {
  const auth = requireAdmin(req); if (auth instanceof Response) return auth;
  let body: any;
  try { body = await req.json(); } catch { return json({ error: "Invalid JSON" }, 400); }
  const db = getDB();
  if (!db.query("SELECT id FROM users WHERE id = ?").get(userId)) return json({ error: "User not found" }, 404);
  const isScam = body.isScam === true ? 1 : 0;
  db.run("UPDATE users SET is_scam = ?, scam_reason = ? WHERE id = ?", [isScam, body.reason ?? null, userId]);
  console.log(`[ADMIN] SCAM flag: userId=${userId} isScam=${isScam}`);
  return json({ message: isScam ? "User marked as SCAM" : "SCAM flag removed" });
}

export function handleAdminDeleteMessage(req: Request, msgId: string): Response {
  const auth = requireAdmin(req); if (auth instanceof Response) return auth;
  const db = getDB();
  if (!db.query("SELECT id FROM messages WHERE id = ?").get(msgId)) return json({ error: "Message not found" }, 404);
  db.run("UPDATE messages SET deleted = 1, deleted_by_admin = 1 WHERE id = ?", [msgId]);
  return json({ message: "Message deleted" });
}

export function handleAdminDeleteAllMessages(req: Request, chatId: string): Response {
  const auth = requireAdmin(req); if (auth instanceof Response) return auth;
  const db = getDB();
  if (!db.query("SELECT id FROM chats WHERE id = ?").get(chatId)) return json({ error: "Chat not found" }, 404);
  const r = db.run("UPDATE messages SET deleted = 1, deleted_by_admin = 1 WHERE chat_id = ? AND deleted = 0", [chatId]);
  console.log(`[ADMIN] Deleted all messages in chat: ${chatId}`);
  return json({ message: "All messages deleted", count: r.changes });
}

export function handleAdminRevokeUserSessions(req: Request, userId: string): Response {
  const auth = requireAdmin(req); if (auth instanceof Response) return auth;
  getDB().run("UPDATE refresh_tokens SET revoked = 1 WHERE user_id = ?", [userId]);
  return json({ message: "All sessions revoked" });
}
