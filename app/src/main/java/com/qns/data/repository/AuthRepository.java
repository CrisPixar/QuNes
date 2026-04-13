package com.qns.data.repository;

import android.content.Context;
import androidx.datastore.preferences.core.*;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;
import com.qns.data.remote.ApiService;
import com.qns.data.remote.model.*;
import com.qns.utils.Constants;
import javax.inject.Inject;
import javax.inject.Singleton;
import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.schedulers.Schedulers;

@Singleton
public class AuthRepository {
    private final ApiService api;
    private final RxDataStore<Preferences> ds;

    @Inject
    public AuthRepository(ApiService api, @ApplicationContext Context ctx) {
        this.api = api;
        this.ds  = new RxPreferenceDataStoreBuilder(ctx, "qns_prefs").build();
    }

    public Single<AuthResponse> login(String username, String password) {
        return api.login(new AuthRequest(username, password))
            .subscribeOn(Schedulers.io())
            .flatMap(r -> saveTokens(r).andThen(Single.just(r)));
    }

    public Single<AuthResponse> register(String username, String password) {
        return api.register(new AuthRequest(username, password))
            .subscribeOn(Schedulers.io())
            .flatMap(ignored -> login(username, password));
    }

    public Completable logout() {
        return ds.updateDataAsync(p -> {
            MutablePreferences m = p.toMutablePreferences();
            for (String k : new String[]{
                Constants.PREF_ACCESS_TOKEN, Constants.PREF_REFRESH_TOKEN,
                Constants.PREF_USER_ID, Constants.PREF_USERNAME, Constants.PREF_USER_ROLE })
                m.remove(PreferencesKeys.stringKey(k));
            return Single.just(m);
        }).ignoreElement().subscribeOn(Schedulers.io());
    }

    public Flowable<Boolean> observeLoggedIn() {
        return ds.data().map(p -> {
            String t = p.get(PreferencesKeys.stringKey(Constants.PREF_ACCESS_TOKEN));
            return t != null && !t.isEmpty();
        });
    }

    public Flowable<String> observeRole() {
        return ds.data().map(p -> {
            String r = p.get(PreferencesKeys.stringKey(Constants.PREF_USER_ROLE));
            return r != null ? r : "user";
        });
    }

    public Single<String> getAccessToken() {
        return ds.data().firstOrError().map(p -> {
            String t = p.get(PreferencesKeys.stringKey(Constants.PREF_ACCESS_TOKEN));
            return t != null ? t : "";
        });
    }

    private Completable saveTokens(AuthResponse r) {
        return ds.updateDataAsync(p -> {
            MutablePreferences m = p.toMutablePreferences();
            if (r.accessToken  != null) m.set(PreferencesKeys.stringKey(Constants.PREF_ACCESS_TOKEN),  r.accessToken);
            if (r.refreshToken != null) m.set(PreferencesKeys.stringKey(Constants.PREF_REFRESH_TOKEN), r.refreshToken);
            if (r.user != null) {
                m.set(PreferencesKeys.stringKey(Constants.PREF_USER_ID),   r.user.id);
                m.set(PreferencesKeys.stringKey(Constants.PREF_USERNAME),  r.user.username);
                m.set(PreferencesKeys.stringKey(Constants.PREF_USER_ROLE), r.user.role != null ? r.user.role : "user");
            }
            return Single.just(m);
        }).ignoreElement().subscribeOn(Schedulers.io());
    }
}
