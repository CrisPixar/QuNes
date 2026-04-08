package com.qunes.app.domain.service

import com.qunes.app.data.repository.SettingsRepository
import io.socket.client.Socket
import kotlinx.coroutines.flow.first
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.qunes.app.data.worker.SocketKeepAliveWorker
class MessagingClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository
) {
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagingClient @Inject constructor(
        scheduleBackgroundSync()
    private val settings: SettingsRepository
) {
    private var socket: Socket? = null

    fun attachSocket(socket: Socket) {
        this.socket = socket
    }

    /**
     * Step 25 Logic: Acknowledge reading a message.
     * Only sends to server if Ghost Mode is disabled and Secret Read is off
     * OR if specifically triggered by user manually.
     */
    suspend fun acknowledgeRead(chatId: String, messageId: String, manual: Boolean = false) {
        val isGhost = settings.ghostMode.first()
        val secretRead = settings.secretRead.first()

        if (isGhost) return // Ghost Mode: No presence/read indicators ever.

        if (manual || !secretRead) {
            // Send status back to server only if configured to do so automatically
            // or if the user tapped the 'read' icon manually in Secret Read mode.
            socket?.emit("read-ack", mapOf(
                "chatId" to chatId,
                "messageId" to messageId
            ))
        }
    }
    
    fun sendQuantumPacket(chatId: String, packet: Any) {
        socket?.emit("q-packet", packet)
    }
}

    /**
     * Step 26 Logic: Inform the server about updated privacy constraints.
     */
    fun syncPrivacySettings(ghostMode: Boolean, hideLastSeen: Boolean) {
        socket?.emit("sync-privacy", mapOf(
            "ghostMode" to ghostMode,
            "hideLastSeen" to hideLastSeen
        ))
    }

    private fun scheduleBackgroundSync() {
        val request = PeriodicWorkRequestBuilder<SocketKeepAliveWorker>(15, TimeUnit.MINUTES)
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "qunes_session_alive",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}