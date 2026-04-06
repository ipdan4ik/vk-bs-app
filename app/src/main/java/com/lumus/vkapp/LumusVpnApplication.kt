package com.lumus.vkapp

import android.app.Application
import com.lumus.vkapp.di.AppContainer

class LumusVpnApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
