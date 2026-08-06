import { getDB } from "../db/index.js";
import { requireAdmin, json } from "../middleware/auth.js";
import { hashPassword } from "../crypto/index.js";
import { normalizePassword, normalizeUsername, textWithin } from "../utils/validation.js";

function count(db: ReturnType<typeof getDB>, query: string, ...params: any[]): number {
  return Number((db.query(query).get(...params) as any)?.count ?? 0);
}

export function handleAdminStats(req: Request): Response {
  const auth = requireAdmin(req);
  if (auth instanceof Response) return auth;
  const db = getDB();
  return json({
    totalUsers: count(db, "SELECT COUNT(*) as count FROM users"),
    totalMessages: count(db, "SELECT COUNT(*) as count FROM messages WHERE deleted = 0"),
    totalChats: count(db, "SELECT COUNT(*) as count FROM chats"),
    scamUsers: count(db, "SELECT COUNT(*) as count FROM users WHERE is_scam = 1"),
    verifiedUsers: count(db, "SELECT COUNT(*) as count FROM users WHERE is_verified = 1"),
    betaTesters: count(db, "SELECT COUNT(*) as count FROM users WHERE is_beta_tester = 1"),
    activeSessions: count(db, "SELECT COUNT(*) as count FROM refresh_tokens WHERE revoked = 0 AND expires_at > ?", Date.now()),
    activeWs: count(db, "SELECT COUNT(*) as count FROM ws_sessions"),
    uptime: process.uptime(),
    timestamp: Date.now(),
  });
}

export function handleAdminGetUsers(req: Request): Response {
  const auth = requireAdmin(req);
  if (auth instanceof Response) return auth;
  const params = new URL(req.url).searchParams;
  const query = textWithin(params.get("q"), 64) ?? "";
  const pageValue = Number.parseInt(params.get("page") ?? "1", 10);
  const limitValue = Number.parseInt(params.get("limit") ?? "50", 10);
  const page = Number.isInteger(pageValue) && pageValue > 0 ? pageValue : 1;
  const limit = Number.isInteger(limitValue) && limitValue > 0 ? Math.min(limitValue, 100) : 50;
  const db = getDB();
  const users = db.query(`
    SELECT u.id, u.username, u.role, u.is_root_admin, u.is_verified, u.is_beta_tester, u.is_scam, u.scam_reason, u.last_ip, u.last_seen, u.created_at,
      (SELECT COUNT(*) FROM refresh_tokens rt WHERE rt.user_id = u.id AND rt.revoked = 0 AND rt.expires_at > ?) as active_sessions,
      (SELECT COUNT(*) FROM messages m WHERE m.sender_id = u.id AND m.deleted = 0) as message_count
    FROM users u
    WHERE u.username LIKE ? COLLATE NOCASE
    ORDER BY u.created_at DESC
    LIMIT ? OFFSET ?
  `).all(Date.now(), `%${query}%`, limit, (page - 1) * limit) as any[];
  const total = count(db, "SELECT COUNT(*) as count FROM users WHERE username LIKE ? COLLATE NOCASE", `%${query}%`);
  return json({
    users: users.map((user) => ({
      id: user.id,
      username: user.username,
      role: user.role,
      isRootAdmin: user.is_root_admin === 1,
      isVerified: user.is_verified === 1,
      isBetaTester: user.is_beta_tester === 1,
      isScam: user.is_scam === 1,
      scamReason: user.scam_reason,
      lastIp: user.last_ip,
      lastSeen: user.last_seen,
      createdAt: user.created_at,
      activeSessions: user.active_sessions,
      messageCount: user.message_count,
    })),
    total,
    page,
    limit,
  });
}

export function handleAdminGetUser(req: Request, userId: string): Response {
  const auth = requireAdmin(req);
  if (auth instanceof Response) return auth;
  const db = getDB();
  const user = db.query(
    "SELECT id, username, role, is_root_admin, is_verified, is_beta_tester, is_scam, scam_reason, last_ip, last_seen, created_at FROM users WHERE id = ?",
  ).get(userId) as any;
  if (!user) return json({ error: "User not found" }, 404);
  const sessions = db.query(
    "SELECT id, ip_address, user_agent, created_at, expires_at, revoked FROM refresh_tokens WHERE user_id = ? ORDER BY created_at DESC LIMIT 50",
  ).all(userId) as any[];
  const connections = db.query(
    "SELECT id, ip_address, user_agent, connected_at FROM ws_sessions WHERE user_id = ?",
  ).all(userId) as any[];
  return json({
    user: { ...user, isRootAdmin: user.is_root_admin === 1, isVerified: user.is_verified === 1, isBetaTester: user.is_beta_tester === 1, isScam: user.is_scam === 1, scamReason: user.scam_reason },
    sessions,
    wsConnections: connections,
  });
}

