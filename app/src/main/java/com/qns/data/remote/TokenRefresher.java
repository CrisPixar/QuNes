package com.qns.data.remote;

import com.qns.data.local.PrefsStore;
import com.qns.data.remote.model.AuthResponse;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import retrofit2.HttpException;

/**
 * Выполняет синхронный refresh access-токена (вызывается из OkHttp Authenticator
 * на фоновом потоке). При успехе обновляет и память, и диск. Если refresh-токен
 * отсутствует или сервер вернул 401 (сессия отозвана) — сообщает о необходимости logout.
 */
@Singleton
public final class TokenRefresher {
    private final ApiService refreshApi;
    private final ServerRepository servers;
    private final PrefsStore prefs;
    private final TokenStore tokens;
    private final AuthEvents events;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private volatile long lastRefreshAt = 0L;

    @Inject
    public TokenRefresher(
        @Named("refresh") ApiService refreshApi,
        ServerRepository servers,
        PrefsStore prefs,
        TokenStore tokens,
        AuthEvents events
    ) {
        this.refreshApi = refreshApi;
        this.servers = servers;
        this.prefs = prefs;
        this.tokens = tokens;
        this.events = events;
    }

    /**
     * @return true если токен обновлён и запрос можно повторить.
     */
    public synchronized boolean tryRefresh() {
        // Защита от «горячего» цикла: если refresh уже выполнен недавно,
        // а сервер всё ещё возвращает 401 — не повторяем бесконечно.
        long now = System.currentTimeMillis();
        if (refreshing.get()) return false;
        if (now - lastRefreshAt < 5_000L) return false;

        refreshing.set(true);
        try {
            String refresh = tokens.getRefreshToken();
            if (refresh == null || refresh.isEmpty()) {
                events.requestLogout();
                return false;
            }
            AuthResponse response = refreshApi.refresh(
                servers.current().api("api/auth/refresh"),
                Map.of("refreshToken", refresh)
            ).blockingGet();
            if (response.accessToken == null || response.accessToken.isEmpty()) {
                events.requestLogout();
                return false;
            }
            prefs.saveTokens(response).blockingAwait();
            tokens.set(response.accessToken);
            tokens.setRefreshToken(response.refreshToken);
            lastRefreshAt = now;
            return true;
        } catch (HttpException error) {
            if (error.code() == 401) {
                // refresh-токен отозван/истёк — сессия мертва
                events.requestLogout();
            }
            return false;
        } catch (Throwable error) {
            // сетевая/иная ошибка — не выкидываем пользователя, просто не ретраим
            return false;
        } finally {
            refreshing.set(false);
        }
    }
}
