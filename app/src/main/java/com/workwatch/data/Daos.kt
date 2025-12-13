package com.workwatch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.workwatch.entities.GPSTrailPoint
import com.workwatch.entities.HashLeak
import com.workwatch.entities.UserConfig
import com.workwatch.entities.WorkerLogEntry

@Dao
interface WorkerLogDao {
    @Query("SELECT * FROM worker_logs ORDER BY id DESC LIMIT 1")
    suspend fun getLatestLogEntry(): WorkerLogEntry?

    @Insert
    suspend fun insert(logEntry: WorkerLogEntry)

    @Update
    suspend fun update(logEntry: WorkerLogEntry)

    @Query("SELECT * FROM worker_logs WHERE isSynced = 0")
    suspend fun getUnsyncedLogs(): List<WorkerLogEntry>

    @Query("UPDATE worker_logs SET isSynced = 1 WHERE id IN (:logIds)")
    suspend fun updateLogSyncStatus(logIds: List<Long>)

    @Query("SELECT * FROM worker_logs")
    suspend fun getAllLogs(): List<WorkerLogEntry>

    @Query("SELECT currentHash FROM worker_logs ORDER BY id DESC LIMIT 1")
    suspend fun getLastLogHash(): ByteArray?

    @Query("SELECT * FROM worker_logs WHERE checkInTime >= :startOfDay AND checkInTime < :endOfDay")
    suspend fun getLogsForDay(startOfDay: Long, endOfDay: Long): List<WorkerLogEntry>

    @Query("SELECT * FROM worker_logs WHERE checkInTime >= :startTime ORDER BY checkInTime ASC")
    suspend fun getLogsSince(startTime: Long): List<WorkerLogEntry>
}

@Dao
interface ConfigDao {
    @Query("SELECT * FROM user_config WHERE configId = 1")
    suspend fun getConfig(): UserConfig?

    @Insert
    suspend fun insert(config: UserConfig)
}

@Dao
interface HashLeakDao {
    @Insert
    suspend fun insert(leak: HashLeak)
}

@Dao
interface GPSTrailDao {
    @Insert
    suspend fun insert(point: GPSTrailPoint)

    @Query("SELECT * FROM gps_trail WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    suspend fun getTrailSince(startTime: Long): List<GPSTrailPoint>

    @Query("SELECT * FROM gps_trail WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    suspend fun getTrailBetween(startTime: Long, endTime: Long): List<GPSTrailPoint>

    @Query("DELETE FROM gps_trail WHERE timestamp < :beforeTime")
    suspend fun deleteOldTrail(beforeTime: Long)

    @Query("SELECT * FROM gps_trail ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastPoint(): GPSTrailPoint?
}
