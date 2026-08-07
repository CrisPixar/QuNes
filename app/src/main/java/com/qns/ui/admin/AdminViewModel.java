package com.qns.ui.admin;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.qns.data.remote.ApiService;
import com.qns.data.remote.ServerRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@HiltViewModel
public class AdminViewModel extends ViewModel {
    private final ApiService api;
    private final ServerRepository servers;
    private final CompositeDisposable bag = new CompositeDisposable();

    public final MutableLiveData<List<AdminUser>> users = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<AdminStats> stats = new MutableLiveData<>();
    public final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    public final MutableLiveData<String> error = new MutableLiveData<>();

    @Inject
    public AdminViewModel(ApiService api, ServerRepository servers) {
        this.api = api;
        this.servers = servers;
    }

    public void loadData() {
        loading.setValue(true);
        String base = servers.current().baseUrl;
        bag.add(api.getAdminStats(base + "api/admin/stats")
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(map -> stats.setValue(new AdminStats(
                number(map, "totalUsers"),
                number(map, "totalMessages"),
                number(map, "scamUsers"),
                number(map, "activeSessions"),
                number(map, "activeWs")
            )), errorValue -> error.setValue(message(errorValue))));

        bag.add(api.getAdminUsers(base + "api/admin/users", "", 1)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(map -> {
                List<AdminUser> result = new ArrayList<>();
                Object raw = map.get("users");
                if (raw instanceof List) {
                    for (Object item : (List<?>) raw) {
                        if (!(item instanceof Map)) continue;
                        Map<?, ?> user = (Map<?, ?>) item;
                        result.add(new AdminUser(
                            string(user, "id"),
                            string(user, "username"),
                            string(user, "role"),
                            bool(user, "isRootAdmin"),
                            bool(user, "isVerified"),
                            bool(user, "isBetaTester"),
                            bool(user, "isScam"),
                            string(user, "scamReason"),
                            string(user, "lastIp"),
                            number(user, "activeSessions")
                        ));
                    }
                }
                users.setValue(result);
                loading.setValue(false);
            }, errorValue -> {
                loading.setValue(false);
                error.setValue(message(errorValue));
            }));
    }

    public void deleteUser(String id) {
        bag.add(api.deleteAdminUser(servers.current().api("api/admin/users/" + id))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(ignored -> loadData(), errorValue -> error.setValue(message(errorValue))));
    }

    public void toggleScam(String id, boolean scam, String reason) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("isScam", scam);
        body.put("reason", reason == null ? "" : reason);
        bag.add(api.setScam(
                servers.current().api("api/admin/users/" + id + "/scam"),
                body
            )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(ignored -> loadData(), errorValue -> error.setValue(message(errorValue))));
    }

    public void setVerified(String id, boolean enabled) {
        updateFlag(id, "isVerified", enabled);
    }

    public void setBetaTester(String id, boolean enabled) {
        updateFlag(id, "isBetaTester", enabled);
    }

    private void updateFlag(String id, String key, boolean enabled) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put(key, enabled);
        bag.add(api.updateAdminUser(servers.current().api("api/admin/users/" + id), body)
            .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe(ignored -> loadData(), errorValue -> error.setValue(message(errorValue))));
    }

    public void setAdmin(String id, boolean admin) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("role", admin ? "admin" : "user");
        bag.add(api.updateAdminUser(
                servers.current().api("api/admin/users/" + id),
                body
            )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(ignored -> loadData(), errorValue -> error.setValue(message(errorValue))));
    }

    public void revokeUserSessions(String id) {
        bag.add(api.revokeAdminSessions(servers.current().api("api/admin/users/" + id + "/sessions"))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(ignored -> loadData(), errorValue -> error.setValue(message(errorValue))));
    }

    public void deleteAllMessages(String chatId) {
        bag.add(api.deleteAllMessages(servers.current().api("api/admin/chats/" + chatId + "/messages"))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(ignored -> {}, errorValue -> error.setValue(message(errorValue))));
    }

    @Override
    protected void onCleared() {
        bag.clear();
        super.onCleared();
    }

    private static long number(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) return ((Number) value).longValue();
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return 0; }
    }

    private static boolean bool(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static String string(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String message(Throwable error) {
        return com.qns.utils.ErrorMapper.message(error);
    }

    public static class AdminUser {
        public final String id;
        public final String username;
        public final String role;
        public final boolean isRootAdmin;
        public final boolean isVerified;
        public final boolean isBetaTester;
        public final boolean isScam;
        public final String scamReason;
        public final String lastIp;
        public final long activeSessions;

        public AdminUser(String id, String username, String role, boolean root, boolean verified, boolean beta, boolean scam, String scamReason, String lastIp, long sessions) {
            this.id = id;
            this.username = username;
            this.role = role;
            this.isRootAdmin = root;
            this.isVerified = verified;
            this.isBetaTester = beta;
            this.isScam = scam;
            this.scamReason = scamReason;
            this.lastIp = lastIp;
            this.activeSessions = sessions;
        }
    }

    public static class AdminStats {
        public final long totalUsers;
        public final long totalMessages;
        public final long scamUsers;
        public final long activeSessions;
        public final long activeWs;

        public AdminStats(long users, long messages, long scam, long sessions, long ws) {
            totalUsers = users;
            totalMessages = messages;
            scamUsers = scam;
            activeSessions = sessions;
            activeWs = ws;
        }
    }
}
