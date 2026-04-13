// ================================================================
//  QNS SERVER CONSTANTS
//  Все магические числа собраны здесь для удобства аудита.
// ================================================================

export const PORT                      = parseInt(process.env.PORT ?? "3000");
export const DB_PATH                   = process.env.DB_PATH ?? "./data/qns.db";

// JWT — алгоритм EdDSA (Ed25519)
export const JWT_SECRET_SEED           = process.env.JWT_SECRET_SEED ?? "";
export const JWT_ACCESS_EXPIRY_SEC     = 15 * 60;            // 15 минут
export const JWT_REFRESH_EXPIRY_SEC    = 30 * 24 * 60 * 60;  // 30 дней

// Argon2id — OWASP 2024 рекомендации
export const ARGON2_MEMORY_COST        = 65536; // 64 MB в KiB
export const ARGON2_TIME_COST          = 3;
export const ARGON2_PARALLELISM        = 4;
export const ARGON2_HASH_LENGTH        = 32;

// One-time prekeys
export const PREKEYS_INITIAL_COUNT     = 100;
export const PREKEYS_LOW_THRESHOLD     = 10;

// Double Ratchet
export const RATCHET_MAX_SKIP          = 1000;
export const RATCHET_ROTATION_MSGS     = 100;
export const RATCHET_ROTATION_MS       = 24 * 60 * 60 * 1000;

// Rate limiting
export const RATE_LIMIT_HTTP_RPS       = 10;   // запросов/сек на IP
export const RATE_LIMIT_WS_PER_MIN     = 100;  // WS сообщений/мин на user

// Pagination
export const MESSAGES_PAGE_SIZE        = 50;

// Admin
export const ADMIN_USERNAME            = process.env.ADMIN_USERNAME ?? "admin";
export const ADMIN_PASSWORD            = process.env.ADMIN_PASSWORD ?? "";

// CORS
export const ALLOWED_ORIGINS           = (process.env.ALLOWED_ORIGINS ?? "*").split(",");
