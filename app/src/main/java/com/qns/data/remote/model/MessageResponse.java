package com.qns.data.remote.model;
public class MessageResponse {
    public String  id, chatId, senderId, encryptedPayload, ratchetHeader, signature;
    public long    createdAt;
    public boolean delivered, read;
}
