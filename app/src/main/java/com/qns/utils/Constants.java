package com.qns.utils;

public final class Constants {
    private Constants() {}

    // ⚠️ Укажите свой сервер перед сборкой:
    public static final String SERVER_BASE_URL = "https://your-qns-server.com/";
    public static final String SERVER_WS_URL   = "wss://your-qns-server.com/ws";

    // HTTP timeouts
    public static final long HTTP_CONNECT_TIMEOUT_MS = 10_000L;
    public static final long HTTP_READ_TIMEOUT_MS    = 30_000L;
    public static final long HTTP_WRITE_TIMEOUT_MS   = 30_000L;
    public static final long WS_RECONNECT_DELAY_MS   = 3_000L;

    // Android Keystore
    public static final String KEYSTORE_PROVIDER     = "AndroidKeyStore";
    public static final String DB_KEY_ALIAS          = "qns_db_key";
    public static final String IDENTITY_KEM_ALIAS    = "qns_identity_kem";
    public static final String IDENTITY_DSA_ALIAS    = "qns_identity_dsa";
    public static final String IDENTITY_X25519_ALIAS = "qns_identity_x25519";
    public static final String IDENTITY_ED25519_ALIAS= "qns_identity_ed25519";
    public static final String SIGNED_PREKEY_ALIAS   = "qns_signed_prekey";

    // Crypto params
    public static final int PREKEYS_COUNT        = 100;
    public static final int RATCHET_MAX_SKIP     = 1000;
    public static final int RATCHET_ROTATION_MSGS= 100;

    // DataStore keys
    public static final String PREF_USER_ID       = "user_id";
    public static final String PREF_USERNAME      = "username";
    public static final String PREF_USER_ROLE     = "user_role";
    public static final String PREF_ACCESS_TOKEN  = "access_token";
    public static final String PREF_REFRESH_TOKEN = "refresh_token";
    public static final String PREF_THEME_MODE    = "theme_mode";
    public static final String PREF_DYNAMIC_COLOR = "dynamic_color";

    public static final int MESSAGES_PAGE_SIZE = 50;
}
