package com.qns.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "ratchet_sessions")
public class RatchetSessionEntity {
    @PrimaryKey @NonNull public String chatId = "";
    public String remoteUserId = "";
    public String stateJson = "";
    public String remoteIdentityPublicKey = "";
    public String fingerprint = "";
    public int protocolVersion = 2;
    public boolean initialized = false;
    public long createdAt;
    public long updatedAt;
}
