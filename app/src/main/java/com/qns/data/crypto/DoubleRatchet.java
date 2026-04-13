package com.qns.data.crypto;

import android.util.Base64;
import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;

import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.util.Arrays;

/**
 * Double Ratchet Algorithm (Signal Protocol).
 *
 * Обеспечивает:
 *   Forward Secrecy      — компрометация текущего ключа не раскрывает прошлые
 *   Break-in Recovery    — после компрометации новые ключи снова безопасны
 *   Асинхронная работа   — работает без одновременного онлайна обеих сторон
 *
 * Два механизма:
 *   1. DH Ratchet    — при получении нового DH ключа собеседника
 *   2. Symmetric Ratchet — на каждое сообщение (KDF_CK)
 */
public class DoubleRatchet {

    // ---- State ----
    private byte[] rootKey;
    private byte[] sendChainKey;
    private byte[] recvChainKey;
    private byte[] dhSendPriv;
    private byte[] dhSendPub;
    private byte[] dhRecvPub;
    private int sendN = 0, recvN = 0, prevN = 0;
    private final Map<String, byte[]> skippedKeys = new HashMap<>();
    private static final SecureRandom RNG = new SecureRandom();

    // ---- Инициализация Alice (отправитель первого сообщения) ----

    public void initAsSender(byte[] sharedSecret, byte[] bobDhPub) throws Exception {
        AsymmetricCipherKeyPair kp = genDHPair();
        dhSendPriv = ((X25519PrivateKeyParameters) kp.getPrivate()).getEncoded();
        dhSendPub  = ((X25519PublicKeyParameters)  kp.getPublic()).getEncoded();
        dhRecvPub  = bobDhPub.clone();
        byte[] dh = x25519(dhSendPriv, dhRecvPub);
        byte[][] rk_ck = kdfRK(sharedSecret, dh);
        rootKey      = rk_ck[0];
        sendChainKey = rk_ck[1];
        recvChainKey = null;
        Arrays.fill(dh, (byte) 0);
        Arrays.fill(sharedSecret, (byte) 0);
    }

    // ---- Инициализация Bob (получатель первого сообщения) ----

    public void initAsReceiver(byte[] sharedSecret, byte[] bobDhPriv, byte[] bobDhPub) {
        rootKey      = sharedSecret.clone();
        dhSendPriv   = bobDhPriv.clone();
        dhSendPub    = bobDhPub.clone();
        dhRecvPub    = null;
        sendChainKey = null;
        recvChainKey = null;
        Arrays.fill(sharedSecret, (byte) 0);
    }

    // ---- Шифрование ----

    public EncryptedMessage encrypt(byte[] plaintext) throws Exception {
        byte[][] ck_mk = kdfCK(sendChainKey);
        byte[] newCK = ck_mk[0], mk = ck_mk[1];
        Header hdr = new Header(dhSendPub, sendN, prevN);
        byte[] ct = aeadEncrypt(mk, plaintext, hdr.toBytes());
        Arrays.fill(sendChainKey, (byte) 0);
        sendChainKey = newCK;
        sendN++;
        KeyManager.wipe(mk);
        return new EncryptedMessage(hdr, ct);
    }

    // ---- Расшифровка ----

    public byte[] decrypt(EncryptedMessage msg) throws Exception {
        Header hdr = msg.header;
        String skKey = skipKey(hdr.dhPub, hdr.n);
        if (skippedKeys.containsKey(skKey)) {
            byte[] mk = skippedKeys.remove(skKey);
            byte[] pt = aeadDecrypt(mk, msg.ct, hdr.toBytes());
            KeyManager.wipe(mk); return pt;
        }
        if (dhRecvPub == null || !Arrays.equals(hdr.dhPub, dhRecvPub)) {
            if (recvChainKey != null) skipKeys(recvN, hdr.prevN);
            dhRatchet(hdr.dhPub);
            recvN = 0;
        }
        skipKeys(recvN, hdr.n);
        byte[][] ck_mk = kdfCK(recvChainKey);
        byte[] newCK = ck_mk[0], mk = ck_mk[1];
        Arrays.fill(recvChainKey, (byte) 0);
        recvChainKey = newCK;
        recvN++;
        byte[] pt = aeadDecrypt(mk, msg.ct, hdr.toBytes());
        KeyManager.wipe(mk); return pt;
    }

