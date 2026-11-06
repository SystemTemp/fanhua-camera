package com.fanhua.camera.media.watermark

import android.graphics.RectF
import org.json.JSONObject

/**
 * 水印规格数据模型。
 *
 * 数据来自 `assets/watermark_specs.json`，由「徕卡一瞬」APK 的 85 个水印配置
 * 逐字段解析而来。所有尺寸单位是 **dp**，渲染时乘以 dp 基准得到像素
 * （dp 基准 = min(照片宽, 照片高) / 640，与徕卡原生一致）。
 */
data class WatermarkSpec(
    val id: String,
    val category: String,
    val nativeId: String,
    val names: Map<String, String>,
    /** 渲染方式，由根布局结构推断，比原始的 position 字段可靠。 */
    val renderMode: RenderMode,
    val priority: Float,
    /** 画框参数（仅 frame/side/filmstrip 有）。 */
    val frame: FrameSpec?,
    /** 主题表（颜色方案），可能为空（6 个纯文字水印没有主题）。 */
    val themes: Map<String, ThemeSpec>,
    val defaultTheme: String?,
    /** 字体定义表，key 如 f1/f2。 */
    val fonts: Map<String, FontSpec>,
    /** 各槽位的布局树，key 如 layout / bottom_left / top_right。 */
    val slots: Map<String, List<LayoutNode>>
) {
    /** 取当前语言下的显示名。 */
    fun displayName(langTag: String): String = when {
        langTag.startsWith("zh-TW") || langTag.startsWith("zh-HK") ->
            names["zhTW"] ?: names["zh"] ?: names["en"] ?: id
        langTag.startsWith("zh") -> names["zh"] ?: names["en"] ?: id
        else -> names["en"] ?: names["zh"] ?: id
    }
}

/** 水印渲染方式。 */
enum class RenderMode {
    /** 照片外上下画框（照片被白/黑边包围）。 */
    FRAME,
    /** 左右侧栏画框。 */
    SIDE,
    /** 叠在照片内。 */
    OVERLAY,
    /** 侧边竖排 EXIF（含旋转文本）。 */
    BORDER,
    /** 胶片齿孔样式（上下条带背景图）。 */
    FILMSTRIP;

    companion object {
        fun from(raw: String?): RenderMode = when (raw?.lowercase()) {
            "frame" -> FRAME
            "side" -> SIDE
            "overlay" -> OVERLAY
            "border" -> BORDER
            "filmstrip" -> FILMSTRIP
            else -> OVERLAY
        }
    }
}

/** 画框参数。 */
data class FrameSpec(
    val mode: String,
    val paddingH: Float,
    val paddingLeft: Float,
    val paddingRight: Float,
    val topH: Float,
    /** 底部可有多段（每段一个高度）。 */
    val bottomParts: List<Float>
) {
    val bottomTotal: Float get() = bottomParts.sum()
}

/** 主题（配色方案）。 */
data class ThemeSpec(
    val name: String,
    /** 纯色背景（#AARRGGBB）。 */
    val color: String?,
    /** 贴图背景资源名（drawable）。 */
    val imageRes: String?,
    /** 渐变参数。 */
    val gradient: GradientSpec?,
    /** 运行时模糊：light_blur / dark_blur / noise_blur。 */
    val algo: String?,
    /** 前景色序列：0=主色 1=次色 2=描边色 3=弱化色 4=备用。 */
    val foreground: List<String>
) {
    /** 按索引取前景色，越界回退到主色。 */
    fun foregroundAt(index: Int): String =
        foreground.getOrNull(index) ?: foreground.firstOrNull() ?: "#FF000000"
}

/** 渐变参数。 */
data class GradientSpec(
    val angle: Float,
    val colors: List<String>,
    val stops: List<Float>
)

/** 字体定义。 */
data class FontSpec(
    val size: Float,
    val color: String?,
    /** 按主题覆盖的颜色。 */
    val colorByTheme: Map<String, String>,
    /** 字体族：default / lg1056 / oxanium。 */
    val fontFamily: String,
    val letterSpacing: Float,
    /** 描边宽度（0 表示无描边）。 */
    val strokeWidth: Float
) {
    /** 在指定主题下的实际颜色。 */
    fun colorIn(theme: String?, fallback: String): String =
        colorByTheme[theme] ?: color ?: fallback
}

