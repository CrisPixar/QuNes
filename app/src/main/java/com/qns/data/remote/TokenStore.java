package com.qns.data.remote;

import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Держит access/refresh токены текущей сессии в памяти (быстрый доступ из OkHttp interceptor). */
@Singleton
public final class TokenStore {
    private final AtomicReference<String> accessToken = new AtomicReference<>("");
    private final AtomicReference<String> refreshToken = new AtomicReference<>("");

    @Inject
    public TokenStore() {}

    public void set(String token) {
        accessToken.set(token == null ? "" : token);
    }

    public void setRefreshToken(String token) {
        refreshToken.set(token == null ? "" : token);
    }

    public void clear() {
        accessToken.set("");
        refreshToken.set("");
    }

    public String get() {
        return accessToken.get();
    }

    public String getRefreshToken() {
        return refreshToken.get();
    }
}
