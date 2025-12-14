package com.workwatch.manager

import android.content.Context
import android.util.Log
import com.workwatch.data.WorkerRepository
import com.workwatch.entities.WorkerLogEntry
import com.workwatch.reporting.ReportSender
import com.workwatch.reporting.WorkerLeakSender
import com.workwatch.security.KeyStoreManager
import com.workwatch.validation.MasterValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkerCheckManager(
    val validator: MasterValidator,
    val reportSender: ReportSender,
    val leakSender: WorkerLeakSender,
    val keyStoreManager: KeyStoreManager,
    val repository: WorkerRepository
) {
    private var isCheckedIn = false
    private var lastCheckInTime = 0L

    companion object {
        private const val TAG = "WorkerCheckManager"
    }

    /**
     * Initialize the manager
     */
    suspend fun initializeManager(context: Context): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            // Load configuration from database
            val config = repository.getUserConfig()
            isCheckedIn = false
            Log.d(TAG, "Manager initialized for worker: ${config?.workerId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize manager", e)
            false
        }
    }

    /**
     * Get current worker status
     */
    suspend fun getWorkerStatus(): WorkerStatus = withContext(Dispatchers.IO) {
        return@withContext try {
            val config = repository.getUserConfig()
            val latestLog = repository.getLatestLog()

            WorkerStatus(
                workerId = config?.workerId ?: "Unknown",
                isCheckedIn = isCheckedIn,
                lastCheckInTime = lastCheckInTime,
                lastCheckOutTime = latestLog?.checkOutTime,
                latitude = latestLog?.latitude ?: 0.0,
                longitude = latestLog?.longitude ?: 0.0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get worker status", e)
            WorkerStatus(
                workerId = "Error",
                isCheckedIn = false,
                lastCheckInTime = 0L,
                lastCheckOutTime = null,
                latitude = 0.0,
                longitude = 0.0
            )
        }
    }

    /**
     * Attempt check-in or check-out
     */
    suspend fun attemptCheckInOut(
        checkInTime: Long,
        latitude: Double,
        longitude: Double
    ): CheckInOutResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val config = repository.getUserConfig()
                ?: return@withContext CheckInOutResult.Failure("Configuration not found")

            // Get the previous hash for chain validation
            val previousHash = repository.getLastLogHash() ?: ByteArray(32) { 0x00 }

            // Create new log entry (simplified, real implementation would include encryption)
            val logEntry = WorkerLogEntry(
                previousHash = previousHash,
                currentHash = ByteArray(32), // Would be calculated from encrypted data
                encryptedLogData = ByteArray(0), // Would contain encrypted log data
                checkInTime = checkInTime,
                checkOutTime = if (isCheckedIn) System.currentTimeMillis() else null,
                latitude = latitude,
                longitude = longitude,
                workerSaltBase64 = ""
            )

            // Save to database
            repository.insertLog(logEntry)

            // Toggle check-in state
            isCheckedIn = !isCheckedIn
            if (isCheckedIn) {
                lastCheckInTime = checkInTime
            }

            CheckInOutResult.Success(
                message = if (isCheckedIn) "Checked in successfully" else "Checked out successfully",
                newStatus = WorkerStatus(
                    workerId = config.workerId,
                    isCheckedIn = isCheckedIn,
                    lastCheckInTime = lastCheckInTime,
                    lastCheckOutTime = if (!isCheckedIn) System.currentTimeMillis() else null,
                    latitude = latitude,
                    longitude = longitude
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attempt check in/out", e)
            CheckInOutResult.Failure("Check in/out failed: ${e.message}")
        }
    }
}

/**
 * Worker status data class
 */
data class WorkerStatus(
    val workerId: String,
    val isCheckedIn: Boolean,
    val lastCheckInTime: Long,
    val lastCheckOutTime: Long?,
    val latitude: Double,
    val longitude: Double
)

/**
 * Result of check-in/check-out operation
 */
sealed class CheckInOutResult {
    data class Success(val message: String, val newStatus: WorkerStatus) : CheckInOutResult()
    data class Failure(val error: String) : CheckInOutResult()
}
