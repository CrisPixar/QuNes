// ================================================================
//  QNS CRYPTO UTILITIES
//
//  Слой 1: ML-KEM-1024 (Kyber, FIPS 203) — пост-квантовый KEM
//           ML-DSA-87  (Dilithium, FIPS 204) — пост-квантовая подпись
//  Слой 2: X25519 + Ed25519 — классическая гибридная схема
//  Итоговый сессионный ключ: HKDF(ML-KEM_secret XOR X25519_secret)
//  Слой 3: XChaCha20-Poly1305 (payload) + AES-256-GCM (хранение)
//  Слой 4: Double Ratchet + X3DH (Forward Secrecy)
//
//  Сервер использует только: Argon2id, BLAKE3, Ed25519 (JWT).
//  Всё остальное шифрование — строго на клиенте.
// ================================================================

import { ml_kem1024 }                   from "@noble/post-quantum/ml-kem";
import { ml_dsa87 }                     from "@noble/post-quantum/ml-dsa";
import { ed25519 }                      from "@noble/curves/ed25519";
import { blake3 }                       from "@noble/hashes/blake3";
import { argon2id }                     from "@noble/hashes/argon2";
import { hmac }                         from "@noble/hashes/hmac";
import { sha512 }                       from "@noble/hashes/sha2";
import { randomBytes, bytesToHex, hexToBytes } from "@noble/hashes/utils";
import {
  ARGON2_MEMORY_COST, ARGON2_TIME_COST,
  ARGON2_PARALLELISM, ARGON2_HASH_LENGTH,
} from "../constants.js";

// ---- Encoding ----
export function toBase64url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");
}
export function fromBase64url(s: string): Uint8Array {
  const p = s.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - s.length % 4) % 4);
  return Uint8Array.from(atob(p), c => c.charCodeAt(0));
}
export { bytesToHex, hexToBytes, randomBytes };

// ---- BLAKE3 (хэширование всего) ----
// Быстрее SHA-3, квантово-устойчив при 256-bit выводе.
export function hash(data: Uint8Array | string): Uint8Array {
  return blake3(typeof data === "string" ? new TextEncoder().encode(data) : data);
}
export function hashHex(data: Uint8Array | string): string {
  return bytesToHex(hash(data));
}

// ---- Argon2id (пароли) ----
// memory=64MB, time=3, parallelism=4 — OWASP 2024.
export async function hashPassword(password: string): Promise<string> {
  const salt = randomBytes(16);
  const h = argon2id(password, salt, {
    m: ARGON2_MEMORY_COST, t: ARGON2_TIME_COST,
    p: ARGON2_PARALLELISM, dkLen: ARGON2_HASH_LENGTH,
  });
  return `${bytesToHex(salt)}:${bytesToHex(h)}`;
}
export async function verifyPassword(password: string, stored: string): Promise<boolean> {
  try {
    const [sh, hh] = stored.split(":");
    if (!sh || !hh) return false;
    const h = argon2id(password, hexToBytes(sh), {
      m: ARGON2_MEMORY_COST, t: ARGON2_TIME_COST,
      p: ARGON2_PARALLELISM, dkLen: ARGON2_HASH_LENGTH,
    });
    return constantTimeEqual(h, hexToBytes(hh));
  } catch { return false; }
}

// ---- Constant-time compare (защита от timing attacks) ----
export function constantTimeEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a[i] ^ b[i];
  return d === 0;
}

// ---- HMAC-SHA512 (MAC для метаданных) ----
export function computeHmac(key: Uint8Array, data: Uint8Array): Uint8Array {
  return hmac(sha512, key, data);
}

// ---- UUID v4 ----
export function generateId(): string {
  const b = randomBytes(16);
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = bytesToHex(b);
  return [h.slice(0,8), h.slice(8,12), h.slice(12,16), h.slice(16,20), h.slice(20)].join("-");
}

// ---- Ed25519 (используется для JWT подписей) ----
export function generateEd25519KeyPair(seed?: Uint8Array) {
  const sk = seed ?? randomBytes(32);
  return { privateKey: sk, publicKey: ed25519.getPublicKey(sk) };
}
export function signEd25519(sk: Uint8Array, msg: Uint8Array): Uint8Array {
  return ed25519.sign(msg, sk);
}
export function verifyEd25519(pk: Uint8Array, msg: Uint8Array, sig: Uint8Array): boolean {
  try { return ed25519.verify(sig, msg, pk); } catch { return false; }
}

// ---- ML-KEM-1024 (Kyber) — только для клиента, сервер хранит pubkey ----
export function generateKemKeyPair() { return ml_kem1024.keygen(); }

// ---- ML-DSA-87 (Dilithium) — сервер верифицирует подписи клиента ----
export function generateDsaKeyPair() { return ml_dsa87.keygen(); }
export function verifyDsaSignature(pk: Uint8Array, msg: Uint8Array, sig: Uint8Array): boolean {
  try { return ml_dsa87.verify(pk, msg, sig); } catch { return false; }
}
