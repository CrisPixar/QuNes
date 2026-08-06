package com.qns.data.crypto;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class CryptoProtocolTest {
    @Test
    public void x3dhDerivesTheSameSecretWithAndWithoutOpk() throws Exception {
        AsymmetricCipherKeyPair aliceIdentity = X3DH.generateX25519Pair();
        AsymmetricCipherKeyPair bobIdentity = X3DH.generateX25519Pair();
        AsymmetricCipherKeyPair bobSigned = X3DH.generateX25519Pair();
        AsymmetricCipherKeyPair bobOpk = X3DH.generateX25519Pair();
        byte[] alicePrivate = ((X25519PrivateKeyParameters) aliceIdentity.getPrivate()).getEncoded();
        byte[] alicePublic = ((X25519PublicKeyParameters) aliceIdentity.getPublic()).getEncoded();
        byte[] bobIdentityPrivate = ((X25519PrivateKeyParameters) bobIdentity.getPrivate()).getEncoded();
        byte[] bobIdentityPublic = ((X25519PublicKeyParameters) bobIdentity.getPublic()).getEncoded();
        byte[] bobSignedPrivate = ((X25519PrivateKeyParameters) bobSigned.getPrivate()).getEncoded();
        byte[] bobSignedPublic = ((X25519PublicKeyParameters) bobSigned.getPublic()).getEncoded();
        byte[] bobOpkPrivate = ((X25519PrivateKeyParameters) bobOpk.getPrivate()).getEncoded();
        byte[] bobOpkPublic = ((X25519PublicKeyParameters) bobOpk.getPublic()).getEncoded();

        X3DH.InitResult sender = X3DH.senderInitV2(alicePrivate, bobIdentityPublic, bobSignedPublic, bobOpkPublic);
        byte[] receiver = X3DH.receiverRespondV2(bobIdentityPrivate, bobSignedPrivate, bobOpkPrivate, alicePublic, sender.ephemeralPublicKey);
        assertArrayEquals(sender.sharedSecret, receiver);
    }

    @Test
    public void doubleRatchetSupportsRepliesAndOutOfOrderMessages() throws Exception {
        AsymmetricCipherKeyPair bob = X3DH.generateX25519Pair();
        byte[] bobPrivate = ((X25519PrivateKeyParameters) bob.getPrivate()).getEncoded();
        byte[] bobPublic = ((X25519PublicKeyParameters) bob.getPublic()).getEncoded();
        byte[] secret = X3DH.hkdf("test".getBytes(StandardCharsets.UTF_8), CryptoConstants.HKDF_INFO_SESSION, 32);

        DoubleRatchet alice = new DoubleRatchet();
        DoubleRatchet receiver = new DoubleRatchet();
        alice.initAsSender(secret.clone(), bobPublic);
        receiver.initAsReceiver(secret.clone(), bobPrivate, bobPublic);
        DoubleRatchet.EncryptedMessage first = alice.encrypt("hello".getBytes(StandardCharsets.UTF_8));
        assertEquals("hello", new String(receiver.decrypt(first), StandardCharsets.UTF_8));
        DoubleRatchet.EncryptedMessage reply = receiver.encrypt("world".getBytes(StandardCharsets.UTF_8));
        assertEquals("world", new String(alice.decrypt(reply), StandardCharsets.UTF_8));
    }
}
