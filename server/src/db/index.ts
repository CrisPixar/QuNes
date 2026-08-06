import { Database } from "bun:sqlite";
import { mkdirSync } from "fs";
import { dirname } from "path";
import { ADMIN_USERNAME, DB_PATH } from "../constants.js";
import { SCHEMA } from "./schema.js";

let database: Database | null = null;

function columns(db: Database, table: string): Set<string> {
  return new Set((db.query(`PRAGMA table_info(${table})`).all() as any[]).map((row) => row.name));
}

function addColumn(db: Database, table: string, name: string, definition: string): void {
  if (!columns(db, table).has(name)) db.exec(`ALTER TABLE ${table} ADD COLUMN ${name} ${definition}`);
}

function migrate(db: Database): void {
  addColumn(db, "users", "is_root_admin", "INTEGER NOT NULL DEFAULT 0");
  addColumn(db, "users", "is_verified", "INTEGER NOT NULL DEFAULT 0");
  addColumn(db, "users", "is_beta_tester", "INTEGER NOT NULL DEFAULT 0");
  addColumn(db, "chats", "direct_key", "TEXT");
  addColumn(db, "messages", "client_message_id", "TEXT");
  addColumn(db, "messages", "protocol_version", "INTEGER NOT NULL DEFAULT 1");
  db.exec("CREATE UNIQUE INDEX IF NOT EXISTS idx_direct_key ON chats(direct_key) WHERE direct_key IS NOT NULL");
  db.exec("CREATE UNIQUE INDEX IF NOT EXISTS idx_client_message ON messages(sender_id, client_message_id) WHERE client_message_id IS NOT NULL");
  db.run("UPDATE users SET is_root_admin = 1 WHERE username = ? COLLATE NOCASE AND role = 'admin'", [ADMIN_USERNAME]);

  const directChats = db.query("SELECT id FROM chats WHERE type = 'direct' AND direct_key IS NULL").all() as any[];
  for (const chat of directChats) {
    const users = db.query("SELECT user_id FROM chat_members WHERE chat_id = ? ORDER BY user_id").all(chat.id) as any[];
    if (users.length !== 2) continue;
    try { db.run("UPDATE chats SET direct_key = ? WHERE id = ?", [[users[0].user_id, users[1].user_id].join(":"), chat.id]); } catch {}
  }
}

export function getDB(): Database {
  if (database) return database;
  mkdirSync(dirname(DB_PATH), { recursive: true });
  database = new Database(DB_PATH, { create: true });
  database.exec(SCHEMA);
  migrate(database);
  console.log(`[DB] Connected: ${DB_PATH}`);
  return database;
}

export function closeDB(): void {
  database?.close();
  database = null;
}
