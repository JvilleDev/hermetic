package com.hermetic.app.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("assets") val assets: List<GitHubAsset>
)

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String
)

class UpdateManager(private val context: Context) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val repoUrl = "https://api.github.com/repos/johann-dev/Hermetic/releases/latest"

    suspend fun checkForUpdates(currentVersion: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(repoUrl).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val release = gson.fromJson(body, GitHubRelease::class.java)
                    val cleanLatest = release.tagName.removePrefix("v").trim()
                    val cleanCurrent = currentVersion.removePrefix("v").trim()

                    if (isNewerVersion(cleanCurrent, cleanLatest)) {
                        val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                        apkAsset?.downloadUrl
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val minLen = minOf(currentParts.size, latestParts.size)
        for (i in 0 until minLen) {
            if (latestParts[i] > currentParts[i]) return true
            if (latestParts[i] < currentParts[i]) return false
        }
        return latestParts.size > currentParts.size
    }

    suspend fun downloadAndInstallApk(url: String, onProgress: (Float) -> Unit): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Failed to download APK"))

                val file = File(context.cacheDir, "update.apk")
                if (file.exists()) file.delete()

                val body = response.body ?: return@withContext Result.failure(Exception("Empty body"))
                val totalBytes = body.contentLength()
                val input: InputStream = body.byteStream()
                val output = FileOutputStream(file)

                val buffer = ByteArray(4096)
                var bytesRead: Int
                var totalRead: Long = 0

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) {
                        onProgress(totalRead.toFloat() / totalBytes)
                    }
                }
                output.close()
                input.close()

                triggerInstall(file)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun triggerInstall(file: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
