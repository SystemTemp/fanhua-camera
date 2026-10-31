package com.fanhua.camera.camera.detect

import android.content.Context
import com.fanhua.camera.debug.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 模型下载器。
 *
 * 设计要点：
 * 1. **多源**：同一模型可配置多个镜像，按顺序尝试；某个源失败（超时 / 404 / 校验不过）
 *    立即切换到下一个，不浪费用户时间。
 * 2. **断点续传**：用 HTTP Range 从已下载的字节继续，避免弱网下反复从头开始。
 * 3. **完整性校验**：下载完成后校验 TFLite 魔数与文件大小，避免半包文件被当成有效模型。
 * 4. **原子落盘**：先写 .part 临时文件，校验通过后再 rename，杜绝「下载到一半被杀进程
 *    留下坏文件」的情况。
 * 5. **速度自适应**：记录每个源上次的下载速度，下次优先用最快的源。
 */
class ModelDownloader(private val context: Context) {

    /** 下载进度。 */
    data class Progress(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long,
        val sourceName: String
    ) {
        val percent: Int
            get() = if (totalBytes > 0) {
                ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
            } else {
                0
            }
    }

    sealed interface Result {
        data object Success : Result
        data class Failed(val reason: String) : Result
        data object Cancelled : Result
    }

    private val speedPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 下载指定模型。
     *
     * @param onProgress 在主线程回调（调用方负责切线程，这里在 IO 上直接调用，
     *                   UI 侧用 withContext(Main) 包一层即可）。
     */
    suspend fun download(
        definition: ModelDefinition,
        onProgress: (Progress) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        val target = DetectorStore.fileOf(context, definition.id)
        if (DetectorStore.isDownloaded(context, definition.id)) {
            return@withContext Result.Success
        }

        val sources = sortBySpeed(definition.sources)
        if (sources.isEmpty()) {
            return@withContext Result.Failed("该模型没有可用的下载源")
        }

        val errors = mutableListOf<String>()
        for (source in sources) {
            currentCoroutineContext().ensureActive()
            DebugLog.log("Detector", "尝试从 ${source.name} 下载 ${definition.id}")
            val active = currentCoroutineContext().isActive
            when (val outcome = trySource(source, target, onProgress) { active }) {
                is Result.Success -> {
                    // 最终校验
                    if (DetectorStore.isDownloaded(context, definition.id)) {
                        DebugLog.log("Detector", "模型 ${definition.id} 下载完成")
                        return@withContext Result.Success
                    }
                    errors += "${source.name}: 文件校验未通过"
                    target.delete()
                }
                is Result.Cancelled -> return@withContext Result.Cancelled
                is Result.Failed -> {
                    errors += "${source.name}: ${outcome.reason}"
                    DebugLog.log("Detector", "${source.name} 失败: ${outcome.reason}")
                }
            }
        }
        Result.Failed(errors.joinToString("；"))
    }

    /** 按历史速度排序，快的优先。 */
    private fun sortBySpeed(sources: List<ModelSource>): List<ModelSource> =
        sources.sortedByDescending { speedPrefs.getLong(speedKey(it.url), 0L) }

    private fun speedKey(url: String): String = "speed_" + url.hashCode().toString(16)

    private fun trySource(
        source: ModelSource,
        target: File,
        onProgress: (Progress) -> Unit,
        isActive: () -> Boolean
    ): Result {
        val part = File(target.parentFile, target.name + ".part")
        var downloaded = if (part.exists()) part.length() else 0L

        // 已下载部分若已超过期望大小，说明是脏数据，重来
        if (source.expectedBytes > 0 && downloaded >= source.expectedBytes) {
            part.delete()
            downloaded = 0L
        }

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                if (downloaded > 0) {
                    setRequestProperty("Range", "bytes=$downloaded-")
                }
            }

            val code = connection.responseCode
            // 206 = 部分内容（续传成功）；200 = 完整内容（服务端不支持 Range，从头来）
            if (code == HttpURLConnection.HTTP_OK && downloaded > 0) {
                part.delete()
                downloaded = 0L
            } else if (code != HttpURLConnection.HTTP_OK &&
                code != HttpURLConnection.HTTP_PARTIAL
            ) {
                return Result.Failed("HTTP $code")
            }

            val contentLength = connection.contentLength.toLong()
            val total = if (contentLength > 0) contentLength + downloaded else source.expectedBytes

            val startAt = System.currentTimeMillis()
            var lastReport = 0L
            var speedWindowBytes = 0L
            var speedWindowStart = startAt

            connection.inputStream.use { input ->
                java.io.FileOutputStream(part, downloaded > 0L).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (!isActive()) {
                            output.flush()
                            return Result.Cancelled
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        speedWindowBytes += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                            lastReport = now
                            val elapsed = (now - speedWindowStart).coerceAtLeast(1L)
                            val speed = speedWindowBytes * 1000L / elapsed
                            onProgress(Progress(downloaded, total, speed, source.name))
                            speedWindowBytes = 0L
                            speedWindowStart = now
                        }
                    }
                    output.flush()
                }
            }

            val elapsed = (System.currentTimeMillis() - startAt).coerceAtLeast(1L)
            val avgSpeed = downloaded * 1000L / elapsed
            speedPrefs.edit().putLong(speedKey(source.url), avgSpeed).apply()

            // 大小校验
            if (source.expectedBytes > 0 && part.length() != source.expectedBytes) {
                part.delete()
                return Result.Failed("大小不符（期望 ${source.expectedBytes}，实际 ${part.length()}）")
            }

            // 原子替换
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                // 某些文件系统不支持跨目录 rename，退化为复制
                try {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                } catch (e: Exception) {
                    part.delete()
                    return Result.Failed("保存失败: ${e.message}")
                }
            }
            Result.Success
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failed(e.javaClass.simpleName + ": " + (e.message ?: "未知错误"))
        } finally {
            try {
                connection?.disconnect()
            } catch (ignore: Exception) {
            }
        }
    }

    /** 清理下载残留。 */
    fun cleanup() {
        try {
            DetectorStore.dir(context).listFiles()?.forEach { file ->
                if (file.name.endsWith(".part")) file.delete()
            }
        } catch (e: Exception) {
            DebugLog.log("Detector", "清理下载残留失败: ${e.message}")
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_MS = 200L
        const val USER_AGENT = "FanhuaCamera/Detector"
        const val PREFS = "fanhua_detector_speed"
    }
}
