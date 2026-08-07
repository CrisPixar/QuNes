package com.qns.ui.chat;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.qns.data.crypto.CryptoSessionManager;
import com.qns.data.local.dao.RatchetSessionDao;
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
import io.reactivex.rxjava3.core.Single;
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
    private final RatchetSessionDao ratchetSessions;
    private final CompositeDisposable bag = new CompositeDisposable();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<String> decrypting = new HashSet<>();
    private final Set<String> sendingMessageIds = new HashSet<>();

    public final MutableLiveData<List<MessageEntity>> messages = new MutableLiveData<>();
    public final MutableLiveData<ChatEntity> chat = new MutableLiveData<>();
    public final MutableLiveData<Boolean> connected = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> isTyping = new MutableLiveData<>(false);
    public final MutableLiveData<String> error = new MutableLiveData<>();
    public final MutableLiveData<String> fingerprint = new MutableLiveData<>("");
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
        CryptoSessionManager crypto,
        RatchetSessionDao ratchetSessions
    ) {
        this.repository = repository;
        this.webSocket = webSocket;
        this.sendUseCase = sendUseCase;
        this.api = api;
        this.servers = servers;
        this.auth = auth;
        this.crypto = crypto;
        this.ratchetSessions = ratchetSessions;
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
        // Safety number / fingerprint для TOFU-проверки.
        bag.add(ratchetSessions.get(chatId).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe(session -> fingerprint.setValue(session.fingerprint == null ? "" : session.fingerprint), ignored -> {}));
    }

    public void sendText(String text) {
        if (chatId == null || text == null || text.trim().isEmpty()) return;
        // Защита от двойной отправки при быстром нажатии на кнопку.
        if (sendingMessageIds.size() > 0) return;
        String clientMessageId = UUID.randomUUID().toString();
        String cleanText = text.trim();
        sendingMessageIds.add(clientMessageId);
        bag.add(repository.getChat(chatId)
            .flatMap(chatEntity -> {
                String otherUserId = chatEntity == null ? null : chatEntity.otherUserId;
                if (otherUserId == null || otherUserId.isEmpty()) {
                    return Single.<Map<String, Object>>error(new IllegalStateException("Собеседник не определён"));
                }
                return api.getKeyBundle(servers.current().api("api/keys/bundle/" + otherUserId))
                    .map(bundle -> {
                        if (bundle == null) throw new IllegalStateException("Сервер вернул пустой key bundle");
                        return bundle;
                    });
            })
            .flatMap(bundle -> {
                // Безопасное извлечение внутреннего map: bundle может быть null,
                // либо не содержать ключа "bundle".
                Map<String, Object> inner = extractInnerBundle(bundle);
                if (inner == null) {
                    return Single.<String>error(new IllegalStateException("Key bundle пустой или невалидный"));
                }
                return repository.getChat(chatId).flatMap(chatEntity -> {
                    String targetUser = chatEntity == null ? "" : chatEntity.otherUserId;
                    return crypto.encrypt(chatId, targetUser, cleanText, inner);
                });
            })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(envelope -> {
                sendingMessageIds.remove(clientMessageId);
                sendUseCase.execute(chatId, clientMessageId, envelope, null, null);
                bag.add(repository.saveOutgoing(chatId, currentUserId, clientMessageId, envelope, null, null, cleanText, 2)
                    .observeOn(AndroidSchedulers.mainThread()).subscribe(() -> {}, value -> error.setValue(value.getMessage())));
            }, value -> {
                sendingMessageIds.remove(clientMessageId);
                error.setValue(value.getMessage() == null ? "Не удалось отправить сообщение" : value.getMessage());
            }));
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
        if ("read_receipt".equals(type) && chatId != null && chatId.equals(string(event, "chatId"))) {
            String messageId = string(event, "messageId");
            if (!messageId.isEmpty()) bag.add(repository.markRead(messageId).subscribe());
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
            // Не повторяем расшифровку для «постоянных» ошибок (ключ сменился, невалидная подпись,
            // неподдерживаемая версия). Повторяем только транзиентные состояния (ждём ключ сессии).
            if (message.decryptionFailed && !isTransientDecryptError(message.decryptionError)) continue;
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

    /** Достаёт внутренний key-bundle из ответа /api/keys/bundle/:id, безопасно для null. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractInnerBundle(Map<String, Object> bundle) {
        if (bundle == null) return null;
        Object raw = bundle.get("bundle");
        return raw instanceof Map ? (Map<String, Object>) raw : null;
    }

    private static boolean isTransientDecryptError(String error) {
        return error == null || error.isEmpty() || "WAITING_FOR_SESSION".equals(error);
    }

    @Override protected void onCleared() {
        mainHandler.removeCallbacksAndMessages(null);
        bag.clear();
        sendingMessageIds.clear();
        super.onCleared();
    }
}
