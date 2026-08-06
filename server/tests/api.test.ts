import "./helpers/setup.js";

import { describe, it, expect, beforeAll } from "bun:test";

import { getDB } from "../src/db/index.js";
import { hashPassword, generateId, randomBytes } from "../src/crypto/index.js";
import { signAccessToken } from "../src/utils/jwt.js";
import {
  handleAdminDeleteUser,
  handleAdminUpdateUser,
  handleAdminSetScam,
  handleAdminRevokeUserSessions,
} from "../src/routes/admin.js";
import { handleGetKeyBundle, handleGetPrekeys, handleUploadPrekeys } from "../src/routes/keys.js";
import { handleGetUser } from "../src/routes/users.js";
import { handleCreateChat } from "../src/routes/chats.js";

// ---- helpers ----
function headers(auth?: string): Headers {
  const h = new Headers();
  h.set("Content-Type", "application/json");
  if (auth) h.set("Authorization", "Bearer " + auth);
  return h;
}

function request(method: string, path: string, token?: string, body?: unknown): Request {
  return new Request("http://localhost" + path, {
    method,
    headers: headers(token),
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

interface CreateUserExtra {
  isRootAdmin?: boolean;
  isVerified?: boolean;
  isBetaTester?: boolean;
  isScam?: boolean;
  scamReason?: string;
}

async function createUser(username: string, role = "user", extra: CreateUserExtra = {}) {
  const db = getDB();
  const id = generateId();
  const passwordHash = await hashPassword("Password123!");
  db.run(
    `INSERT INTO users (id, username, password_hash, role, is_root_admin, is_verified, is_beta_tester, is_scam, scam_reason, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      id, username, passwordHash, role,
      extra.isRootAdmin ? 1 : 0,
      extra.isVerified ? 1 : 0,
      extra.isBetaTester ? 1 : 0,
      extra.isScam ? 1 : 0,
      extra.scamReason ?? null,
      Date.now(),
    ],
  );
  return { id, username };
}

function auth(userId: string, username: string, role = "user") {
  return signAccessToken(userId, username, role);
}

function toBase64(bytes: Uint8Array): string {
  return Buffer.from(bytes).toString("base64");
}

beforeAll(async () => {
  // Гарантируем root-админа из окружения для тестов, которые его защищают.
  const db = getDB();
  const existing = db.query("SELECT id FROM users WHERE username = ?").get("admin") as any;
  if (!existing) await createUser("admin", "admin", { isRootAdmin: true });
});

describe("unauthorized access", () => {
  it("rejects requests without a token", () => {
    const response = handleGetUser(request("GET", "/api/users/someid"), "someid");
    expect(response.status).toBe(401);
  });
});

describe("key upload validation", () => {
  it("rejects oversized keys", async () => {
    const u = await createUser("keyuser");
    const token = auth(u.id, u.username);
    const huge = toBase64(randomBytes(64 * 1024));
    const response = await handleUploadPrekeys(request("POST", "/api/keys/prekeys", token, { prekeys: [huge] }));
    expect(response.status).toBe(400);
  });

  it("rejects invalid (non-string) keys", async () => {
    const u = await createUser("keyuser2");
    const token = auth(u.id, u.username);
    const response = await handleUploadPrekeys(request("POST", "/api/keys/prekeys", token, { prekeys: [12345] }));
    expect(response.status).toBe(400);
  });
});

describe("one-time prekeys", () => {
  it("serves a prekey exactly once", async () => {
    const u = await createUser("opkuser");
    const db = getDB();
    const keyId = generateId();
    db.run(
      "INSERT INTO user_keys (id,user_id,key_type,public_key,created_at) VALUES (?,?,'one_time_prekey',?,?)",
      [keyId, u.id, toBase64(randomBytes(32)), Date.now()],
    );
    const token = auth(u.id, u.username);
    const first = handleGetPrekeys(request("GET", `/api/keys/prekeys/${u.id}`, token), u.id);
    expect(first.status).toBe(200);
    const firstBody = await first.json() as any;
    expect(firstBody.prekey).not.toBeNull();
    expect(firstBody.lowPrekeys).toBe(true); // 1 оставшийся после выдачи < порога 10

    const second = handleGetPrekeys(request("GET", `/api/keys/prekeys/${u.id}`, token), u.id);
    const secondBody = await second.json() as any;
    expect(secondBody.prekey).toBeNull();
  });

  it("reports lowPrekeys when the pool is below threshold", async () => {
    const u = await createUser("lowuser");
    const db = getDB();
    for (let i = 0; i < 3; i++) {
      db.run(
        "INSERT INTO user_keys (id,user_id,key_type,public_key,created_at) VALUES (?,?,'one_time_prekey',?,?)",
        [generateId(), u.id, toBase64(randomBytes(32)), Date.now()],
      );
    }
    const token = auth(u.id, u.username);
    const body = (await handleGetPrekeys(request("GET", `/api/keys/prekeys/${u.id}`, token), u.id).json()) as any;
    expect(body.remaining).toBeLessThan(10);
    expect(body.lowPrekeys).toBe(true);
  });
});

describe("key bundle", () => {
  it("returns identity, signed prekey and signature", async () => {
    const u = await createUser("bundleuser");
    const db = getDB();
    const now = Date.now();
    const identity = toBase64(randomBytes(32));
    const signed = toBase64(randomBytes(32));
    const sig = toBase64(randomBytes(64));
    db.run("INSERT INTO user_keys (id,user_id,key_type,public_key,signature,created_at) VALUES (?,?,'identity_x25519',?,NULL,?)", [generateId(), u.id, identity, now]);
    db.run("INSERT INTO user_keys (id,user_id,key_type,public_key,signature,created_at) VALUES (?,?,'identity_ed25519',?,NULL,?)", [generateId(), u.id, identity, now]);
    db.run("INSERT INTO user_keys (id,user_id,key_type,public_key,signature,created_at) VALUES (?,?,'signed_prekey',?,?,?)", [generateId(), u.id, signed, sig, now]);

    const caller = await createUser("caller");
    const token = auth(caller.id, caller.username);
    const response = handleGetKeyBundle(request("GET", `/api/keys/bundle/${u.id}`, token), u.id);
    expect(response.status).toBe(200);
    const body = await response.json() as any;
    expect(body.bundle.signed_prekey.signature).toBe(sig);
    expect(body.bundle.identity_x25519.publicKey).toBe(identity);
  });
});

describe("root admin protection", () => {
  it("cannot demote the root admin", async () => {
    const caller = await createUser("rootadmin_caller", "admin");
    const root = await createUser("rootadmin", "admin", { isRootAdmin: true });
    const token = auth(caller.id, caller.username, "admin");
    const response = await handleAdminUpdateUser(request("PUT", `/api/admin/users/${root.id}`, token, { role: "user" }), root.id);
    expect(response.status).toBe(403);
  });

  it("cannot delete the root admin", async () => {
    const caller = await createUser("rootadmin2_caller", "admin");
    const root = await createUser("rootadmin2", "admin", { isRootAdmin: true });
    const token = auth(caller.id, caller.username, "admin");
    const response = handleAdminDeleteUser(request("DELETE", `/api/admin/users/${root.id}`, token), root.id);
    expect(response.status).toBe(403);
  });

  it("cannot revoke root admin sessions", async () => {
    const root = await createUser("rootadmin3", "admin", { isRootAdmin: true });
    const token = auth(root.id, root.username, "admin");
    const response = handleAdminRevokeUserSessions(request("DELETE", `/api/admin/users/${root.id}/sessions`, token), root.id);
    expect(response.status).toBe(403);
  });

  it("allows demoting a non-root admin", async () => {
    const caller = await createUser("demoter", "admin");
    const target = await createUser("regadmin", "admin");
    const token = auth(caller.id, caller.username, "admin");
    const response = await handleAdminUpdateUser(request("PUT", `/api/admin/users/${target.id}`, token, { role: "user" }), target.id);
    expect(response.status).toBe(200);
  });
});

describe("verified and beta tester flags", () => {
  it("toggles verified and beta tester", async () => {
    const caller = await createUser("flagadmin", "admin");
    const target = await createUser("flaguser");
    const token = auth(caller.id, caller.username, "admin");
    const r1 = await handleAdminUpdateUser(request("PUT", `/api/admin/users/${target.id}`, token, { isVerified: true }), target.id);
    const r2 = await handleAdminUpdateUser(request("PUT", `/api/admin/users/${target.id}`, token, { isBetaTester: true }), target.id);
    expect(r1.status).toBe(200);
    expect(r2.status).toBe(200);
    const db = getDB();
    const row = db.query("SELECT is_verified, is_beta_tester FROM users WHERE id = ?").get(target.id) as any;
    expect(row.is_verified).toBe(1);
    expect(row.is_beta_tester).toBe(1);
  });
});

describe("scam reason", () => {
  it("persists and returns a scam reason", async () => {
    const caller = await createUser("scamadmin", "admin");
    const target = await createUser("scamuser");
    const token = auth(caller.id, caller.username, "admin");
    const set = await handleAdminSetScam(
      request("POST", `/api/admin/users/${target.id}/scam`, token, { isScam: true, reason: "Отправляет фишинг" }),
      target.id,
    );
    expect(set.status).toBe(200);

    const viewer = await createUser("scamviewer");
    const viewToken = auth(viewer.id, viewer.username);
    const get = await handleGetUser(request("GET", `/api/users/${target.id}`, viewToken), target.id);
    const body = await get.json() as any;
    expect(body.isScam).toBe(true);
    expect(body.scamReason).toBe("Отправляет фишинг");
  });
});

describe("direct chat uniqueness", () => {
  it("does not duplicate a direct chat", async () => {
    const a = await createUser("dc_a");
    const b = await createUser("dc_b");
    const token = auth(a.id, a.username);
    const first = await handleCreateChat(request("POST", "/api/chats", token, { type: "direct", memberIds: [b.id] }));
    const firstBody = await first.json() as any;
    expect(first.status).toBe(201);
    const second = await handleCreateChat(request("POST", "/api/chats", token, { type: "direct", memberIds: [b.id] }));
    const secondBody = await second.json() as any;
    expect(secondBody.chatId).toBe(firstBody.chatId);
    expect(secondBody.existing).toBe(true);
    const db = getDB();
    const count = db.query("SELECT COUNT(*) as c FROM chats WHERE direct_key = ?").get([a.id, b.id].sort().join(":")) as any;
    expect(Number(count.c)).toBe(1);
  });
});
