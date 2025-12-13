package com.workwatch.tracking

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.workwatch.entities.UserConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Geofence Manager - Otomatik alan algılama
 * İşyerine girince/çıkınca otomatik check-in/out
 */
@Singleton
class GeofenceManager @Inject constructor() {

    private var geofencingClient: GeofencingClient? = null

    companion object {
        const val GEOFENCE_ID = "workplace_geofence"
        const val GEOFENCE_RADIUS_DEFAULT = 100f // 100 metre
        const val GEOFENCE_EXPIRATION = Geofence.NEVER_EXPIRE

        const val ACTION_GEOFENCE_EVENT = "com.workwatch.GEOFENCE_EVENT"
        const val EXTRA_GEOFENCE_TRANSITION = "transition_type"
    }

    /**
     * Geofence'i kur (İşyeri konumu)
     */
    fun setupGeofence(context: Context, config: UserConfig) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            android.util.Log.e("Geofence", "Location permission not granted")
            return
        }

        geofencingClient = LocationServices.getGeofencingClient(context)

        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(
                config.latitude,
                config.longitude,
                config.minDistanceMeters.toFloat()
            )
            .setExpirationDuration(GEOFENCE_EXPIRATION)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .setLoiteringDelay(30000) // 30 saniye içeride kalırsa ENTER
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val pendingIntent = getGeofencePendingIntent(context)

        try {
            geofencingClient?.addGeofences(geofencingRequest, pendingIntent)?.run {
                addOnSuccessListener {
                    android.util.Log.d(
                        "Geofence",
                        "Geofence added: ${config.latitude}, ${config.longitude} (radius: ${config.minDistanceMeters}m)"
                    )
                }
                addOnFailureListener { e ->
                    android.util.Log.e("Geofence", "Failed to add geofence", e)
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.e("Geofence", "Security exception", e)
        }
    }

    /**
     * Geofence'i kaldır
     */
    fun removeGeofence(context: Context) {
        geofencingClient = LocationServices.getGeofencingClient(context)
        geofencingClient?.removeGeofences(listOf(GEOFENCE_ID))?.run {
            addOnSuccessListener {
                android.util.Log.d("Geofence", "Geofence removed")
            }
            addOnFailureListener { e ->
                android.util.Log.e("Geofence", "Failed to remove geofence", e)
            }
        }
    }

    /**
     * Geofence PendingIntent
     */
    private fun getGeofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = ACTION_GEOFENCE_EVENT
        }

        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /**
     * Manuel mesafe kontrolü (Geofence olmadan)
     */
    fun isWithinWorkplace(
        currentLocation: Location,
        workplaceLat: Double,
        workplaceLon: Double,
        radiusMeters: Int
    ): Boolean {
        val workplaceLocation = Location("workplace").apply {
            latitude = workplaceLat
            longitude = workplaceLon
        }

        val distance = currentLocation.distanceTo(workplaceLocation)
        return distance <= radiusMeters
    }
}

/**
 * Geofence Broadcast Receiver
 * Geofence event'lerini dinler
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            android.util.Log.e(
                "GeofenceReceiver",
                "Geofence error: ${geofencingEvent.errorCode}"
            )
            return
        }

        val transition = geofencingEvent.geofenceTransition
        val location = geofencingEvent.triggeringLocation

        when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                android.util.Log.d("GeofenceReceiver", "ENTERED workplace")
                showNotification(
                    context,
                    "İşyerine Girdiniz",
                    "Check-in yapmak ister misiniz?",
                    isEnter = true
                )
            }

            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                android.util.Log.d("GeofenceReceiver", "EXITED workplace")
                showNotification(
                    context,
                    "İşyerinden Ayrıldınız",
                    "Check-out yapmak ister misiniz?",
                    isEnter = false
                )

                // GPS Trail'e exit noktasını kaydet
                location?.let {
                    // TODO: GPSTrailManager'a inject et
                    // gpsTrailManager.saveGeofenceExitPoint(it)
                }
            }
        }
    }

    private fun showNotification(
        context: Context,
        title: String,
        message: String,
        isEnter: Boolean
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager

        val channelId = "geofence_channel"

        // Android O+ için notification channel
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Geofence Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // MainActivity'yi aç
        val intent = Intent(context, com.workwatch.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("auto_action", if (isEnter) "check_in" else "check_out")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = android.app.Notification.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(
            if (isEnter) 1001 else 1002,
            notification
        )
    }
}
