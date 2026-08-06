package com.qns.data.local;

/**
 * Чистая логика слияния сообщений при синхронизации (REST/WebSocket).
 * Гарантирует, что уже расшифрованный текст не затирается новым sync-проходом
 * и что ошибка расшифровки сохраняется для последующего отображения.
 * Отдельный класс — чтобы покрывать unit-тестами без Room/RxJava.
 */
public final class MessageMerge {
    private MessageMerge() {}

    public static final class Merged {
        public final String decryptedCache;
        public final String decryptionError;
        public final boolean decryptionFailed;
        public final boolean isMine;

        Merged(String decryptedCache, String decryptionError, boolean decryptionFailed, boolean isMine) {
            this.decryptedCache = decryptedCache;
            this.decryptionError = decryptionError;
            this.decryptionFailed = decryptionFailed;
            this.isMine = isMine;
        }
    }

    /**
     * @param hasExisting          был ли уже сохранён локальный вариант сообщения
     * @param existingDecrypted    сохранённый ранее расшифрованный текст (может быть null)
     * @param existingError        сохранённая ранее ошибка расшифровки (может быть null)
     * @param existingIsMine       признак «моё» из локальной записи
     * @param incomingDecrypted    расшифрованный текст из входящего события (может быть null)
     * @param incomingError        ошибка из входящего события (может быть null)
     * @param incomingIsMine       признак «моё» по server senderId
     */
    public static Merged decide(
        boolean hasExisting,
        String existingDecrypted,
        String existingError,
        boolean existingIsMine,
        String incomingDecrypted,
        String incomingError,
        boolean incomingIsMine
    ) {
        String decrypted = incomingDecrypted;
        if (hasExisting && existingDecrypted != null && decrypted == null) decrypted = existingDecrypted;
        String error = incomingError;
        if (hasExisting && existingError != null && error == null) error = existingError;
        boolean failed = decrypted == null && error != null && !error.isEmpty();
        boolean isMine = hasExisting ? (existingIsMine || incomingIsMine) : incomingIsMine;
        return new Merged(decrypted, error, failed, isMine);
    }
}