    // ---- DH Ratchet step ----

    private void dhRatchet(byte[] newRemotePub) throws Exception {
        prevN = sendN; sendN = 0;
        byte[] dh1 = x25519(dhSendPriv, newRemotePub);
        byte[][] rk_recv = kdfRK(rootKey, dh1);
        Arrays.fill(rootKey, (byte) 0); Arrays.fill(dh1, (byte) 0);
        rootKey      = rk_recv[0];
        recvChainKey = rk_recv[1];
        if (dhRecvPub != null) Arrays.fill(dhRecvPub, (byte) 0);
        dhRecvPub = newRemotePub.clone();
        AsymmetricCipherKeyPair newKP = genDHPair();
        Arrays.fill(dhSendPriv, (byte) 0);
        dhSendPriv = ((X25519PrivateKeyParameters) newKP.getPrivate()).getEncoded();
        dhSendPub  = ((X25519PublicKeyParameters)  newKP.getPublic()).getEncoded();
        byte[] dh2 = x25519(dhSendPriv, newRemotePub);
        byte[][] rk_send = kdfRK(rootKey, dh2);
        Arrays.fill(rootKey, (byte) 0); Arrays.fill(dh2, (byte) 0);
        rootKey      = rk_send[0];
        sendChainKey = rk_send[1];
    }

    // ---- KDF функции ----

    private byte[][] kdfRK(byte[] rk, byte[] dhOut) {
        byte[] m = hkdfExpand(dhOut, rk, CryptoConstants.HKDF_INFO_ROOT, 64);
        return new byte[][]{ Arrays.copyOf(m,32), Arrays.copyOfRange(m,32,64) };
    }

    private byte[][] kdfCK(byte[] ck) {
        return new byte[][]{ hmac256(ck, new byte[]{(byte)CryptoConstants.CHAIN_INPUT_CHAIN}),
                             hmac256(ck, new byte[]{(byte)CryptoConstants.CHAIN_INPUT_MESSAGE}) };
    }

    private void skipKeys(int from, int to) throws Exception {
        if (to - from > CryptoConstants.RATCHET_MAX_SKIP)
            throw new Exception("Too many skipped messages");
        while (from < to) {
            byte[][] ck_mk = kdfCK(recvChainKey);
            byte[] newCK = ck_mk[0], mk = ck_mk[1];
            Arrays.fill(recvChainKey, (byte) 0);
            recvChainKey = newCK;
            skippedKeys.put(skipKey(dhRecvPub, from++), mk);
        }
    }

    // ---- AEAD (AES-256-GCM) ----

    private byte[] aeadEncrypt(byte[] mk, byte[] plain, byte[] ad) throws Exception {
        byte[] encKey = Arrays.copyOf(mk, 32);
        byte[] iv     = new byte[CryptoConstants.AES_GCM_NONCE_SIZE]; RNG.nextBytes(iv);
        Cipher c = Cipher.getInstance(CryptoConstants.AES_GCM);
        c.init(Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(encKey,"AES"),
               new GCMParameterSpec(CryptoConstants.AES_GCM_TAG_SIZE, iv));
        c.updateAAD(ad);
        byte[] enc = c.doFinal(plain);
        Arrays.fill(encKey, (byte) 0);
        byte[] out = new byte[12 + enc.length];
        System.arraycopy(iv,0,out,0,12); System.arraycopy(enc,0,out,12,enc.length);
        return out;
    }

    private byte[] aeadDecrypt(byte[] mk, byte[] ct, byte[] ad) throws Exception {
        byte[] encKey = Arrays.copyOf(mk, 32);
        byte[] iv  = Arrays.copyOf(ct, 12);
        byte[] enc = Arrays.copyOfRange(ct, 12, ct.length);
        Cipher c = Cipher.getInstance(CryptoConstants.AES_GCM);
        c.init(Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(encKey,"AES"),
               new GCMParameterSpec(CryptoConstants.AES_GCM_TAG_SIZE, iv));
        c.updateAAD(ad);
        Arrays.fill(encKey, (byte) 0);
        return c.doFinal(enc);
    }

    // ---- Helpers ----

