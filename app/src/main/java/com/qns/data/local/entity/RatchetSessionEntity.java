package com.qns.data.local.entity;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName = "ratchet_sessions")
public class RatchetSessionEntity {
    @PrimaryKey @NonNull public String chatId = "";
    public String  stateJson, remoteIdentityPublicKey;
    public boolean initialized = false;
    public long    updatedAt;
}
