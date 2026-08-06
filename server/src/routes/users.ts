import { getDB } from "../db/index.js";
import { requireAuth, json } from "../middleware/auth.js";
import { textWithin } from "../utils/validation.js";

export function handleSearchUsers(req: Request): Response {
  const auth = requireAuth(req);
  if (auth instanceof Response) return auth;
  const query = textWithin(new URL(req.url).searchParams.get("q"), 64);
  if (!query || query.length < 2) return json({ error: "Query must contain at least 2 characters" }, 400);
  const users = getDB().query(
    "SELECT id, username, last_seen, is_scam, scam_reason, is_verified FROM users WHERE username LIKE ? COLLATE NOCASE AND id != ? LIMIT 20",
  ).all(`%${query}%`, auth.userId) as any[];
  return json(users.map((user) => ({
    id: user.id,
    username: user.username,
    lastSeen: user.last_seen,
    isScam: user.is_scam === 1,
    scamReason: user.scam_reason ?? null,
    isVerified: user.is_verified === 1,
  })));
}

export function handleGetUser(req: Request, userId: string): Response {
  const auth = requireAuth(req);
  if (auth instanceof Response) return auth;
  const user = getDB().query(
    "SELECT id, username, last_seen, is_scam, scam_reason, is_verified, created_at FROM users WHERE id = ?",
  ).get(userId) as any;
  if (!user) return json({ error: "User not found" }, 404);
  return json({
    id: user.id,
    username: user.username,
    lastSeen: user.last_seen,
    isScam: user.is_scam === 1,
    scamReason: user.scam_reason ?? null,
    isVerified: user.is_verified === 1,
    createdAt: user.created_at,
  });
}
