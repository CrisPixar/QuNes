package com.qns.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.qns.data.local.entity.RatchetSessionEntity;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;

@Dao
public interface RatchetSessionDao {
    @Query("SELECT * FROM ratchet_sessions WHERE chatId = :chatId LIMIT 1")
    Maybe<RatchetSessionEntity> get(String chatId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable upsert(RatchetSessionEntity session);

    @Query("DELETE FROM ratchet_sessions")
    Completable clearAll();

    @Query("DELETE FROM ratchet_sessions WHERE chatId = :chatId")
    Completable delete(String chatId);
}
