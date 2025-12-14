package com.workwatch.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "worker_logs")
data class WorkerLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val previousHash: ByteArray,
    val currentHash: ByteArray,
    val encryptedLogData: ByteArray,
    val isSynced: Boolean = false,
    val checkInTime: Long,
    val longitude: Double,
    val latitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val provider: String = "fused",
    val checkOutTime: Long? = null,
    val workerSaltBase64: String,
    val keyVersion: Int = 1
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as WorkerLogEntry
        if (id != other.id) return false
        if (!previousHash.contentEquals(other.previousHash)) return false
        if (!currentHash.contentEquals(other.currentHash)) return false
        if (!encryptedLogData.contentEquals(other.encryptedLogData)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + previousHash.contentHashCode()
        result = 31 * result + currentHash.contentHashCode()
        result = 31 * result + encryptedLogData.contentHashCode()
        return result
    }
}

@Entity(tableName = "user_config")
data class UserConfig(
    @PrimaryKey val configId: Int = 1,
    val workerId: String,
    val phoneNumber: String? = null,  // Kullanıcıdan manuel alınır
    val adminIpAddress: String? = null,
    val p2pPort: Int = 8080,
    val minDistanceMeters: Int,
    val isMockGpsAllowed: Boolean = false,
    val configHash: ByteArray,
    val latitude: Double,
    val longitude: Double,
    val workerSalt: ByteArray,
    val sharedSecret: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UserConfig
        if (configId != other.configId) return false
        if (!configHash.contentEquals(other.configHash)) return false
        if (!workerSalt.contentEquals(other.workerSalt)) return false
        if (!sharedSecret.contentEquals(other.sharedSecret)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = configId
        result = 31 * result + configHash.contentHashCode()
        result = 31 * result + workerSalt.contentHashCode()
        result = 31 * result + sharedSecret.contentHashCode()
        return result
    }
}

@Entity(tableName = "hash_leaks")
data class HashLeak(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workerId: String,
    val hashBase64: String,
    val timestamp: Long,
    val nonce: String
)

@Entity(tableName = "gps_trail")
data class GPSTrailPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float,
    val provider: String
)

/**
 * LogData - Hash zincirine işlenecek tüm veriler
 * Cell Tower bilgisi de eklendi! 🚨
 */
data class LogData(
    val checkInTime: Long,
    val checkOutTime: Long?,
    val latitude: Double,
    val longitude: Double,

    // Cell Tower bilgisi (Mock GPS tespiti için!)
    val cellTower: CellTowerData?
) : Serializable

/**
 * Cell Tower Data - Hash zincirine işlenecek
 */
data class CellTowerData(
    val cellId: String,
    val lac: String,
    val mcc: String,
    val mnc: String,
    val operatorName: String,
    val operatorCode: String,
    val networkType: String,
    val signalStrength: Int,
    val timestamp: Long
) : Serializable
