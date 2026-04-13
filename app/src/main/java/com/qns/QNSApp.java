package com.qns;

import android.app.Application;
import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class QNSApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Загружаем нативные библиотеки SQLCipher
        net.sqlcipher.database.SQLiteDatabase.loadLibs(this);
    }
}
