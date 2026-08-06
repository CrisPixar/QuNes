// Вызывается первым в API-тестах, чтобы задать изолированную БД и валидный
// JWT-секрет ДО импорта модулей сервера (ESM порядок импорта гарантирует это).
process.env.DB_PATH = process.env.DB_PATH_TEST || `./data/test_${Date.now()}_${Math.random().toString(36).slice(2, 8)}.db`;
process.env.JWT_SECRET_SEED =
  process.env.JWT_SECRET_SEED || "0000000000000000000000000000000000000000000000000000000000000001";
process.env.ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || "TestAdmin123!";
