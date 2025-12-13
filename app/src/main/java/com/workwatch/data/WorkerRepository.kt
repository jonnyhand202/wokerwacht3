package com.workwatch.data

import com.workwatch.entities.GPSTrailPoint
import com.workwatch.entities.UserConfig
import com.workwatch.entities.WorkerLogEntry
import java.util.*

class WorkerRepository(
    private val workerLogDao: WorkerLogDao,
    private val configDao: ConfigDao,
    private val gpsTrailDao: GPSTrailDao
) {
    suspend fun getLatestLogEntry(): WorkerLogEntry? {
        return workerLogDao.getLatestLogEntry()
    }

    suspend fun insertLogEntry(logEntry: WorkerLogEntry) {
        workerLogDao.insert(logEntry)
    }

    suspend fun updateLogEntry(logEntry: WorkerLogEntry) {
        workerLogDao.update(logEntry)
    }

    suspend fun getUserConfig(): UserConfig? {
        return configDao.getConfig()
    }

    suspend fun insertConfig(config: UserConfig) {
        configDao.insert(config)
    }

    suspend fun getLastLogHash(): ByteArray? {
        return workerLogDao.getLastLogHash()
    }

    suspend fun getUnsyncedLogs(): List<WorkerLogEntry> {
        return workerLogDao.getUnsyncedLogs()
    }

    suspend fun getAllLogs(): List<WorkerLogEntry> {
        return workerLogDao.getAllLogs()
    }

    // GPS Trail methods
    suspend fun insertGPSTrailPoint(point: GPSTrailPoint) {
        gpsTrailDao.insert(point)
    }

    suspend fun getGPSTrailSince(startTime: Long): List<GPSTrailPoint> {
        return gpsTrailDao.getTrailSince(startTime)
    }

    suspend fun getGPSTrailBetween(startTime: Long, endTime: Long): List<GPSTrailPoint> {
        return gpsTrailDao.getTrailBetween(startTime, endTime)
    }

    suspend fun getTodayGPSTrail(): List<GPSTrailPoint> {
        val startOfDay = getTodayStartTimestamp()
        return gpsTrailDao.getTrailSince(startOfDay)
    }

    suspend fun deleteOldGPSTrail(daysToKeep: Int = 7) {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        gpsTrailDao.deleteOldTrail(cutoffTime)
    }

    // Today's logs
    suspend fun getTodayLogs(): List<WorkerLogEntry> {
        val startOfDay = getTodayStartTimestamp()
        val endOfDay = startOfDay + (24 * 60 * 60 * 1000L)
        return workerLogDao.getLogsForDay(startOfDay, endOfDay)
    }

    // Yesterday's logs
    suspend fun getYesterdayLogs(): List<WorkerLogEntry> {
        val startOfYesterday = getTodayStartTimestamp() - (24 * 60 * 60 * 1000L)
        val endOfYesterday = getTodayStartTimestamp()
        return workerLogDao.getLogsForDay(startOfYesterday, endOfYesterday)
    }

    private fun getTodayStartTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
