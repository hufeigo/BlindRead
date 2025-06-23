package com.hihi.ttsserver

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class App : Application() {
    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 手动初始化 Chaquopy
        if (! Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }
} 