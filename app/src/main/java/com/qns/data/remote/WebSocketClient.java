package com.qns.data.remote;

import android.util.Log;
import com.google.gson.Gson;
import com.qns.utils.Constants;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import okhttp3.*;

@Singleton
public class WebSocketClient {
    private static final String TAG = "QNS_WS";
    private final Gson gson = new Gson();
    private final OkHttpClient http;
    private WebSocket ws;
    private boolean authed = false;
    private String  token;
    private final PublishSubject<Map<String,Object>> events = PublishSubject.create();

    @Inject
    public WebSocketClient(OkHttpClient http) { this.http = http; }

    public void connect(String token) {
        this.token = token; this.authed = false;
        ws = http.newWebSocket(
            new Request.Builder().url(Constants.SERVER_WS_URL).build(),
            new WebSocketListener() {
                @Override public void onOpen(WebSocket s, Response r) {
                    sendRaw(Map.of("type","auth","token",WebSocketClient.this.token));
                }
                @Override @SuppressWarnings("unchecked")
                public void onMessage(WebSocket s, String text) {
                    try {
                        Map<String,Object> e = gson.fromJson(text, Map.class);
                        if ("auth_ok".equals(e.get("type"))) { authed = true; Log.d(TAG,"Authenticated"); }
                        else events.onNext(e);
                    } catch (Exception ex) { Log.e(TAG,"Parse error", ex); }
                }
                @Override public void onFailure(WebSocket s, Throwable t, Response r) {
                    authed = false; Log.e(TAG,"Failure: " + t.getMessage());
                    Observable.timer(Constants.WS_RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS)
                        .subscribe(x -> connect(WebSocketClient.this.token));
                }
                @Override public void onClosed(WebSocket s, int c, String r) { authed = false; }
            }
        );
    }

    public void disconnect() {
        if (ws != null) ws.close(1000, "logout");
        ws = null; authed = false; token = null;
    }

    public void sendMessage(String chatId, String enc, String hdr, String sig) {
        sendRaw(Map.of("type","message","chatId",chatId,"encryptedPayload",enc,
            "ratchetHeader",hdr!=null?hdr:"","signature",sig!=null?sig:""));
    }
    public void sendTyping(String chatId)  { sendRaw(Map.of("type","typing","chatId",chatId)); }
    public void sendRead(String cid, String mid) { sendRaw(Map.of("type","read","chatId",cid,"messageId",mid)); }
    public void sendPing() { sendRaw(Map.of("type","ping")); }

    public Observable<Map<String,Object>> events() { return events.hide(); }
    public boolean isAuthenticated() { return authed; }

    private void sendRaw(Map<String,Object> d) { if (ws != null) ws.send(gson.toJson(d)); }
}
