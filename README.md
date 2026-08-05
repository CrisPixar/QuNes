QNS Messenger

QNS это Android клиент и Bun сервер для защищённого обмена сообщениями.

В проекте есть:

- интерфейс Android на Jetpack Compose и Material 3
- выбор сервера перед входом
- добавление собственного HTTPS или HTTP адреса для локальной разработки
- регистрация и вход
- поиск пользователей и создание личных чатов
- список чатов, контакты, сообщения, typing и read receipts
- зашифрованный конверт сообщения на клиенте
- локальная база Room с SQLCipher
- темы System, Light и Dark
- управление активными сессиями
- серверная панель администратора
- роли user и admin
- список пользователей, статистика, SCAM метки, отзыв сессий и удаление аккаунтов
- GitHub Actions для typecheck, тестов и сборки APK

Быстрый запуск сервера

```text
cd server
cp .env.example .env
bun install
```

Заполни `.env`:

```text
PORT=3000
DB_PATH=./data/qns.db
JWT_SECRET_SEED=64_hex_symbols
ADMIN_USERNAME=admin
ADMIN_PASSWORD=StrongPassword123!
ALLOWED_ORIGINS=http://localhost:3000,http://10.0.2.2:3000
```

Сгенерировать секрет:

```text
openssl rand -hex 32
```

Создать или обновить администратора:

```text
ADMIN_PASSWORD=StrongPassword123! bun run scripts/create-admin.ts
```

Запустить:

```text
bun run dev
```

Проверка:

```text
curl http://localhost:3000/health
bun run typecheck
bun test
```

Запуск Android

```text
./gradlew assembleDebug
```

Для эмулятора Android используй встроенный профиль `Локальный сервер`. Он обращается к `10.0.2.2:3000`.

Для реального устройства добавь профиль с адресом компьютера в локальной сети или с публичным HTTPS адресом.

Выбор сервера

На экране входа есть список серверов. Встроены локальный адрес и шаблон облачного адреса. Кнопка `Добавить сервер` сохраняет собственный профиль в настройках устройства. Для каждого профиля автоматически создаётся WebSocket адрес.

Если сервер изменён после входа, выйди из аккаунта и войди заново. Токены принадлежат выбранному серверу.

Панель администратора

Создай администратора через `server/scripts/create-admin.ts`. После входа с ролью `admin` в настройках появится панель администратора.

Доступны:

- статистика пользователей, сообщений, чатов и WebSocket соединений
- поиск и список пользователей
- отметка SCAM и причина
- отзыв всех сессий пользователя
- удаление аккаунта с защитой от удаления самого себя
- изменение роли или пароля через серверный API
- удаление сообщения или всех сообщений чата через API

Публичные HTTP маршруты

```text
POST   /api/auth/register
POST   /api/auth/login
POST   /api/auth/refresh
DELETE /api/auth/logout
GET    /health
```

Защищённые маршруты используют заголовок:

```text
Authorization: Bearer ACCESS_TOKEN
```

GitHub Actions

Workflow `.github/workflows/build.yml` запускается для push и pull request в `main`.

Проверки сервера:

- Bun install по `server/bun.lock`
- TypeScript typecheck
- Bun tests
- артефакт исходников сервера

Проверки Android:

- Gradle wrapper
- `assembleDebug`
- JVM tests
- артефакт debug APK
- release APK при наличии signing secrets

На tag формата `v1.0.0` создаётся GitHub Release с APK и архивом сервера.

Криптография

Приватные ключи устройства сохраняются в зашифрованном хранилище Android Keystore. Сервер хранит публичные ключи и зашифрованный payload сообщения. Пароли хешируются Argon2id. Access и refresh токены подписываются Ed25519. Double Ratchet и X3DH оставлены отдельным слоем для дальнейшей миграции уже созданных сессий.

Перед публичным запуском проверь схему обмена ключами на двух чистых устройствах. Не используй production секреты в `.env.example`.
