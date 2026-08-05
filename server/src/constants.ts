const numberFromEnv = (name: string, fallback: number, minimum: number): number => {
  const value = Number.parseInt(process.env[name] ?? "", 10);
  return Number.isInteger(value) && value >= minimum ? value : fallback;
};

export const PORT = numberFromEnv("PORT", 3000, 1);
export const DB_PATH = process.env.DB_PATH?.trim() || "./data/qns.db";

export const JWT_SECRET_SEED = (process.env.JWT_SECRET_SEED ?? "").trim().toLowerCase();
export const JWT_ACCESS_EXPIRY_SEC = 15 * 60;
export const JWT_REFRESH_EXPIRY_SEC = 30 * 24 * 60 * 60;

export const ARGON2_MEMORY_COST = 65536;
export const ARGON2_TIME_COST = 3;
export const ARGON2_PARALLELISM = 4;
export const ARGON2_HASH_LENGTH = 32;

export const PREKEYS_INITIAL_COUNT = 100;
export const PREKEYS_LOW_THRESHOLD = 10;
export const RATCHET_MAX_SKIP = 1000;
export const RATCHET_ROTATION_MSGS = 100;
export const RATCHET_ROTATION_MS = 24 * 60 * 60 * 1000;

export const RATE_LIMIT_HTTP_RPS = 10;
export const RATE_LIMIT_WS_PER_MIN = 100;
export const MESSAGES_PAGE_SIZE = 50;
export const MAX_MESSAGE_PAYLOAD = 256 * 1024;
export const MAX_HEADER_SIZE = 16 * 1024;
export const MAX_USERNAME_LENGTH = 32;
export const MAX_PASSWORD_LENGTH = 256;

export const ADMIN_USERNAME = process.env.ADMIN_USERNAME?.trim() || "admin";
export const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD ?? "";

export const ALLOWED_ORIGINS = (process.env.ALLOWED_ORIGINS ?? "*")
  .split(",")
  .map((value) => value.trim())
  .filter(Boolean);
