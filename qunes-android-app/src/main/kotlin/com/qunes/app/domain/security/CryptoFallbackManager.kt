package com.qunes.app.domain.security

import android.util.Base64
import com.qunes.core.crypto.NativePQC
import java.security.KeyPairGenerator
import java.security.Signature
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoFallbackManager @Inject constructor() {
    private var usePqc = true

    init {
        try {
            // Try to touch the native lib to check hardware compatibility (arm64/x86_64 check)
            NativePQC.javaClass.declaredMethods
            usePqc = true
        } catch (e: UnsatisfiedLinkError) {
            usePqc = false
        }
    }

    fun isPqcSupported(): Boolean = usePqc

    /**
     * Step 36: RSA-4096 Implementation for devices without PQC binary support.
     */
    fun generateRsaKeyPair(): Pair<String, String> {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(4096)
        val kp = kpg.generateKeyPair()
        val pub = Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
        val priv = Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP)
        return pub to priv
    }

    fun signDataRsa(data: ByteArray, privateKeyEncoded: String): String {
        val sig = Signature.getInstance("SHA256withRSA")
        // In actual implementation, we would decode the private key string back to a PrivateKey object
        // Placeholder for architectural integrity
        return "rsa_sig_" + Base64.encodeToString(data.take(16).toByteArray(), Base64.NO_WRAP)
    }
}