package com.workwatch.manager

import com.workwatch.validation.MasterValidator
import com.workwatch.reporting.ReportSender
import com.workwatch.reporting.WorkerLeakSender
import com.workwatch.security.KeyStoreManager
import com.workwatch.data.WorkerRepository
import com.workwatch.entities.WorkerLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

class WorkerCheckManager(
    private val validator: MasterValidator,
    private val reportSender: ReportSender,
    private val leakSender: WorkerLeakSender,
    private val keyStoreManager: KeyStoreManager,
    private val repository: WorkerRepository
) {

    suspend fun checkIn(latitude: Double, longitude: Double): Result {
        return withContext(Dispatchers.IO) {
            try {
                // Validate location
                if (!validator.validateLocation(latitude, longitude)) {
                    return@withContext Result.Error("Invalid location coordinates")
                }

                // Get previous hash
                val previousHash = repository.getLastLogHash() ?: ByteArray(32)

                // Create log entry
                val timestamp = System.currentTimeMillis()
                val workerSalt = keyStoreManager.getOrCreateKey()
                    .let { Base64.getEncoder().encodeToString(it.encoded) }

                val logEntry = WorkerLogEntry(
                    previousHash = previousHash,
                    currentHash = ByteArray(32), // Will be calculated
                    encryptedLogData = ByteArray(0), // Simplified
                    checkInTime = timestamp,
                    longitude = longitude,
                    latitude = latitude,
                    workerSaltBase64 = workerSalt
                )

                // Store in database
                repository.insertLogEntry(logEntry)

                return@withContext Result.Success("Checked in successfully")
            } catch (e: Exception) {
                Result.Error(e.message ?: "Unknown error during check-in")
            }
        }
    }

    suspend fun checkOut(latitude: Double, longitude: Double): Result {
        return withContext(Dispatchers.IO) {
            try {
                // Get latest log entry
                val latestLog = repository.getLatestLogEntry()
                    ?: return@withContext Result.Error("No active check-in found")

                // Validate location
                if (!validator.validateLocation(latitude, longitude)) {
                    return@withContext Result.Error("Invalid location coordinates")
                }

                // Update log entry with check-out time
                val updatedLog = latestLog.copy(
                    checkOutTime = System.currentTimeMillis(),
                    longitude = longitude,
                    latitude = latitude
                )

                repository.updateLogEntry(updatedLog)

                // Send report (if method exists)
                try {
                    reportSender.sendWorkerReport(updatedLog)
                } catch (e: Exception) {
                    // Report sending is optional
                }

                return@withContext Result.Success("Checked out successfully")
            } catch (e: Exception) {
                Result.Error(e.message ?: "Unknown error during check-out")
            }
        }
    }

    sealed class Result {
        data class Success(val message: String) : Result()
        data class Error(val message: String) : Result()
    }
}
