package com.berpod.app

import android.app.Application
import com.berpod.app.di.AppContainer
import com.berpod.app.di.DefaultAppContainer

class BerpodApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
