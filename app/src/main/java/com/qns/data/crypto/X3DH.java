package com.qns.data.crypto;

import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * X3DH — Extended Triple Diffie-Hellman (Signal Protocol).
 *
 * Alice (отправитель первого сообщения) вычисляет:
 *   DH1 = X25519(IK_A,  SPK_B)   — связь identity Alice со signed prekey Bob
 *   DH2 = X25519(EK_A,  IK_B)    — ephemeral Alice с identity Bob
 *   DH3 = X25519(EK_A,  SPK_B)   — ephemeral Alice со signed prekey Bob
 *   DH4 = X25519(EK_A,  OPK_B)   — (опционально) one-time prekey
 *   SK  = HKDF(DH1 ‖ DH2 ‖ DH3 ‖ [DH4])
 *
 * Bob получает SK из тех же слагаемых (симметрично).
 */
public final class X3DH {

    public static class InitResult {
        public final byte[] sharedSecret;
        public final byte[] ephemeralPublicKey;  // EK_A для передачи Bob'у
        public InitResult(byte[] sk, byte[] epk) { this.sharedSecret = sk; this.ephemeralPublicKey = epk; }
    }

    /** Alice инициирует сессию. */
    public static InitResult senderInit(
        byte[] aliceIdentityPrivate,
        byte[] bobIdentityPublic,
        byte[] bobSignedPrePublic,
        byte[] bobOneTimePrePublic  // может быть null
    ) throws Exception {
        AsymmetricCipherKeyPair ekPair = generateX25519Pair();
        X25519PrivateKeyParameters ekPriv = (X25519PrivateKeyParameters) ekPair.getPrivate();
        X25519PublicKeyParameters  ekPub  = (X25519PublicKeyParameters)  ekPair.getPublic();

        byte[] dh1 = x25519(aliceIdentityPrivate, bobSignedPrePublic);
        byte[] dh2 = x25519(ekPriv.getEncoded(),  bobIdentityPublic);
        byte[] dh3 = x25519(ekPriv.getEncoded(),  bobSignedPrePublic);

        byte[] ikm;
        if (bobOneTimePrePublic != null) {
            byte[] dh4 = x25519(ekPriv.getEncoded(), bobOneTimePrePublic);
            ikm = concat(dh1, dh2, dh3, dh4);
            Arrays.fill(dh4, (byte) 0);
        } else {
            ikm = concat(dh1, dh2, dh3);
        }
        Arrays.fill(dh1, (byte) 0); Arrays.fill(dh2, (byte) 0); Arrays.fill(dh3, (byte) 0);

        byte[] sk = hkdf(ikm, CryptoConstants.HKDF_INFO_X3DH, 32);
        Arrays.fill(ikm, (byte) 0);
        return new InitResult(sk, ekPub.getEncoded());
    }

    /** Bob получает SK из сообщения Alice. */
    public static byte[] receiverRespond(
        byte[] bobIdentityPrivate,
        byte[] bobSignedPrePrivate,
        byte[] bobOneTimePrePrivate,  // null если не было
        byte[] aliceIdentityPublic,
        byte[] aliceEphemeralPublic
    ) throws Exception {
        byte[] dh1 = x25519(bobSignedPrePrivate,  aliceIdentityPublic);
        byte[] dh2 = x25519(bobIdentityPrivate,   aliceEphemeralPublic);
        byte[] dh3 = x25519(bobSignedPrePrivate,  aliceEphemeralPublic);

        byte[] ikm;
        if (bobOneTimePrePrivate != null) {
            byte[] dh4 = x25519(bobOneTimePrePrivate, aliceEphemeralPublic);
            ikm = concat(dh1, dh2, dh3, dh4);
            Arrays.fill(dh4, (byte) 0);
        } else {
            ikm = concat(dh1, dh2, dh3);
        }
        Arrays.fill(dh1, (byte) 0); Arrays.fill(dh2, (byte) 0); Arrays.fill(dh3, (byte) 0);

        byte[] sk = hkdf(ikm, CryptoConstants.HKDF_INFO_X3DH, 32);
        Arrays.fill(ikm, (byte) 0);
        return sk;
    }

    // ---- helpers ----

    static byte[] x25519(byte[] priv, byte[] pub) throws Exception {
        X25519Agreement ag = new X25519Agreement();
        ag.init(new X25519PrivateKeyParameters(priv, 0));
        byte[] out = new byte[ag.getAgreementSize()];
        ag.calculateAgreement(new X25519PublicKeyParameters(pub, 0), out, 0);
        return out;
    }

    public static byte[] hkdf(byte[] ikm, byte[] info, int len) {
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(ikm, null, info));
        byte[] out = new byte[len];
        hkdf.generateBytes(out, 0, len);
        return out;
    }

    static AsymmetricCipherKeyPair generateX25519Pair() {
        X25519KeyPairGenerator gen = new X25519KeyPairGenerator();
        gen.init(new KeyGenerationParameters(new SecureRandom(), 255));
        return gen.generateKeyPair();
    }

    static byte[] concat(byte[]... arrays) {
        int len = 0; for (byte[] a : arrays) len += a.length;
        byte[] r = new byte[len]; int pos = 0;
        for (byte[] a : arrays) { System.arraycopy(a, 0, r, pos, a.length); pos += a.length; }
        return r;
    }
}
