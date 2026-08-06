package com.qns.utils;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

import retrofit2.HttpException;

public final class ErrorMapper {
    private ErrorMapper() {}

    public static String message(Throwable error) {
        if (error == null) return "Неизвестная ошибка";
        if (error instanceof HttpException) {
            switch (((HttpException) error).code()) {
                case 400: return "Неверный запрос";
                case 401: return "Сессия истекла. Войдите снова.";
                case 403: return "Недостаточно прав";
                case 404: return "Не найдено";
                case 409: return "Конфликт данных";
                case 429: return "Слишком много запросов";
                default: return "Ошибка сервера: " + ((HttpException) error).code();
            }
        }
        if (error instanceof SocketTimeoutException) return "Сервер не отвечает вовремя";
        if (error instanceof UnknownHostException) return "Не удалось найти сервер";
        if (error instanceof SSLException) return "Ошибка защищённого соединения";
        return error.getMessage() == null ? "Ошибка операции" : error.getMessage();
    }
}
