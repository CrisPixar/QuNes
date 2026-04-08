package com.qunes.app.presentation.call

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor() : ViewModel() {
    data class CallState(
        val isVideoEnabled: Boolean = true,
        val isMuted: Boolean = false,
        val connectionStatus: String = "SECURE TUNNEL ESTABLISHED",
        val protectionLevel: String = "CRYSTALS-KYBER-1024",
        val callDuration: String = "00:00"
    )

    private val _state = MutableStateFlow(CallState())
    val state = _state.asStateFlow()

    fun endCall() {
        // Logical disconnect of peer connections
    }

    fun toggleVideo() {
        _state.value = _state.value.copy(isVideoEnabled = !_state.value.isVideoEnabled)
    }

    fun toggleMute() {
        _state.value = _state.value.copy(isMuted = !_state.value.isMuted)
    }
}