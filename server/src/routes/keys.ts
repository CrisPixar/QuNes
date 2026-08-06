import { getDB } from "../db/index.js";
import { generateId } from "../crypto/index.js";
import { requireAuth, json } from "../middleware/auth.js";
import { PREKEYS_LOW_THRESHOLD } from "../constants.js";
import { isSafeKey } from "../utils/validation.js";

const MAX_PREKEYS = 100;
const MAX_KEY_SIZE = 16 * 1024;

export function handleGetPrekeys(req: Request, userId: string): Response {
  const auth = requireAuth(req);
  if (auth instanceof Response) return auth;
  const db = getDB();
  if (!db.query("SELECT id FROM users WHERE id = ?").get(userId)) return json({ error: "User not found" }, 404);
  const key = db.query(
    "SELECT id, public_key FROM user_keys WHERE user_id = ? AND key_type = 'one_time_prekey' AND used = 0 LIMIT 1",
  ).get(userId) as any;
  if (key) db.run("UPDATE user_keys SET used = 1 WHERE id = ?", [key.id]);
  const remaining = (db.query(
    "SELECT COUNT(*) as count FROM user_keys WHERE user_id = ? AND key_type = 'one_time_prekey' AND used = 0",
  ).get(userId) as any)?.count ?? 0;
  return json({
    prekey: key ? { id: key.id, publicKey: key.public_key } : null,
    remaining,
    lowPrekeys: remaining < PREKEYS_LOW_THRESHOLD,
  });
}

export async function handleUploadPrekeys(req: Request): Promise<Response> {
  const auth = requireAuth(req);
  if (auth instanceof Response) return auth;
  const body = await req.json().catch(() => null);
  if (!body || !Array.isArray(body.prekeys) || body.prekeys.length === 0) {
    return json({ error: "prekeys array required" }, 400);
  }
  if (body.prekeys.length > MAX_PREKEYS) return json({ error: "Max 100 prekeys per upload" }, 400);

  const db = getDB();
  const now = Date.now();
  let uploaded = 0;
  for (const key of body.prekeys) {
    if (!isSafeKey(key, MAX_KEY_SIZE)) continue;
    db.run(
      "INSERT INTO user_keys (id,user_id,key_type,public_key,created_at) VALUES (?,?,'one_time_prekey',?,?)",
      [generateId(), auth.userId, key, now],
    );
    uploaded++;
  }
  if (!uploaded) return json({ error: "No valid prekeys" }, 400);
  return json({ uploaded });
}

export async function handleUploadIdentityKeys(req: Request): Promise<Response> {
  const auth = requireAuth(req);
  if (auth instanceof Response) return auth;
  const body = await req.json().catch(() => null);
  const publicKeys = body?.publicKeys;
  if (!publicKeys || typeof publicKeys !== "object") return json({ error: "publicKeys object required" }, 400);

  const db = getDB();
  const allowed = ["identity_kem", "identity_dsa", "identity_x25519", "identity_ed25519", "signed_prekey"];
  let uploaded = 0;
  for (const keyType of allowed) {
    const value = publicKeys[keyType];
    const key = value && typeof value === "object" ? value.key : null;
    const signature = value && typeof value === "object" ? value.signature : null;
    if (!isSafeKey(key, MAX_KEY_SIZE)) continue;
    if (signature !== null && signature !== undefined && !isSafeKey(signature, MAX_KEY_SIZE)) continue;
    db.run("DELETE FROM user_keys WHERE user_id = ? AND key_type = ?", [auth.userId, keyType]);
    db.run(
      "INSERT INTO user_keys (id,user_id,key_type,public_key,signature,created_at) VALUES (?,?,?,?,?,?)",
      [generateId(), auth.userId, keyType, key, signature ?? null, Date.now()],
    );
    uploaded++;
  }
  const prekeys = Array.isArray(publicKeys.one_time_prekeys) ? publicKeys.one_time_prekeys.slice(0, MAX_PREKEYS) : [];
  for (const value of prekeys) {
    const key = typeof value === "string" ? value : value?.key;
    const id = typeof value === "object" && isSafeKey(value?.id, 128) ? value.id : generateId();
    if (!isSafeKey(key, MAX_KEY_SIZE)) continue;
    if (!db.query("SELECT id FROM user_keys WHERE id = ?").get(id)) {
      db.run("INSERT INTO user_keys (id,user_id,key_type,public_key,created_at) VALUES (?,?,'one_time_prekey',?,?)", [id, auth.userId, key, Date.now()]);
      uploaded++;
    }
  }
  if (!uploaded) return json({ error: "No valid identity keys" }, 400);
  return json({ uploaded });
}

export function handleGetKeyBundle(req: Request, targetId: string): Response {
  const auth = requireAuth(req);
  if (auth instanceof Response) return auth;
  const db = getDB();
  if (!db.query("SELECT id FROM users WHERE id = ?").get(targetId)) return json({ error: "User not found" }, 404);

  const keys = db.query(
    "SELECT id, key_type, public_key, signature FROM user_keys WHERE user_id = ? AND key_type != 'one_time_prekey'",
  ).all(targetId) as any[];
  const oneTime = db.query(
    "SELECT id, public_key FROM user_keys WHERE user_id = ? AND key_type = 'one_time_prekey' AND used = 0 LIMIT 1",
  ).get(targetId) as any;
  if (oneTime) db.run("UPDATE user_keys SET used = 1 WHERE id = ?", [oneTime.id]);

  const bundle: Record<string, unknown> = {};
  for (const key of keys) bundle[key.key_type] = { id: key.id, publicKey: key.public_key, signature: key.signature };
  if (oneTime) bundle.one_time_prekey = { id: oneTime.id, publicKey: oneTime.public_key };

  const remaining = (db.query(
    "SELECT COUNT(*) as count FROM user_keys WHERE user_id = ? AND key_type = 'one_time_prekey' AND used = 0",
  ).get(targetId) as any)?.count ?? 0;
  return json({ bundle, lowPrekeys: remaining < PREKEYS_LOW_THRESHOLD });
}
