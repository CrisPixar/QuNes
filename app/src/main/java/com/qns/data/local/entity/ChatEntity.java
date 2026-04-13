package com.qns.data.local.entity;
import androidx.annotation.NonNull;
import androidx.room.*;
@Entity(tableName = "chats")
public class ChatEntity {
    @PrimaryKey @NonNull public String id = "";
    public String  type, name, otherUserId, otherUsername;
    public long    lastMessageAt, createdAt;
    public boolean otherUserOnline = false, otherUserScam = false;
    public int     unreadCount = 0;
}
