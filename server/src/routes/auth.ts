// Регистрация: только username + password (публичные ключи опциональны)
import { getDB }                            from "../db/index.js";
import { hashPassword, verifyPassword, generateId, hashHex } from "../crypto/index.js";
import { signAccessToken, signRefreshToken, verifyToken }    from "../utils/jwt.js";
import { json }                             from "../middleware/auth.js";
import { getClientIp }                      from "../middleware/rateLimit.js";

export async function handleRegister(req: Request): Promise<Response> {
  let body: any;
  try { body = await req.json(); } catch { return json({ error: "Invalid JSON" }, 400); }

  const { username, password, publicKeys } = body;
  if (!username || typeof username !== "string" || username.length < 3 || username.length > 32)
    return json({ error: "Username: 3–32 chars" }, 400);
  if (!password || typeof password !== "string" || password.length < 8)
    return json({ error: "Password: min 8 chars" }, 400);
  if (!/^[a-zA-Z0-9_\-]+$/.test(username))
    return json({ error: "Username: only letters, digits, _ -" }, 400);

  const db = getDB();
  if (db.query("SELECT id FROM users WHERE username = ?").get(username))
    return json({ error: "Username already taken" }, 409);

  const userId = generateId();
  const passwordHash = await hashPassword(password);
  const now = Date.now();

  db.run(
    "INSERT INTO users (id, username, password_hash, role, created_at) VALUES (?, ?, ?, 'user', ?)",
    [userId, username, passwordHash, now]
  );

  // Опциональные публичные ключи (ML-KEM, ML-DSA, X25519, Ed25519, prekeys)
  if (publicKeys && typeof publicKeys === "object") {
    const keyTypes = ["identity_kem","identity_dsa","identity_x25519","identity_ed25519","signed_prekey"];
    for (const kt of keyTypes) {
      if (publicKeys[kt])
        db.run("INSERT INTO user_keys (id,user_id,key_type,public_key,signature,created_at) VALUES (?,?,?,?,?,?)",
          [generateId(), userId, kt, publicKeys[kt].key, publicKeys[kt].signature ?? null, now]);
    }
    if (Array.isArray(publicKeys.one_time_prekeys)) {
      for (const pk of publicKeys.one_time_prekeys.slice(0, 100))
        if (typeof pk === "string")
          db.run("INSERT INTO user_keys (id,user_id,key_type,public_key,created_at) VALUES (?,?,'one_time_prekey',?,?)",
            [generateId(), userId, pk, now]);
    }
  }

  console.log(`[AUTH] Registered: ${username} (${userId})`);
  return json({ userId, username }, 201);
}

export async function handleLogin(req: Request): Promise<Response> {
  let body: any;
  try { body = await req.json(); } catch { return json({ error: "Invalid JSON" }, 400); }

  const { username, password } = body;
  if (!username || !password) return json({ error: "Username and password required" }, 400);

  const db = getDB();
  const user = db.query(
    "SELECT id, username, password_hash, role, is_scam FROM users WHERE username = ?"
  ).get(username) as any;

  // Constant-time: фиктивная операция чтобы не раскрывать факт существования аккаунта
  if (!user) { await hashPassword("qns_dummy_timing_protection"); return json({ error: "Invalid credentials" }, 401); }
  if (!await verifyPassword(password, user.password_hash)) return json({ error: "Invalid credentials" }, 401);

  const ip = getClientIp(req);
  const ua = req.headers.get("user-agent") ?? "";
  const now = Date.now();

  db.run("UPDATE users SET last_ip = ?, last_seen = ? WHERE id = ?", [ip, now, user.id]);

  const accessToken  = signAccessToken(user.id, user.username, user.role);
  const refreshToken = signRefreshToken(user.id, user.username, user.role);
  const tokenHash    = hashHex(new TextEncoder().encode(refreshToken));

  db.run(
    "INSERT INTO refresh_tokens (id,user_id,token_hash,ip_address,user_agent,expires_at,created_at) VALUES (?,?,?,?,?,?,?)",
    [generateId(), user.id, tokenHash, ip, ua, now + JWT_REFRESH_EXPIRY_SEC * 1000, now]
  );

  console.log(`[AUTH] Login: ${username} from ${ip}`);
  return json({
    accessToken, refreshToken,
    user: { id: user.id, username: user.username, role: user.role, isScam: user.is_scam === 1 },
  });
}

// Импортируем константу для рефреш-токенов
import { JWT_REFRESH_EXPIRY_SEC } from "../constants.js";

export async function handleRefresh(req: Request): Promise<Response> {
  let body: any;
  try { body = await req.json(); } catch { return json({ error: "Invalid JSON" }, 400); }

  const { refreshToken } = body;
  if (!refreshToken) return json({ error: "Refresh token required" }, 400);

  const db = getDB();
  const tokenHash = hashHex(new TextEncoder().encode(refreshToken));
  const stored = db.query(
    "SELECT * FROM refresh_tokens WHERE token_hash = ? AND revoked = 0 AND expires_at > ?"
  ).get(tokenHash, Date.now()) as any;
  if (!stored) return json({ error: "Invalid or expired refresh token" }, 401);

  let payload: any;
  try { payload = verifyToken(refreshToken); } catch { return json({ error: "Invalid token" }, 401); }

  const user = db.query("SELECT id, username, role FROM users WHERE id = ?").get(payload.sub) as any;
  if (!user) return json({ error: "User not found" }, 401);

  // Token rotation: отзываем старый, выдаём новый
  db.run("UPDATE refresh_tokens SET revoked = 1 WHERE id = ?", [stored.id]);

  const newAccess  = signAccessToken(user.id, user.username, user.role);
  const newRefresh = signRefreshToken(user.id, user.username, user.role);
  const newHash    = hashHex(new TextEncoder().encode(newRefresh));
  const now        = Date.now();

  db.run(
    "INSERT INTO refresh_tokens (id,user_id,token_hash,ip_address,user_agent,expires_at,created_at) VALUES (?,?,?,?,?,?,?)",
    [generateId(), user.id, newHash, stored.ip_address, stored.user_agent, now + JWT_REFRESH_EXPIRY_SEC * 1000, now]
  );

  return json({ accessToken: newAccess, refreshToken: newRefresh });
}

export async function handleLogout(req: Request): Promise<Response> {
  let body: any;
  try { body = await req.json(); } catch { return json({ message: "Logged out" }); }
  if (body?.refreshToken) {
    const h = hashHex(new TextEncoder().encode(body.refreshToken));
    getDB().run("UPDATE refresh_tokens SET revoked = 1 WHERE token_hash = ?", [h]);
  }
  return json({ message: "Logged out" });
}
