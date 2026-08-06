package com.qns.ui.theme;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.MutableLiveData;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public final class ThemeRepository {
    private static final String PREFS = "qns_theme";
    private static final String KEY_MODE = "mode";
    private final SharedPreferences prefs;
    public final MutableLiveData<String> mode = new MutableLiveData<>();

    @Inject
    public ThemeRepository(@ApplicationContext Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_MODE, "system");
        mode.setValue(stored);
        FurryTheme.INSTANCE.setEnabled("furry".equals(stored));
    }

    public void setMode(String value) {
        String normalized = "dark".equals(value) || "light".equals(value) || "furry".equals(value) ? value : "system";
        prefs.edit().putString(KEY_MODE, normalized).apply();
        mode.postValue(normalized);
        // Включаем/выключаем замену иконок на boykisser для furry-темы.
        FurryTheme.INSTANCE.setEnabled("furry".equals(normalized));
    }
}
