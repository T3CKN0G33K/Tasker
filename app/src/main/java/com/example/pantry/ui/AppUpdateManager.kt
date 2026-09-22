package com.example.pantry.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
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
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class SemVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVersion> {
    override fun compareTo(other: SemVersion): Int {
        if (this.major != other.major) return this.major.compareTo(other.major)
        if (this.minor != other.minor) return this.minor.compareTo(other.minor)
        return this.patch.compareTo(other.patch)
    }

    override fun toString(): String {
        return "$major.$minor.$patch"
    }

    companion object {
        fun parse(versionStr: String): SemVersion {
            val clean = versionStr.trim().removePrefix("v").removePrefix("V").substringBefore("-")
            val parts = clean.split(".").mapNotNull { it.toIntOrNull() }
            val major = parts.getOrNull(0) ?: 0
            val minor = parts.getOrNull(1) ?: 0
            val patch = parts.getOrNull(2) ?: 0
            return SemVersion(major, minor, patch)
        }
    }
}

data class UpdateInfo(
    val latestVersionName: String,
    val apkUrl: String,
    val releaseNotes: String
)

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun checkGitHubForUpdates(
        currentVersionStr: String,
        onUpdateAvailable: (UpdateInfo) -> Unit
    ) {
        val currentSemVer = SemVersion.parse(currentVersionStr)
        val client = OkHttpClient()

        // Query all GitHub releases array to parse semantic versions
        val request = Request.Builder()
            .url("https://api.github.com/repos/T3CKN0G33K/Tasker/releases")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to query GitHub releases: ${e.message}", e)
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                try {
                    val releasesArray = JSONArray(bodyStr)
                    var highestReleaseSemVer: SemVersion? = null
                    var highestApkUrl: String? = null
                    var highestReleaseNotes: String? = null
                    var highestTagName: String? = null

                    for (i in 0 until releasesArray.length()) {
                        val releaseObj = releasesArray.getJSONObject(i)
                        val isDraft = releaseObj.optBoolean("draft", false)
                        if (isDraft) continue

                        val tagName = releaseObj.optString("tag_name", "")
                        val semVer = SemVersion.parse(tagName)
                        val assets = releaseObj.optJSONArray("assets")

                        if (assets != null && assets.length() > 0) {
                            val downloadUrl = assets.getJSONObject(0).optString("browser_download_url", "")
                            val notes = releaseObj.optString("body", "New GitHub release available!")

                            if (downloadUrl.isNotBlank()) {
                                if (highestReleaseSemVer == null || semVer > highestReleaseSemVer) {
                                    highestReleaseSemVer = semVer
                                    highestApkUrl = downloadUrl
                                    highestReleaseNotes = if (notes.isNotBlank()) notes else "Release $tagName update"
                                    highestTagName = tagName
                                }
                            }
                        }
                    }

                    if (highestReleaseSemVer != null && highestApkUrl != null) {
                        Log.d(TAG, "Highest GitHub Release: $highestTagName (SemVer: $highestReleaseSemVer), Current: v$currentVersionStr (SemVer: $currentSemVer)")
                        
                        if (highestReleaseSemVer > currentSemVer) {
                            val updateInfo = UpdateInfo(
                                latestVersionName = highestTagName ?: "v$highestReleaseSemVer",
                                apkUrl = highestApkUrl,
                                releaseNotes = highestReleaseNotes ?: "New update available!"
                            )
                            CoroutineScope(Dispatchers.Main).launch {
                                onUpdateAvailable(updateInfo)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing GitHub releases: ${e.message}", e)
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

        checkGitHubForUpdates(currentVersionName) { updateInfo ->
            onUpdateAvailable(updateInfo)
        }

        if (forceShowToast) {
            Toast.makeText(context, "Checking GitHub for latest release (v$currentVersionName)...", Toast.LENGTH_SHORT).show()
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
