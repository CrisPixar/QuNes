import { ed25519 } from "@noble/curves/ed25519";
import { hexToBytes } from "@noble/hashes/utils";
import { JWT_SECRET_SEED, JWT_ACCESS_EXPIRY_SEC, JWT_REFRESH_EXPIRY_SEC } from "../constants.js";
import { toBase64url, fromBase64url, generateId } from "../crypto/index.js";

export type TokenType = "access" | "refresh";

export interface JwtPayload {
  sub: string;
  username: string;
  role: string;
  type: TokenType;
  jti: string;
  iat: number;
  exp: number;
}

function getKeyPair(): { privateKey: Uint8Array; publicKey: Uint8Array } {
  if (!/^[0-9a-f]{64}$/.test(JWT_SECRET_SEED)) {
    throw new Error("JWT_SECRET_SEED must contain exactly 64 hexadecimal characters");
  }
  const privateKey = hexToBytes(JWT_SECRET_SEED);
  return { privateKey, publicKey: ed25519.getPublicKey(privateKey) };
}

function encode(payload: JwtPayload, privateKey: Uint8Array): string {
  const encoder = new TextEncoder();
  const header = toBase64url(encoder.encode(JSON.stringify({ alg: "EdDSA", typ: "JWT" })));
  const body = toBase64url(encoder.encode(JSON.stringify(payload)));
  const input = `${header}.${body}`;
  const signature = ed25519.sign(encoder.encode(input), privateKey);
  return `${input}.${toBase64url(signature)}`;
}

function sign(userId: string, username: string, role: string, type: TokenType, ttl: number): string {
  const { privateKey } = getKeyPair();
  const now = Math.floor(Date.now() / 1000);
  return encode({
    sub: userId,
    username,
    role,
    type,
    jti: generateId(),
    iat: now,
    exp: now + ttl,
  }, privateKey);
}

export function signAccessToken(userId: string, username: string, role: string): string {
  return sign(userId, username, role, "access", JWT_ACCESS_EXPIRY_SEC);
}

export function signRefreshToken(userId: string, username: string, role: string): string {
  return sign(userId, username, role, "refresh", JWT_REFRESH_EXPIRY_SEC);
}

export function verifyToken(token: string, expectedType?: TokenType): JwtPayload {
  const { publicKey } = getKeyPair();
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("Invalid JWT format");
  const [header, body, signature] = parts;
  const payload = JSON.parse(new TextDecoder().decode(fromBase64url(body))) as JwtPayload;
  if (payload.type !== "access" && payload.type !== "refresh") throw new Error("Invalid token type");
  if (expectedType && payload.type !== expectedType) throw new Error("Unexpected token type");
  const valid = ed25519.verify(
    fromBase64url(signature),
    new TextEncoder().encode(`${header}.${body}`),
    publicKey,
  );
  if (!valid) throw new Error("Invalid signature");
  if (!Number.isInteger(payload.exp) || payload.exp <= Math.floor(Date.now() / 1000)) {
    throw new Error("Token expired");
  }
  return payload;
}

export { generateId };
