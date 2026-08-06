package com.qns.data.local.entity;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName = "messages",
    indices = { @Index("chatId"), @Index({"chatId","createdAt"}) })
public class MessageEntity {
    @PrimaryKey @NonNull public String id = "";
    public String chatId, senderId, clientMessageId, encryptedContent, ratchetHeader, decryptedCache, decryptionError;
    public int protocolVersion = 1;
    public boolean decryptionFailed = false, delivered = false, read = false, isMine = false;
    public long createdAt;
}
