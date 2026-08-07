package com.qns.data.repository;

import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;

import com.qns.data.crypto.IdentityStore;
import com.qns.data.local.AppDatabase;
import com.qns.data.local.PrefsStore;
import com.qns.data.remote.ApiService;
import com.qns.data.remote.ServerRepository;
import com.qns.data.remote.TokenStore;
import com.qns.data.remote.WebSocketClient;
import com.qns.data.remote.model.AuthRequest;
import com.qns.data.remote.model.AuthResponse;
import com.qns.data.remote.model.SessionInfo;
import com.qns.utils.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

@Singleton
public class AuthRepository {
    private final ApiService api;
    private final ServerRepository servers;
    private final WebSocketClient webSocket;
    private final TokenStore tokenStore;
    private final IdentityStore identityStore;
    private final AppDatabase database;
    private final PrefsStore prefs;

    @Inject
    public AuthRepository(
        ApiService api,
        ServerRepository servers,
        WebSocketClient webSocket,
        TokenStore tokenStore,
        IdentityStore identityStore,
        AppDatabase database,
        PrefsStore prefs
    ) {
        this.api = api;
        this.servers = servers;
        this.webSocket = webSocket;
        this.tokenStore = tokenStore;
        this.identityStore = identityStore;
        this.database = database;
        this.prefs = prefs;
    }

    public Single<AuthResponse> login(String username, String password) {
        return api.login(
                servers.current().api("api/auth/login"),
                new AuthRequest(username, password)
            )
            .subscribeOn(Schedulers.io())
            .flatMap(response -> saveTokens(response)
                .andThen(Single.fromCallable(() -> {
                    tokenStore.set(response.accessToken);
                    return identityStore.publicKeys();
                }).onErrorReturnItem(Collections.<String, Object>emptyMap()))
                .flatMap(keys -> {
                    Map<String, Object> body = new java.util.HashMap<>();
                    body.put("publicKeys", keys);
                    return api.uploadIdentityKeys(
                        servers.current().api("api/keys/identity"), body
                    ).onErrorReturnItem(Collections.<String, Object>emptyMap());
                })
                .flatMap(ignored -> Single.fromCallable(() -> {
                    if (response.accessToken != null) webSocket.connect(response.accessToken);
                    return response;
                })));
    }

    public Single<AuthResponse> register(String username, String password) {
        return Single.fromCallable(() -> new AuthRequest(username, password, identityStore.publicKeys()))
            .subscribeOn(Schedulers.io())
            .flatMap(request -> api.register(servers.current().api("api/auth/register"), request))
            .flatMap(ignored -> login(username, password));
    }

    public Completable logout() {
        return prefs.first().flatMapCompletable(preferences -> {
            String refresh = preferences.get(PreferencesKeys.stringKey(Constants.PREF_REFRESH_TOKEN));
            Single<Map<String, String>> request = refresh == null || refresh.isEmpty()
                ? Single.just(Map.of())
                : api.logout(
                    servers.current().api("api/auth/logout"),
                    Map.of("refreshToken", refresh)
                ).onErrorReturnItem(Map.of());
            return request.ignoreElement().andThen(clearLocalUserDataOnLogout()).andThen(clearLocalSession());
        }).subscribeOn(Schedulers.io());
    }

    public Flowable<Boolean> observeLoggedIn() {
        return prefs.flow().map(preferences -> {
            String token = preferences.get(PreferencesKeys.stringKey(Constants.PREF_ACCESS_TOKEN));
            return token != null && !token.isEmpty();
        });
    }

    public Flowable<String> observeRole() {
        return prefs.flow().map(preferences -> {
            String role = preferences.get(PreferencesKeys.stringKey(Constants.PREF_USER_ROLE));
            return role == null ? "user" : role;
        });
    }

    public Flowable<Boolean> observeBetaTester() {
        return prefs.flow().map(preferences -> Boolean.TRUE.equals(preferences.get(PreferencesKeys.booleanKey(Constants.PREF_BETA_TESTER))));
    }

    public Single<String> getAccessToken() {
        return prefs.first().map(preferences -> {
            String token = preferences.get(PreferencesKeys.stringKey(Constants.PREF_ACCESS_TOKEN));
            return token == null ? "" : token;
        });
    }

    public Completable restoreSession() {
        return getRefreshToken()
            .flatMap(refresh -> {
                if (refresh == null || refresh.isEmpty()) return Single.just("");
                return api.refresh(
                    servers.current().api("api/auth/refresh"),
                    Map.of("refreshToken", refresh)
                )
                .flatMap(response -> saveTokens(response)
                    .andThen(Single.fromCallable(() -> {
                        tokenStore.set(response.accessToken);
                        if (response.accessToken != null) webSocket.connect(response.accessToken);
                        return response.accessToken;
                    })))
                .onErrorReturnItem("");
            })
            .flatMap(token -> {
                if (token != null && !token.isEmpty()) return Single.just(token);
                return getAccessToken().doOnSuccess(saved -> {
                    if (saved != null && !saved.isEmpty()) webSocket.connect(saved);
                });
            })
            .ignoreElement()
            .subscribeOn(Schedulers.io());
    }

    public Single<String> getCurrentUserId() {
        return prefs.first().map(preferences -> {
            String value = preferences.get(PreferencesKeys.stringKey(Constants.PREF_USER_ID));
            return value == null ? "" : value;
        });
    }

    public Single<String> getRefreshToken() {
        return prefs.first().map(preferences -> {
            String token = preferences.get(PreferencesKeys.stringKey(Constants.PREF_REFRESH_TOKEN));
            return token == null ? "" : token;
        });
    }

    public Single<List<SessionInfo>> getSessions() {
        return api.getSessions(servers.current().api("api/auth/sessions"))
            .subscribeOn(Schedulers.io())
            .map(raw -> {
                List<SessionInfo> result = new ArrayList<>();
                for (Map<String, Object> item : raw) {
                    result.add(new SessionInfo(
                        string(item, "id"),
                        string(item, "ip"),
                        string(item, "userAgent"),
                        number(item, "createdAt"),
                        number(item, "expiresAt")
                    ));
                }
                return result;
            });
    }

    public Completable revokeSession(String id) {
        return api.revokeSession(servers.current().api("api/auth/sessions/" + id))
            .ignoreElement()
            .subscribeOn(Schedulers.io());
    }

    public Completable revokeAllSessions() {
        return api.revokeAllSessions(servers.current().api("api/auth/sessions"))
            .ignoreElement()
            .subscribeOn(Schedulers.io());
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static long number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private Completable saveTokens(AuthResponse response) {
        return prefs.saveTokens(response);
    }

    public Completable clearLocalUserDataOnLogout() {
        return database.messageDao().clearAll()
            .andThen(database.chatDao().clearAll())
            .andThen(database.ratchetSessionDao().clearAll())
            .subscribeOn(Schedulers.io());
    }

    private Completable clearLocalSession() {
        tokenStore.clear();
        webSocket.disconnect();
        return prefs.clearSession();
    }
}
