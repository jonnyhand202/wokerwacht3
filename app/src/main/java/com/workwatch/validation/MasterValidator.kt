package com.workwatch.validation

import android.util.Log
import com.workwatch.entities.WorkerLogEntry
import com.workwatch.security.CryptoUtils

class MasterValidator(private val cryptoUtils: CryptoUtils) {
    companion object {
        private const val TAG = "MasterValidator"
    }

    /**
     * Validate a single log entry against its hash
     */
    fun validateLogEntry(logEntry: WorkerLogEntry): Boolean {
        return try {
            // Recalculate the current hash
            val calculatedHash = cryptoUtils.calculateCurrentHash(
                logEntry.encryptedLogData,
                logEntry.previousHash
            )

            // Compare with stored hash
            calculatedHash.contentEquals(logEntry.currentHash).also {
                if (!it) {
                    Log.w(TAG, "Hash mismatch for log entry ${logEntry.id}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating log entry", e)
            false
        }
    }

    /**
     * Validate a chain of log entries
     */
    fun validateLogChain(logs: List<WorkerLogEntry>): Boolean {
        if (logs.isEmpty()) return true

        var previousHash = logs.first().previousHash

        for ((index, log) in logs.withIndex()) {
            // Check if previous hash matches
            if (!log.previousHash.contentEquals(previousHash)) {
                Log.w(TAG, "Chain broken at index $index: previousHash mismatch")
                return false
            }

            // Check if current hash is valid
            val calculatedHash = cryptoUtils.calculateCurrentHash(
                log.encryptedLogData,
                log.previousHash
            )

            if (!calculatedHash.contentEquals(log.currentHash)) {
                Log.w(TAG, "Invalid hash at index $index")
                return false
            }

            previousHash = log.currentHash
        }

        return true
    }

    /**
     * Get validation status
     */
    fun getValidationStatus(logs: List<WorkerLogEntry>): ValidationStatus {
        return ValidationStatus(
            isValid = validateLogChain(logs),
            totalLogs = logs.size,
            validatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * Validation status result
 */
data class ValidationStatus(
    val isValid: Boolean,
    val totalLogs: Int,
    val validatedAt: Long
)
