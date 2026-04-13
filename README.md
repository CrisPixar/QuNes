# QNS — Quantum Secure Messenger 🔐

## Быстрый старт — Сервер
```bash
cd server
cp .env.example .env          # заполните секреты
bun install
ADMIN_PASSWORD=StrongPass123! bun run scripts/create-admin.ts
bun run dev
```

## Быстрый старт — Android
```bash
./gradlew assembleDebug
# release:
export KEYSTORE_BASE64=$(base64 -w 0 qns.keystore)
./gradlew assembleRelease
```

## GitHub Secrets
| Secret | Как получить |
|--------|-------------|
| `JWT_SECRET_SEED` | `openssl rand -hex 32` |
| `KEYSTORE_BASE64` | `base64 -w 0 qns.keystore` |
| `KEY_ALIAS` | алиас ключа |
| `KEY_PASSWORD` | пароль ключа |
| `STORE_PASSWORD` | пароль keystore |
| `GRADLE_CACHE_KEY` | любая случайная строка |
