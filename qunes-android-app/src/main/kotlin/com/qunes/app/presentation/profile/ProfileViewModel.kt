package com.qunes.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qunes.app.data.repository.SettingsRepository
import com.qunes.app.domain.service.MessagingClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val messagingClient: MessagingClient
) : ViewModel() {

    val ghostMode = settingsRepository.ghostMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val hideLastSeen = settingsRepository.hideLastSeen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val accentColor = settingsRepository.accentColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "#00E5FF")

    fun toggleGhostMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setGhostMode(enabled)
            syncPrivacyWithServer()
        }
    }

    fun toggleHideLastSeen(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHideLastSeen(enabled)
            syncPrivacyWithServer()
        }
    }

    fun setAccentColor(hex: String) {
        viewModelScope.launch { 
            settingsRepository.setAccentColor(hex) 
        }
    }

    private suspend fun syncPrivacyWithServer() {
        val currentGhost = settingsRepository.ghostMode.first()
        val currentHideSeen = settingsRepository.hideLastSeen.first()
        messagingClient.syncPrivacySettings(currentGhost, currentHideSeen)
    }
}