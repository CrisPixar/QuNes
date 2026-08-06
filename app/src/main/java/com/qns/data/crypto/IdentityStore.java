package com.qns.data.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.KeyGenerationParameters;
import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private static final int OPK_COUNT = 100;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SharedPreferences preferences;

    @Inject
    public IdentityStore(@ApplicationContext Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized DeviceKeys getDeviceKeys() {
        return load();
    }

    public synchronized Map<String, Object> publicKeys() {
        DeviceKeys keys = load();
        Map<String, Object> result = new HashMap<>();
        result.put("identity_x25519", keyObject(keys.identityXPublic, null, null));
        result.put("identity_ed25519", keyObject(keys.identityEdPublic, null, null));
        result.put("signed_prekey", keyObject(keys.signedPrePublic, keys.signedPreId, keys.signedPreSignature));
        List<Map<String, String>> oneTime = new ArrayList<>();
        for (PreKey key : keys.oneTimePreKeys) {
            if (!key.used) {
                Map<String, String> item = new HashMap<>();
                item.put("id", key.id);
                item.put("key", base64(key.publicKey));
                oneTime.add(item);
            }
        }
        result.put("one_time_prekeys", oneTime);
        return result;
    }

    public synchronized PreKey consumeOneTimePrekey(String id) {
        DeviceKeys keys = load();
        for (PreKey key : keys.oneTimePreKeys) {
            if (key.id.equals(id) && !key.used) {
                key.used = true;
                save(keys);
                return key.copy();
            }
        }
        return null;
    }

    public synchronized byte[] sign(byte[] data) {
        DeviceKeys keys = load();
        Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(keys.identityEdPrivate, 0);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKey);
        signer.update(data, 0, data.length);
        return signer.generateSignature();
    }

    public static boolean verify(byte[] publicKey, byte[] data, byte[] signature) {
        try {
            Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, new Ed25519PublicKeyParameters(publicKey, 0));
            verifier.update(data, 0, data.length);
            return verifier.verifySignature(signature);
        } catch (Exception error) {
            return false;
        }
    }

    public synchronized String encrypt(String text, String recipientPublicKey) throws Exception {
        if (text == null || recipientPublicKey == null || recipientPublicKey.isEmpty()) throw new IllegalArgumentException("Missing encryption key");
        DeviceKeys keys = load();
        byte[] recipient = Base64.decode(recipientPublicKey, Base64.DEFAULT);
        AsymmetricCipherKeyPair ephemeral = x25519Pair();
        byte[] shared = derive(rawPrivate(ephemeral), recipient);
        byte[] key = deriveAesKey(shared);
        byte[] iv = randomBytes(12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        JSONObject envelope = new JSONObject();
        envelope.put("v", 1);
        envelope.put("epk", base64(rawPublic(ephemeral)));
        envelope.put("iv", base64(iv));
        envelope.put("ct", base64(ciphertext));
        wipe(shared, key, iv, ciphertext, rawPrivate(ephemeral));
        return envelope.toString();
    }

    public synchronized String decrypt(String envelope) throws Exception {
        return decryptLegacy(envelope);
    }

    public synchronized String decryptLegacy(String envelope) throws Exception {
        JSONObject value = new JSONObject(envelope);
        if (value.optInt("v", 1) != 1) throw new IllegalArgumentException("Not a legacy message");
        DeviceKeys keys = load();
        byte[] ephemeralPublic = Base64.decode(value.getString("epk"), Base64.DEFAULT);
        byte[] shared = derive(keys.legacyXPrivate, ephemeralPublic);
        byte[] key = deriveAesKey(shared);
        byte[] iv = Base64.decode(value.getString("iv"), Base64.DEFAULT);
        byte[] ciphertext = Base64.decode(value.getString("ct"), Base64.DEFAULT);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] plain = cipher.doFinal(ciphertext);
        String result = new String(plain, StandardCharsets.UTF_8);
        wipe(ephemeralPublic, shared, key, iv, ciphertext, plain);
        return result;
    }

    public static String fingerprint(byte[] first, byte[] second) {
        try {
            byte[] left = first;
            byte[] right = second;
            if (compare(left, right) > 0) { left = second; right = first; }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(left);
            digest.update(right);
            byte[] hash = digest.digest();
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < hash.length; i++) {
                if (i > 0 && i % 2 == 0) out.append(' ');
                out.append(String.format("%02x", hash[i] & 0xff));
            }
            return out.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private DeviceKeys load() {
        String stored = preferences.getString(KEY_DATA, null);
        try {
            if (stored != null) {
                byte[] plain = unseal(Base64.decode(stored, Base64.NO_WRAP));
                if (plain.length == 64) return migrateLegacyRaw(plain);
                JSONObject value = new JSONObject(new String(plain, StandardCharsets.UTF_8));
                if (value.optInt("version", 0) == 2) return DeviceKeys.fromJson(value);
                if (value.has("private") && value.has("public")) return migrateLegacy(value);
            }
            DeviceKeys keys = create();
            save(keys);
            return keys;
        } catch (Exception error) {
            throw new IllegalStateException("Identity key is unavailable", error);
        }
    }

    private DeviceKeys migrateLegacyRaw(byte[] raw) throws Exception {
        DeviceKeys keys = create();
        keys.legacyXPrivate = java.util.Arrays.copyOfRange(raw, 0, 32);
        keys.identityXPrivate = keys.legacyXPrivate.clone();
        keys.identityXPublic = java.util.Arrays.copyOfRange(raw, 32, 64);
        save(keys);
        return keys;
    }

    private DeviceKeys migrateLegacy(JSONObject value) throws Exception {
        byte[] legacyPrivate = Base64.decode(value.getString("private"), Base64.DEFAULT);
        DeviceKeys keys = create();
        keys.legacyXPrivate = legacyPrivate.clone();
        keys.identityXPrivate = legacyPrivate.clone();
        keys.identityXPublic = Base64.decode(value.getString("public"), Base64.DEFAULT);
        save(keys);
        return keys;
    }

    private static DeviceKeys create() throws Exception {
        DeviceKeys keys = new DeviceKeys();
        AsymmetricCipherKeyPair identityX = x25519Pair();
        keys.identityXPrivate = rawPrivate(identityX);
        keys.identityXPublic = rawPublic(identityX);
        keys.legacyXPrivate = keys.identityXPrivate.clone();
        Ed25519PrivateKeyParameters edPrivate = new Ed25519PrivateKeyParameters(RANDOM);
        keys.identityEdPrivate = edPrivate.getEncoded();
        keys.identityEdPublic = edPrivate.generatePublicKey().getEncoded();
        AsymmetricCipherKeyPair signed = x25519Pair();
        keys.signedPreId = UUID.randomUUID().toString();
        keys.signedPrePrivate = rawPrivate(signed);
        keys.signedPrePublic = rawPublic(signed);
        keys.signedPreSignature = signStatic(keys.identityEdPrivate, keys.signedPrePublic);
        for (int i = 0; i < OPK_COUNT; i++) {
            AsymmetricCipherKeyPair opk = x25519Pair();
            keys.oneTimePreKeys.add(new PreKey(UUID.randomUUID().toString(), rawPrivate(opk), rawPublic(opk), false));
        }
        return keys;
    }

    private static byte[] signStatic(byte[] privateKey, byte[] data) {
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, new Ed25519PrivateKeyParameters(privateKey, 0));
        signer.update(data, 0, data.length);
        return signer.generateSignature();
    }

    private void save(DeviceKeys keys) throws Exception {
        preferences.edit().putString(KEY_DATA, Base64.encodeToString(seal(keys.toJson().toString().getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP)).apply();
    }

    private static Map<String, String> keyObject(byte[] publicKey, String id, byte[] signature) {
        Map<String, String> value = new HashMap<>();
        value.put("key", base64(publicKey));
        if (id != null) value.put("id", id);
        if (signature != null) value.put("signature", base64(signature));
        return value;
    }

    private static AsymmetricCipherKeyPair x25519Pair() {
        X25519KeyPairGenerator generator = new X25519KeyPairGenerator();
        generator.init(new KeyGenerationParameters(RANDOM, 255));
        return generator.generateKeyPair();
    }

    private static byte[] rawPrivate(AsymmetricCipherKeyPair pair) { return ((X25519PrivateKeyParameters) pair.getPrivate()).getEncoded(); }
    private static byte[] rawPublic(AsymmetricCipherKeyPair pair) { return ((X25519PublicKeyParameters) pair.getPublic()).getEncoded(); }

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

    private static byte[] randomBytes(int size) { byte[] value = new byte[size]; RANDOM.nextBytes(value); return value; }
    private static String base64(byte[] value) { return Base64.encodeToString(value, Base64.NO_WRAP); }
    private static void wipe(byte[]... values) { for (byte[] value : values) if (value != null) java.util.Arrays.fill(value, (byte) 0); }
    private static int compare(byte[] a, byte[] b) { for (int i = 0; i < Math.min(a.length, b.length); i++) { int result = Byte.compare(a[i], b[i]); if (result != 0) return result; } return Integer.compare(a.length, b.length); }

    private SecretKey sealKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(SEAL_ALIAS)) return (SecretKey) store.getKey(SEAL_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(SEAL_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
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
        byte[] iv = java.util.Arrays.copyOf(value, 12);
        byte[] encrypted = java.util.Arrays.copyOfRange(value, 12, value.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, sealKey(), new GCMParameterSpec(128, iv));
        return cipher.doFinal(encrypted);
    }

    public static final class PreKey {
        public final String id;
        public final byte[] privateKey;
        public final byte[] publicKey;
        public boolean used;
        PreKey(String id, byte[] privateKey, byte[] publicKey, boolean used) { this.id = id; this.privateKey = privateKey; this.publicKey = publicKey; this.used = used; }
        public PreKey copy() { return new PreKey(id, privateKey.clone(), publicKey.clone(), used); }
    }

    public static final class DeviceKeys {
        public byte[] identityXPrivate, identityXPublic, legacyXPrivate;
        public byte[] identityEdPrivate, identityEdPublic;
        public String signedPreId;
        public byte[] signedPrePrivate, signedPrePublic, signedPreSignature;
        public final List<PreKey> oneTimePreKeys = new ArrayList<>();

        JSONObject toJson() throws Exception {
            JSONObject value = new JSONObject();
            value.put("version", 2);
            value.put("identityXPrivate", base64(identityXPrivate)); value.put("identityXPublic", base64(identityXPublic)); value.put("legacyXPrivate", base64(legacyXPrivate));
            value.put("identityEdPrivate", base64(identityEdPrivate)); value.put("identityEdPublic", base64(identityEdPublic));
            value.put("signedPreId", signedPreId); value.put("signedPrePrivate", base64(signedPrePrivate)); value.put("signedPrePublic", base64(signedPrePublic)); value.put("signedPreSignature", base64(signedPreSignature));
            JSONArray opks = new JSONArray();
            for (PreKey key : oneTimePreKeys) { JSONObject opk = new JSONObject(); opk.put("id", key.id); opk.put("private", base64(key.privateKey)); opk.put("public", base64(key.publicKey)); opk.put("used", key.used); opks.put(opk); }
            value.put("oneTimePreKeys", opks);
            return value;
        }

        static DeviceKeys fromJson(JSONObject value) throws Exception {
            DeviceKeys keys = new DeviceKeys();
            keys.identityXPrivate = Base64.decode(value.getString("identityXPrivate"), Base64.DEFAULT); keys.identityXPublic = Base64.decode(value.getString("identityXPublic"), Base64.DEFAULT); keys.legacyXPrivate = Base64.decode(value.optString("legacyXPrivate", value.getString("identityXPrivate")), Base64.DEFAULT);
            keys.identityEdPrivate = Base64.decode(value.getString("identityEdPrivate"), Base64.DEFAULT); keys.identityEdPublic = Base64.decode(value.getString("identityEdPublic"), Base64.DEFAULT);
            keys.signedPreId = value.getString("signedPreId"); keys.signedPrePrivate = Base64.decode(value.getString("signedPrePrivate"), Base64.DEFAULT); keys.signedPrePublic = Base64.decode(value.getString("signedPrePublic"), Base64.DEFAULT); keys.signedPreSignature = Base64.decode(value.getString("signedPreSignature"), Base64.DEFAULT);
            JSONArray opks = value.optJSONArray("oneTimePreKeys");
            if (opks != null) for (int i = 0; i < opks.length(); i++) { JSONObject opk = opks.getJSONObject(i); keys.oneTimePreKeys.add(new PreKey(opk.getString("id"), Base64.decode(opk.getString("private"), Base64.DEFAULT), Base64.decode(opk.getString("public"), Base64.DEFAULT), opk.optBoolean("used", false))); }
            return keys;
        }
    }
}
