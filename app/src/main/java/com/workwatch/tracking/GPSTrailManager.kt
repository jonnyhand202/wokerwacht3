package com.workwatch.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.workwatch.data.WorkerRepository
import com.workwatch.entities.GPSTrailPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GPS Trail Manager - Esnek GPS takip sistemi
 * Kullanıcı 15/30/60 dakika veya kapalı seçebilir
 */
@Singleton
class GPSTrailManager @Inject constructor(
    private val repository: WorkerRepository
) {
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val INTERVAL_15_MIN = 15 * 60 * 1000L // 15 dakika
        const val INTERVAL_30_MIN = 30 * 60 * 1000L // 30 dakika
        const val INTERVAL_60_MIN = 60 * 60 * 1000L // 60 dakika
        const val INTERVAL_DISABLED = 0L

        const val PREFS_NAME = "gps_trail_prefs"
        const val KEY_INTERVAL = "trail_interval"
        const val KEY_ENABLED = "trail_enabled"
    }

    /**
     * GPS Trail ayarlarını kaydet
     */
    fun saveSettings(context: Context, intervalMs: Long, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_INTERVAL, intervalMs)
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    /**
     * GPS Trail ayarlarını oku
     */
    fun getSettings(context: Context): Pair<Long, Boolean> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val interval = prefs.getLong(KEY_INTERVAL, INTERVAL_30_MIN)
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        return Pair(interval, enabled)
    }

    /**
     * GPS Trail'i başlat
     */
    fun startTracking(context: Context) {
        val (interval, enabled) = getSettings(context)

        if (!enabled || interval == INTERVAL_DISABLED) {
            android.util.Log.d("GPSTrail", "GPS Trail disabled by user")
            return
        }

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            android.util.Log.e("GPSTrail", "Location permission not granted")
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            interval
        ).apply {
            setMinUpdateIntervalMillis(interval / 2) // Minimum interval yarısı
            setWaitForAccurateLocation(true)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    saveGPSPoint(location)
                }
            }
        }

        try {
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            android.util.Log.d("GPSTrail", "GPS Trail started with interval: ${interval / 60000}min")
        } catch (e: SecurityException) {
            android.util.Log.e("GPSTrail", "Failed to start tracking", e)
        }
    }

    /**
     * GPS Trail'i durdur
     */
    fun stopTracking() {
        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
        }
        fusedLocationClient = null
        locationCallback = null
        android.util.Log.d("GPSTrail", "GPS Trail stopped")
    }

    /**
     * GPS noktasını kaydet
     */
    private fun saveGPSPoint(location: Location) {
        scope.launch {
            try {
                val point = GPSTrailPoint(
                    timestamp = System.currentTimeMillis(),
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    accuracy = location.accuracy,
                    speed = location.speed,
                    bearing = location.bearing,
                    provider = location.provider ?: "unknown"
                )

                repository.insertGPSTrailPoint(point)

                android.util.Log.d(
                    "GPSTrail",
                    "GPS point saved: ${point.latitude}, ${point.longitude} (accuracy: ${point.accuracy}m)"
                )
            } catch (e: Exception) {
                android.util.Log.e("GPSTrail", "Failed to save GPS point", e)
            }
        }
    }

    /**
     * Geofence exit tetiklendiğinde manuel GPS kaydı
     */
    fun saveGeofenceExitPoint(location: Location) {
        android.util.Log.d("GPSTrail", "Geofence exit triggered - saving GPS point")
        saveGPSPoint(location)
    }

    /**
     * Bugünün GPS trail'ini al
     */
    suspend fun getTodayTrail(): List<GPSTrailPoint> {
        val startOfDay = getTodayStartTimestamp()
        return repository.getGPSTrailSince(startOfDay)
    }

    /**
     * Belirli tarih aralığındaki GPS trail'i al
     */
    suspend fun getTrailBetween(startTime: Long, endTime: Long): List<GPSTrailPoint> {
        return repository.getGPSTrailBetween(startTime, endTime)
    }

    private fun getTodayStartTimestamp(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
