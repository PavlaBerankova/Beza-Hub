package com.berpod.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.berpod.app.data.remote.appHttpClient
import com.berpod.app.di.AppContainer
import com.berpod.app.di.DefaultAppContainer

class BerpodApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }

    /** Covers load through a client that introduces itself properly; some hosts refuse the default "okhttp/x.y". */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { appHttpClient() })) }
            .build()
}
