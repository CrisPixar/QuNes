package com.qns.data.repository;

import com.qns.data.local.dao.ChatDao;
import com.qns.data.local.dao.MessageDao;
import com.qns.data.local.entity.ChatEntity;
import com.qns.data.local.entity.MessageEntity;
import com.qns.data.remote.ApiService;
import com.qns.data.remote.WebSocketClient;
import com.qns.data.remote.model.MessageResponse;
import com.qns.utils.Constants;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.schedulers.Schedulers;

@Singleton
public class ChatRepository {
    private final ApiService    api;
    private final ChatDao       chatDao;
    private final MessageDao    msgDao;
    private final WebSocketClient ws;

    @Inject
    public ChatRepository(ApiService api, ChatDao chatDao, MessageDao msgDao, WebSocketClient ws) {
        this.api     = api;
        this.chatDao = chatDao;
        this.msgDao  = msgDao;
        this.ws      = ws;
    }

    /** Загружает список чатов с сервера и сохраняет в локальную БД. */
    public Completable syncChats() {
        return api.getChats()
            .subscribeOn(Schedulers.io())
            .flatMapCompletable(list -> {
                List<ChatEntity> entities = new ArrayList<>();
                for (Map<String,Object> m : list) {
                    ChatEntity e = new ChatEntity();
                    e.id   = str(m,"id");
                    e.type = str(m,"type");
                    e.name = str(m,"name");
                    @SuppressWarnings("unchecked")
                    Map<String,Object> other = (Map<String,Object>) m.get("otherUser");
                    if (other != null) { e.otherUserId = str(other,"id"); e.otherUsername = str(other,"username"); e.otherUserScam = bool(other,"is_scam"); }
                    entities.add(e);
                }
                return chatDao.upsertAll(entities);
            });
    }

    public Flowable<List<ChatEntity>> observeChats() { return chatDao.getAll(); }

    /** Загружает историю сообщений и сохраняет локально. */
    public Completable syncMessages(String chatId) {
        return api.getMessages(chatId, null, Constants.MESSAGES_PAGE_SIZE)
            .subscribeOn(Schedulers.io())
            .flatMapCompletable(list -> {
                List<MessageEntity> entities = new ArrayList<>();
                for (MessageResponse r : list) {
                    MessageEntity e = new MessageEntity();
                    e.id               = r.id;
                    e.chatId           = r.chatId;
                    e.senderId         = r.senderId;
                    e.encryptedContent = r.encryptedPayload;
                    e.ratchetHeader    = r.ratchetHeader;
                    e.createdAt        = r.createdAt;
                    e.delivered        = r.delivered;
                    e.read             = r.read;
                    entities.add(e);
                }
                return msgDao.insertAll(entities);
            });
    }

    public Flowable<List<MessageEntity>> observeMessages(String chatId) { return msgDao.getByChat(chatId); }

    public Completable createDirectChat(String otherUserId) {
        return api.createChat(Map.of("type","direct","memberIds",List.of(otherUserId)))
            .subscribeOn(Schedulers.io())
            .flatMapCompletable(r -> syncChats());
    }

    private static String str(Map<String,Object> m, String k) {
        Object v = m.get(k); return v instanceof String ? (String) v : v != null ? v.toString() : null;
    }
    private static boolean bool(Map<String,Object> m, String k) {
        Object v = m.get(k); return Boolean.TRUE.equals(v) || "1".equals(String.valueOf(v));
    }
}
