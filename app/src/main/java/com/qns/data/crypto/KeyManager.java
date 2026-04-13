package com.qns.data.crypto;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.crypto.crystals.kyber.*;
import org.bouncycastle.pqc.crypto.crystals.dilithium.*;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;

import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Управление криптографическими ключами QNS.
 *
 * Хранение:
 *   - AES-256 мастер-ключ → Android Keystore (TEE/StrongBox)
 *   - ML-KEM-1024 + ML-DSA-87 + X25519 + Ed25519 приватные ключи →
 *     шифруются мастер-ключом перед сохранением в SQLCipher БД
 *
 * Все byte[] с ключевым материалом обнуляются после использования.
 */
@Singleton
public class KeyManager {
    private static final String TAG = "QNS_KeyManager";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null)
            Security.addProvider(new BouncyCastleProvider());
        if (Security.getProvider("BCPQC") == null)
            Security.addProvider(new BouncyCastlePQCProvider());
    }

    private final Context context;
    private final SecureRandom random;
    private KeyStore keyStore;

    @Inject
    public KeyManager(Context context) {
        this.context = context;
        this.random  = new SecureRandom();
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
        } catch (Exception e) { Log.e(TAG, "Keystore init failed", e); }
    }

    // ---- Android Keystore AES-256 мастер-ключ ----

    public SecretKey getOrCreateMasterKey(String alias) throws Exception {
        if (keyStore.containsAlias(alias))
            return (SecretKey) keyStore.getKey(alias, null);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build();
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(spec);
        return kg.generateKey();
    }

    // ---- ML-KEM-1024 (CRYSTALS-Kyber, FIPS 203) ----
    // Пост-квантовый KEM — устойчив к атаке Шора

    public AsymmetricCipherKeyPair generateKyberKeyPair() {
        KyberKeyPairGenerator gen = new KyberKeyPairGenerator();
        gen.init(new KyberKeyGenerationParameters(random, KyberParameters.kyber1024));
        return gen.generateKeyPair();
    }

    public byte[] getKyberPublicKeyBytes(AsymmetricCipherKeyPair kp) {
        return ((KyberPublicKeyParameters) kp.getPublic()).getEncoded();
    }

    public byte[] getKyberPrivateKeyBytes(AsymmetricCipherKeyPair kp) {
        return ((KyberPrivateKeyParameters) kp.getPrivate()).getEncoded();
    }

    // ---- ML-DSA-87 (CRYSTALS-Dilithium 5, FIPS 204) ----

    public AsymmetricCipherKeyPair generateDilithiumKeyPair() {
        DilithiumKeyPairGenerator gen = new DilithiumKeyPairGenerator();
        gen.init(new DilithiumKeyGenerationParameters(random, DilithiumParameters.dilithium5));
        return gen.generateKeyPair();
    }

    public byte[] getDilithiumPublicKeyBytes(AsymmetricCipherKeyPair kp) {
        return ((DilithiumPublicKeyParameters) kp.getPublic()).getEncoded();
    }

    public byte[] signWithDilithium(byte[] privKeyBytes, byte[] message) throws Exception {
        DilithiumSigner signer = new DilithiumSigner();
        signer.init(true, new DilithiumPrivateKeyParameters(DilithiumParameters.dilithium5, privKeyBytes));
        return signer.generateSignature(message);
    }

    public boolean verifyDilithium(byte[] pubKeyBytes, byte[] message, byte[] signature) {
        try {
            DilithiumSigner v = new DilithiumSigner();
            v.init(false, new DilithiumPublicKeyParameters(DilithiumParameters.dilithium5, pubKeyBytes));
            return v.verifySignature(message, signature);
        } catch (Exception e) { Log.e(TAG, "Dilithium verify failed", e); return false; }
    }

    // ---- X25519 (ECDH для гибридной схемы) ----

    public KeyPair generateX25519KeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519", "BC");
        kpg.initialize(255);
        return kpg.generateKeyPair();
    }

    // ---- Ed25519 (подписи для гибридной схемы) ----

    public KeyPair generateEd25519KeyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519", "BC").generateKeyPair();
    }

    // ---- Утилиты ----

    public byte[] randomBytes(int n) {
        byte[] b = new byte[n]; random.nextBytes(b); return b;
    }

    /** Обнуляет byte[] с ключевым материалом — критически важно! */
    public static void wipe(byte[]... keys) {
        for (byte[] k : keys) if (k != null) Arrays.fill(k, (byte) 0);
    }

    public static String toBase64Url(byte[] b) {
        return Base64.encodeToString(b, Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
    }

    public static byte[] fromBase64Url(String s) {
        return Base64.decode(s, Base64.URL_SAFE | Base64.NO_PADDING);
    }
}