/** 布局节点（Layout 或 TextView 或 ImageView）。 */
data class LayoutNode(
    val type: NodeType,
    val id: String,
    val width: Dim,
    val height: Dim,
    val orientation: String,
    val gravity: List<String>,
    val marginLeft: Float,
    val marginTop: Float,
    val marginRight: Float,
    val marginBottom: Float,
    val background: BackgroundRef?,
    /** 纯色块（如分隔线），值为颜色字符串。 */
    val backgroundColor: String?,
    /** 旋转角度（部分水印的竖排文本）。 */
    val degree: Float,
    // ---- TextView 专属 ----
    val textParsed: TextSpec?,
    val fontRef: String?,
    val textSize: Float?,
    val textColor: String?,
    // ---- ImageView 专属 ----
    val imageRes: String?,
    /** 品牌 logo 候选（按设备品牌选）。 */
    val logoCandidates: Map<String, String>?,
    val children: List<LayoutNode>
)

enum class NodeType { LAYOUT, TEXT, IMAGE, UNKNOWN }

/** 尺寸：dp 固定值 / wrap_content / match_parent / 百分比。 */
data class Dim(val kind: DimKind, val value: Float?) {
    companion object {
        val WRAP = Dim(DimKind.WRAP, null)
        val MATCH = Dim(DimKind.MATCH, null)
        fun dp(v: Float) = Dim(DimKind.DP, v)
        fun pct(v: Float) = Dim(DimKind.PERCENT, v)
    }
}

enum class DimKind { DP, WRAP, MATCH, PERCENT }

/** 背景引用：来自主题或前景色索引。 */
data class BackgroundRef(val theme: String?, val foregroundIndex: Int?)

/** 文本内容规格。 */
data class TextSpec(
    val kind: TextKind,
    /** 原始模板串。 */
    val template: String,
    /** 模板里出现的字段名，如 ["focal","aperture"]。 */
    val fields: List<String>,
    /** 默认透明度（@custom=0.4 里的 0.4）。 */
    val defaultAlpha: Float?
)

enum class TextKind {
    MODEL, EXIF, TIME, LOCATION, CUSTOM, FILTER, LITERAL, UNKNOWN
}

/**
 * 水印规格仓库。
 *
 * 启动时把 2MB 的 JSON 解析成内存模型；按需懒解析（首次用到某个水印才解析）。
 */
