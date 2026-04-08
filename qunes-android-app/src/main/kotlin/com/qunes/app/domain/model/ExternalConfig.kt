package com.qunes.app.domain.model

import androidx.annotation.Keep

@Keep
data class ExternalConfig(
    val serverUrl: String,
    val mediaServerUrl: String,
    val turnServers: List<TurnServerConfig>,
    val pqc: PqcConfig,
    val privacyDefaults: PrivacyDefaults
)

@Keep
data class TurnServerConfig(
    val urls: String,
    val username: String,
    val credential: String
)

@Keep
data class PqcConfig(
    val kemAlgorithm: String,
    val sigAlgorithm: String,
    val rotationInterval: Int,
    val fallbackRsa: Boolean
)

@Keep
data class PrivacyDefaults(
    val ghostMode: Boolean,
    val hideLastSeen: Boolean,
    val secretRead: Boolean
)