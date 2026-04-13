package com.qns.data.local.dao;
import androidx.room.*;
import com.qns.data.local.entity.ChatEntity;
import java.util.List;
import io.reactivex.rxjava3.core.*;
@Dao
public interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) Completable upsert(ChatEntity c);
    @Insert(onConflict = OnConflictStrategy.REPLACE) Completable upsertAll(List<ChatEntity> cs);
    @Query("SELECT * FROM chats ORDER BY lastMessageAt DESC") Flowable<List<ChatEntity>> getAll();
    @Query("SELECT * FROM chats WHERE id = :id LIMIT 1") Flowable<ChatEntity> getById(String id);
    @Query("UPDATE chats SET otherUserOnline = :online WHERE otherUserId = :uid") Completable setOnline(String uid, boolean online);
    @Query("UPDATE chats SET unreadCount = unreadCount + 1 WHERE id = :id") Completable incUnread(String id);
    @Query("UPDATE chats SET unreadCount = 0 WHERE id = :id") Completable clearUnread(String id);
    @Query("DELETE FROM chats WHERE id = :id") Completable delete(String id);
}
