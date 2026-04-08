package com.qunes.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "qunes_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val GHOST_MODE = booleanPreferencesKey("ghost_mode")
        private val SECRET_READ = booleanPreferencesKey("secret_read")
        private val HIDE_LAST_SEEN = booleanPreferencesKey("hide_last_seen")
        private val ACCENT_COLOR = stringPreferencesKey("accent_color")
    }

    val ghostMode: Flow<Boolean> = context.dataStore.data.map { it[GHOST_MODE] ?: false }
    val secretRead: Flow<Boolean> = context.dataStore.data.map { it[SECRET_READ] ?: true }
    val hideLastSeen: Flow<Boolean> = context.dataStore.data.map { it[HIDE_LAST_SEEN] ?: false }
    val accentColor: Flow<String> = context.dataStore.data.map { it[ACCENT_COLOR] ?: "#00E5FF" }

    suspend fun setGhostMode(enabled: Boolean) {
        context.dataStore.edit { it[GHOST_MODE] = enabled }
    }

    suspend fun setSecretRead(enabled: Boolean) {
        context.dataStore.edit { it[SECRET_READ] = enabled }
    }

    suspend fun setHideLastSeen(enabled: Boolean) {
        context.dataStore.edit { it[HIDE_LAST_SEEN] = enabled }
    }

    suspend fun setAccentColor(hex: String) {
        context.dataStore.edit { it[ACCENT_COLOR] = hex }
    }
}