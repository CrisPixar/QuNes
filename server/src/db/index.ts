import { Database } from "bun:sqlite";
import { DB_PATH } from "../constants.js";
import { SCHEMA } from "./schema.js";
import { mkdirSync } from "fs";
import { dirname } from "path";

let _db: Database | null = null;

export function getDB(): Database {
  if (_db) return _db;
  try { mkdirSync(dirname(DB_PATH), { recursive: true }); } catch {}
  _db = new Database(DB_PATH, { create: true });
  _db.exec(SCHEMA);
  console.log(`[DB] Connected: ${DB_PATH}`);
  return _db;
}

export function closeDB(): void {
  _db?.close();
  _db = null;
}