export async function handleAdminUpdateUser(req: Request, userId: string): Promise<Response> {
  const auth = requireAdmin(req);
  if (auth instanceof Response) return auth;
  const body = await req.json().catch(() => null);
  if (!body) return json({ error: "Invalid JSON" }, 400);
  const db = getDB();
  const target = db.query("SELECT id, is_root_admin FROM users WHERE id = ?").get(userId) as any;
  if (!target) return json({ error: "User not found" }, 404);

  const updates: string[] = [];
  const values: any[] = [];
  if (body.username !== undefined) {
    const username = normalizeUsername(body.username);
    if (!username) return json({ error: "Invalid username" }, 400);
    if (db.query("SELECT id FROM users WHERE username = ? COLLATE NOCASE AND id != ?").get(username, userId)) {
      return json({ error: "Username already taken" }, 409);
    }
    updates.push("username = ?");
    values.push(username);
  }
  if (body.password !== undefined) {
    const password = normalizePassword(body.password);
    if (!password) return json({ error: "Invalid password" }, 400);
    updates.push("password_hash = ?");
    values.push(await hashPassword(password));
  }
  if (body.role !== undefined) {
    if (!['user', 'admin'].includes(body.role)) return json({ error: "Invalid role" }, 400);
    if (target.is_root_admin === 1 && body.role !== "admin") return json({ error: "Root admin is protected" }, 403);
    if (userId === auth.userId && body.role !== "admin") return json({ error: "You cannot remove your own admin role" }, 400);
    updates.push("role = ?");
    values.push(body.role);
  }
  if (body.isVerified !== undefined) {
    if (typeof body.isVerified !== "boolean") return json({ error: "Invalid verified flag" }, 400);
    updates.push("is_verified = ?");
    values.push(body.isVerified ? 1 : 0);
  }
  if (body.isBetaTester !== undefined) {
    if (typeof body.isBetaTester !== "boolean") return json({ error: "Invalid beta tester flag" }, 400);
    updates.push("is_beta_tester = ?");
    values.push(body.isBetaTester ? 1 : 0);
  }
  if (!updates.length) return json({ error: "Nothing to update" }, 400);
  values.push(userId);
  db.run(`UPDATE users SET ${updates.join(", ")} WHERE id = ?`, values);
  return json({ message: "User updated" });
}

export function handleAdminDeleteUser(req: Request, userId: string): Response {
  const auth = requireAdmin(req);
  if (auth instanceof Response) return auth;
  if (userId === auth.userId) return json({ error: "You cannot delete your own account here" }, 400);
  const db = getDB();
  const user = db.query("SELECT id, username, is_root_admin FROM users WHERE id = ?").get(userId) as any;
  if (!user) return json({ error: "User not found" }, 404);
  if (user.is_root_admin === 1) return json({ error: "Root admin is protected" }, 403);
  db.run("DELETE FROM users WHERE id = ?", [userId]);
  return json({ message: "User deleted" });
}

export async function handleAdminSetScam(req: Request, userId: string): Promise<Response> {
  const auth = requireAdmin(req);
  if (auth instanceof Response) return auth;
  const body = await req.json().catch(() => null);
  if (!body) return json({ error: "Invalid JSON" }, 400);
  const db = getDB();
  const target = db.query("SELECT id, is_root_admin FROM users WHERE id = ?").get(userId) as any;
  if (!target) return json({ error: "User not found" }, 404);
  if (target.is_root_admin === 1) return json({ error: "Root admin is protected" }, 403);
  const isScam = body.isScam === true ? 1 : 0;
  const reason = isScam ? textWithin(body.reason, 512) : null;
  db.run("UPDATE users SET is_scam = ?, scam_reason = ? WHERE id = ?", [isScam, reason, userId]);
  return json({ message: isScam ? "User marked as SCAM" : "SCAM flag removed" });
}

export function handleAdminDeleteMessage(req: Request, messageId: string): Response {
  const auth = requireAdmin(req);
  if (auth instanceof Response) return auth;
  const db = getDB();
  if (!db.query("SELECT id FROM messages WHERE id = ?").get(messageId)) return json({ error: "Message not found" }, 404);
  db.run("UPDATE messages SET deleted = 1, deleted_by_admin = 1 WHERE id = ?", [messageId]);
  return json({ message: "Message deleted" });
}

export function handleAdminDeleteAllMessages(req: Request, chatId: string): Response {
  const auth = requireAdmin(req);
  if (auth instanceof Response) return auth;
  const db = getDB();
  if (!db.query("SELECT id FROM chats WHERE id = ?").get(chatId)) return json({ error: "Chat not found" }, 404);
  const result = db.run(
    "UPDATE messages SET deleted = 1, deleted_by_admin = 1 WHERE chat_id = ? AND deleted = 0",
    [chatId],
  );
  return json({ message: "All messages deleted", count: result.changes });
}

export function handleAdminRevokeUserSessions(req: Request, userId: string): Response {
  const auth = requireAdmin(req);
  if (auth instanceof Response) return auth;
  const target = getDB().query("SELECT is_root_admin FROM users WHERE id = ?").get(userId) as any;
  if (!target) return json({ error: "User not found" }, 404);
  if (target.is_root_admin === 1) return json({ error: "Root admin sessions are protected" }, 403);
  const result = getDB().run("UPDATE refresh_tokens SET revoked = 1 WHERE user_id = ?", [userId]);
  return json({ message: "All sessions revoked", count: result.changes });
}
