package com.qns.data.crypto;

import com.google.gson.Gson;
import com.qns.data.local.dao.RatchetSessionDao;
import com.qns.data.local.entity.RatchetSessionEntity;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

@Singleton
public final class CryptoSessionManager {
    private final RatchetSessionDao sessions;
    private final IdentityStore identities;
    private final Gson gson;

    @Inject
    public CryptoSessionManager(RatchetSessionDao sessions, IdentityStore identities, Gson gson) {
        this.sessions = sessions;
        this.identities = identities;
        this.gson = gson;
    }

    public Single<String> encrypt(String chatId, String remoteUserId, String plaintext, Map<String, Object> bundle) {
        return Single.fromCallable(() -> encryptBlocking(chatId, remoteUserId, plaintext, bundle)).subscribeOn(Schedulers.io());
    }

    public Single<DecryptResult> decrypt(String chatId, String senderId, String envelope) {
        return Single.fromCallable(() -> decryptBlocking(chatId, senderId, envelope)).subscribeOn(Schedulers.io());
    }

    private synchronized String encryptBlocking(String chatId, String remoteUserId, String plaintext, Map<String, Object> bundle) throws Exception {
        RatchetSessionEntity stored = sessions.get(chatId).blockingGet();
        DeviceBundle remote = DeviceBundle.from(bundle);
        if (!IdentityStore.verify(remote.identityEd25519, remote.signedPrekey, remote.signedPrekeySignature)) {
            throw new CryptoException("KEY_INVALID", "Подпись signed prekey недействительна");
        }
        IdentityStore.DeviceKeys local = identities.getDeviceKeys();
        String remoteIdentity = b64(remote.identityX25519);
        String fingerprint = IdentityStore.fingerprint(local.identityXPublic, remote.identityX25519);
        if (stored != null && stored.initialized && !remoteIdentity.equals(stored.remoteIdentityPublicKey)) {
            throw new CryptoException("KEY_CHANGED", "Ключ безопасности пользователя изменился");
        }

        DoubleRatchet ratchet = new DoubleRatchet();
        JSONObject envelope = new JSONObject();
        if (stored == null || !stored.initialized) {
            X3DH.InitResult init = X3DH.senderInitV2(
                local.identityXPrivate,
                remote.identityX25519,
                remote.signedPrekey,
                remote.oneTimePrekey
            );
            ratchet.initAsSender(init.sharedSecret.clone(), remote.signedPrekey);
            DoubleRatchet.EncryptedMessage encrypted = ratchet.encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
            envelope.put("v", 2);
            envelope.put("protocol", "qns-x3dh-dr-v1");
            envelope.put("type", "prekey_message");
            envelope.put("aliceIdentityKey", b64(local.identityXPublic));
            envelope.put("aliceSigningKey", b64(local.identityEdPublic));
            envelope.put("aliceEphemeralKey", b64(init.ephemeralPublicKey));
            envelope.put("aliceSignature", b64(identities.sign(init.ephemeralPublicKey)));
            envelope.put("bobSignedPrekeyId", remote.signedPrekeyId);
            envelope.put("bobOneTimePrekeyId", remote.oneTimePrekeyId == null ? JSONObject.NULL : remote.oneTimePrekeyId);
            envelope.put("fingerprint", fingerprint);
            envelope.put("header", b64(encrypted.header.toBytes()));
            envelope.put("ciphertext", b64(encrypted.ct));
        } else {
            ratchet.importState(gson.fromJson(stored.stateJson, DoubleRatchet.State.class));
            DoubleRatchet.EncryptedMessage encrypted = ratchet.encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
            envelope.put("v", 2);
            envelope.put("protocol", "qns-double-ratchet-v1");
            envelope.put("type", "message");
            envelope.put("fingerprint", stored.fingerprint);
            envelope.put("header", b64(encrypted.header.toBytes()));
            envelope.put("ciphertext", b64(encrypted.ct));
        }
        saveState(chatId, remoteUserId, remoteIdentity, fingerprint, ratchet);
        return envelope.toString();
    }

