package com.qunes.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val isGroup: Boolean,
    val lastMessage: String?,
    val updatedAt: Long
)