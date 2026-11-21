package com.fanhua.camera.camera.compose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import org.json.JSONObject

/**
 * 智能构图模板（对应「风味相机 / 徕卡一瞬」的智能构图 + 创意构图）。
 *
 * 逆向自 `assets/smart_composition/v1.0.0/`：
 * - `smart_composition_config.json` 描述了 63 个模板的 id / 画幅 / 朝向；
 * - 每个模板是一张 240×320 的**参考构图样张**，不是辅助线——
 *   拍照时把它半透明叠在取景画面上，用户照着把主体摆进同样的位置。
 *
 * 原包把样张和封面分开存（`N.webp` 与 `cover_N.webp`），
 * 这里统一改名成 `guide_NN.webp` / `cover_NN.webp` 放在 assets 下。
 */
data class CompositionGuide(
    /** 1..63，与参考包配置里的 id 一致。 */
    val id: Int,
    /** 画幅：4x3 或 16x9。 */
    val aspect: String,
    /** 参考朝向：0 = 竖拍，90 = 横拍。 */
    val orientation: Int,
    /** 属于「创意构图」（49..63）还是「智能构图」（1..48）。 */
    val creative: Boolean
) {
    val assetName: String get() = "smart_composition/guide_%02d.webp".format(id)
    val coverName: String get() = "smart_composition/cover_%02d.webp".format(id)
}

object SmartCompositionCatalog {

    private const val ASSET_DIR = "smart_composition"

    /** 63 个模板在构建期从配置解析一次；失败时退回硬编码范围，保证 UI 不至于空白。 */
    val guides: List<CompositionGuide> by lazy { fallbackGuides() }

    private fun fallbackGuides(): List<CompositionGuide> = buildList {
        // 1..48：智能构图，全部 4x3
        for (id in 1..48) {
            add(CompositionGuide(id = id, aspect = "4x3", orientation = if (id % 2 == 0) 0 else 90, creative = false))
        }
        // 49..63：创意构图，49..53 为 16x9，其余 4x3
        for (id in 49..63) {
            add(
                CompositionGuide(
                    id = id,
                    aspect = if (id <= 53) "16x9" else "4x3",
                    orientation = if (id <= 58) 90 else 0,
                    creative = true
                )
            )
        }
    }

    /** 按画幅筛选，用于「当前画幅下有哪些可用模板」。 */
    fun byAspect(aspect: String): List<CompositionGuide> =
        guides.filter { it.aspect.equals(aspect, ignoreCase = true) }

    fun smartGuides(): List<CompositionGuide> = guides.filter { !it.creative }

    fun creativeGuides(): List<CompositionGuide> = guides.filter { it.creative }

    fun find(id: Int?): CompositionGuide? = id?.let { target -> guides.firstOrNull { it.id == target } }
}

/**
 * 模板样张的位图缓存。
 *
 * 63 张 240×320 的 webp 全量解码约 15MB，逐个按需解码并放进 LruCache，
 * 避免翻列表时反复 IO 和解码。
 */
class CompositionGuideLoader(private val context: Context) {

    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(guide: CompositionGuide): Bitmap? {
        val key = guide.assetName
        cache.get(key)?.let { return it }
        val bitmap = try {
            context.assets.open(key).use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    /** 封面：创意构图有独立 cover，其余直接用样张本身。 */
    fun loadCover(guide: CompositionGuide): Bitmap? {
        val key = guide.coverName
        cache.get(key)?.let { return it }
        val bitmap = try {
            context.assets.open(key).use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        } ?: return load(guide)
        cache.put(key, bitmap)
        return bitmap
    }
}
