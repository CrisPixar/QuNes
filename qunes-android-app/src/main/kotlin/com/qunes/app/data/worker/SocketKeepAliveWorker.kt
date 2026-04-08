package com.qunes.app.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.qunes.app.domain.service.MessagingClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SocketKeepAliveWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val messagingClient: MessagingClient
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        messagingClient.sendQuantumPacket("keepalive", "ping")
        return Result.success()
    }
}
