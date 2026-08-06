package com.qns.data.repository;

import com.qns.data.local.dao.ChatDao;
import com.qns.data.local.dao.MessageDao;
import com.qns.data.local.entity.ChatEntity;
import com.qns.data.local.entity.MessageEntity;
import com.qns.data.remote.ApiService;
import com.qns.data.remote.ServerRepository;
import com.qns.data.remote.WebSocketClient;
import com.qns.data.remote.model.MessageResponse;
import com.qns.utils.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

@Singleton
public class ChatRepository {
    private final ApiService api;
    private final ServerRepository servers;
    private final ChatDao chatDao;
    private final MessageDao messageDao;
    private final WebSocketClient webSocket;

    @Inject
    public ChatRepository(ApiService api, ServerRepository servers, ChatDao chatDao, MessageDao messageDao, WebSocketClient webSocket) {
        this.api = api;
        this.servers = servers;
        this.chatDao = chatDao;
        this.messageDao = messageDao;
        this.webSocket = webSocket;
    }

    public Completable syncChats() {
        return api.getChats(servers.current().api("api/chats"))
            .subscribeOn(Schedulers.io())
            .flatMapCompletable(list -> {
                List<ChatEntity> entities = new ArrayList<>();
                for (Map<String, Object> raw : list) {
                    ChatEntity entity = new ChatEntity();
                    entity.id = value(raw, "id");
                    entity.type = value(raw, "type");
                    entity.name = value(raw, "name");
                    entity.lastMessageAt = number(raw, "last_message_at", number(raw, "lastMessageAt", 0));
                    Map<String, Object> other = map(raw.get("otherUser"));
                    if (other != null) {
                        entity.otherUserId = value(other, "id");
                        entity.otherUsername = value(other, "username");
                        entity.otherUserScam = bool(other, "isScam");
                        entity.otherUserScamReason = value(other, "scamReason");
                        entity.otherUserVerified = bool(other, "isVerified");
                        entity.otherUserOnline = bool(other, "online");
                    }
                    entities.add(entity);
                }
                return chatDao.upsertAll(entities);
            });
    }

    public Flowable<List<ChatEntity>> observeChats() { return chatDao.getAll(); }

    public Single<ChatEntity> getChat(String chatId) { return chatDao.getById(chatId).firstOrError().subscribeOn(Schedulers.io()); }

    public Completable syncMessages(String chatId, String currentUserId) {
        return api.getMessages(servers.current().api("api/chats/" + chatId + "/messages"), null, Constants.MESSAGES_PAGE_SIZE)
            .subscribeOn(Schedulers.io())
            .flatMapCompletable(list -> {
                for (MessageResponse response : list) merge(response, currentUserId, null);
                return Completable.complete();
            });
    }

    public Flowable<List<MessageEntity>> observeMessages(String chatId) { return messageDao.getByChat(chatId); }

    public Completable saveIncoming(MessageResponse response, String currentUserId, String decryptedText, String error, int protocolVersion) {
        return Completable.fromAction(() -> merge(response, currentUserId, decryptedText, error, protocolVersion)).subscribeOn(Schedulers.io());
    }

    public Completable saveOutgoing(String chatId, String currentUserId, String clientMessageId, String payload, String header, String signature, String plainText, int protocolVersion) {
        MessageEntity entity = new MessageEntity();
        entity.id = UUID.randomUUID().toString();
        entity.chatId = chatId;
        entity.senderId = currentUserId;
        entity.clientMessageId = clientMessageId;
        entity.encryptedContent = payload;
        entity.ratchetHeader = header;
        entity.decryptedCache = plainText;
        entity.protocolVersion = protocolVersion;
        entity.isMine = true;
        entity.delivered = false;
        entity.createdAt = System.currentTimeMillis();
        return messageDao.insert(entity).subscribeOn(Schedulers.io());
    }

    public Completable markRead(String messageId) { return messageDao.markRead(messageId).subscribeOn(Schedulers.io()); }
    public Completable markDelivered(String clientMessageId) { return messageDao.markDeliveredByClientId(clientMessageId).subscribeOn(Schedulers.io()); }
    public Completable saveDecrypted(String id, String text) { return messageDao.setDecrypted(id, text).subscribeOn(Schedulers.io()); }
    public Completable saveDecryptionError(String id, String error) { return messageDao.markFailed(id, error).subscribeOn(Schedulers.io()); }

    public Completable createDirectChat(String otherUserId) {
        return api.createChat(servers.current().api("api/chats"), Map.of("type", "direct", "memberIds", List.of(otherUserId)))
            .subscribeOn(Schedulers.io()).flatMapCompletable(ignored -> syncChats());
    }

    private void merge(MessageResponse response, String currentUserId, String decryptedText) { merge(response, currentUserId, decryptedText, null, response.protocolVersion); }

    private void merge(MessageResponse response, String currentUserId, String decryptedText, String error, int protocolVersion) {
        MessageEntity entity = new MessageEntity();
        entity.id = response.id;
        entity.chatId = response.chatId;
        entity.senderId = response.senderId;
        entity.clientMessageId = response.clientMessageId;
        entity.encryptedContent = response.encryptedPayload;
        entity.ratchetHeader = response.ratchetHeader;
        entity.createdAt = response.createdAt;
        entity.delivered = response.delivered;
        entity.read = response.read;
        entity.protocolVersion = protocolVersion;
        entity.isMine = currentUserId != null && currentUserId.equals(response.senderId);
        entity.decryptedCache = decryptedText;
        entity.decryptionError = error;
        entity.decryptionFailed = error != null && !error.isEmpty();
        MessageEntity existing = messageDao.getById(response.id).blockingGet();
        if (existing == null && response.clientMessageId != null && !response.clientMessageId.isEmpty()) existing = messageDao.getByClientMessageId(response.clientMessageId).blockingGet();
        if (existing == null) messageDao.insert(entity).blockingAwait();
        else {
            if (existing.decryptedCache != null && entity.decryptedCache == null) entity.decryptedCache = existing.decryptedCache;
            if (existing.decryptionError != null && entity.decryptionError == null) entity.decryptionError = existing.decryptionError;
            entity.decryptionFailed = entity.decryptedCache == null && entity.decryptionError != null;
            entity.isMine = existing.isMine || entity.isMine;
            messageDao.update(entity).blockingAwait();
        }
    }

    private static String value(Map<String, Object> map, String key) { Object value = map.get(key); return value == null ? null : String.valueOf(value); }
    private static long number(Map<String, Object> map, String key, long fallback) { Object value = map.get(key); return value instanceof Number ? ((Number) value).longValue() : fallback; }
    private static boolean bool(Map<String, Object> map, String key) { Object value = map.get(key); return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value)); }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return value instanceof Map ? (Map<String, Object>) value : null; }
}
