package com.qns.data.crypto;

public final class CryptoConstants {
    private CryptoConstants() {}

    // AES-256-GCM
    public static final String AES_GCM             = "AES/GCM/NoPadding";
    public static final int    AES_KEY_SIZE         = 32;
    public static final int    AES_GCM_NONCE_SIZE   = 12;   // 96-bit
    public static final int    AES_GCM_TAG_SIZE      = 128;  // bits

    // Double Ratchet
    public static final int    RATCHET_KEY_SIZE     = 32;
    public static final int    RATCHET_MAX_SKIP     = 1000;
    public static final int    CHAIN_INPUT_CHAIN    = 0x02;
    public static final int    CHAIN_INPUT_MESSAGE  = 0x01;

    // HKDF info строки (домен-разделение)
    public static final byte[] HKDF_INFO_ROOT       = "QNS_RootKey_v1".getBytes();
    public static final byte[] HKDF_INFO_CHAIN      = "QNS_ChainKey_v1".getBytes();
    public static final byte[] HKDF_INFO_MESSAGE    = "QNS_MessageKey_v1".getBytes();
    public static final byte[] HKDF_INFO_X3DH       = "QNS_X3DH_v1".getBytes();
    public static final byte[] HKDF_INFO_SESSION    = "QNS_Session_v1".getBytes();

    // Bouncy Castle provider name
    public static final String BC_PROVIDER          = "BC";
}