    private static byte[] x25519(byte[] priv, byte[] pub) throws Exception {
        X25519Agreement ag = new X25519Agreement();
        ag.init(new X25519PrivateKeyParameters(priv, 0));
        byte[] out = new byte[ag.getAgreementSize()];
        ag.calculateAgreement(new X25519PublicKeyParameters(pub, 0), out, 0);
        return out;
    }

    private static AsymmetricCipherKeyPair genDHPair() {
        X25519KeyPairGenerator g = new X25519KeyPairGenerator();
        g.init(new KeyGenerationParameters(RNG, 255)); return g.generateKeyPair();
    }

    private static byte[] hmac256(byte[] key, byte[] data) {
        HMac h = new HMac(new SHA256Digest());
        h.init(new KeyParameter(key)); h.update(data,0,data.length);
        byte[] out = new byte[h.getMacSize()]; h.doFinal(out, 0); return out;
    }

    private static byte[] hkdfExpand(byte[] ikm, byte[] salt, byte[] info, int len) {
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(ikm, salt, info));
        byte[] out = new byte[len]; hkdf.generateBytes(out, 0, len); return out;
    }

    private static String skipKey(byte[] dhPub, int n) {
        return Base64.encodeToString(dhPub, Base64.NO_WRAP) + ":" + n;
    }

    // ---- Inner classes ----

    public static class Header {
        public final byte[] dhPub; public final int n, prevN;
        public Header(byte[] dhPub, int n, int prevN) { this.dhPub=dhPub; this.n=n; this.prevN=prevN; }
        public byte[] toBytes() {
            byte[] r = new byte[40];
            System.arraycopy(dhPub,0,r,0,32);
            r[32]=(byte)(n>>24); r[33]=(byte)(n>>16); r[34]=(byte)(n>>8); r[35]=(byte)n;
            r[36]=(byte)(prevN>>24); r[37]=(byte)(prevN>>16); r[38]=(byte)(prevN>>8); r[39]=(byte)prevN;
            return r;
        }
        public static Header fromBytes(byte[] d) {
            byte[] dh = Arrays.copyOf(d,32);
            int n    = ((d[32]&0xFF)<<24)|((d[33]&0xFF)<<16)|((d[34]&0xFF)<<8)|(d[35]&0xFF);
            int prev = ((d[36]&0xFF)<<24)|((d[37]&0xFF)<<16)|((d[38]&0xFF)<<8)|(d[39]&0xFF);
            return new Header(dh,n,prev);
        }
    }

    public static class EncryptedMessage {
        public final Header header; public final byte[] ct;
        public EncryptedMessage(Header h, byte[] ct) { this.header=h; this.ct=ct; }
    }

    // ---- Сериализация состояния для Room ----

    public State exportState() {
        return new State(
            rootKey!=null?rootKey.clone():null,
            sendChainKey!=null?sendChainKey.clone():null,
            recvChainKey!=null?recvChainKey.clone():null,
            dhSendPriv!=null?dhSendPriv.clone():null,
            dhSendPub!=null?dhSendPub.clone():null,
            dhRecvPub!=null?dhRecvPub.clone():null,
            sendN, recvN, prevN
        );
    }

    public void importState(State s) {
        rootKey=s.rootKey!=null?s.rootKey.clone():null;
        sendChainKey=s.sendChainKey!=null?s.sendChainKey.clone():null;
        recvChainKey=s.recvChainKey!=null?s.recvChainKey.clone():null;
        dhSendPriv=s.dhSendPriv!=null?s.dhSendPriv.clone():null;
        dhSendPub=s.dhSendPub!=null?s.dhSendPub.clone():null;
        dhRecvPub=s.dhRecvPub!=null?s.dhRecvPub.clone():null;
        sendN=s.sendN; recvN=s.recvN; prevN=s.prevN;
    }

    public static class State {
        public byte[] rootKey,sendChainKey,recvChainKey,dhSendPriv,dhSendPub,dhRecvPub;
        public int sendN,recvN,prevN;
        public State(byte[] rk,byte[] sck,byte[] rck,byte[] dsp,byte[] dspub,byte[] drp,int sn,int rn,int pn){
            rootKey=rk;sendChainKey=sck;recvChainKey=rck;dhSendPriv=dsp;
            dhSendPub=dspub;dhRecvPub=drp;sendN=sn;recvN=rn;prevN=pn;
        }
    }
}
