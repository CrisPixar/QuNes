# QuNes: Quantum-Secured Communication Ecosystem

## Структура проекта
- `qunes-android-app`: Клиентское приложение (Kotlin/Compose)
- `qunes-msg-server`: Node.js сервер сообщений и админ-панель
- `qunes-server-media`: Mediasoup SFU сервер для аудио/видео вызовов
- `qunes-core-crypto`: Нативные библиотеки (PQC)

## Быстрый запуск инфраструктуры
1. Скорректируйте параметры в `.env`.
2. Запустите стек:
   ```bash
   docker-compose up -d --build
   ```

## Админ-панель
- **URL:** `http://localhost:3001/white-admin`
- **Login:** Whitekiller
- **Password:** VtL5y05HIq9I

## Безопасность
Проект использует постквантовые алгоритмы Kyber-1024 и Dilithium-3. Все медиа-потоки защищены E2EE через механизм Insertable Streams.