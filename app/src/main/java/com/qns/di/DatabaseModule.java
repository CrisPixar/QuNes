package com.qns.di;
import android.content.Context;
import android.security.keystore.*;
import com.qns.data.local.AppDatabase;
import com.qns.data.local.dao.*;
import com.qns.utils.Constants;
import java.security.*;
import java.security.SecureRandom;
import javax.crypto.*;
import javax.inject.Singleton;
import dagger.Module; import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
@Module @InstallIn(SingletonComponent.class)
public class DatabaseModule {
    @Provides @Singleton
    AppDatabase provideDatabase(@ApplicationContext Context ctx) {
        return AppDatabase.getInstance(ctx, dbPassphrase());
    }
    @Provides ChatDao    provideChatDao(AppDatabase db)    { return db.chatDao(); }
    @Provides MessageDao provideMessageDao(AppDatabase db) { return db.messageDao(); }

    private byte[] dbPassphrase() {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
            if (!ks.containsAlias(Constants.DB_KEY_ALIAS)) {
                KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    Constants.DB_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build();
                KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                kg.init(spec); kg.generateKey();
            }
            // Derivation из alias — в продакшене хранить зашифрованный passphrase в EncryptedSharedPreferences
            byte[] p = new byte[32];
            System.arraycopy(Constants.DB_KEY_ALIAS.getBytes(), 0, p, 0, Math.min(Constants.DB_KEY_ALIAS.length(), 32));
            return p;
        } catch (Exception e) {
            byte[] fb = new byte[32]; new SecureRandom().nextBytes(fb); return fb;
        }
    }
}
