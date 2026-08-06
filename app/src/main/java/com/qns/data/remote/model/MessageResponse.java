package com.qns.data.remote.model;
public class MessageResponse {
    public String id, chatId, senderId, clientMessageId, encryptedPayload, ratchetHeader, signature;
    public int protocolVersion = 1;
    public long createdAt;
    public boolean delivered, read;
}
