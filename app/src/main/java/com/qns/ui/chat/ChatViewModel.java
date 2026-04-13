package com.qns.ui.chat;
import androidx.lifecycle.*;
import com.qns.data.local.entity.MessageEntity;
import com.qns.data.remote.WebSocketClient;
import com.qns.data.repository.ChatRepository;
import com.qns.domain.usecase.SendMessageUseCase;
import java.util.List; import java.util.Map;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.*;

@HiltViewModel
public class ChatViewModel extends ViewModel {
    private final ChatRepository    repo;
    private final WebSocketClient   ws;
    private final SendMessageUseCase sendUC;
    private final CompositeDisposable bag = new CompositeDisposable();
    public final MutableLiveData<List<MessageEntity>> messages = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isTyping = new MutableLiveData<>(false);
    private String chatId;

    @Inject
    public ChatViewModel(ChatRepository repo, WebSocketClient ws, SendMessageUseCase sendUC) {
        this.repo = repo; this.ws = ws; this.sendUC = sendUC;
        // Слушаем входящие WS события
        bag.add(ws.events().observeOn(AndroidSchedulers.mainThread()).subscribe(event -> {
            String type = (String) event.get("type");
            if ("typing".equals(type)) isTyping.setValue(true);
        }));
    }
    public void init(String chatId) {
        this.chatId = chatId;
        bag.add(repo.observeMessages(chatId).observeOn(AndroidSchedulers.mainThread()).subscribe(messages::setValue));
        bag.add(repo.syncMessages(chatId).observeOn(AndroidSchedulers.mainThread()).subscribe());
    }
    /** encryptedPayload уже зашифрован Double Ratchet на клиенте */
    public void sendEncrypted(String encryptedPayload, String ratchetHeader, String signature) {
        sendUC.execute(chatId, encryptedPayload, ratchetHeader, signature);
    }
    public void sendTypingIndicator() { if (chatId != null) ws.sendTyping(chatId); }
    public void markRead(String messageId) { if (chatId != null) ws.sendRead(chatId, messageId); }
    @Override protected void onCleared() { super.onCleared(); bag.clear(); }
}
