package com.qunes.app.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qunes.app.data.network.TrafficMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val trafficMonitor: TrafficMonitor
) : ViewModel() {

    data class TrafficPoint(val upload: Long, val download: Long)

    private val _history = MutableStateFlow<List<TrafficPoint>>(emptyList())
    val history: StateFlow<List<TrafficPoint>> = _history

    init {
        viewModelScope.launch {
            var lastUp = 0L
            var lastDown = 0L
            
            while (true) {
                val currentUp = trafficMonitor.upBytes.value
                val currentDown = trafficMonitor.downBytes.value
                
                val diffUp = if (lastUp == 0L) 0L else currentUp - lastUp
                val diffDown = if (lastDown == 0L) 0L else currentDown - lastDown
                
                val newList = _history.value.takeLast(59) + TrafficPoint(diffUp, diffDown)
                _history.value = newList
                
                lastUp = currentUp
                lastDown = currentDown
                
                delay(1000)
            }
        }
    }
}