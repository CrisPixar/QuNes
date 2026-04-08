package com.qunes.app.di

import android.content.Context
import com.qunes.app.domain.model.ExternalConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.json.JSONObject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ConfigModule {

    @Provides
    @Singleton
    fun provideExternalConfig(@ApplicationContext context: Context): ExternalConfig {
        val jsonString = context.assets.open("external-config.json").bufferedReader().use { it.readText() }
        val json = JSONObject(jsonString)
        val pqcJson = json.getJSONObject("pqc")
        val privacyJson = json.getJSONObject("privacy_defaults")

        return ExternalConfig(
            serverUrl = json.getString("server_url"),
            mediaServerUrl = json.getString("media_server_url"),
            turnServers = emptyList(), // Simplified for the architectural skeleton
            pqc = com.qunes.app.domain.model.PqcConfig(
                kemAlgorithm = pqcJson.getString("kem_algorithm"),
                sigAlgorithm = pqcJson.getString("sig_algorithm"),
                rotationInterval = pqcJson.getInt("rotation_interval"),
                fallbackRsa = pqcJson.getBoolean("fallback_rsa")
            ),
            privacyDefaults = com.qunes.app.domain.model.PrivacyDefaults(
                ghostMode = privacyJson.getBoolean("ghost_mode"),
                hideLastSeen = privacyJson.getBoolean("hide_last_seen"),
                secretRead = privacyJson.getBoolean("secret_read")
            )
        )
    }
}