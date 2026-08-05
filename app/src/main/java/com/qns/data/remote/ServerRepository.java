package com.qns.data.remote;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public final class ServerRepository {
    private static final String PREFS = "qns_servers";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_CUSTOM = "custom";
    private final SharedPreferences prefs;

    @Inject
    public ServerRepository(@ApplicationContext Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<ServerProfile> getServers() {
        List<ServerProfile> result = new ArrayList<>();
        result.add(new ServerProfile("local", "Локальный сервер", "http://10.0.2.2:3000/", "ws://10.0.2.2:3000/ws", false));
        result.add(new ServerProfile("template", "Облачный сервер", "https://your-qns-server.com/", "wss://your-qns-server.com/ws", false));
        String raw = prefs.getString(KEY_CUSTOM, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                result.add(new ServerProfile(
                    item.getString("id"),
                    item.getString("name"),
                    item.getString("baseUrl"),
                    item.getString("wsUrl"),
                    true
                ));
            }
        } catch (Exception ignored) {
            prefs.edit().putString(KEY_CUSTOM, "[]").apply();
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized ServerProfile current() {
        String active = prefs.getString(KEY_ACTIVE, "local");
        for (ServerProfile profile : getServers()) {
            if (profile.id.equals(active)) return profile;
        }
        ServerProfile fallback = getServers().get(0);
        prefs.edit().putString(KEY_ACTIVE, fallback.id).apply();
        return fallback;
    }

    public synchronized void select(String id) {
        for (ServerProfile profile : getServers()) {
            if (profile.id.equals(id)) {
                prefs.edit().putString(KEY_ACTIVE, id).apply();
                return;
            }
        }
        throw new IllegalArgumentException("Unknown server");
    }

    public synchronized ServerProfile addCustom(String name, String baseUrl) {
        String cleanName = name == null ? "" : name.trim();
        String cleanUrl = baseUrl == null ? "" : baseUrl.trim();
        if (cleanName.length() < 2 || cleanName.length() > 40) throw new IllegalArgumentException("Название сервера: 2-40 символов");
        if (!ServerProfile.isValid(cleanUrl)) throw new IllegalArgumentException("Укажите корректный URL http или https");
        String ws = cleanUrl.replaceFirst("^http", "ws");
        ServerProfile profile = new ServerProfile(UUID.randomUUID().toString(), cleanName, cleanUrl, ws + (ws.endsWith("/") ? "ws" : "/ws"), true);
        JSONArray array;
        try {
            array = new JSONArray(prefs.getString(KEY_CUSTOM, "[]"));
        } catch (Exception ignored) {
            array = new JSONArray();
        }
        JSONObject item = new JSONObject();
        try {
            item.put("id", profile.id);
            item.put("name", profile.name);
            item.put("baseUrl", profile.baseUrl);
            item.put("wsUrl", profile.wsUrl);
            array.put(item);
        } catch (Exception error) {
            throw new IllegalStateException("Server profile could not be saved", error);
        }
        prefs.edit().putString(KEY_CUSTOM, array.toString()).putString(KEY_ACTIVE, profile.id).apply();
        return profile;
    }

    public synchronized void removeCustom(String id) {
        JSONArray result = new JSONArray();
        try {
            JSONArray source = new JSONArray(prefs.getString(KEY_CUSTOM, "[]"));
            for (int i = 0; i < source.length(); i++) {
                JSONObject item = source.getJSONObject(i);
                if (!id.equals(item.optString("id"))) result.put(item);
            }
        } catch (Exception ignored) {
        }
        String active = prefs.getString(KEY_ACTIVE, "local");
        if (id.equals(active)) active = "local";
        prefs.edit().putString(KEY_CUSTOM, result.toString()).putString(KEY_ACTIVE, active).apply();
    }
}
