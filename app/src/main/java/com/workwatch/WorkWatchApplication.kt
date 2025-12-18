package com.workwatch

import android.app.Application
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.workwatch.firebase.CloudSyncWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WorkWatchApplication : Application() {

    companion object {
        private const val TAG = "WorkWatchApp"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        try {
            Firebase.initialize(this)
            Log.d(TAG, "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase: ${e.message}", e)
        }

        // Schedule cloud sync worker
        try {
            CloudSyncWorker.scheduleSync(this)
            Log.d(TAG, "Cloud sync worker scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling cloud sync: ${e.message}", e)
        }

        Log.d(TAG, "Application initialization complete")
    }
}
