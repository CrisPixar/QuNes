package com.qns.utils;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

import okhttp3.ResponseBody;
import retrofit2.HttpException;

/**
 * Единый маппер HTTP/сетевых ошибок в понятные пользователю сообщения.
 * Пытается прочитать тело ошибки сервера в формате { "error": "...", "code": "..." }.
 */
public final class ErrorMapper {
    private ErrorMapper() {}

    public static String message(Throwable error) {
        return message(error, false);
    }

    /** Для экранов логина/регистрации: 401 = неверные учётные данные. */
    public static String messageForAuth(Throwable error) {
        return message(error, true);
    }

    private static String message(Throwable error, boolean authContext) {
        if (error == null) return "Неизвестная ошибка";
        if (error instanceof HttpException) {
            HttpException http = (HttpException) error;
            String serverMessage = readServerError(http);
            switch (http.code()) {
                case 400: return notBlank(serverMessage) ? serverMessage : "Неверный запрос";
                case 401: return authContext
                    ? "Неверный логин или пароль"
                    : "Сессия истекла. Войдите снова.";
                case 403: return notBlank(serverMessage) ? serverMessage : "Нет доступа";
                case 404: return "Не найдено";
                case 409: return notBlank(serverMessage) ? serverMessage : "Конфликт данных, например имя занято";
                case 429: return "Слишком много запросов. Попробуйте позже";
                default:  return "Ошибка сервера: " + http.code();
            }
        }
        if (error instanceof SocketTimeoutException) return "Сервер не отвечает. Проверьте соединение";
        if (error instanceof UnknownHostException) return "Не удалось найти сервер";
        if (error instanceof SSLException) return "Ошибка защищённого соединения";
        if (error.getMessage() == null) return "Ошибка операции";
        return error.getMessage();
    }

    /** Достаёт поле "error" из JSON-тела ошибки, не бросая исключений. */
    private static String readServerError(HttpException http) {
        try {
            ResponseBody body = http.response() == null ? null : http.response().errorBody();
            if (body == null) return null;
            String text = body.string();
            int start = text.indexOf("\"error\"");
            if (start < 0) return null;
            int colon = text.indexOf(':', start);
            if (colon < 0) return null;
            int valueStart = text.indexOf('"', colon + 1);
            if (valueStart < 0) return null;
            int valueEnd = text.indexOf('"', valueStart + 1);
            if (valueEnd < 0) return null;
            return text.substring(valueStart + 1, valueEnd);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
