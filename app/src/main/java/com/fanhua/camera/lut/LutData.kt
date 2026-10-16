package com.fanhua.camera.lut

import android.content.Context
import com.fanhua.camera.debug.DebugLog
import java.io.File

/**
 * 3D LUT 数据（.cube），size=每维度采样点数，data 为 size^3*3 的 RGB 序列。
 * 索引顺序遵循 Adobe .cube 规范：b 最慢、g 次之、r 最快。
 */
class LutData(
    val size: Int,
    val data: FloatArray,
    val domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
    val domainMax: FloatArray = floatArrayOf(1f, 1f, 1f)
)

/**
 * 当前选中的 LUT 缓存（内存 + 磁盘持久化文件名）。
 */
object LutStore {

    @Volatile
    var cached: LutData? = null
        private set

    @Volatile
    var path: String? = null
        private set

    fun dir(context: Context): File =
        File(context.filesDir, "luts").apply { if (!exists()) mkdirs() }

    fun fileOf(context: Context, name: String): File = File(dir(context), name)

    fun listNames(context: Context): List<String> =
        dir(context).listFiles { f -> f.isFile && f.extension.equals("cube", ignoreCase = true) }
            ?.map { it.name }?.sorted() ?: emptyList()

    fun select(context: Context, name: String?, onChanged: () -> Unit = {}) {
        val target = name?.takeIf { it.isNotBlank() }
        path = target
        cached = target?.let { parseFromFile(fileOf(context, it)) }
        DebugLog.log("Lut", "select LUT=${target ?: "(none)"} parsed=${cached?.size}")
        onChanged()
    }

    fun parseFromFile(file: File): LutData? {
        if (!file.exists()) return null
        return try {
            file.inputStream().use { LutLoader.parse(it) }
        } catch (e: Exception) {
            DebugLog.log("Lut", "parse failed ${file.name}: $e")
            null
        }
    }

    fun clear() {
        cached = null
        path = null
    }
}