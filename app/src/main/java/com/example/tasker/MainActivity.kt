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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.pantry.service.MyFirebaseMessagingService
import com.example.pantry.ui.AppRoot
import com.example.pantry.ui.theme.ChristmasColors
import com.example.pantry.ui.theme.DefaultObsidianColors
import com.example.pantry.ui.theme.HalloweenColors
import com.example.pantry.ui.theme.LocalAppColors
import com.example.pantry.ui.theme.NewYearColors
import com.example.pantry.ui.theme.TaskerTheme
import com.example.pantry.ui.theme.ThanksgivingColors
import com.example.pantry.ui.theme.ThemeScheduleHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore

data class AppUpdateInfo(
    val versionCode: Long = 0,
    val versionName: String = "v1.29",
    val releaseNotes: String = "",
    val driveUrl: String = ""
)

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
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("pantry_user_prefs", Context.MODE_PRIVATE) }
            val householdId = remember {
                prefs.getString("household_id", null) ?: "e5514a42-8ce1-4374-8080-bcaf4e48ef6e"
            }

            var activeThemeName by remember { mutableStateOf("default") }
            var seasonalOverride by remember { mutableStateOf(false) }
            var autoSchedule by remember { mutableStateOf(true) }
            var pendingUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }

            // Theme Config Listener
            DisposableEffect(householdId) {
                Log.e("TASKER_THEME_DEBUG", ">>> ATTACHING THEME LISTENER: households/$householdId/config/theme")
                val db = Firebase.firestore
                val docRef = db.collection("households")
                    .document(householdId)
                    .collection("config")
                    .document("theme")

                val listener = docRef.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("TASKER_THEME_DEBUG", ">>> Theme Listener Error: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        activeThemeName = snapshot.getString("activeTheme") ?: "default"
                        seasonalOverride = snapshot.getBoolean("seasonalOverride") ?: false
                        autoSchedule = snapshot.getBoolean("autoSchedule") ?: true
                        Log.e("TASKER_THEME_DEBUG", ">>> THEME SYNC: autoSchedule=$autoSchedule, override=$seasonalOverride, manualTheme=$activeThemeName")
                    } else {
                        Log.e("TASKER_THEME_DEBUG", ">>> Theme config document missing. Writing initial doc...")
                        docRef.set(mapOf("activeTheme" to "halloween", "seasonalOverride" to true, "autoSchedule" to true))
                    }
                }

                onDispose { listener.remove() }
            }

            // Google Drive OTA Update Listener
            DisposableEffect(householdId) {
                val db = Firebase.firestore
                val listener = db.collection("households")
                    .document(householdId)
                    .collection("config")
                    .document("updates")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                        val remoteCode = snapshot.getLong("latestVersionCode") ?: 0L
                        val currentCode = BuildConfig.VERSION_CODE.toLong()

                        if (remoteCode > currentCode) {
                            pendingUpdate = AppUpdateInfo(
                                versionCode = remoteCode,
                                versionName = snapshot.getString("latestVersionName") ?: "v1.29",
                                releaseNotes = snapshot.getString("releaseNotes") ?: "• Vector Glass UI Redesign across all 5 tabs\n• Real-Time Cloud Firestore Sync for Pantry, Board & Bills\n• Dynamic Seasonal Theme Engine (Halloween, Thanksgiving, Christmas, New Year)\n• Scope-Aware Calendar Agenda & Alarm Scheduler\n• Past Monthly Budget Archives & Read-Only Protection\n• Wi-Fi Master Admin PIN Unlock & Credential Editor",
                                driveUrl = snapshot.getString("driveUrl") ?: "https://drive.google.com"
                            )
                        } else {
                            pendingUpdate = null
                        }
                    }
                onDispose { listener.remove() }
            }

            val calendarTheme = remember { ThemeScheduleHelper.getSeasonalThemeForDate() }

            val resolvedThemeName = when {
                autoSchedule -> calendarTheme
                seasonalOverride -> activeThemeName
                else -> null
            }

            val activeColors = when (resolvedThemeName?.lowercase()) {
                "halloween" -> HalloweenColors
                "thanksgiving" -> ThanksgivingColors
                "christmas" -> ChristmasColors
                "newyear", "winter" -> NewYearColors
                else -> DefaultObsidianColors
            }

            CompositionLocalProvider(LocalAppColors provides activeColors) {
                TaskerTheme(colors = activeColors) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = LocalAppColors.current.background
                    ) {
                        AppRoot()

                        // Google Drive OTA Update Prompt
                        pendingUpdate?.let { updateInfo ->
                            GoogleDriveOtaUpdateDialog(
                                updateInfo = updateInfo,
                                onDismiss = { pendingUpdate = null }
                            )
                        }
                    }
                }
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
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("BatteryOptimization", "Error requesting exemption", e)
                }
            }
        }
    }
}

@Composable
fun GoogleDriveOtaUpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appTheme = LocalAppColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val url = updateInfo.driveUrl.ifBlank { "https://drive.google.com" }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = appTheme.primaryAccent),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Download Update (Google Drive)", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later", color = Color(0xFF94A3B8))
            }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "New App Update Available: ${updateInfo.versionName}",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Build ${updateInfo.versionCode} • Official Release",
                    fontSize = 12.sp,
                    color = Color(0xFF38BDF8)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "WHAT'S NEW (v1.28 → ${updateInfo.versionName})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.8.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(appTheme.cardSurface)
                        .border(1.dp, appTheme.cardBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = updateInfo.releaseNotes.ifBlank {
                            "• Full Vector Glass UI redesign across all 5 tabs\n• Real-Time Cloud Firestore Sync for Pantry, Board & Bills\n• Dynamic Seasonal Theme Engine (Halloween, Thanksgiving, Christmas, New Year)\n• Scope-Aware Calendar Agenda & Alarm Scheduler\n• Past Monthly Budget Archives & Read-Only Protection\n• Wi-Fi Master Admin PIN Unlock & Credential Editor"
                        },
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        lineHeight = 17.sp
                    )
                }
            }
        },
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(24.dp)
    )
}
