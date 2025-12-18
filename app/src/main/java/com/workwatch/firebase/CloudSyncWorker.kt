package com.workwatch.firebase

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.workwatch.data.WorkerRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.work.HiltWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Background worker for syncing unsynced logs to Firestore
 * Runs periodically to ensure data is backed up to cloud
 */
@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: WorkerRepository,
    private val firestoreService: FirestoreServiceImpl
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CloudSyncWorker"
        private const val SYNC_WORK_NAME = "cloud_sync_work"
        private const val SYNC_INTERVAL_HOURS = 4L

        fun scheduleSync(context: Context) {
            val syncRequest = PeriodicWorkRequestBuilder<CloudSyncWorker>(
                SYNC_INTERVAL_HOURS,
                TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )

            Log.d(TAG, "Cloud sync scheduled every $SYNC_INTERVAL_HOURS hours")
        }

        fun stopSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
            Log.d(TAG, "Cloud sync cancelled")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting cloud sync...")

            // Get unsynced logs
            val unsyncedLogs = repository.getUnsyncedLogs()
            Log.d(TAG, "Found ${unsyncedLogs.size} unsynced logs")

            if (unsyncedLogs.isEmpty()) {
                Log.d(TAG, "No unsynced logs to sync")
                return@withContext Result.success()
            }

            // Sync each log to Firestore
            val syncedLogIds = mutableListOf<Long>()
            for (log in unsyncedLogs) {
                val synced = firestoreService.saveWorkerLog(log, "worker_1")
                if (synced) {
                    syncedLogIds.add(log.id)
                    Log.d(TAG, "Synced log ${log.id}")
                } else {
                    Log.w(TAG, "Failed to sync log ${log.id}")
                }
            }

            // Update sync status in local database
            if (syncedLogIds.isNotEmpty()) {
                repository.updateLogSyncStatus(syncedLogIds)
                Log.d(TAG, "Updated sync status for ${syncedLogIds.size} logs")
            }

            Log.d(TAG, "Cloud sync completed. Synced ${syncedLogIds.size}/${unsyncedLogs.size} logs")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cloud sync: ${e.message}", e)
            // Retry on failure
            Result.retry()
        }
    }
}

/**
 * Extension function on WorkerRepository to mark logs as synced in the database
 */
suspend fun WorkerRepository.updateLogSyncStatus(logIds: List<Long>) {
    try {
        if (logIds.isNotEmpty()) {
            // Get the DAO and update the sync status for each log
            val allLogs = getAllLogs()
            for (log in allLogs) {
                if (log.id in logIds) {
                    updateLogEntry(log.copy(isSynced = true))
                }
            }
            Log.d("CloudSyncWorker", "Updated sync status for ${logIds.size} logs")
        }
    } catch (e: Exception) {
        Log.e("CloudSyncWorker", "Error updating sync status: ${e.message}", e)
    }
}
