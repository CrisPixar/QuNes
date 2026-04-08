package com.qunes.app.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrafficMonitor @Inject constructor() {
    private val _upBytes = MutableStateFlow(0L)
    val upBytes = _upBytes.asStateFlow()

    private val _downBytes = MutableStateFlow(0L)
    val downBytes = _downBytes.asStateFlow()

    fun logUpload(bytes: Long) {
        _upBytes.value += bytes
    }

    fun logDownload(bytes: Long) {
        _downBytes.value += bytes
    }

    fun reset() {
        _upBytes.value = 0L
        _downBytes.value = 0L
    }
}

class TrafficInterceptor @Inject constructor(
    private val monitor: TrafficMonitor
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        // Log Request Size
        val requestBody = request.body
        if (requestBody != null) {
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            monitor.logUpload(buffer.size)
        }

        val response = chain.proceed(request)

        // Log Response Size
        val responseBody = response.peekBody(Long.MAX_VALUE)
        monitor.logDownload(responseBody.contentLength())

        return response
    }
}