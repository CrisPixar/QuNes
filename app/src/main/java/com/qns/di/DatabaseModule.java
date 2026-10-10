package com.qns.di;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import com.qns.data.crypto.KeyManager;
import com.qns.data.local.AppDatabase;
import com.qns.data.local.dao.ChatDao;
import com.qns.data.local.dao.MessageDao;
import com.qns.data.local.dao.RatchetSessionDao;
import com.qns.utils.Constants;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

/**
 * Модуль базы данных с отказоустойчивой инициализацией мастер-ключа и пароля SQLCipher.
 * Гарантирует запуск приложения даже при временных сбоях AndroidKeyStore.
 */
@Module
@InstallIn(SingletonComponent.class)
public class DatabaseModule {
    private static final String TAG = "QNS_DatabaseModule";
    private static final String PREFS = "qns_database";
    private static final String SEALED_PASSPHRASE = "sealed_passphrase";
    private static final String KEY_ALIAS = "qns_database_master";

    @Provides
    @Singleton
    AppDatabase provideDatabase(@ApplicationContext Context context, KeyManager keyManager) {
        return AppDatabase.getInstance(context, databasePassphrase(context, keyManager));
    }

    @Provides
    ChatDao provideChatDao(AppDatabase database) {
        return database.chatDao();
    }

    @Provides
    MessageDao provideMessageDao(AppDatabase database) {
        return database.messageDao();
    }

    @Provides
    RatchetSessionDao provideRatchetSessionDao(AppDatabase database) {
        return database.ratchetSessionDao();
    }

    private static synchronized byte[] databasePassphrase(Context context, KeyManager keyManager) {
        try {
            SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            SecretKey key = keyManager.getOrCreateMasterKey(KEY_ALIAS);
            String sealed = preferences.getString(SEALED_PASSPHRASE, null);

            if (sealed == null || sealed.trim().isEmpty()) {
                byte[] passphrase = context.getDatabasePath("qns.db").exists()
                    ? legacyPassphrase()
                    : randomPassphrase();
                saveSealedPassphrase(preferences, keyManager, key, passphrase);
                return passphrase;
            }

            try {
                return openSealedPassphrase(keyManager, key, sealed);
            } catch (Throwable decryptErr) {
                Log.w(TAG, "Failed to decrypt sealed database passphrase, regenerating session key", decryptErr);
                byte[] passphrase = randomPassphrase();
                saveSealedPassphrase(preferences, keyManager, key, passphrase);
                return passphrase;
            }
        } catch (Throwable error) {
            Log.e(TAG, "Encrypted database key fallback activated", error);
            return legacyPassphrase();
        }
    }

    private static byte[] randomPassphrase() {
        byte[] passphrase = new byte[32];
        new SecureRandom().nextBytes(passphrase);
        return passphrase;
    }

    private static byte[] legacyPassphrase() {
        byte[] passphrase = new byte[32];
        byte[] alias = Constants.DB_KEY_ALIAS.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(alias, 0, passphrase, 0, Math.min(alias.length, passphrase.length));
        return passphrase;
    }

    private static void saveSealedPassphrase(SharedPreferences preferences, KeyManager keyManager, SecretKey key, byte[] passphrase) throws Exception {
        byte[] sealed = keyManager.sealData(key, passphrase);
        preferences.edit().putString(SEALED_PASSPHRASE, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply();
    }

    private static byte[] openSealedPassphrase(KeyManager keyManager, SecretKey key, String sealed) throws Exception {
        byte[] raw = Base64.decode(sealed, Base64.NO_WRAP);
        return keyManager.unsealData(key, raw);
    }
}
