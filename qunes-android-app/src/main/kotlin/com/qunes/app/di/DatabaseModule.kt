package com.qunes.app.di

import android.content.Context
import androidx.room.Room
import com.qunes.app.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        // В реальности пароль должен извлекаться из Secure Hardware / Biometric Auth
        val passhrase = "qunes_quantum_persistence_key_2024".toByteArray()
        val factory = SupportFactory(passhrase)

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "qunes_secure_v1.db"
        )
        .openHelperFactory(factory)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideChatDao(db: AppDatabase) = db.chatDao()

    @Provides
    fun provideMessageDao(db: AppDatabase) = db.messageDao()
}