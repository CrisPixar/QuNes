package com.qns.data.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
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
    private final SharedPreferences preferences;

    static {
        if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
    }

    @Inject
    public IdentityStore(@ApplicationContext Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized Map<String, Object> publicKeys() {
        Map<String, Object> result = new HashMap<>();
        Map<String, String> identity = new HashMap<>();
        identity.put("key", base64(identity().getPublic().getEncoded()));
        result.put("identity_x25519", identity);
        return result;
    }

    public synchronized String encrypt(String text, String recipientPublicKey) throws Exception {
        if (text == null || recipientPublicKey == null || recipientPublicKey.isEmpty()) throw new IllegalArgumentException("Missing encryption key");
        KeyPair ephemeral = generatePair();
        byte[] shared = derive(ephemeral.getPrivate().getEncoded(), Base64.decode(recipientPublicKey, Base64.DEFAULT));
        byte[] key = deriveAesKey(shared);
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        JSONObject envelope = new JSONObject();
        envelope.put("v", 1);
        envelope.put("epk", base64(ephemeral.getPublic().getEncoded()));
        envelope.put("iv", base64(iv));
        envelope.put("ct", base64(ciphertext));
        KeyManager.wipe(shared, key, iv, ciphertext);
        return envelope.toString();
    }

    public synchronized String decrypt(String envelope) throws Exception {
        JSONObject value = new JSONObject(envelope);
        if (value.optInt("v", 0) != 1) throw new IllegalArgumentException("Unsupported envelope");
        byte[] ephemeralPublic = Base64.decode(value.getString("epk"), Base64.DEFAULT);
        byte[] shared = derive(identity().getPrivate().getEncoded(), ephemeralPublic);
        byte[] key = deriveAesKey(shared);
        byte[] iv = Base64.decode(value.getString("iv"), Base64.DEFAULT);
        byte[] ciphertext = Base64.decode(value.getString("ct"), Base64.DEFAULT);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] plain = cipher.doFinal(ciphertext);
        String result = new String(plain, StandardCharsets.UTF_8);
        KeyManager.wipe(shared, key, iv, ciphertext, plain);
        return result;
    }

    private KeyPair identity() {
        String stored = preferences.getString(KEY_DATA, null);
        try {
            if (stored != null) return decodePair(unseal(Base64.decode(stored, Base64.NO_WRAP)));
            KeyPair pair = generatePair();
            preferences.edit().putString(KEY_DATA, Base64.encodeToString(seal(encodePair(pair)), Base64.NO_WRAP)).apply();
            return pair;
        } catch (Exception error) {
            throw new IllegalStateException("Identity key is unavailable", error);
        }
    }

    private static KeyPair generatePair() throws Exception {
        return KeyPairGenerator.getInstance("X25519", "BC").generateKeyPair();
    }

    private static byte[] derive(byte[] privateEncoded, byte[] publicEncoded) throws Exception {
        KeyFactory factory = KeyFactory.getInstance("X25519", "BC");
        KeyAgreement agreement = KeyAgreement.getInstance("X25519", "BC");
        agreement.init(factory.generatePrivate(new PKCS8EncodedKeySpec(privateEncoded)));
        agreement.doPhase(factory.generatePublic(new X509EncodedKeySpec(publicEncoded)), true);
        return agreement.generateSecret();
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
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, sealKey(), new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plain);
        return ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
    }

    private byte[] unseal(byte[] value) throws Exception {
        if (value.length < 13) throw new IllegalArgumentException("Invalid identity data");
        byte[] iv = new byte[12];
        byte[] encrypted = new byte[value.length - 12];
        System.arraycopy(value, 0, iv, 0, 12);
        System.arraycopy(value, 12, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, sealKey(), new GCMParameterSpec(128, iv));
        return cipher.doFinal(encrypted);
    }

    private static byte[] encodePair(KeyPair pair) {
        byte[] privateKey = pair.getPrivate().getEncoded();
        byte[] publicKey = pair.getPublic().getEncoded();
        return ByteBuffer.allocate(4 + privateKey.length + publicKey.length)
            .putInt(privateKey.length).put(privateKey).put(publicKey).array();
    }

    private static KeyPair decodePair(byte[] encoded) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        int privateLength = buffer.getInt();
        if (privateLength <= 0 || privateLength >= encoded.length) throw new IllegalArgumentException("Invalid identity data");
        byte[] privateKey = new byte[privateLength];
        byte[] publicKey = new byte[buffer.remaining() - privateLength];
        buffer.get(privateKey).get(publicKey);
        KeyFactory factory = KeyFactory.getInstance("X25519", "BC");
        return new KeyPair(
            factory.generatePublic(new X509EncodedKeySpec(publicKey)),
            factory.generatePrivate(new PKCS8EncodedKeySpec(privateKey))
        );
    }

    private static String base64(byte[] value) {
        return Base64.encodeToString(value, Base64.NO_WRAP | Base64.DEFAULT);
    }
}
