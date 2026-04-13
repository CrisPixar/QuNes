import { getDB }          from "../db/index.js";
import { generateId }     from "../crypto/index.js";
import { requireAuth, json } from "../middleware/auth.js";
import { PREKEYS_LOW_THRESHOLD } from "../constants.js";

export function handleGetPrekeys(req: Request, userId: string): Response {
  const auth = requireAuth(req); if (auth instanceof Response) return auth;
  const db = getDB();
  const pk = db.query(
    "SELECT id, public_key FROM user_keys WHERE user_id = ? AND key_type = 'one_time_prekey' AND used = 0 LIMIT 1"
  ).get(userId) as any;
  if (pk) db.run("UPDATE user_keys SET used = 1 WHERE id = ?", [pk.id]);
  const rem = (db.query(
    "SELECT COUNT(*) as c FROM user_keys WHERE user_id = ? AND key_type = 'one_time_prekey' AND used = 0"
  ).get(userId) as any)?.c ?? 0;
  return json({ prekey: pk ? { id: pk.id, publicKey: pk.public_key } : null, remaining: rem, lowPrekeys: rem < PREKEYS_LOW_THRESHOLD });
}

export async function handleUploadPrekeys(req: Request): Promise<Response> {
  const auth = requireAuth(req); if (auth instanceof Response) return auth;
  let body: any;
  try { body = await req.json(); } catch { return json({ error: "Invalid JSON" }, 400); }
  const { prekeys } = body;
  if (!Array.isArray(prekeys) || prekeys.length === 0) return json({ error: "prekeys array required" }, 400);
  if (prekeys.length > 100) return json({ error: "Max 100 prekeys per upload" }, 400);
  const db = getDB(); const now = Date.now();
  for (const pk of prekeys)
    if (typeof pk === "string" && pk.length > 0)
      db.run("INSERT INTO user_keys (id,user_id,key_type,public_key,created_at) VALUES (?,?,'one_time_prekey',?,?)",
        [generateId(), (auth as any).userId, pk, now]);
  return json({ uploaded: prekeys.length });
}

export function handleGetKeyBundle(req: Request, targetId: string): Response {
  const auth = requireAuth(req); if (auth instanceof Response) return auth;
  const db = getDB();
  const keys = db.query(
    "SELECT key_type, public_key, signature FROM user_keys WHERE user_id = ? AND key_type != 'one_time_prekey'"
  ).all(targetId) as any[];
  const otp = db.query(
    "SELECT id, public_key FROM user_keys WHERE user_id = ? AND key_type = 'one_time_prekey' AND used = 0 LIMIT 1"
  ).get(targetId) as any;
  if (otp) db.run("UPDATE user_keys SET used = 1 WHERE id = ?", [otp.id]);
  const bundle: Record<string, any> = {};
  for (const k of keys) bundle[k.key_type] = { publicKey: k.public_key, signature: k.signature };
  if (otp) bundle.one_time_prekey = { id: otp.id, publicKey: otp.public_key };
  const rem = (db.query(
    "SELECT COUNT(*) as c FROM user_keys WHERE user_id = ? AND key_type = 'one_time_prekey' AND used = 0"
  ).get(targetId) as any)?.c ?? 0;
  return json({ bundle, lowPrekeys: rem < PREKEYS_LOW_THRESHOLD });
}
