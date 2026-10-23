package com.fanhua.camera.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(
    private val context: Context
) {

    data class UpdateInfo(
        val tag: String,
        val versionName: String,
        val url: String,
        val notes: String
    )

    data class DownloadProgress(
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    )

    fun currentVersionName(): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun checkLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            parseGitee(httpGet(GITEE_VERSION_URL))
                ?: parseLatest(httpGet(RELEASE_API))
        } catch (e: Exception) {
            null
        }
    }

    fun hasUpdate(remote: UpdateInfo): Boolean {
        return compareVersions(remote.versionName, currentVersionName()) > 0
    }

    private fun compareVersions(a: String, b: String): Int {
        fun parse(v: String): Pair<List<Int>, Boolean> {
            val cleaned = v.trim().lowercase()
            val parts = cleaned.split("-")
            val nums = parts[0].split(".").mapNotNull { it.toIntOrNull() }
            val prerelease = parts.getOrNull(1) != null && parts[1].isNotEmpty()
            return nums to prerelease
        }
        val (an, ap) = parse(a)
        val (bn, bp) = parse(b)
        for (i in 0 until maxOf(an.size, bn.size)) {
            val x = an.getOrElse(i) { 0 }
            val y = bn.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return when {
            ap == bp -> 0
            ap -> -1
            else -> 1
        }
    }

    suspend fun download(info: UpdateInfo, onProgress: (DownloadProgress) -> Unit): File? =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "fanhua-camera-update.apk")
            for (attempt in 1..2) {
                try {
                    val conn = URL(info.url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 20000
                    conn.readTimeout = 60000
                    conn.setRequestProperty("User-Agent", "FanhuaCamera")
                    val total = conn.contentLength.toLong()
                    conn.inputStream.use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            var downloaded = 0L
                            var lastReport = 0L
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (total > 0) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastReport >= 100 || downloaded >= total) {
                                        lastReport = now
                                        onProgress(
                                            DownloadProgress(
                                                progressPercent = ((downloaded * 100) / total).toInt(),
                                                downloadedBytes = downloaded,
                                                totalBytes = total
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (file.length() > 0) {
                        return@withContext file
                    }
                    file.delete()
                } catch (e: Exception) {
                    file.delete()
                }
            }
            null
        }

    fun needsInstallPermission(): Boolean =
        !context.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission() {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
        }
    }

    fun install(file: File): Boolean {
        if (needsInstallPermission()) {
            requestInstallPermission()
            return false
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    file.delete()
                } catch (e: Exception) {
                }
            }, 120000)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun cleanup() {
        try {
            val dir = File(context.cacheDir, "updates")
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
        }
    }

    private fun parseLatest(json: String): UpdateInfo? {
        if (json.isBlank()) return null
        return try {
            val root = JSONObject(json)
            val tag = root.optString("tag_name", "").removePrefix("v")
            val notes = root.optString("body", "")
            var url = ""
            val assets = root.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name", "").endsWith(".apk")) {
                        url = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }
            if (tag.isEmpty() || url.isEmpty()) {
                null
            } else {
                UpdateInfo(tag = tag, versionName = tag, url = url, notes = notes)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseGitee(json: String): UpdateInfo? {
        if (json.isBlank()) return null
        return try {
            val root = JSONObject(json)
            val version = root.optString("version", "").removePrefix("v")
            val apk = root.optString("apk", "")
            val notes = root.optString("notes", "")
            if (version.isEmpty() || apk.isEmpty()) {
                null
            } else {
                UpdateInfo(tag = version, versionName = version, url = apk, notes = notes)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 12000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "FanhuaCamera")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode in 200..299) {
                return conn.inputStream.bufferedReader().readText()
            }
            return ""
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val REPO = "SystemTemp/fanhua-camera"
        private const val RELEASE_API = "https://api.github.com/repos/$REPO/releases/latest"
        private const val GITEE_VERSION_URL =
            "https://gitee.com/TDSI/file/raw/master/fanhua/version.json"
    }
}
