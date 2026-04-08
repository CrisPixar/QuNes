package com.qunes.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val payload: String, // Зашифровано Kyber
    val iv: String,
    val signature: String,
    val type: String,
    val timestamp: Long,
    val isOutgoing: Boolean
)