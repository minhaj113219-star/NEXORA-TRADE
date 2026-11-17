package com.nexora.trade

import android.app.Application
import com.nexora.trade.di.AppContainer
import com.nexora.trade.di.DefaultAppContainer

class NexoraApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
    }
}