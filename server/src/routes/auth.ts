import { getDB } from "../db/index.js";
import { hashPassword, verifyPassword, generateId, hashHex } from "../crypto/index.js";
import { signAccessToken, signRefreshToken, verifyToken } from "../utils/jwt.js";
import { json, requireAuth } from "../middleware/auth.js";
import { getClientIp } from "../middleware/rateLimit.js";
import { isSafeKey, normalizePassword, normalizeUsername } from "../utils/validation.js";
import { JWT_REFRESH_EXPIRY_SEC } from "../constants.js";

const MAX_PUBLIC_KEY_SIZE = 16 * 1024;

function readJson(request: Request): Promise<any> {
  return request.json().catch(() => null);
}

function validPublicKey(value: unknown): value is { key: string; signature?: string } {
  if (!value || typeof value !== "object") return false;
  const entry = value as Record<string, unknown>;
  return isSafeKey(entry.key, MAX_PUBLIC_KEY_SIZE)
    && (entry.signature === undefined || isSafeKey(entry.signature, MAX_PUBLIC_KEY_SIZE));
}

export async function handleRegister(req: Request): Promise<Response> {
  const body = await readJson(req);
  if (!body) return json({ error: "Invalid JSON" }, 400);

  const username = normalizeUsername(body.username);
  const password = normalizePassword(body.password);
  if (!username) return json({ error: "Username must contain 3 to 32 letters, digits, _ or -" }, 400);
  if (!password) return json({ error: "Password must contain 8 to 256 characters" }, 400);

  const db = getDB();
  if (db.query("SELECT id FROM users WHERE username = ? COLLATE NOCASE").get(username)) {
    return json({ error: "Username already taken" }, 409);
  }

  const userId = generateId();
  const passwordHash = await hashPassword(password);
  const now = Date.now();
  db.run(
    "INSERT INTO users (id, username, password_hash, role, created_at) VALUES (?, ?, ?, 'user', ?)",
    [userId, username, passwordHash, now],
  );

  const publicKeys = body.publicKeys;
  const keyTypes = ["identity_kem", "identity_dsa", "identity_x25519", "identity_ed25519", "signed_prekey"];
  if (publicKeys && typeof publicKeys === "object") {
    for (const keyType of keyTypes) {
      const value = publicKeys[keyType];
      if (validPublicKey(value)) {
        db.run(
          "INSERT INTO user_keys (id,user_id,key_type,public_key,signature,created_at) VALUES (?,?,?,?,?,?)",
          [generateId(), userId, keyType, value.key, value.signature ?? null, now],
        );
      }
    }
    if (Array.isArray(publicKeys.one_time_prekeys)) {
      for (const key of publicKeys.one_time_prekeys.slice(0, 100)) {
        if (isSafeKey(key, MAX_PUBLIC_KEY_SIZE)) {
          db.run(
            "INSERT INTO user_keys (id,user_id,key_type,public_key,created_at) VALUES (?,?,'one_time_prekey',?,?)",
            [generateId(), userId, key, now],
          );
        }
      }
    }
  }

  return json({ userId, username }, 201);
}

export async function handleLogin(req: Request): Promise<Response> {
  const body = await readJson(req);
  if (!body) return json({ error: "Invalid JSON" }, 400);
  const username = normalizeUsername(body.username);
  const password = normalizePassword(body.password);
  if (!username || !password) return json({ error: "Invalid credentials" }, 401);

  const db = getDB();
  const user = db.query(
    "SELECT id, username, password_hash, role, is_scam FROM users WHERE username = ? COLLATE NOCASE",
  ).get(username) as any;

  if (!user) {
    await hashPassword("qns_dummy_timing_protection");
    return json({ error: "Invalid credentials" }, 401);
  }
  if (!await verifyPassword(password, user.password_hash)) return json({ error: "Invalid credentials" }, 401);

  const ip = getClientIp(req);
  const userAgent = req.headers.get("user-agent") ?? "";
  const now = Date.now();
  db.run("UPDATE users SET last_ip = ?, last_seen = ? WHERE id = ?", [ip, now, user.id]);

  const accessToken = signAccessToken(user.id, user.username, user.role);
  const refreshToken = signRefreshToken(user.id, user.username, user.role);
  const tokenHash = hashHex(new TextEncoder().encode(refreshToken));
  db.run(
    "INSERT INTO refresh_tokens (id,user_id,token_hash,ip_address,user_agent,expires_at,created_at) VALUES (?,?,?,?,?,?,?)",
    [generateId(), user.id, tokenHash, ip, userAgent.slice(0, 512), now + JWT_REFRESH_EXPIRY_SEC * 1000, now],
  );

  return json({
    accessToken,
    refreshToken,
    user: { id: user.id, username: user.username, role: user.role, isScam: user.is_scam === 1 },
  });
}

