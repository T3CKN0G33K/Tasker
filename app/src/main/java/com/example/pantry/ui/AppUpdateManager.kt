package com.example.pantry.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class UpdateInfo(
    val latestVersionName: String,
    val apkUrl: String,
    val releaseNotes: String
)

object AppUpdateManager {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun checkGitHubForUpdates(
        currentVersion: String,
        onUpdateAvailable: (String) -> Unit
    ) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.github.com/repos/T3CKN0G33K/Tasker/releases/latest")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    try {
                        val json = JSONObject(responseBody)
                        val tagName = json.getString("tag_name") // e.g., "v1.10"
                        val cleanTag = tagName.removePrefix("v") // e.g., "1.10"
                        
                        val assets = json.getJSONArray("assets")
                        if (assets.length() > 0) {
                            val downloadUrl = assets.getJSONObject(0).getString("browser_download_url")
                            
                            // If the GitHub tag doesn't match our app version, an update is available
                            if (cleanTag != currentVersion) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    onUpdateAvailable(downloadUrl) // Pass the URL to your existing download function
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }

    fun checkForUpdates(
        context: Context,
        forceShowToast: Boolean = false,
        onUpdateAvailable: (UpdateInfo) -> Unit = {}
    ) {
        val currentVersionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) { "1.0" }

        checkGitHubForUpdates(currentVersionName) { downloadUrl ->
            val info = UpdateInfo(
                latestVersionName = "GitHub Release",
                apkUrl = downloadUrl,
                releaseNotes = "New GitHub release available!"
            )
            onUpdateAvailable(info)
        }

        if (forceShowToast) {
            Toast.makeText(context, "Checking GitHub for updates (v$currentVersionName)...", Toast.LENGTH_SHORT).show()
        }
    }

    fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit = {},
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Check "Install Unknown Apps" permission on Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(context, "Please grant 'Install Unknown Apps' permission to update.", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening settings: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
            mainHandler.post { onError("Package install permission required.") }
            return
        }

        thread {
            try {
                var url = URL(downloadUrl)
                var connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.connect()

                // Handle HTTP redirects (301, 302, 307, 308)
                var redirectCount = 0
                while ((connection.responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        connection.responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        connection.responseCode == 307 ||
                        connection.responseCode == 308) && redirectCount < 5
                ) {
                    val newUrlStr = connection.getHeaderField("Location") ?: break
                    url = URL(newUrlStr)
                    connection = url.openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = true
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.connect()
                    redirectCount++
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    val code = connection.responseCode
                    mainHandler.post { onError("Server returned HTTP $code") }
                    return@thread
                }

                val fileLength = connection.contentLength
                val storageDir = context.externalCacheDir ?: context.cacheDir
                val apkFile = File(storageDir, "update_installer.apk")
                if (apkFile.exists()) apkFile.delete()

                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(apkFile)

                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int

                while (inputStream.read(data).also { count = it } != -1) {
                    total += count.toLong()
                    outputStream.write(data, 0, count)
                    if (fileLength > 0) {
                        val progressFraction = total.toFloat() / fileLength.toFloat()
                        mainHandler.post { onProgress(progressFraction) }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                mainHandler.post {
                    onComplete()
                    installApk(context, apkFile)
                }
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Unknown download error"
                mainHandler.post { onError("Download error: $msg") }
            }
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                Toast.makeText(context, "Downloaded APK is invalid or incomplete.", Toast.LENGTH_LONG).show()
                return
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Installation error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
