package com.workwatch.reporting

import android.content.Context
import android.location.Location
import android.util.Base64
import com.workwatch.data.WorkerRepository
import com.workwatch.entities.CellTowerData
import com.workwatch.entities.LogData
import com.workwatch.entities.WorkerLogEntry
import com.workwatch.security.CellTowerInfo
import com.workwatch.security.CryptoUtils
import com.workwatch.security.TelephonyCollector
import com.google.gson.Gson
import javax.crypto.SecretKey

sealed class SaveLogResult {
    data class Success(val entry: WorkerLogEntry) : SaveLogResult()
    data class Failure(val error: SaveLogError) : SaveLogResult()
}

enum class SaveLogError {
    ENCRYPTION_FAILED,
    DATABASE_ERROR
}

/**
 * Report Sender - Log oluşturucu
 * Cell Tower bilgisi de eklenir! 🚨
 */
class ReportSender(
    private val cryptoUtils: CryptoUtils,
    private val gson: Gson,
    private val telephonyCollector: TelephonyCollector
) {
    private val GENESIS_HASH = ByteArray(32) { 0x00 }

    suspend fun getPreviousLogHash(repository: WorkerRepository): ByteArray {
        return repository.getLastLogHash() ?: GENESIS_HASH
    }

    suspend fun saveNewLogEntry(
        context: Context,
        repository: WorkerRepository,
        workerKey: SecretKey,
        currentLocation: Location,
        workerSalt: ByteArray,
        checkInTime: Long,
        checkOutTime: Long? = null,
        previousHashOverride: ByteArray? = null
    ): SaveLogResult {
        return try {
            val previousHash = previousHashOverride ?: getPreviousLogHash(repository)

            // 🚨 CELL TOWER BİLGİSİNİ TOPLA!
            val cellTowerInfo = telephonyCollector.collectCellTowerInfo(context)
            val cellTowerData = when (cellTowerInfo) {
                is CellTowerInfo.Available -> CellTowerData(
                    cellId = cellTowerInfo.cellId,
                    lac = cellTowerInfo.lac,
                    mcc = cellTowerInfo.mcc,
                    mnc = cellTowerInfo.mnc,
                    operatorName = cellTowerInfo.operatorName,
                    operatorCode = cellTowerInfo.operatorCode,
                    networkType = cellTowerInfo.networkType,
                    signalStrength = cellTowerInfo.signalStrength,
                    timestamp = cellTowerInfo.timestamp
                )

                is CellTowerInfo.NoPermission -> {
                    android.util.Log.w("ReportSender", "Cell tower: No permission")
                    null
                }

                is CellTowerInfo.NotAvailable -> {
                    android.util.Log.w("ReportSender", "Cell tower: ${cellTowerInfo.reason}")
                    null
                }
            }

            // LogData oluştur (Cell Tower dahil!)
            val rawLogData = LogData(
                checkInTime = checkInTime,
                checkOutTime = checkOutTime,
                latitude = currentLocation.latitude,
                longitude = currentLocation.longitude,
                cellTower = cellTowerData  // 🚨 CELL TOWER HASH ZİNCİRİNE EKLENDİ!
            )

            // Log et
            if (cellTowerData != null) {
                android.util.Log.d(
                    "ReportSender",
                    "Cell Tower: ${cellTowerData.cellId} | ${cellTowerData.operatorName} | ${cellTowerData.networkType}"
                )
            } else {
                android.util.Log.w("ReportSender", "Cell Tower bilgisi yok")
            }

            val logDataBytes = gson.toJson(rawLogData).toByteArray(Charsets.UTF_8)
            val encryptedResult = try {
                cryptoUtils.encryptLogData(logDataBytes, workerKey)
            } catch (e: Exception) {
                return SaveLogResult.Failure(SaveLogError.ENCRYPTION_FAILED)
            }

            val fullEncryptedData =
                encryptedResult.initializationVector + encryptedResult.cipherText
            val newCurrentHash = cryptoUtils.calculateCurrentHash(fullEncryptedData, previousHash)

            val newEntry = WorkerLogEntry(
                previousHash = previousHash,
                currentHash = newCurrentHash,
                encryptedLogData = fullEncryptedData,
                isSynced = false,
                checkInTime = rawLogData.checkInTime,
                latitude = rawLogData.latitude,
                longitude = rawLogData.longitude,
                checkOutTime = rawLogData.checkOutTime,
                workerSaltBase64 = Base64.encodeToString(workerSalt, Base64.NO_WRAP)
            )

            try {
                repository.insertLogEntry(newEntry)
                SaveLogResult.Success(newEntry)
            } catch (e: Exception) {
                SaveLogResult.Failure(SaveLogError.DATABASE_ERROR)
            }
        } catch (e: Exception) {
            SaveLogResult.Failure(SaveLogError.DATABASE_ERROR)
        }
    }

    suspend fun sendWorkerReport(logEntry: WorkerLogEntry) {
        try {
            // Prepare report JSON
            val reportData = mapOf(
                "workerId" to "worker_1",
                "timestamp" to logEntry.checkInTime,
                "checkOutTime" to logEntry.checkOutTime,
                "latitude" to logEntry.latitude,
                "longitude" to logEntry.longitude,
                "hashChain" to android.util.Base64.encodeToString(
                    logEntry.currentHash,
                    android.util.Base64.NO_WRAP
                )
            )
            val reportJson = gson.toJson(reportData)
            android.util.Log.d("ReportSender", "Report sent: $reportJson")
        } catch (e: Exception) {
            android.util.Log.e("ReportSender", "Error sending report: ${e.message}")
        }
    }
}
