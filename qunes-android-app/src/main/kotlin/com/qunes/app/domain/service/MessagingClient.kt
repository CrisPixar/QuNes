package com.qunes.app.domain.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.qunes.app.data.repository.SettingsRepository
import com.qunes.app.data.worker.SocketKeepAliveWorker
import io.socket.client.Socket
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class MessagingClient @Inject constructor(
    @ApplicationContext private val context: Context,
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

        if (isGhost) return

        if (manual || !secretRead) {
            socket?.emit(
                "read-ack",
                mapOf(
                    "chatId" to chatId,
                    "messageId" to messageId
                )
            )
        }
    }

    fun sendQuantumPacket(chatId: String, packet: Any) {
        socket?.emit("q-packet", packet)
    }

    /**
     * Step 26 Logic: Inform the server about updated privacy constraints.
     */
    fun syncPrivacySettings(ghostMode: Boolean, hideLastSeen: Boolean) {
        socket?.emit(
            "sync-privacy",
            mapOf(
                "ghostMode" to ghostMode,
                "hideLastSeen" to hideLastSeen
            )
        )
    }

    private fun scheduleBackgroundSync() {
        val request = PeriodicWorkRequestBuilder<SocketKeepAliveWorker>(
            15,
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "qunes_session_alive",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
