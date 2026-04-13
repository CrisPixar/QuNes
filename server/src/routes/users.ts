import { getDB }             from "../db/index.js";
import { requireAuth, json } from "../middleware/auth.js";

export function handleSearchUsers(req: Request): Response {
  const auth = requireAuth(req); if (auth instanceof Response) return auth;
  const q = new URL(req.url).searchParams.get("q")?.trim();
  if (!q || q.length < 2) return json({ error: "Query min 2 chars" }, 400);
  const users = getDB().query(
    "SELECT id, username, last_seen, is_scam, scam_reason FROM users WHERE username LIKE ? AND id != ? LIMIT 20"
  ).all(`%${q}%`, (auth as any).userId) as any[];
  return json(users.map(u => ({ id: u.id, username: u.username, lastSeen: u.last_seen, isScam: u.is_scam === 1, scamReason: u.scam_reason ?? null })));
}

export function handleGetUser(req: Request, userId: string): Response {
  const auth = requireAuth(req); if (auth instanceof Response) return auth;
  const u = getDB().query(
    "SELECT id, username, last_seen, is_scam, scam_reason, created_at FROM users WHERE id = ?"
  ).get(userId) as any;
  if (!u) return json({ error: "User not found" }, 404);
  return json({ id: u.id, username: u.username, lastSeen: u.last_seen, isScam: u.is_scam === 1, scamReason: u.scam_reason ?? null, createdAt: u.created_at });
}
