package com.qunes.core.crypto

object NativePQC {
    init {
        System.loadLibrary("qunes_pqc")
    }

    external fun generateKyberKeys(): ByteArray
    external fun signWithDilithium(data: ByteArray, secretKey: ByteArray): ByteArray
    external fun secureZeroMemory(target: ByteArray)

    fun clearSecrets(vararg keys: ByteArray?) {
        keys.forEach { it?.let { secureZeroMemory(it) } }
    }
}