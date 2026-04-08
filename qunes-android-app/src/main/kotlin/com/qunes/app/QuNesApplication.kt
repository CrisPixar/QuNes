package com.qunes.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class QuNesApplication : Application(), ImageLoaderFactory {
    
    @Inject
    lateinit var imageLoaderProvider: Provider<ImageLoader>

    override fun newImageLoader(): ImageLoader = imageLoaderProvider.get()
}