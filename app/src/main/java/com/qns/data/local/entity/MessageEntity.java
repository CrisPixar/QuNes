package com.qns.data.local.entity;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName = "messages",
    indices = { @Index("chatId"), @Index({"chatId","createdAt"}) })
public class MessageEntity {
    @PrimaryKey @NonNull public String id = "";
    public String  chatId, senderId, encryptedContent, ratchetHeader, decryptedCache;
    public boolean decryptionFailed = false, delivered = false, read = false, isMine = false;
    public long    createdAt;
}
