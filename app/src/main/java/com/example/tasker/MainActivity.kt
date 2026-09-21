package com.example.tasker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.pantry.service.MyFirebaseMessagingService
import com.example.pantry.ui.AppRoot
import com.example.tasker.ui.theme.TaskerTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    companion object {
        var appContext: Context? = null
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContext = applicationContext
        enableEdgeToEdge()

        // Force silent Anonymous Authentication on startup
        try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously()
                    .addOnSuccessListener { result ->
                        Log.d("FirebaseAuth", "Signed in anonymously as ${result.user?.uid}")
                    }
                    .addOnFailureListener { e ->
                        Log.e("FirebaseAuth", "Auth Failed: ${e.message}", e)
                    }
            } else {
                Log.d("FirebaseAuth", "Already authenticated as ${auth.currentUser?.uid}")
            }
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Firebase Auth initialization error", e)
        }

        createNotificationChannel()
        checkAndRequestPermissions()
        requestBatteryOptimizationExemption()

        setContent {
            TaskerTheme(darkTheme = true) {
                AppRoot()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Restock Alerts"
            val descriptionText = "Notifications when household items run low or empty"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(MyFirebaseMessagingService.CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(intent)
                    } catch (ignored: Exception) {
                    }
                }
            }
        }
    }
}
