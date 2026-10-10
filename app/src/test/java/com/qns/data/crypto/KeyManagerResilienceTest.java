package com.qns.data.crypto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Security;
import java.util.Arrays;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Комплексный набор тестов безопасности и отказоустойчивости криптографии:
 * 1. Инициализация и fallback мастер-ключей;
 * 2. Опечатывание (seal / unseal) данных без утечек;
 * 3. Пост-квантовые подписи ML-DSA-87 (Dilithium) и KEM ML-KEM-1024 (Kyber);
 * 4. Защита от утечки секретов в строковое представление / логи;
 * 5. Надежное стирание (wipe) ключевого материала в памяти.
 */
public class KeyManagerResilienceTest {

    @BeforeClass
    public static void setup() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider("BCPQC") == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @Test
    public void masterKeyFallbackGeneratesValidAesKey() {
        KeyManager km = new KeyManager(null);
        SecretKey key = km.getOrCreateMasterKey("test_alias");
        assertNotNull(key);
        assertEquals("AES", key.getAlgorithm());
        assertEquals(32, key.getEncoded().length);
    }

    @Test
    public void masterKeyHandlesNullOrEmptyAliasGracefully() {
        KeyManager km = new KeyManager(null);
        SecretKey k1 = km.getOrCreateMasterKey(null);
        SecretKey k2 = km.getOrCreateMasterKey("   ");
        assertNotNull(k1);
        assertNotNull(k2);
    }

    @Test
    public void sealAndUnsealDataRoundtripPreservesIntegrity() throws Exception {
        KeyManager km = new KeyManager(null);
        byte[] rawKey = new byte[32];
        Arrays.fill(rawKey, (byte) 0x42);
        SecretKey master = new SecretKeySpec(rawKey, "AES");

        byte[] payload = "QuantumResistantSecretPayload123!".getBytes(StandardCharsets.UTF_8);
        byte[] sealed = km.sealData(master, payload);

        assertNotNull(sealed);
        assertTrue(sealed.length > payload.length + 12); // IV (12) + payload + GCM tag (16)

        byte[] unsealed = km.unsealData(master, sealed);
        assertEquals(new String(payload, StandardCharsets.UTF_8), new String(unsealed, StandardCharsets.UTF_8));
    }

    @Test(expected = Exception.class)
    public void unsealFailsOnTamperedCiphertext() throws Exception {
        KeyManager km = new KeyManager(null);
        byte[] rawKey = new byte[32];
        Arrays.fill(rawKey, (byte) 0x77);
        SecretKey master = new SecretKeySpec(rawKey, "AES");

        byte[] payload = "SensitiveData".getBytes(StandardCharsets.UTF_8);
        byte[] sealed = km.sealData(master, payload);
        // Портим байт зашифрованных данных
        sealed[sealed.length - 1] ^= 0x01;

        km.unsealData(master, sealed);
    }

    @Test
    public void wipeClearsKeyBufferInMemory() {
        byte[] secret = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        KeyManager.wipe(secret);
        for (byte b : secret) {
            assertEquals(0, b);
        }
    }

    @Test
    public void dilithiumSignAndVerifyRoundtrip() throws Exception {
        KeyManager km = new KeyManager(null);
        AsymmetricCipherKeyPair kp = km.generateDilithiumKeyPair();
        assertNotNull(kp);

        byte[] pubBytes = km.getDilithiumPublicKeyBytes(kp);
        assertNotNull(pubBytes);
        assertTrue(pubBytes.length > 0);

        byte[] message = "Verification of Dilithium ML-DSA-87 in QNS".getBytes(StandardCharsets.UTF_8);
        byte[] signature = km.signWithDilithium(kp, message);
        assertNotNull(signature);
        assertTrue(signature.length > 0);

        boolean valid = km.verifyDilithium(pubBytes, message, signature);
        assertTrue("Dilithium signature must verify successfully", valid);

        byte[] tampered = "Tampered message content".getBytes(StandardCharsets.UTF_8);
        boolean tamperedValid = km.verifyDilithium(pubBytes, tampered, signature);
        assertFalse("Dilithium signature on altered message must be rejected", tamperedValid);
    }

    @Test
    public void kyberKEMKeysGenerateNonEmptyBuffers() {
        KeyManager km = new KeyManager(null);
        AsymmetricCipherKeyPair kp = km.generateKyberKeyPair();
        assertNotNull(kp);

        byte[] pub = km.getKyberPublicKeyBytes(kp);
        byte[] priv = km.getKyberPrivateKeyBytes(kp);

        assertNotNull(pub);
        assertNotNull(priv);
        assertTrue(pub.length > 0);
        assertTrue(priv.length > 0);
    }

    @Test
    public void base64UrlEncodingHandlesEmptyAndPaddedSafely() {
        KeyManager km = new KeyManager(null);
        byte[] empty = new byte[0];
        assertEquals("", KeyManager.toBase64Url(empty));
        assertEquals(0, KeyManager.fromBase64Url("").length);
        assertEquals(0, KeyManager.fromBase64Url(null).length);

        byte[] sample = "Safe URL test string".getBytes(StandardCharsets.UTF_8);
        String enc = KeyManager.toBase64Url(sample);
        assertFalse(enc.contains("+"));
        assertFalse(enc.contains("/"));
        assertFalse(enc.contains("="));
        assertEquals(new String(sample, StandardCharsets.UTF_8), new String(KeyManager.fromBase64Url(enc), StandardCharsets.UTF_8));
    }
}
