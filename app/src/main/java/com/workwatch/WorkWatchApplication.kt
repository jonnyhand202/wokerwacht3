package com.workwatch

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WorkWatchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Application initialization
    }
}
