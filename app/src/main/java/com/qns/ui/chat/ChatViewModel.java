package com.qns.ui.chat;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.qns.data.crypto.CryptoSessionManager;
import com.qns.data.local.entity.ChatEntity;
import com.qns.data.local.entity.MessageEntity;
import com.qns.data.remote.ApiService;
import com.qns.data.remote.ServerRepository;
import com.qns.data.remote.WebSocketClient;
import com.qns.data.remote.model.MessageResponse;
import com.qns.data.repository.AuthRepository;
import com.qns.data.repository.ChatRepository;
import com.qns.domain.usecase.SendMessageUseCase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@HiltViewModel
public class ChatViewModel extends ViewModel {
    private final ChatRepository repository;
    private final WebSocketClient webSocket;
    private final SendMessageUseCase sendUseCase;
    private final ApiService api;
    private final ServerRepository servers;
    private final AuthRepository auth;
    private final CryptoSessionManager crypto;
    private final CompositeDisposable bag = new CompositeDisposable();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<String> decrypting = new HashSet<>();

    public final MutableLiveData<List<MessageEntity>> messages = new MutableLiveData<>();
    public final MutableLiveData<ChatEntity> chat = new MutableLiveData<>();
    public final MutableLiveData<Boolean> connected = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> isTyping = new MutableLiveData<>(false);
    public final MutableLiveData<String> error = new MutableLiveData<>();
    private String chatId;
    private String currentUserId = "";

    @Inject
    public ChatViewModel(
        ChatRepository repository,
        WebSocketClient webSocket,
        SendMessageUseCase sendUseCase,
        ApiService api,
        ServerRepository servers,
        AuthRepository auth,
        CryptoSessionManager crypto
    ) {
        this.repository = repository;
        this.webSocket = webSocket;
        this.sendUseCase = sendUseCase;
        this.api = api;
        this.servers = servers;
        this.auth = auth;
        this.crypto = crypto;
        bag.add(auth.getCurrentUserId().subscribeOn(Schedulers.io()).subscribe(id -> currentUserId = id == null ? "" : id, ignored -> {}));
        bag.add(webSocket.connection().observeOn(AndroidSchedulers.mainThread()).subscribe(connected::setValue, ignored -> connected.setValue(false)));
        bag.add(webSocket.events().observeOn(AndroidSchedulers.mainThread()).subscribe(this::handleEvent, value -> error.setValue(value.getMessage())));
    }

    public void init(String id) {
        if (id == null || id.isEmpty() || id.equals(chatId)) return;
        chatId = id;
        bag.add(auth.getCurrentUserId()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(userId -> {
                currentUserId = userId == null ? "" : userId;
                loadChat();
            }, value -> error.setValue(value.getMessage())));
    }

    private void loadChat() {
        bag.add(repository.getChat(chatId).observeOn(AndroidSchedulers.mainThread()).subscribe(chat::setValue, value -> error.setValue(value.getMessage())));
        bag.add(repository.observeMessages(chatId).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(this::publishMessages, value -> error.setValue(value.getMessage())));
        bag.add(repository.syncMessages(chatId, currentUserId).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(() -> {}, value -> error.setValue(value.getMessage())));
    }

    public void sendText(String text) {
        if (chatId == null || text == null || text.trim().isEmpty()) return;
        String clientMessageId = UUID.randomUUID().toString();
        String cleanText = text.trim();
        bag.add(repository.getChat(chatId)
            .flatMap(chatEntity -> api.getKeyBundle(servers.current().api("api/keys/bundle/" + chatEntity.otherUserId))
                .flatMap(bundle -> crypto.encrypt(chatId, chatEntity.otherUserId, cleanText, bundle.get("bundle") instanceof Map ? (Map<String, Object>) bundle.get("bundle") : null)))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(envelope -> {
                sendUseCase.execute(chatId, clientMessageId, envelope, null, null);
                bag.add(repository.saveOutgoing(chatId, currentUserId, clientMessageId, envelope, null, null, cleanText, 2)
                    .observeOn(AndroidSchedulers.mainThread()).subscribe(() -> {}, value -> error.setValue(value.getMessage())));
            }, value -> error.setValue(value.getMessage())));
    }

