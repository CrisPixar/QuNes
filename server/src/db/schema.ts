// ================================================================
//  СХЕМА БД
//  Сервер хранит ТОЛЬКО зашифрованные blob'ы сообщений.
//  E2EE строго на клиенте — сервер физически не читает переписку.
// ================================================================

export const SCHEMA = `
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;
PRAGMA synchronous  = NORMAL;

CREATE TABLE IF NOT EXISTS users (
  id            TEXT PRIMARY KEY,
  username      TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,            -- Argon2id
  role          TEXT NOT NULL DEFAULT 'user',  -- 'user' | 'admin'
  is_scam       INTEGER NOT NULL DEFAULT 0,
  scam_reason   TEXT,
  last_ip       TEXT,
  last_seen     INTEGER,
  created_at    INTEGER NOT NULL
);

-- Только ПУБЛИЧНЫЕ ключи. Приватные никогда не покидают устройство.
CREATE TABLE IF NOT EXISTS user_keys (
  id          TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  key_type    TEXT NOT NULL,
  -- identity_kem | identity_dsa | identity_x25519 | identity_ed25519
  -- signed_prekey | one_time_prekey
  public_key  TEXT NOT NULL,
  signature   TEXT,
  used        INTEGER NOT NULL DEFAULT 0,
  created_at  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
  id          TEXT PRIMARY KEY,
  user_a_id   TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  user_b_id   TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  initialized INTEGER NOT NULL DEFAULT 0,
  created_at  INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id          TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  TEXT NOT NULL UNIQUE,  -- BLAKE3 хэш токена
  ip_address  TEXT,
  user_agent  TEXT,
  expires_at  INTEGER NOT NULL,
  created_at  INTEGER NOT NULL,
  revoked     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ws_sessions (
  id           TEXT PRIMARY KEY,
  user_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  ip_address   TEXT,
  user_agent   TEXT,
  connected_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS chats (
  id         TEXT PRIMARY KEY,
  type       TEXT NOT NULL,  -- 'direct' | 'group'
  name       TEXT,
  created_by TEXT NOT NULL REFERENCES users(id),
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_members (
  chat_id   TEXT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
  user_id   TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  joined_at INTEGER NOT NULL,
  PRIMARY KEY (chat_id, user_id)
);

-- Только зашифрованный payload + метаданные для маршрутизации
CREATE TABLE IF NOT EXISTS messages (
  id                TEXT PRIMARY KEY,
  chat_id           TEXT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
  sender_id         TEXT NOT NULL REFERENCES users(id),
  encrypted_payload TEXT NOT NULL,  -- XChaCha20-Poly1305 blob
  ratchet_header    TEXT,
  server_nonce      TEXT,
  signature         TEXT,
  payload_size      INTEGER,
  created_at        INTEGER NOT NULL,
  delivered         INTEGER NOT NULL DEFAULT 0,
  read              INTEGER NOT NULL DEFAULT 0,
  deleted           INTEGER NOT NULL DEFAULT 0,
  deleted_by_admin  INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_messages_chat    ON messages(chat_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_sender  ON messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_user_keys_user   ON user_keys(user_id, key_type);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens   ON refresh_tokens(user_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_chat_members     ON chat_members(user_id);
CREATE INDEX IF NOT EXISTS idx_users_username   ON users(username);
`;
