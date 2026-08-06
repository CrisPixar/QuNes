package com.qns.data.remote;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * Глобальные события авторизации. Используется, чтобы из любого места
 * (OkHttp authenticator, WebSocket) инициировать принудительный logout при
 * истёкшей/отозванной сессии.
 */
@Singleton
public final class AuthEvents {
    private final PublishSubject<Boolean> forcedLogout = PublishSubject.create();

    @Inject
    public AuthEvents() {}

    public Observable<Boolean> forcedLogout() {
        return forcedLogout.hide();
    }

    /** Вызывается при неудачном refresh (сессия мертва). */
    public void requestLogout() {
        forcedLogout.onNext(true);
    }
}
