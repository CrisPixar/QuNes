package com.qns.domain.model;
public class Chat {
    public String  id, type, name, otherUserId, otherUsername, lastMessagePreview;
    public boolean otherUserOnline, otherUserScam;
    public int     unreadCount;
    public long    lastMessageAt;
}
