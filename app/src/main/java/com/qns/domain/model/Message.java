package com.qns.domain.model;
public class Message {
    public String  id, chatId, senderId, senderName, text;
    public boolean encrypted, isMine, delivered, read, decryptionFailed;
    public long    createdAt;
}
