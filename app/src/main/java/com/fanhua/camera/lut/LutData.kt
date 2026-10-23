package com.fanhua.camera.lut

import android.content.Context
import com.fanhua.camera.debug.DebugLog
import java.io.File

class LutData(
    val size: Int,
    val data: FloatArray,
    val domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
    val domainMax: FloatArray = floatArrayOf(1f, 1f, 1f)
)

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
