package com.fanhua.camera.media.watermark

import android.content.Context
import com.fanhua.camera.debug.DebugLog

/**
 * 水印注册表：负责加载规格数据、记住用户选择。
 *
 * 规格数据来自 `assets/watermark_specs.json`（2.1MB），首次访问才解析，
 * 之后按水印 id 懒解析并缓存。
 */
object WatermarkRegistry {

    private const val PREFS = "fanhua_watermark"
    private const val KEY_ID = "watermark_id"

    /** 默认水印：徕卡 92 号白边画框（项目一直在用的那个）。 */
    const val DEFAULT_ID = "leica_92"

    @Volatile
    private var repo: WatermarkRepository? = null

    /** 获取规格仓库（单例）。 */
    fun repository(context: Context): WatermarkRepository {
        repo?.let { return it }
        synchronized(this) {
            repo?.let { return it }
            val appContext = context.applicationContext
            val created = WatermarkRepository {
                appContext.assets.open("watermark_specs.json")
                    .bufferedReader().use { it.readText() }
            }
            repo = created
            DebugLog.log("Watermark", "规格仓库已加载")
            return created
        }
    }

    /** 当前选中的水印 id。 */
    fun currentId(context: Context): String {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ID, null)
        if (id.isNullOrBlank()) return DEFAULT_ID
        // 校验该 id 确实存在
        val exists = repository(context).spec(id) != null
        return if (exists) id else DEFAULT_ID
    }

    /** 选择水印。 */
    fun select(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ID, id)
            .apply()
        DebugLog.log("Watermark", "已选择水印: $id")
    }

    /** 全部可用水印（按分类分组）。 */
    fun catalog(context: Context): List<WatermarkGroup> {
        val r = repository(context)
        return r.groups().map { (key, name) ->
            val ids = r.idsInGroup(key)
            WatermarkGroup(
                key = key,
                displayName = name,
                items = ids.mapNotNull { id ->
                    r.spec(id)?.let { spec ->
                        WatermarkItem(
                            id = id,
                            displayName = spec.names["zh"] ?: spec.names["en"] ?: id,
                            renderMode = spec.renderMode
                        )
                    }
                }
            )
        }.filter { it.items.isNotEmpty() }
    }
}

/** 水印分组。 */
data class WatermarkGroup(
    val key: String,
    val displayName: String,
    val items: List<WatermarkItem>
)

/** 单个水印条目。 */
data class WatermarkItem(
    val id: String,
    val displayName: String,
    val renderMode: RenderMode
)
