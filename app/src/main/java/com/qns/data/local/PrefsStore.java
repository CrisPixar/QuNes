package com.qns.data.local;

import android.content.Context;

import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;

import com.qns.data.remote.TokenStore;
import com.qns.data.remote.model.AuthResponse;
import com.qns.utils.Constants;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * Единая точка доступа к preferences-хранилищу аккаунта.
 * Оба токена (access/refresh) зеркалируются в {@link TokenStore} для синхронного
 * чтения из OkHttp interceptor, поэтому после refresh приложение продолжает сессию
 * даже после перезапуска (новые токены сохраняются на диск).
 */
@Singleton
public final class PrefsStore {
    private final RxDataStore<Preferences> dataStore;

    @Inject
    public PrefsStore(@ApplicationContext Context context, TokenStore tokenStore) {
        this.dataStore = new RxPreferenceDataStoreBuilder(context, "qns_prefs").build();
        dataStore.data().subscribe(
            preferences -> {
                tokenStore.set(preferences.get(PreferencesKeys.stringKey(Constants.PREF_ACCESS_TOKEN)));
                tokenStore.setRefreshToken(preferences.get(PreferencesKeys.stringKey(Constants.PREF_REFRESH_TOKEN)));
            },
            ignored -> {
                tokenStore.clear();
            }
        );
    }

    public RxDataStore<Preferences> data() {
        return dataStore;
    }

    public Single<Preferences> first() {
        return dataStore.data().firstOrError();
    }

    /** Сохраняет токены и данные пользователя после логина/refresh. */
    public Completable saveTokens(AuthResponse response) {
        return dataStore.updateDataAsync(preferences -> {
            MutablePreferences mutable = preferences.toMutablePreferences();
            if (response.accessToken != null) {
                mutable.set(PreferencesKeys.stringKey(Constants.PREF_ACCESS_TOKEN), response.accessToken);
            }
            if (response.refreshToken != null) {
                mutable.set(PreferencesKeys.stringKey(Constants.PREF_REFRESH_TOKEN), response.refreshToken);
            }
            if (response.user != null) {
                if (response.user.id != null) {
                    mutable.set(PreferencesKeys.stringKey(Constants.PREF_USER_ID), response.user.id);
                }
                if (response.user.username != null) {
                    mutable.set(PreferencesKeys.stringKey(Constants.PREF_USERNAME), response.user.username);
                }
                mutable.set(
                    PreferencesKeys.stringKey(Constants.PREF_USER_ROLE),
                    response.user.role == null ? "user" : response.user.role
                );
                mutable.set(PreferencesKeys.booleanKey(Constants.PREF_BETA_TESTER), response.user.isBetaTester);
            }
            return Single.just(mutable);
        }).ignoreElement();
    }

    /** Очищает локальную сессию (токены и данные пользователя). */
    public Completable clearSession() {
        return dataStore.updateDataAsync(preferences -> {
            MutablePreferences mutable = preferences.toMutablePreferences();
            mutable.remove(PreferencesKeys.stringKey(Constants.PREF_ACCESS_TOKEN));
            mutable.remove(PreferencesKeys.stringKey(Constants.PREF_REFRESH_TOKEN));
            mutable.remove(PreferencesKeys.stringKey(Constants.PREF_USER_ID));
            mutable.remove(PreferencesKeys.stringKey(Constants.PREF_USERNAME));
            mutable.remove(PreferencesKeys.stringKey(Constants.PREF_USER_ROLE));
            mutable.remove(PreferencesKeys.booleanKey(Constants.PREF_BETA_TESTER));
            return Single.just(mutable);
        }).ignoreElement();
    }
}
