package com.qns.data.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.KeyGenerationParameters;
import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import javax.inject.Inject;
import javax.inject.Singleton;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public final class IdentityStore {
    private static final String PREFS = "qns_identity";
    private static final String KEY_DATA = "identity_data";
    private static final String SEAL_ALIAS = "qns_identity_seal";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SharedPreferences preferences;

    @Inject
    public IdentityStore(@ApplicationContext Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized Map<String, Object> publicKeys() {
        Map<String, Object> result = new HashMap<>();
        Map<String, String> identity = new HashMap<>();
        identity.put("key", base64(identity().publicKey));
        result.put("identity_x25519", identity);
        return result;
    }

    public synchronized String encrypt(String text, String recipientPublicKey) throws Exception {
        if (text == null || recipientPublicKey == null || recipientPublicKey.isEmpty()) throw new IllegalArgumentException("Missing encryption key");
        byte[] recipient = Base64.decode(recipientPublicKey, Base64.DEFAULT);
        if (recipient.length != 32) throw new IllegalArgumentException("Invalid recipient key");
        AsymmetricCipherKeyPair ephemeral = generatePair();
        byte[] ephemeralPrivate = ((X25519PrivateKeyParameters) ephemeral.getPrivate()).getEncoded();
        byte[] ephemeralPublic = ((X25519PublicKeyParameters) ephemeral.getPublic()).getEncoded();
        byte[] shared = derive(ephemeralPrivate, recipient);
        byte[] key = deriveAesKey(shared);
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        JSONObject envelope = new JSONObject();
        envelope.put("v", 1);
        envelope.put("epk", base64(ephemeralPublic));
        envelope.put("iv", base64(iv));
        envelope.put("ct", base64(ciphertext));
        KeyManager.wipe(ephemeralPrivate, ephemeralPublic, shared, key, iv, ciphertext);
        return envelope.toString();
    }

    public synchronized String decrypt(String envelope) throws Exception {
        JSONObject value = new JSONObject(envelope);
        if (value.optInt("v", 0) != 1) throw new IllegalArgumentException("Unsupported envelope");
        byte[] ephemeralPublic = Base64.decode(value.getString("epk"), Base64.DEFAULT);
        if (ephemeralPublic.length != 32) throw new IllegalArgumentException("Invalid ephemeral key");
        IdentityKeys identity = identity();
        byte[] shared = derive(identity.privateKey, ephemeralPublic);
        byte[] key = deriveAesKey(shared);
        byte[] iv = Base64.decode(value.getString("iv"), Base64.DEFAULT);
        byte[] ciphertext = Base64.decode(value.getString("ct"), Base64.DEFAULT);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] plain = cipher.doFinal(ciphertext);
        String result = new String(plain, StandardCharsets.UTF_8);
        KeyManager.wipe(ephemeralPublic, shared, key, iv, ciphertext, plain);
        return result;
    }

    private IdentityKeys identity() {
        String stored = preferences.getString(KEY_DATA, null);
        try {
            if (stored != null) {
                byte[] decoded = unseal(Base64.decode(stored, Base64.NO_WRAP));
                if (decoded.length == 64) return new IdentityKeys(decoded);
            }
            IdentityKeys keys = new IdentityKeys(generatePair());
            preferences.edit().putString(KEY_DATA, Base64.encodeToString(seal(keys.encode()), Base64.NO_WRAP)).apply();
            return keys;
        } catch (Exception error) {
            throw new IllegalStateException("Identity key is unavailable", error);
        }
    }

    private static AsymmetricCipherKeyPair generatePair() {
        X25519KeyPairGenerator generator = new X25519KeyPairGenerator();
        generator.init(new KeyGenerationParameters(RANDOM, 255));
        return generator.generateKeyPair();
    }

    private static byte[] derive(byte[] privateKey, byte[] publicKey) {
        X25519Agreement agreement = new X25519Agreement();
        agreement.init(new X25519PrivateKeyParameters(privateKey, 0));
        byte[] shared = new byte[agreement.getAgreementSize()];
        agreement.calculateAgreement(new X25519PublicKeyParameters(publicKey, 0), shared, 0);
        return shared;
    }

    private static byte[] deriveAesKey(byte[] shared) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update("QNS-E2EE-envelope-v1".getBytes(StandardCharsets.UTF_8));
        return digest.digest(shared);
    }

    private SecretKey sealKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(SEAL_ALIAS)) return (SecretKey) store.getKey(SEAL_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
            SEAL_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build());
        return generator.generateKey();
    }

    private byte[] seal(byte[] plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, sealKey());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(plain);
        return ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
    }

    private byte[] unseal(byte[] value) throws Exception {
        if (value.length < 13) throw new IllegalArgumentException("Invalid identity data");
        byte[] iv = new byte[12];
        byte[] encrypted = new byte[value.length - iv.length];
        System.arraycopy(value, 0, iv, 0, iv.length);
        System.arraycopy(value, iv.length, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, sealKey(), new GCMParameterSpec(128, iv));
        return cipher.doFinal(encrypted);
    }

    private static String base64(byte[] value) {
        return Base64.encodeToString(value, Base64.NO_WRAP);
    }

    private static final class IdentityKeys {
        final byte[] privateKey;
        final byte[] publicKey;

        IdentityKeys(AsymmetricCipherKeyPair pair) {
            privateKey = ((X25519PrivateKeyParameters) pair.getPrivate()).getEncoded();
            publicKey = ((X25519PublicKeyParameters) pair.getPublic()).getEncoded();
        }

        IdentityKeys(byte[] encoded) {
            privateKey = new byte[32];
            publicKey = new byte[32];
            System.arraycopy(encoded, 0, privateKey, 0, 32);
            System.arraycopy(encoded, 32, publicKey, 0, 32);
        }

        byte[] encode() {
            return ByteBuffer.allocate(64).put(privateKey).put(publicKey).array();
        }
    }
}
