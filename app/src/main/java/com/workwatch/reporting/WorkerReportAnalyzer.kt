package com.workwatch.reporting

import com.google.gson.Gson
import com.workwatch.security.CryptoUtils
import com.workwatch.data.WorkerLogDao
import com.workwatch.entities.WorkerLogEntry

class WorkerReportAnalyzer(
    private val cryptoUtils: CryptoUtils,
    private val logDao: WorkerLogDao,
    private val gson: Gson
) {

    suspend fun analyzeWorkerReport(workerId: String): ReportAnalysis {
        val allLogs = logDao.getAllLogs()

        return ReportAnalysis(
            totalLogs = allLogs.size,
            totalWorkTime = calculateTotalWorkTime(allLogs),
            averageLogSize = if (allLogs.isNotEmpty()) {
                allLogs.map { it.encryptedLogData.size }.sum() / allLogs.size
            } else {
                0
            },
            isValid = validateReportIntegrity(allLogs)
        )
    }

    private fun calculateTotalWorkTime(logs: List<WorkerLogEntry>): Long {
        return logs.mapNotNull { log ->
            if (log.checkOutTime != null) {
                log.checkOutTime - log.checkInTime
            } else {
                null
            }
        }.sum()
    }

    private fun validateReportIntegrity(logs: List<WorkerLogEntry>): Boolean {
        // Validate hash chain integrity
        if (logs.isEmpty()) return true

        for (i in 1 until logs.size) {
            val current = logs[i]
            val previous = logs[i - 1]
            if (!current.previousHash.contentEquals(previous.currentHash)) {
                return false
            }
        }
        return true
    }

    data class ReportAnalysis(
        val totalLogs: Int,
        val totalWorkTime: Long,
        val averageLogSize: Int,
        val isValid: Boolean
    )
}
