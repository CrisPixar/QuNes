// ================================================================
//  JWT — EdDSA (Ed25519)
//  Access token:  15 минут
//  Refresh token: 30 дней
//  Формат: base64url(header).base64url(payload).base64url(sig)
// ================================================================

import { ed25519 }    from "@noble/curves/ed25519";
import { hexToBytes } from "@noble/hashes/utils";
import {
  JWT_SECRET_SEED, JWT_ACCESS_EXPIRY_SEC, JWT_REFRESH_EXPIRY_SEC,
} from "../constants.js";
import { toBase64url, fromBase64url, generateId } from "../crypto/index.js";

function getKeyPair(): { privateKey: Uint8Array; publicKey: Uint8Array } {
  if (!JWT_SECRET_SEED) throw new Error("JWT_SECRET_SEED not set!");
  const sk = hexToBytes(JWT_SECRET_SEED.padEnd(64, "0").slice(0, 64));
  return { privateKey: sk, publicKey: ed25519.getPublicKey(sk) };
}

export interface JwtPayload {
  sub:      string;  // user ID
  username: string;
  role:     string;
  jti:      string;  // уникальный ID токена
  iat:      number;
  exp:      number;
}

function encode(payload: JwtPayload, sk: Uint8Array): string {
  const enc = new TextEncoder();
  const h = toBase64url(enc.encode(JSON.stringify({ alg: "EdDSA", typ: "JWT" })));
  const p = toBase64url(enc.encode(JSON.stringify(payload)));
  const input = `${h}.${p}`;
  const sig = ed25519.sign(enc.encode(input), sk);
  return `${input}.${toBase64url(sig)}`;
}

export function signAccessToken(userId: string, username: string, role: string): string {
  const { privateKey } = getKeyPair();
  const now = Math.floor(Date.now() / 1000);
  return encode({ sub: userId, username, role, jti: generateId(), iat: now, exp: now + JWT_ACCESS_EXPIRY_SEC }, privateKey);
}

export function signRefreshToken(userId: string, username: string, role: string): string {
  const { privateKey } = getKeyPair();
  const now = Math.floor(Date.now() / 1000);
  return encode({ sub: userId, username, role, jti: generateId(), iat: now, exp: now + JWT_REFRESH_EXPIRY_SEC }, privateKey);
}

export function verifyToken(token: string): JwtPayload {
  const { publicKey } = getKeyPair();
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("Invalid JWT format");
  const [hb, pb, sb] = parts;
  const payload: JwtPayload = JSON.parse(new TextDecoder().decode(fromBase64url(pb)));
  const isValid = ed25519.verify(fromBase64url(sb), new TextEncoder().encode(`${hb}.${pb}`), publicKey);
  if (!isValid) throw new Error("Invalid signature");
  if (payload.exp < Math.floor(Date.now() / 1000)) throw new Error("Token expired");
  return payload;
}

export { generateId };