    private synchronized DecryptResult decryptBlocking(String chatId, String senderId, String envelopeText) throws Exception {
        JSONObject envelope = new JSONObject(envelopeText);
        int version = envelope.optInt("v", 1);
        if (version == 1) return new DecryptResult(identities.decryptLegacy(envelopeText), "OK", 1, "");
        if (version != 2) throw new CryptoException("UNSUPPORTED_VERSION", "Неподдерживаемая версия сообщения");

        RatchetSessionEntity stored = sessions.get(chatId).blockingGet();
        String type = envelope.optString("type", "message");
        DoubleRatchet ratchet = new DoubleRatchet();
        String fingerprint;
        String remoteIdentity;
        IdentityStore.DeviceKeys local = identities.getDeviceKeys();
        if (stored == null || !stored.initialized) {
            if (!"prekey_message".equals(type)) return new DecryptResult(null, "WAITING_FOR_SESSION", 2, "");
            byte[] aliceIdentity = decode(envelope.getString("aliceIdentityKey"));
            byte[] aliceSigningKey = decode(envelope.getString("aliceSigningKey"));
            byte[] aliceEphemeral = decode(envelope.getString("aliceEphemeralKey"));
            byte[] aliceSignature = decode(envelope.getString("aliceSignature"));
            if (!IdentityStore.verify(aliceSigningKey, aliceEphemeral, aliceSignature)) throw new CryptoException("INVALID_INITIAL_SIGNATURE", "Подпись initial message недействительна");
            String opkId = envelope.isNull("bobOneTimePrekeyId") ? null : envelope.optString("bobOneTimePrekeyId", null);
            IdentityStore.PreKey opk = opkId == null ? null : identities.consumeOneTimePrekey(opkId);
            byte[] shared = X3DH.receiverRespondV2(
                local.identityXPrivate,
                local.signedPrePrivate,
                opk == null ? null : opk.privateKey,
                aliceIdentity,
                aliceEphemeral
            );
            ratchet.initAsReceiver(shared.clone(), local.signedPrePrivate, local.signedPrePublic);
            remoteIdentity = b64(aliceIdentity);
            fingerprint = IdentityStore.fingerprint(local.identityXPublic, aliceIdentity);
        } else {
            remoteIdentity = stored.remoteIdentityPublicKey;
            fingerprint = stored.fingerprint;
            ratchet.importState(gson.fromJson(stored.stateJson, DoubleRatchet.State.class));
            String incomingFingerprint = envelope.optString("fingerprint", fingerprint);
            if (!incomingFingerprint.isEmpty() && !incomingFingerprint.equals(fingerprint)) throw new CryptoException("KEY_CHANGED", "Ключ безопасности пользователя изменился");
        }
        DoubleRatchet.Header header = DoubleRatchet.Header.fromBytes(decode(envelope.getString("header")));
        byte[] plaintext = ratchet.decrypt(new DoubleRatchet.EncryptedMessage(header, decode(envelope.getString("ciphertext"))));
        saveState(chatId, senderId, remoteIdentity, fingerprint, ratchet);
        return new DecryptResult(new String(plaintext, StandardCharsets.UTF_8), "OK", 2, fingerprint);
    }

    private void saveState(String chatId, String remoteUserId, String remoteIdentity, String fingerprint, DoubleRatchet ratchet) {
        RatchetSessionEntity session = new RatchetSessionEntity();
        session.chatId = chatId;
        session.remoteUserId = remoteUserId;
        session.remoteIdentityPublicKey = remoteIdentity;
        session.fingerprint = fingerprint;
        session.stateJson = gson.toJson(ratchet.exportState());
        session.protocolVersion = 2;
        session.initialized = true;
        session.createdAt = System.currentTimeMillis();
        session.updatedAt = System.currentTimeMillis();
        sessions.upsert(session).blockingAwait();
    }

    private static String b64(byte[] value) { return android.util.Base64.encodeToString(value, android.util.Base64.NO_WRAP); }
    private static byte[] decode(String value) { return android.util.Base64.decode(value, android.util.Base64.DEFAULT); }

    private static final class DeviceBundle {
        byte[] identityX25519, identityEd25519, signedPrekey, signedPrekeySignature, oneTimePrekey;
        String signedPrekeyId, oneTimePrekeyId;

        static DeviceBundle from(Map<String, Object> bundle) {
            DeviceBundle result = new DeviceBundle();
            Map<?, ?> identityX = map(bundle.get("identity_x25519"));
            Map<?, ?> identityEd = map(bundle.get("identity_ed25519"));
            Map<?, ?> signed = map(bundle.get("signed_prekey"));
            Map<?, ?> oneTime = map(bundle.get("one_time_prekey"));
            result.identityX25519 = decode(String.valueOf(identityX.get("publicKey")));
            result.identityEd25519 = decode(String.valueOf(identityEd.get("publicKey")));
            result.signedPrekey = decode(String.valueOf(signed.get("publicKey")));
            result.signedPrekeySignature = decode(String.valueOf(signed.get("signature")));
            result.signedPrekeyId = String.valueOf(signed.get("id"));
            if (oneTime != null) { result.oneTimePrekey = decode(String.valueOf(oneTime.get("publicKey"))); result.oneTimePrekeyId = String.valueOf(oneTime.get("id")); }
            return result;
        }

        private static Map<?, ?> map(Object value) { return value instanceof Map ? (Map<?, ?>) value : null; }
    }

    public static final class DecryptResult {
        public final String text, status, fingerprint;
        public final int protocolVersion;
        DecryptResult(String text, String status, int protocolVersion, String fingerprint) { this.text = text; this.status = status; this.protocolVersion = protocolVersion; this.fingerprint = fingerprint; }
    }

    public static final class CryptoException extends Exception {
        public final String code;
        CryptoException(String code, String message) { super(message); this.code = code; }
    }
}
