package com.qns.data.remote;

import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class TokenStore {
    private final AtomicReference<String> accessToken = new AtomicReference<>("");

    @Inject
    public TokenStore() {}

    public void set(String token) {
        accessToken.set(token == null ? "" : token);
    }

    public void clear() {
        accessToken.set("");
    }

    public String get() {
        return accessToken.get();
    }
}
