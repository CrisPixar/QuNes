package com.qns.data.remote;

import android.net.Uri;

import androidx.annotation.NonNull;

public final class ServerProfile {
    public final String id;
    public final String name;
    public final String baseUrl;
    public final String wsUrl;
    public final boolean custom;

    public ServerProfile(String id, String name, String baseUrl, String wsUrl, boolean custom) {
        this.id = id;
        this.name = name;
        this.baseUrl = normalizeBase(baseUrl);
        this.wsUrl = normalizeWs(wsUrl);
        this.custom = custom;
    }

    public String api(String path) {
        return baseUrl + path.replaceFirst("^/", "");
    }

    public static String normalizeBase(String value) {
        String url = value == null ? "" : value.trim();
        return url.endsWith("/") ? url : url + "/";
    }

    public static String normalizeWs(String value) {
        String url = value == null ? "" : value.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static boolean isValid(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        Uri uri = Uri.parse(value.trim());
        String scheme = uri.getScheme();
        return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
            && uri.getHost() != null
            && uri.getUserInfo() == null;
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }
}
