import { MAX_PASSWORD_LENGTH, MAX_USERNAME_LENGTH } from "../constants.js";

const USERNAME_PATTERN = /^[a-zA-Z0-9_-]+$/;

export function normalizeUsername(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const username = value.trim().toLowerCase();
  if (username.length < 3 || username.length > MAX_USERNAME_LENGTH) return null;
  if (!USERNAME_PATTERN.test(username)) return null;
  return username;
}

export function normalizePassword(value: unknown): string | null {
  if (typeof value !== "string") return null;
  if (value.length < 8 || value.length > MAX_PASSWORD_LENGTH) return null;
  return value;
}

export function textWithin(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const text = value.trim();
  return text.length > 0 && text.length <= max ? text : null;
}

export function positiveInteger(value: string | null, fallback: number, max: number): number {
  const parsed = Number.parseInt(value ?? "", 10);
  if (!Number.isInteger(parsed) || parsed < 1) return fallback;
  return Math.min(parsed, max);
}

export function isSafeKey(value: unknown, max = 128): value is string {
  return typeof value === "string" && value.length > 0 && value.length <= max;
}
