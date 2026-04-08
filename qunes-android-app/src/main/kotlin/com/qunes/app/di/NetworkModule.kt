package com.qunes.app.di

import com.qunes.app.data.network.TrafficInterceptor
import com.qunes.app.data.network.TrafficMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideTrafficInterceptor(monitor: TrafficMonitor): TrafficInterceptor {
        return TrafficInterceptor(monitor)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(interceptor: TrafficInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }
}