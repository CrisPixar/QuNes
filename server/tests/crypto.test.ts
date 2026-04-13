import { describe, it, expect } from "bun:test";
import {
  hash, hashHex, hashPassword, verifyPassword, constantTimeEqual,
  generateId, generateEd25519KeyPair, signEd25519, verifyEd25519,
  toBase64url, fromBase64url, randomBytes,
} from "../src/crypto/index.js";

describe("BLAKE3", () => {
  it("produces 32-byte output",        () => expect(hash("test").length).toBe(32));
  it("is deterministic",               () => expect(hashHex("hello")).toBe(hashHex("hello")));
  it("avalanche effect",               () => expect(hashHex("hello")).not.toBe(hashHex("Hello")));
  it("no collisions in 1k samples",    () => {
    const s = new Set(Array.from({ length: 1000 }, (_, i) => hashHex(`msg-${i}`)));
    expect(s.size).toBe(1000);
  });
});

describe("Argon2id", () => {
  it("random salt → different hashes", async () => {
    expect(await hashPassword("P@ss1")).not.toBe(await hashPassword("P@ss1"));
  });
  it("verifies correct password",      async () => expect(await verifyPassword("P@ss1", await hashPassword("P@ss1"))).toBe(true));
  it("rejects wrong password",         async () => expect(await verifyPassword("Wrong", await hashPassword("P@ss1"))).toBe(false));
  it("rejects malformed stored hash",  async () => expect(await verifyPassword("x", "notahash")).toBe(false));
}, { timeout: 60_000 });

describe("Constant-time compare", () => {
  it("equal arrays → true",   () => expect(constantTimeEqual(new Uint8Array([1,2,3]), new Uint8Array([1,2,3]))).toBe(true));
  it("diff value → false",    () => expect(constantTimeEqual(new Uint8Array([1,2,3]), new Uint8Array([1,2,4]))).toBe(false));
  it("diff length → false",   () => expect(constantTimeEqual(new Uint8Array([1,2]),   new Uint8Array([1,2,3]))).toBe(false));
});

describe("UUID v4", () => {
  it("valid v4 format",   () => expect(generateId()).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/));
  it("unique per call",   () => expect(new Set(Array.from({ length: 1000 }, generateId)).size).toBe(1000));
});

describe("Ed25519", () => {
  it("sign+verify round-trip", () => {
    const kp = generateEd25519KeyPair();
    const m  = new TextEncoder().encode("QNS test message");
    expect(verifyEd25519(kp.publicKey, m, signEd25519(kp.privateKey, m))).toBe(true);
  });
  it("rejects tampered message", () => {
    const kp  = generateEd25519KeyPair();
    const m   = new TextEncoder().encode("original");
    const sig = signEd25519(kp.privateKey, m);
    expect(verifyEd25519(kp.publicKey, new TextEncoder().encode("tampered"), sig)).toBe(false);
  });
  it("rejects wrong public key", () => {
    const kp1 = generateEd25519KeyPair(); const kp2 = generateEd25519KeyPair();
    const m   = new TextEncoder().encode("test");
    expect(verifyEd25519(kp2.publicKey, m, signEd25519(kp1.privateKey, m))).toBe(false);
  });
});

describe("Base64url", () => {
  it("round-trip",       () => { const b = randomBytes(64); expect(constantTimeEqual(b, fromBase64url(toBase64url(b)))).toBe(true); });
  it("URL-safe chars",   () => expect(toBase64url(randomBytes(128))).toMatch(/^[A-Za-z0-9\-_]*$/));
});