export async function handleRefresh(req: Request): Promise<Response> {
  const body = await readJson(req);
  if (!body || typeof body.refreshToken !== "string") return json({ error: "Refresh token required" }, 400);

  const refreshToken = body.refreshToken;
  let payload;
  try {
    payload = verifyToken(refreshToken, "refresh");
  } catch {
    return json({ error: "Invalid or expired refresh token" }, 401);
  }

  const db = getDB();
  const tokenHash = hashHex(new TextEncoder().encode(refreshToken));
  const stored = db.query(
    "SELECT * FROM refresh_tokens WHERE token_hash = ? AND revoked = 0 AND expires_at > ?",
  ).get(tokenHash, Date.now()) as any;
  if (!stored || stored.user_id !== payload.sub) return json({ error: "Invalid or expired refresh token" }, 401);

  const user = db.query("SELECT id, username, role FROM users WHERE id = ?").get(payload.sub) as any;
  if (!user) return json({ error: "User not found" }, 401);

  db.run("UPDATE refresh_tokens SET revoked = 1 WHERE id = ?", [stored.id]);
  const accessToken = signAccessToken(user.id, user.username, user.role);
  const nextRefreshToken = signRefreshToken(user.id, user.username, user.role);
  const nextHash = hashHex(new TextEncoder().encode(nextRefreshToken));
  const now = Date.now();
  db.run(
    "INSERT INTO refresh_tokens (id,user_id,token_hash,ip_address,user_agent,expires_at,created_at) VALUES (?,?,?,?,?,?,?)",
    [generateId(), user.id, nextHash, stored.ip_address, stored.user_agent, now + JWT_REFRESH_EXPIRY_SEC * 1000, now],
  );
  return json({ accessToken, refreshToken: nextRefreshToken });
}

export async function handleLogout(req: Request): Promise<Response> {
  const body = await readJson(req);
  if (body && typeof body.refreshToken === "string") {
    const hash = hashHex(new TextEncoder().encode(body.refreshToken));
    getDB().run("UPDATE refresh_tokens SET revoked = 1 WHERE token_hash = ?", [hash]);
  }
  return json({ message: "Logged out" });
}

export function handleGetSessions(req: Request): Response {
  const auth = requireAuth(req);
  if (auth instanceof Response) return auth;
  const sessions = getDB().query(
    "SELECT id, ip_address, user_agent, created_at, expires_at FROM refresh_tokens WHERE user_id = ? AND revoked = 0 AND expires_at > ? ORDER BY created_at DESC",
  ).all(auth.userId, Date.now()) as any[];
  return json(sessions.map((session) => ({
    id: session.id,
    ip: session.ip_address,
    userAgent: session.user_agent,
    createdAt: session.created_at,
    expiresAt: session.expires_at,
  })));
}

export function handleRevokeSession(req: Request, sessionId: string): Response {
  const auth = requireAuth(req);
  if (auth instanceof Response) return auth;
  const result = getDB().run(
    "UPDATE refresh_tokens SET revoked = 1 WHERE id = ? AND user_id = ?",
    [sessionId, auth.userId],
  );
  return result.changes ? json({ message: "Session revoked" }) : json({ error: "Session not found" }, 404);
}

export function handleRevokeAllSessions(req: Request): Response {
  const auth = requireAuth(req);
  if (auth instanceof Response) return auth;
  getDB().run("UPDATE refresh_tokens SET revoked = 1 WHERE user_id = ?", [auth.userId]);
  return json({ message: "All sessions revoked" });
}
