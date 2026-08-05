package com.qns.di;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.qns.data.local.AppDatabase;
import com.qns.data.local.dao.ChatDao;
import com.qns.data.local.dao.MessageDao;
import com.qns.utils.Constants;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class DatabaseModule {
    private static final String PREFS = "qns_database";
    private static final String SEALED_PASSPHRASE = "sealed_passphrase";
    private static final String KEY_ALIAS = "qns_database_master";

    @Provides
    @Singleton
    AppDatabase provideDatabase(@ApplicationContext Context context) {
        return AppDatabase.getInstance(context, databasePassphrase(context));
    }

    @Provides
    ChatDao provideChatDao(AppDatabase database) {
        return database.chatDao();
    }

    @Provides
    MessageDao provideMessageDao(AppDatabase database) {
        return database.messageDao();
    }

    private static byte[] databasePassphrase(Context context) {
        try {
            SecretKey key = getOrCreateKey();
            SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String sealed = preferences.getString(SEALED_PASSPHRASE, null);
            if (sealed == null) {
                byte[] passphrase = new byte[32];
                new SecureRandom().nextBytes(passphrase);
                byte[] iv = new byte[12];
                new SecureRandom().nextBytes(iv);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
                byte[] encrypted = cipher.doFinal(passphrase);
                byte[] value = new byte[iv.length + encrypted.length];
                System.arraycopy(iv, 0, value, 0, iv.length);
                System.arraycopy(encrypted, 0, value, iv.length, encrypted.length);
                preferences.edit().putString(SEALED_PASSPHRASE, Base64.encodeToString(value, Base64.NO_WRAP)).apply();
                return passphrase;
            }

            byte[] value = Base64.decode(sealed, Base64.NO_WRAP);
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[value.length - iv.length];
            System.arraycopy(value, 0, iv, 0, iv.length);
            System.arraycopy(value, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return cipher.doFinal(encrypted);
        } catch (Exception error) {
            throw new IllegalStateException("Encrypted database key is unavailable", error);
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build());
        return generator.generateKey();
    }
}