class WatermarkRepository(
    private val loadJson: () -> String
) {
    private var root: JSONObject? = null
    private val cache = HashMap<String, WatermarkSpec?>()

    private fun root(): JSONObject {
        root?.let { return it }
        val parsed = JSONObject(loadJson())
        root = parsed
        return parsed
    }

    /** 全部分类：key → 中文名。 */
    fun groups(): List<Pair<String, String>> {
        val g = root().optJSONObject("groups") ?: return emptyList()
        val out = ArrayList<Pair<String, String>>()
        for (key in g.keys()) {
            val item = g.optJSONObject(key) ?: continue
            out.add(key to (item.optString("name").ifBlank { key }))
        }
        // 按优先级排序
        return out.sortedBy { (key, _) ->
            root().optJSONObject("groups")?.optJSONObject(key)?.optDouble("priority", 99.0) ?: 99.0
        }
    }

    /** 某分类下的全部水印 id。 */
    fun idsInGroup(group: String): List<String> {
        val w = root().optJSONObject("watermarks") ?: return emptyList()
        val out = ArrayList<String>()
        for (key in w.keys()) {
            val item = w.optJSONObject(key) ?: continue
            if (item.optString("category") == group) out.add(key)
        }
        return out.sortedBy { key -> spec(key)?.priority ?: 99f }
    }

    /** 取某个水印的规格（带缓存）。 */
    fun spec(id: String): WatermarkSpec? {
        cache[id]?.let { return it }
        if (cache.containsKey(id)) return null
        val w = root().optJSONObject("watermarks")?.optJSONObject(id)
        if (w == null) {
            cache[id] = null
            return null
        }
        val parsed = try {
            parseSpec(w)
        } catch (e: Exception) {
            null
        }
        cache[id] = parsed
        return parsed
    }

    /** 全部水印 id（按分类与优先级排序）。 */
    fun allIds(): List<String> =
        groups().flatMap { (g, _) -> idsInGroup(g) }

    // ---------------------------------------------------------------- 解析

    private fun parseSpec(o: JSONObject): WatermarkSpec {
        val names = HashMap<String, String>()
        o.optJSONObject("names")?.let { n ->
            for (k in n.keys()) names[k] = n.optString(k)
        }
        val themes = LinkedHashMap<String, ThemeSpec>()
        o.optJSONObject("themes")?.let { t ->
            for (k in t.keys()) {
                t.optJSONObject(k)?.let { themes[k] = parseTheme(k, it) }
            }
        }
        val fonts = HashMap<String, FontSpec>()
        o.optJSONObject("fonts")?.let { f ->
            for (k in f.keys()) {
                f.optJSONObject(k)?.let { fonts[k] = parseFont(it) }
            }
        }
        val slots = LinkedHashMap<String, List<LayoutNode>>()
        o.optJSONObject("slots")?.let { s ->
            for (k in s.keys()) {
                val arr = s.optJSONArray(k) ?: continue
                slots[k] = (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { parseNode(it) }
                }
            }
        }
        return WatermarkSpec(
            id = o.optString("id"),
            category = o.optString("category"),
            nativeId = o.optString("nativeId"),
            names = names,
            renderMode = RenderMode.from(o.optString("renderMode")),
            priority = o.optDouble("priority", 99.0).toFloat(),
            frame = o.optJSONObject("frame")?.let { parseFrame(it) },
            themes = themes,
            defaultTheme = o.optString("defaultTheme").takeIf { it.isNotBlank() },
            fonts = fonts,
            slots = slots
        )
    }

    private fun parseFrame(o: JSONObject): FrameSpec {
        val parts = ArrayList<Float>()
        o.optJSONArray("bottomParts")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { parts.add(it.optDouble("h", 0.0).toFloat()) }
            }
        }
        return FrameSpec(
            mode = o.optString("mode", "frame"),
            paddingH = o.optDouble("paddingH", 0.0).toFloat(),
            paddingLeft = o.optDouble("paddingLeft", 0.0).toFloat(),
            paddingRight = o.optDouble("paddingRight", 0.0).toFloat(),
            topH = o.optDouble("topH", 0.0).toFloat(),
            bottomParts = parts
        )
    }

    private fun parseTheme(name: String, o: JSONObject): ThemeSpec {
        val fg = ArrayList<String>()
        o.optJSONArray("foreground")?.let { arr ->
            for (i in 0 until arr.length()) fg.add(arr.optString(i))
        }
        val gradient = o.optJSONObject("gradient")?.let { g ->
            val colors = ArrayList<String>()
            val stops = ArrayList<Float>()
            g.optJSONArray("colors")?.let { c ->
                for (i in 0 until c.length()) colors.add(c.optString(i))
            }
            g.optJSONArray("stops")?.let { s ->
                for (i in 0 until s.length()) stops.add(s.optDouble(i, 0.0).toFloat())
            }
            GradientSpec(g.optDouble("angle", 90.0).toFloat(), colors, stops)
        }
        return ThemeSpec(
            name = name,
            color = o.optString("color").takeIf { it.startsWith("#") },
            imageRes = o.optString("imageRes").takeIf { it.isNotBlank() },
            gradient = gradient,
            algo = o.optString("algo").takeIf { it.isNotBlank() },
            foreground = fg
        )
    }

    private fun parseFont(o: JSONObject): FontSpec {
        val byTheme = HashMap<String, String>()
        o.optJSONObject("colorByTheme")?.let { m ->
            for (k in m.keys()) byTheme[k] = m.optString(k)
        }
        return FontSpec(
            size = o.optDouble("size", 20.0).toFloat(),
            color = o.optString("color").takeIf { it.startsWith("#") },
            colorByTheme = byTheme,
            fontFamily = o.optString("fontFamily", "default"),
            letterSpacing = o.optDouble("letterSpacing", 0.0).toFloat(),
            strokeWidth = o.optDouble("strokeWidth", 0.0).toFloat()
        )
    }

    private fun parseNode(o: JSONObject): LayoutNode {
        val type = when (o.optString("type").lowercase()) {
            "layout" -> NodeType.LAYOUT
            "textview", "text" -> NodeType.TEXT
            "imageview", "image" -> NodeType.IMAGE
            else -> NodeType.UNKNOWN
        }
        val gravity = ArrayList<String>()
        o.optJSONArray("gravity")?.let { arr ->
            for (i in 0 until arr.length()) gravity.add(arr.optString(i))
        }
        val children = ArrayList<LayoutNode>()
        o.optJSONArray("children")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { children.add(parseNode(it)) }
            }
        }
        val textParsed = o.optJSONObject("textParsed")?.let { t ->
            val fields = ArrayList<String>()
            t.optJSONArray("fields")?.let { f ->
                for (i in 0 until f.length()) fields.add(f.optString(i))
            }
            TextSpec(
                kind = when (t.optString("kind").lowercase()) {
                    "model" -> TextKind.MODEL
                    "exif" -> TextKind.EXIF
                    "time" -> TextKind.TIME
                    "location" -> TextKind.LOCATION
                    "custom" -> TextKind.CUSTOM
                    "filter" -> TextKind.FILTER
                    "literal" -> TextKind.LITERAL
                    else -> TextKind.UNKNOWN
                },
                template = t.optString("template"),
                fields = fields,
                defaultAlpha = if (t.has("default")) t.optDouble("default").toFloat() else null
            )
        }
        val logoCandidates = o.optJSONObject("logoCandidates")?.let { m ->
            val map = HashMap<String, String>()
            for (k in m.keys()) map[k] = m.optString(k)
            map
        }
        return LayoutNode(
            type = type,
            id = o.optString("id"),
            width = parseDim(o.optJSONObject("width")),
            height = parseDim(o.optJSONObject("height")),
            orientation = o.optString("orientation", "horizontal"),
            gravity = gravity,
            marginLeft = o.optJSONObject("marginLeft")?.optDouble("value", 0.0)?.toFloat() ?: 0f,
            marginTop = o.optJSONObject("marginTop")?.optDouble("value", 0.0)?.toFloat() ?: 0f,
            marginRight = o.optJSONObject("marginRight")?.optDouble("value", 0.0)?.toFloat() ?: 0f,
            marginBottom = o.optJSONObject("marginBottom")?.optDouble("value", 0.0)?.toFloat() ?: 0f,
            background = o.optJSONObject("background")?.let { b ->
                BackgroundRef(
                    theme = b.optString("theme").takeIf { it.isNotBlank() },
                    foregroundIndex = if (b.has("foregroundIndex")) {
                        b.optInt("foregroundIndex")
                    } else {
                        null
                    }
                )
            },
            backgroundColor = o.optString("backgroundColor").takeIf { it.startsWith("#") },
            degree = o.optDouble("degree", 0.0).toFloat(),
            textParsed = textParsed,
            fontRef = o.optString("fontRef").takeIf { it.isNotBlank() },
            textSize = if (o.has("textSize")) o.optDouble("textSize").toFloat() else null,
            textColor = o.optString("textColor").takeIf { it.startsWith("#") },
            imageRes = o.optString("imageRes").takeIf { it.isNotBlank() },
            logoCandidates = logoCandidates,
            children = children
        )
    }

    private fun parseDim(o: JSONObject?): Dim {
        if (o == null) return Dim.WRAP
        return when (o.optString("kind")) {
            "dp" -> Dim.dp(o.optDouble("value", 0.0).toFloat())
            "match" -> Dim.MATCH
            "pct", "percent" -> Dim.pct(o.optDouble("value", 0.0).toFloat())
            else -> Dim.WRAP
        }
    }
}
