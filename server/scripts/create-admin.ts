import { getDB }              from "../src/db/index.js";
import { hashPassword, generateId } from "../src/crypto/index.js";
import { ADMIN_USERNAME, ADMIN_PASSWORD, JWT_SECRET_SEED } from "../src/constants.js";

if (!/^[0-9a-f]{64}$/.test(JWT_SECRET_SEED)) {
  console.error("JWT_SECRET_SEED must contain exactly 64 hexadecimal characters.");
  process.exit(1);
}
if (!ADMIN_PASSWORD || ADMIN_PASSWORD.length < 12) {
  console.error("❌  ADMIN_PASSWORD must be at least 12 characters.");
  console.error("    Usage: ADMIN_PASSWORD=StrongPass123! bun run scripts/create-admin.ts");
  process.exit(1);
}
const db = getDB();
const existing = db.query("SELECT id FROM users WHERE username = ?").get(ADMIN_USERNAME) as any;
if (existing) {
  db.run("UPDATE users SET role = 'admin', is_root_admin = 1, password_hash = ? WHERE username = ?",
    [await hashPassword(ADMIN_PASSWORD), ADMIN_USERNAME]);
  console.log(`✅  Admin updated: ${ADMIN_USERNAME}`);
} else {
  const id = generateId();
  db.run("INSERT INTO users (id,username,password_hash,role,is_root_admin,created_at) VALUES (?,?,?,'admin',1,?)",
    [id, ADMIN_USERNAME, await hashPassword(ADMIN_PASSWORD), Date.now()]);
  console.log(`✅  Admin created: ${ADMIN_USERNAME}  (id: ${id})`);
}
process.exit(0);
