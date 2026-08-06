package com.qns.data.remote;

import android.util.Log;

import com.google.gson.Gson;
import com.qns.utils.Constants;
import com.qns.utils.NotificationHelper;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

@Singleton
public class WebSocketClient {
    private static final String TAG = "QNS_WS";
    private final Gson gson = new Gson();
    private final OkHttpClient http;
    private final ServerRepository servers;
    private final NotificationHelper notifications;
    private final PublishSubject<Map<String, Object>> events = PublishSubject.create();
    private final BehaviorSubject<Boolean> connection = BehaviorSubject.createDefault(false);
    private WebSocket socket;
    private String token;
    private boolean authenticated;
    private boolean stopped;

    @Inject
    public WebSocketClient(OkHttpClient http, ServerRepository servers, NotificationHelper notifications) {
        this.http = http;
        this.servers = servers;
        this.notifications = notifications;
    }

    public synchronized void connect(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) return;
        stopped = false;
        token = accessToken;
        authenticated = false;
        connection.onNext(false);
        if (socket != null) socket.close(1000, "reconnect");
        Request request = new Request.Builder().url(servers.current().wsUrl).build();
        socket = http.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                sendRaw(Map.of("type", "auth", "token", WebSocketClient.this.token));
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    Map<String, Object> event = gson.fromJson(text, Map.class);
                    if ("auth_ok".equals(event.get("type"))) {
                        authenticated = true;
                        connection.onNext(true);
                        Log.d(TAG, "Authenticated");
                    } else {
                        if ("message".equals(event.get("type"))) notifications.showMessage("Новое сообщение", "Зашифрованное сообщение");
                        events.onNext(event);
                    }
                } catch (Exception error) {
                    Log.e(TAG, "Message parse failed", error);
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable error, Response response) {
                authenticated = false;
                connection.onNext(false);
                Log.w(TAG, "Connection failed", error);
                scheduleReconnect();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                authenticated = false;
                connection.onNext(false);
                if (!stopped) scheduleReconnect();
            }
        });
    }

    public synchronized void disconnect() {
        stopped = true;
        if (socket != null) socket.close(1000, "logout");
        socket = null;
        authenticated = false;
        token = null;
        connection.onNext(false);
    }

    public void sendMessage(String chatId, String encryptedPayload, String ratchetHeader, String signature) {
        sendRaw(Map.of(
            "type", "message",
            "chatId", chatId,
            "encryptedPayload", encryptedPayload,
            "ratchetHeader", ratchetHeader == null ? "" : ratchetHeader,
            "signature", signature == null ? "" : signature
        ));
    }

    public void sendTyping(String chatId) {
        if (authenticated) sendRaw(Map.of("type", "typing", "chatId", chatId));
    }

    public void sendRead(String chatId, String messageId) {
        if (authenticated) sendRaw(Map.of("type", "read", "chatId", chatId, "messageId", messageId));
    }

    public void sendPing() {
        if (authenticated) sendRaw(Map.of("type", "ping"));
    }

    public Observable<Map<String, Object>> events() {
        return events.hide();
    }

    public Observable<Boolean> connection() {
        return connection.hide().distinctUntilChanged();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    private void scheduleReconnect() {
        final String reconnectToken = token;
        if (stopped || reconnectToken == null || reconnectToken.isEmpty()) return;
        Observable.timer(Constants.WS_RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS)
            .subscribe(ignored -> {
                if (!stopped && reconnectToken.equals(token)) connect(reconnectToken);
            });
    }

    private synchronized void sendRaw(Map<String, Object> data) {
        if (socket != null) socket.send(gson.toJson(data));
    }
}
