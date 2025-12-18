package com.workwatch.validation

import com.workwatch.security.CryptoUtils
import com.workwatch.entities.WorkerLogEntry
import java.util.Base64

class MasterValidator(private val cryptoUtils: CryptoUtils) {

    fun validateHashChain(logs: List<WorkerLogEntry>): Boolean {
        if (logs.isEmpty()) return true

        // Validate each log against previous hash
        for (i in logs.indices) {
            val currentLog = logs[i]

            if (i > 0) {
                val previousLog = logs[i - 1]
                // Verify that current log's previousHash matches previous log's currentHash
                if (!currentLog.previousHash.contentEquals(previousLog.currentHash)) {
                    return false
                }
            }
        }
        return true
    }

    fun validateLogEntry(logEntry: WorkerLogEntry): Boolean {
        // Check that all required fields are present
        if (logEntry.previousHash.isEmpty()) return false
        if (logEntry.currentHash.isEmpty()) return false
        if (logEntry.encryptedLogData.isEmpty()) return false
        if (logEntry.checkInTime <= 0) return false

        return true
    }

    fun validateWorkerCredentials(
        workerId: String,
        passwordHash: ByteArray,
        storedHash: ByteArray
    ): Boolean {
        return passwordHash.contentEquals(storedHash)
    }

    fun validateDataIntegrity(
        originalData: ByteArray,
        expectedHash: ByteArray
    ): Boolean {
        val calculatedHash = cryptoUtils.calculateHash(originalData)
        return calculatedHash.contentEquals(expectedHash)
    }

    fun validateLocation(latitude: Double, longitude: Double): Boolean {
        // Basic validation for GPS coordinates
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    fun validateTimestamp(timestamp: Long): Boolean {
        // Check if timestamp is reasonable (not too far in past or future)
        val currentTime = System.currentTimeMillis()
        val maxDifference = 24 * 60 * 60 * 1000L // 24 hours

        return Math.abs(currentTime - timestamp) <= maxDifference
    }
}
