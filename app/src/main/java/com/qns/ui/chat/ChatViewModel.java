package com.qns.ui.chat;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.gson.Gson;
import com.qns.data.crypto.IdentityStore;
import com.qns.data.local.entity.ChatEntity;
import com.qns.data.local.entity.MessageEntity;
import com.qns.data.remote.ApiService;
import com.qns.data.remote.ServerRepository;
import com.qns.data.remote.WebSocketClient;
import com.qns.data.remote.model.MessageResponse;
import com.qns.data.repository.ChatRepository;
import com.qns.domain.usecase.SendMessageUseCase;
import com.qns.utils.NotificationHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final IdentityStore identityStore;
    private final NotificationHelper notifications;
    private final CompositeDisposable bag = new CompositeDisposable();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    public final MutableLiveData<List<MessageEntity>> messages = new MutableLiveData<>();
    public final MutableLiveData<ChatEntity> chat = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isTyping = new MutableLiveData<>(false);
    public final MutableLiveData<String> error = new MutableLiveData<>();
    private String chatId;

    @Inject
    public ChatViewModel(
        ChatRepository repository,
        WebSocketClient webSocket,
        SendMessageUseCase sendUseCase,
        ApiService api,
        ServerRepository servers,
        IdentityStore identityStore,
        NotificationHelper notifications
    ) {
        this.repository = repository;
        this.webSocket = webSocket;
        this.sendUseCase = sendUseCase;
        this.api = api;
        this.servers = servers;
        this.identityStore = identityStore;
        this.notifications = notifications;
        bag.add(webSocket.events()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this::handleEvent, value -> error.setValue(value.getMessage())));
    }

    public void init(String id) {
        if (id == null || id.isEmpty() || id.equals(chatId)) return;
        chatId = id;
        bag.add(repository.getChat(id)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(chat::setValue, value -> error.setValue(value.getMessage())));
        bag.add(repository.observeMessages(id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this::publishMessages, value -> error.setValue(value.getMessage())));
        bag.add(repository.syncMessages(id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(() -> {}, value -> error.setValue(value.getMessage())));
    }

    public void sendText(String text) {
        if (chatId == null || text == null || text.trim().isEmpty()) return;
        String cleanText = text.trim();
        bag.add(repository.getChat(chatId)
            .flatMap(chat -> encryptForChat(chat, cleanText))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(envelope -> {
                sendUseCase.execute(chatId, envelope, null, null);
                bag.add(repository.saveOutgoing(chatId, envelope, null, null, cleanText)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> {}, value -> error.setValue(value.getMessage())));
            }, value -> error.setValue(value.getMessage())));
    }

    public void sendEncrypted(String payload, String ratchetHeader, String signature) {
        if (chatId != null && payload != null && !payload.isEmpty()) sendUseCase.execute(chatId, payload, ratchetHeader, signature);
    }

    public void sendTypingIndicator() {
        if (chatId != null) webSocket.sendTyping(chatId);
    }

    public void markRead(String messageId) {
        if (chatId != null) webSocket.sendRead(chatId, messageId);
    }

    private io.reactivex.rxjava3.core.Single<String> encryptForChat(ChatEntity chat, String text) {
        if (chat == null || chat.otherUserId == null || chat.otherUserId.isEmpty()) {
            return io.reactivex.rxjava3.core.Single.error(new IllegalStateException("У чата нет получателя"));
        }
        return api.getKeyBundle(servers.current().api("api/keys/bundle/" + chat.otherUserId))
            .map(bundle -> {
                Object raw = bundle.get("bundle");
                if (!(raw instanceof Map)) throw new IllegalStateException("Ключ получателя недоступен");
                Object rawKey = ((Map<?, ?>) raw).get("identity_x25519");
                if (!(rawKey instanceof Map)) throw new IllegalStateException("Ключ получателя недоступен");
                Object publicKey = ((Map<?, ?>) rawKey).get("publicKey");
                if (!(publicKey instanceof String)) throw new IllegalStateException("Ключ получателя недоступен");
                try {
                    return identityStore.encrypt(text, (String) publicKey);
                } catch (Exception exception) {
                    throw new IllegalStateException("Не удалось зашифровать сообщение", exception);
                }
            });
    }

    private void handleEvent(Map<String, Object> event) {
        String type = string(event, "type");
        if ("typing".equals(type) && chatId != null && chatId.equals(string(event, "chatId"))) {
            isTyping.setValue(true);
            mainHandler.removeCallbacksAndMessages("typing");
            mainHandler.postAtTime(() -> isTyping.setValue(false), "typing", SystemClock.uptimeMillis() + 2500L);
            return;
        }
        if ("message".equals(type) && chatId != null && chatId.equals(string(event, "chatId"))) {
            MessageResponse response = new MessageResponse();
            response.id = string(event, "id");
            if (response.id.isEmpty()) response.id = string(event, "messageId");
            response.chatId = chatId;
            response.senderId = string(event, "senderId");
            response.encryptedPayload = string(event, "encryptedPayload");
            response.ratchetHeader = string(event, "ratchetHeader");
            response.signature = string(event, "signature");
            response.createdAt = number(event, "createdAt");
            response.delivered = false;
            response.read = false;
            String decrypted = null;
            try { decrypted = identityStore.decrypt(response.encryptedPayload); } catch (Exception ignored) {}
            String text = decrypted;
            notifications.showMessage("Новое сообщение", text == null ? "Зашифрованное сообщение" : text);
            bag.add(repository.saveIncoming(response, text)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {}, value -> error.setValue(value.getMessage())));
            return;
        }
        if ("read_receipt".equals(type) && chatId != null && chatId.equals(string(event, "chatId"))) {
            repository.markRead(string(event, "messageId"))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {}, value -> error.setValue(value.getMessage()));
        }
    }

    private void publishMessages(List<MessageEntity> source) {
        List<MessageEntity> result = new ArrayList<>(source);
        for (MessageEntity message : result) {
            if (message.decryptedCache != null || message.encryptedContent == null || message.isMine) continue;
            try { message.decryptedCache = identityStore.decrypt(message.encryptedContent); } catch (Exception ignored) {}
        }
        messages.setValue(result);
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static long number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    @Override
    protected void onCleared() {
        mainHandler.removeCallbacksAndMessages(null);
        bag.clear();
        super.onCleared();
    }
}
