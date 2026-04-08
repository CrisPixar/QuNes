package com.qunes.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val dilithiumPubKey: String,
    val avatarUrl: String?,
    val customAccent: String,
    val ghostMode: Boolean
)