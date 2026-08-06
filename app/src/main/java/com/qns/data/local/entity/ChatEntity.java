package com.qns.data.local.entity;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName = "chats")
public class ChatEntity {
    @PrimaryKey @NonNull public String id = "";
    public String type, name, otherUserId, otherUsername, otherUserScamReason;
    public long lastMessageAt, createdAt;
    public boolean otherUserOnline = false, otherUserScam = false, otherUserVerified = false;
    public int     unreadCount = 0;
}
