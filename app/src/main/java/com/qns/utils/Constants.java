package com.qns.utils;

public final class Constants {
    private Constants() {}

    public static final String RETROFIT_BASE_URL = "https://placeholder.invalid/";
    public static final long HTTP_CONNECT_TIMEOUT_MS = 10_000L;
    public static final long HTTP_READ_TIMEOUT_MS = 30_000L;
    public static final long HTTP_WRITE_TIMEOUT_MS = 30_000L;
    public static final long WS_RECONNECT_DELAY_MS = 3_000L;

    public static final String DB_KEY_ALIAS = "qns_db_key";
    public static final int PREKEYS_COUNT = 100;
    public static final int RATCHET_MAX_SKIP = 1000;
    public static final int RATCHET_ROTATION_MSGS = 100;

    public static final String PREF_USER_ID = "user_id";
    public static final String PREF_USERNAME = "username";
    public static final String PREF_USER_ROLE = "user_role";
    public static final String PREF_ACCESS_TOKEN = "access_token";
    public static final String PREF_REFRESH_TOKEN = "refresh_token";
    public static final String PREF_THEME_MODE = "theme_mode";
    public static final String PREF_DYNAMIC_COLOR = "dynamic_color";
    public static final int MESSAGES_PAGE_SIZE = 50;
}
