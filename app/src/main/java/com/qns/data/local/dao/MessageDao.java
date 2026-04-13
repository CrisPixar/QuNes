package com.qns.data.local.dao;
import androidx.room.*;
import com.qns.data.local.entity.MessageEntity;
import java.util.List;
import io.reactivex.rxjava3.core.*;
@Dao
public interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) Completable insert(MessageEntity m);
    @Insert(onConflict = OnConflictStrategy.IGNORE) Completable insertAll(List<MessageEntity> ms);
    @Update Completable update(MessageEntity m);
    @Query("SELECT * FROM messages WHERE chatId = :cid ORDER BY createdAt ASC") Flowable<List<MessageEntity>> getByChat(String cid);
    @Query("SELECT * FROM messages WHERE chatId = :cid ORDER BY createdAt DESC LIMIT :n") Single<List<MessageEntity>> getRecent(String cid, int n);
    @Query("UPDATE messages SET decryptedCache = :text, decryptionFailed = 0 WHERE id = :id") Completable setDecrypted(String id, String text);
    @Query("UPDATE messages SET decryptionFailed = 1 WHERE id = :id") Completable markFailed(String id);
    @Query("UPDATE messages SET delivered = 1 WHERE id = :id") Completable markDelivered(String id);
    @Query("UPDATE messages SET read = 1 WHERE chatId = :cid") Completable markAllRead(String cid);
    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :cid AND read = 0 AND isMine = 0") Flowable<Integer> unreadCount(String cid);
    @Query("DELETE FROM messages WHERE chatId = :cid") Completable deleteByChat(String cid);
}
