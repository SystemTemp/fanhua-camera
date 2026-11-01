package com.fanhua.camera.lut

import android.content.Context
import android.util.Log
import com.fanhua.camera.debug.DebugLog
import java.io.File

/**
 * LUT 数据。
 *
 * @param size 网格边长（33 = 33³）
 * @param data 长度 = size³ × 3，索引 = (b*size + g)*size + r，即 R 变化最快
 */
class LutData(
    val size: Int,
    val data: FloatArray,
    val domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
    val domainMax: FloatArray = floatArrayOf(1f, 1f, 1f)
) {
    companion object {
        /** 标识该 LUT 是来自 App 内置资源（用于 UI 区分「内置」与「用户导入」）。 */
        const val SOURCE_BUILTIN = "builtin"
    }
}

/**
 * LUT 存储与加载。
 *
 * - **内置 LUT**：来自 `res/raw` 下的 cube 资源，由「徕卡一瞬」App 的真实 3D LUT 转换而来，
 *   首次使用时解析并缓存到内存与 filesDir。
 * - **用户 LUT**：用户导入的 `.cube`，保存在 filesDir/luts/。
 */
object LutStore {

    private const val TAG = "FanhuaCamera"

    @Volatile
    var cached: LutData? = null
        private set

    @Volatile
    var path: String? = null
        private set

    /** 当前 LUT 的来源标识。 */
    @Volatile
    var source: String? = null
        private set

    fun dir(context: Context): File =
        File(context.filesDir, "luts").apply { if (!exists()) mkdirs() }

    fun fileOf(context: Context, name: String): File = File(dir(context), name)

    fun listNames(context: Context): List<String> =
        dir(context).listFiles { f -> f.isFile && f.extension.equals("cube", ignoreCase = true) }
            ?.map { it.name }?.sorted() ?: emptyList()

    /** 选择用户导入的 LUT。 */
    fun select(context: Context, name: String?, onChanged: () -> Unit = {}) {
        val target = name?.takeIf { it.isNotBlank() }
        path = target
        source = target?.let { "user" }
        cached = target?.let { parseFromFile(fileOf(context, it)) }
        DebugLog.log("Lut", "select user LUT=${target ?: "(none)"} parsed=${cached?.size}")
        onChanged()
    }

    /**
     * 选择内置 LUT（res/raw 下的 .cube）。
     *
     * 解析结果缓存到 filesDir/luts/ 下，下次启动直接读文件，省去重复解析 raw 资源。
     */
    fun selectBuiltin(context: Context, assetName: String?, onChanged: () -> Unit = {}) {
        val name = assetName?.takeIf { it.isNotBlank() }
        if (name == null) {
            path = null
            source = null
            cached = null
            onChanged()
            return
        }
        val cachedFile = fileOf(context, "$name.lut")
        val parsed = if (cachedFile.exists() && cachedFile.length() > 0) {
            parseFromFile(cachedFile)
        } else {
            // 首次：从 raw 资源读出并落盘
            try {
                val bytes = context.resources.openRawResource(
                    context.resources.getIdentifier(name, "raw", context.packageName)
                ).use { it.readBytes() }
                cachedFile.writeBytes(bytes)
                parseBinaryFile(cachedFile)
            } catch (e: Exception) {
                Log.e(TAG, "加载内置 LUT 失败: $name", e)
                null
            }
        }
        path = "$name.lut"
        source = LutData.SOURCE_BUILTIN
        cached = parsed
        DebugLog.log("Lut", "select builtin LUT=$name parsed=${parsed?.size}")
        onChanged()
    }

    fun parseFromFile(file: File): LutData? {
        if (!file.exists()) return null
        return try {
            file.inputStream().use { LutLoader.parse(it) }
        } catch (e: Exception) {
            DebugLog.log("Lut", "解析失败 ${file.name}: $e")
            null
        }
    }

    /** 解析 raw 资源里的二进制 LUT。 */
    fun parseFromRaw(context: Context, assetName: String): LutData? = try {
        context.resources.openRawResource(
            context.resources.getIdentifier(assetName, "raw", context.packageName)
        ).use { BinaryLutLoader.parse(it.readBytes()) }
    } catch (e: Exception) {
        DebugLog.log("Lut", "解析 raw 失败 $assetName: $e")
        null
    }

    /** 解析二进制 LUT 文件。 */
    fun parseBinaryFile(file: File): LutData? {
        if (!file.exists()) return null
        return try {
            BinaryLutLoader.parse(file.readBytes())
        } catch (e: Exception) {
            DebugLog.log("Lut", "解析二进制失败 ${file.name}: $e")
            null
        }
    }

    fun clear() {
        cached = null
        path = null
        source = null
    }
}
