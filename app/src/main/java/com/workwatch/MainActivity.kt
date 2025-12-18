package com.workwatch

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.workwatch.firebase.FirestoreServiceImpl
import com.workwatch.manager.WorkerCheckManager
import com.workwatch.reporting.WorkerLeakSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var workerCheckManager: WorkerCheckManager

    @Inject
    lateinit var firestoreService: FirestoreServiceImpl

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var statusText: TextView
    private lateinit var checkInOutButton: Button
    private lateinit var viewLogsButton: Button

    // Location permission launcher
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initializeApp()
        } else {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Find views
        statusText = findViewById(R.id.statusText)
        checkInOutButton = findViewById(R.id.checkInOutButton)
        viewLogsButton = findViewById(R.id.viewLogsButton)

        // Check location permission
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                initializeApp()
            }
            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        // Setup button listeners
        checkInOutButton.setOnClickListener {
            performCheckInOut()
        }

        viewLogsButton.setOnClickListener {
            viewLogs()
        }

        // Handle auto actions from Geofence
        handleAutoAction(intent?.getStringExtra("auto_action"))
    }

    private fun handleAutoAction(action: String?) {
        if (action == "check_in") {
             // Prompt user or auto check-in if configured
             performCheckInOut() // For simplicity in this demo
        } else if (action == "check_out") {
             performCheckInOut()
        }
    }

    private fun initializeApp() {
        lifecycleScope.launch {
            val initialized = workerCheckManager.initializeManager(this@MainActivity)
            if (initialized) {
                updateUI()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Error: Config not set. Please configure the app first.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateUI() {
        lifecycleScope.launch {
            val status = workerCheckManager.getWorkerStatus()
            if (status.isCheckedIn) {
                statusText.text = "Status: CHECKED IN\nSince: ${formatTime(status.checkInTime ?: 0)}"
                checkInOutButton.text = "Check Out"
            } else {
                statusText.text = "Status: NOT CHECKED IN"
                checkInOutButton.text = "Check In"
            }
        }
    }

    private fun performCheckInOut() {
        lifecycleScope.launch {
            try {
                // Get current location
                val location = getCurrentLocation()
                if (location == null) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error: Cannot get location",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Perform check-in/out with real Firestore service
                val result = workerCheckManager.attemptCheckInOut(
                    context = this@MainActivity,
                    currentLocation = location,
                    firestoreService = firestoreService
                )

                // Show result
                Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()

                // Update UI
                if (result.success) {
                    updateUI()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun getCurrentLocation(): Location? {
        return try {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return null
            }

            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            null
        }
    }

    private fun viewLogs() {
        // TODO: Navigate to logs screen
        Toast.makeText(this, "View Logs - Coming Soon", Toast.LENGTH_SHORT).show()
    }

    private fun formatTime(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }
}
