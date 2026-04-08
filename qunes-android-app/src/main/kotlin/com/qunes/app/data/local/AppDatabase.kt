package com.qunes.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.qunes.app.data.local.dao.ChatDao
import com.qunes.app.data.local.dao.MessageDao
import com.qunes.app.data.local.entities.ChatEntity
import com.qunes.app.data.local.entities.MessageEntity
import com.qunes.app.data.local.entities.UserEntity

@Database(
    entities = [UserEntity::class, ChatEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
}