    public void sendEncrypted(String payload, String ratchetHeader, String signature) {
        if (chatId != null && payload != null && !payload.isEmpty()) sendUseCase.execute(chatId, UUID.randomUUID().toString(), payload, ratchetHeader, signature);
    }

    public void sendTypingIndicator() { if (chatId != null) webSocket.sendTyping(chatId); }
    public void markRead(String messageId) { if (chatId != null) webSocket.sendRead(chatId, messageId); }

    private void handleEvent(Map<String, Object> event) {
        String type = string(event, "type");
        if ("message_sent".equals(type) && chatId != null && chatId.equals(string(event, "chatId"))) {
            String clientId = string(event, "clientMessageId");
            if (!clientId.isEmpty()) bag.add(repository.markDelivered(clientId).subscribe());
            return;
        }
        if ("typing".equals(type) && chatId != null && chatId.equals(string(event, "chatId"))) {
            isTyping.setValue(true);
            mainHandler.removeCallbacksAndMessages("typing");
            mainHandler.postAtTime(() -> isTyping.setValue(false), "typing", SystemClock.uptimeMillis() + 2500L);
            return;
        }
        if (!"message".equals(type) || chatId == null || !chatId.equals(string(event, "chatId"))) return;
        String senderId = string(event, "senderId");
        if (!currentUserId.isEmpty() && currentUserId.equals(senderId)) return;
        MessageResponse response = new MessageResponse();
        response.id = string(event, "id");
        if (response.id.isEmpty()) response.id = string(event, "messageId");
        response.clientMessageId = string(event, "clientMessageId");
        response.chatId = chatId;
        response.senderId = senderId;
        response.encryptedPayload = string(event, "encryptedPayload");
        response.ratchetHeader = string(event, "ratchetHeader");
        response.signature = string(event, "signature");
        response.protocolVersion = (int) number(event, "protocolVersion", 1);
        response.createdAt = number(event, "createdAt", 0);
        response.delivered = false;
        response.read = false;
        bag.add(crypto.decrypt(chatId, senderId, response.encryptedPayload)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(result -> {
                String decryptError = "OK".equals(result.status) ? null : result.status;
                bag.add(repository.saveIncoming(response, currentUserId, result.text, decryptError, result.protocolVersion).subscribe());
            }, value -> {
                String message = value.getMessage() == null ? "Не удалось расшифровать сообщение" : value.getMessage();
                bag.add(repository.saveIncoming(response, currentUserId, null, message, response.protocolVersion).subscribe());
                error.setValue(message);
            }));
    }

    private void publishMessages(List<MessageEntity> source) {
        List<MessageEntity> result = new ArrayList<>(source);
        for (MessageEntity message : result) {
            if (message.decryptedCache != null || message.encryptedContent == null || decrypting.contains(message.id)) continue;
            decrypting.add(message.id);
            bag.add(crypto.decrypt(message.chatId, message.senderId, message.encryptedContent)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(value -> {
                    decrypting.remove(message.id);
                    if ("OK".equals(value.status)) bag.add(repository.saveDecrypted(message.id, value.text).subscribe());
                    else bag.add(repository.saveDecryptionError(message.id, value.status).subscribe());
                }, value -> {
                    decrypting.remove(message.id);
                    bag.add(repository.saveDecryptionError(message.id, value.getMessage() == null ? "decrypt_failed" : value.getMessage()).subscribe());
                }));
        }
        messages.setValue(result);
    }

    private static String string(Map<String, Object> map, String key) { Object value = map.get(key); return value == null ? "" : String.valueOf(value); }
    private static long number(Map<String, Object> map, String key, long fallback) { Object value = map.get(key); return value instanceof Number ? ((Number) value).longValue() : fallback; }

    @Override protected void onCleared() {
        mainHandler.removeCallbacksAndMessages(null);
        bag.clear();
        super.onCleared();
    }
}
