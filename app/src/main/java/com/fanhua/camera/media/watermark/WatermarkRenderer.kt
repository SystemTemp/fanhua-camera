package com.fanhua.camera.media.watermark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import com.fanhua.camera.R
import com.fanhua.camera.debug.DebugLog
import androidx.core.content.res.ResourcesCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * 水印渲染器。
 *
 * 按 [WatermarkSpec] 的布局树把水印画到照片上/照片外。
 * 支持 5 种 renderMode：
 * - FRAME：照片外上下画框
 * - SIDE：左右侧栏
 * - OVERLAY：叠在照片内
 * - BORDER：侧边竖排（含旋转文本）
 * - FILMSTRIP：胶片齿孔
 *
 * ## dp 基准
 * 徕卡原生以 640px 短边为 1dp 基准，这里沿用：
 * `dpBase = min(photoW, photoH) / 640f`
 *
 * ## 绘制流程
 * 1. 解析主题（背景色/贴图/渐变/模糊）
 * 2. 按 renderMode 计算画布尺寸与照片位置
 * 3. 递归测量布局树得到每个节点的矩形
 * 4. 递归绘制节点（背景 → 子节点 → 文本/图片）
 */
class WatermarkRenderer(private val context: Context) {

    /** 渲染所需的动态数据（EXIF、时间、位置等）。 */
    data class RenderData(
        val deviceModel: String,
        val appVersion: String,
        val timeMillis: Long,
        val focalLength: String?,
        val aperture: String?,
        val shutterSpeed: String?,
        val iso: String?,
        val location: String?,
        val filterName: String?,
        val themeName: String = "white"
    )

    private val dpBaseCache = HashMap<String, Float>()

    /** 字体缓存：default / lg1056 / oxanium(回退 default)。 */
    private val typefaces = HashMap<String, Typeface?>()

    private fun typeface(family: String): Typeface? {
        typefaces[family]?.let { return it }
        val tf = when (family.lowercase()) {
            "lg1056" -> ResourcesCompat.getFont(context, R.font.leica_lg1056)
            "oxanium" -> null // APK 内无此字体，回退系统默认
            else -> null
        }
        typefaces[family] = tf
        return tf
    }

    /**
     * 给照片应用水印，返回新位图。
     *
     * @param photo 原照片
     * @param spec  水印规格
     * @param data  动态数据
     * @return 带水印的新位图（调用方负责回收原图）
     */
    fun apply(photo: Bitmap, spec: WatermarkSpec, data: RenderData): Bitmap {
        return try {
            when (spec.renderMode) {
                RenderMode.FRAME -> renderFrame(photo, spec, data)
                RenderMode.SIDE -> renderSide(photo, spec, data)
                RenderMode.FILMSTRIP -> renderFilmstrip(photo, spec, data)
                RenderMode.OVERLAY -> renderOverlay(photo, spec, data)
                RenderMode.BORDER -> renderBorder(photo, spec, data)
            }
        } catch (e: Exception) {
            DebugLog.log("Watermark", "渲染失败 ${spec.id}: ${e.javaClass.simpleName}: ${e.message}")
            photo
        }
    }

    // ---------------------------------------------------------------- 各模式

    /** 照片外上下画框：照片居中，上下留白，文字画在留白区。 */
    private fun renderFrame(photo: Bitmap, spec: WatermarkSpec, data: RenderData): Bitmap {
        val dp = dpBase(photo)
        val frame = spec.frame
        val topH = ((frame?.topH ?: 0f) * dp).toInt()
        val bottomH = ((frame?.bottomTotal ?: 0f) * dp).toInt()
        val padH = ((frame?.paddingH ?: 0f) * dp).toInt()
        // 徕卡画框默认左右各 80dp 留白（若 spec 没给则用兜底值）
        val sidePad = if (padH > 0) padH else (80f * dp).toInt()

        val outW = photo.width + sidePad * 2
        val outH = photo.height + topH + bottomH
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val theme = spec.themes[data.themeName] ?: spec.themes[spec.defaultTheme] ?: spec.themes.values.firstOrNull()
        // 1) 背景
        drawBackground(canvas, RectF(0f, 0f, outW.toFloat(), outH.toFloat()), theme, photo)

        // 2) 照片
        canvas.drawBitmap(photo, sidePad.toFloat(), topH.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))

        // 3) 布局：把照片区域告诉布局树，让它知道各元素该画在哪
        val ctx = DrawContext(
            canvas = canvas,
            spec = spec,
            data = data,
            theme = theme,
            dp = dp,
            photoRect = RectF(
                sidePad.toFloat(), topH.toFloat(),
                (sidePad + photo.width).toFloat(), (topH + photo.height).toFloat()
            ),
            canvasRect = RectF(0f, 0f, outW.toFloat(), outH.toFloat())
        )
        drawSlots(ctx, spec.slots["layout"] ?: emptyList())
        return out
    }

    /** 左右侧栏画框。 */
    private fun renderSide(photo: Bitmap, spec: WatermarkSpec, data: RenderData): Bitmap {
        val dp = dpBase(photo)
        val frame = spec.frame
        val leftW = ((frame?.paddingLeft ?: 0f) * dp).toInt().coerceAtLeast((40f * dp).toInt())
        val rightW = ((frame?.paddingRight ?: 0f) * dp).toInt().coerceAtLeast((40f * dp).toInt())

        val outW = photo.width + leftW + rightW
        val outH = photo.height
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val theme = spec.themes[data.themeName] ?: spec.themes[spec.defaultTheme] ?: spec.themes.values.firstOrNull()
        drawBackground(canvas, RectF(0f, 0f, outW.toFloat(), outH.toFloat()), theme, photo)
        canvas.drawBitmap(photo, leftW.toFloat(), 0f, Paint(Paint.FILTER_BITMAP_FLAG))

        val ctx = DrawContext(
            canvas, spec, data, theme, dp,
            photoRect = RectF(leftW.toFloat(), 0f, (leftW + photo.width).toFloat(), photo.height.toFloat()),
            canvasRect = RectF(0f, 0f, outW.toFloat(), outH.toFloat())
        )
        drawSlots(ctx, spec.slots["layout"] ?: emptyList())
        return out
    }

    /** 胶片齿孔样式：照片上下加条带。 */
    private fun renderFilmstrip(photo: Bitmap, spec: WatermarkSpec, data: RenderData): Bitmap {
        // 与 FRAME 类似，但条带更窄且带背景图
        val dp = dpBase(photo)
        val topH = ((spec.frame?.topH ?: 60f) * dp).toInt()
        val bottomH = ((spec.frame?.bottomTotal ?: 60f) * dp).toInt()
        val outW = photo.width
        val outH = photo.height + topH + bottomH
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val theme = spec.themes[data.themeName] ?: spec.themes[spec.defaultTheme] ?: spec.themes.values.firstOrNull()
        drawBackground(canvas, RectF(0f, 0f, outW.toFloat(), outH.toFloat()), theme, photo)
        canvas.drawBitmap(photo, 0f, topH.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        val ctx = DrawContext(
            canvas, spec, data, theme, dp,
            photoRect = RectF(0f, topH.toFloat(), photo.width.toFloat(), (topH + photo.height).toFloat()),
            canvasRect = RectF(0f, 0f, outW.toFloat(), outH.toFloat())
        )
        drawSlots(ctx, spec.slots["layout"] ?: emptyList())
        return out
    }

    /** 叠在照片内：直接在原图上画。 */
    private fun renderOverlay(photo: Bitmap, spec: WatermarkSpec, data: RenderData): Bitmap {
        val out = photo.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val dp = dpBase(photo)
        val theme = spec.themes[data.themeName] ?: spec.themes[spec.defaultTheme] ?: spec.themes.values.firstOrNull()
        val ctx = DrawContext(
            canvas, spec, data, theme, dp,
            photoRect = RectF(0f, 0f, photo.width.toFloat(), photo.height.toFloat()),
            canvasRect = RectF(0f, 0f, photo.width.toFloat(), photo.height.toFloat())
        )
        // overlay 型用第一个非空 slot（通常是 bottom_middle 等）
        val slot = spec.slots.entries.firstOrNull { it.value.isNotEmpty() }?.value ?: emptyList()
        drawSlots(ctx, slot)
        return out
    }

    /** 侧边竖排 EXIF（含旋转）。 */
    private fun renderBorder(photo: Bitmap, spec: WatermarkSpec, data: RenderData): Bitmap {
        val dp = dpBase(photo)
        val barW = (60f * dp).toInt()
        val outW = photo.width + barW
        val outH = photo.height
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val theme = spec.themes[data.themeName] ?: spec.themes[spec.defaultTheme] ?: spec.themes.values.firstOrNull()
        drawBackground(canvas, RectF(0f, 0f, outW.toFloat(), outH.toFloat()), theme, photo)
        canvas.drawBitmap(photo, barW.toFloat(), 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        val ctx = DrawContext(
            canvas, spec, data, theme, dp,
            photoRect = RectF(barW.toFloat(), 0f, (barW + photo.width).toFloat(), photo.height.toFloat()),
            canvasRect = RectF(0f, 0f, outW.toFloat(), outH.toFloat())
        )
        drawSlots(ctx, spec.slots["layout"] ?: emptyList())
        return out
    }

    // ---------------------------------------------------------------- 背景

    private fun drawBackground(canvas: Canvas, rect: RectF, theme: ThemeSpec?, photo: Bitmap) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when {
            theme?.gradient != null -> {
                val g = theme.gradient
                val colors = g.colors.mapNotNull { parseColor(it) }.toIntArray()
                if (colors.size >= 2) {
                    val rad = Math.toRadians(g.angle.toDouble())
                    val dx = (Math.cos(rad) * rect.width()).toFloat()
                    val dy = (Math.sin(rad) * rect.height()).toFloat()
                    paint.shader = LinearGradient(
                        rect.centerX() - dx / 2, rect.centerY() - dy / 2,
                        rect.centerX() + dx / 2, rect.centerY() + dy / 2,
                        colors,
                        if (g.stops.size == colors.size) g.stops.toFloatArray() else null,
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(rect, paint)
                    paint.shader = null
                    return
                }
            }
            theme?.algo != null -> {
                // 运行时模糊：从照片采样并模糊
                drawBlurredBackground(canvas, rect, photo, theme.algo)
                return
            }
            theme?.imageRes != null -> {
                val resId = resId(theme.imageRes)
                if (resId != 0) {
                    val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, resId)
                    if (bmp != null) {
                        // 贴图 + 底色
                        parseColor(theme.color)?.let { canvas.drawColor(it) }
                        canvas.drawBitmap(
                            bmp, null, rect,
                            Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 255 }
                        )
                        bmp.recycle()
                        return
                    }
                }
            }
        }
        // 纯色兜底
        val color = parseColor(theme?.color) ?: Color.WHITE
        canvas.drawColor(color)
    }

    /** 模糊背景：把照片缩小后放大，形成低成本模糊。 */
    private fun drawBlurredBackground(canvas: Canvas, rect: RectF, photo: Bitmap, algo: String) {
        val down = 24
        val small = Bitmap.createScaledBitmap(photo, down, down, true)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        // 按算法调暗/调亮
        canvas.drawBitmap(small, null, rect, paint)
        val overlay = Paint()
        when {
            algo.contains("dark", true) -> overlay.color = Color.argb(150, 0, 0, 0)
            algo.contains("light", true) -> overlay.color = Color.argb(150, 255, 255, 255)
            else -> overlay.color = Color.argb(90, 0, 0, 0)
        }
        canvas.drawRect(rect, overlay)
        small.recycle()
    }

    // ---------------------------------------------------------------- 布局绘制

    private class DrawContext(
        val canvas: Canvas,
        val spec: WatermarkSpec,
        val data: RenderData,
        val theme: ThemeSpec?,
        val dp: Float,
        val photoRect: RectF,
        val canvasRect: RectF
    )

    private fun drawSlots(ctx: DrawContext, nodes: List<LayoutNode>) {
        // 每个 slot 的根节点铺满对应区域
        for (node in nodes) {
            val rect = measure(node, ctx.canvasRect, ctx)
            drawNode(node, rect, ctx)
        }
    }

    /**
     * 测量节点得到矩形。
     *
     * 简化实现：按 orientation 顺序排列子节点，支持 dp/wrap/match/percent 尺寸，
     * 支持负 margin（反向对齐）。
     */
    private fun measure(node: LayoutNode, parent: RectF, ctx: DrawContext): RectF {
        val dp = ctx.dp
        val w = when (node.width.kind) {
            DimKind.DP -> (node.width.value ?: 0f) * dp
            DimKind.MATCH -> parent.width()
            DimKind.PERCENT -> parent.width() * ((node.width.value ?: 0f) / 100f)
            DimKind.WRAP -> parent.width()
        }
        val h = when (node.height.kind) {
            DimKind.DP -> (node.height.value ?: 0f) * dp
            DimKind.MATCH -> parent.height()
            DimKind.PERCENT -> parent.height() * ((node.height.value ?: 0f) / 100f)
            DimKind.WRAP -> parent.height()
        }
        val left = parent.left + node.marginLeft * dp
        val top = parent.top + node.marginTop * dp
        return RectF(left, top, left + w, top + h)
    }

    private fun drawNode(node: LayoutNode, rect: RectF, ctx: DrawContext) {
        val canvas = ctx.canvas
        // 1) 节点自身背景
        node.backgroundColor?.let { c ->
            parseColor(c)?.let { canvas.drawRect(rect, Paint().apply { color = it }) }
        }
        node.background?.let { bg ->
            val colorStr = bg.theme?.let { ctx.spec.themes[it]?.color }
                ?: bg.foregroundIndex?.let { ctx.theme?.foregroundAt(it) }
            parseColor(colorStr)?.let { canvas.drawRect(rect, Paint().apply { color = it }) }
        }

        // 2) 按类型绘制
        when (node.type) {
            NodeType.TEXT -> drawText(node, rect, ctx)
            NodeType.IMAGE -> drawImage(node, rect, ctx)
            else -> Unit
        }

        // 3) 子节点：按 orientation 依次排布
        if (node.children.isNotEmpty()) {
            var cursor = if (node.orientation == "vertical") rect.top else rect.left
            for (child in node.children) {
                val cr = if (node.orientation == "vertical") {
                    val ch = childHeight(child, ctx)
                    val r = RectF(
                        rect.left + child.marginLeft * ctx.dp,
                        cursor + child.marginTop * ctx.dp,
                        rect.right,
                        cursor + child.marginTop * ctx.dp + ch
                    )
                    cursor += child.marginTop * ctx.dp + ch
                    r
                } else {
                    val cw = childWidth(child, ctx)
                    val r = RectF(
                        cursor + child.marginLeft * ctx.dp,
                        rect.top + child.marginTop * ctx.dp,
                        cursor + child.marginLeft * ctx.dp + cw,
                        rect.bottom
                    )
                    cursor += child.marginLeft * ctx.dp + cw
                    r
                }
                drawNode(child, cr, ctx)
            }
        }
    }

    private fun childWidth(node: LayoutNode, ctx: DrawContext): Float = when (node.width.kind) {
        DimKind.DP -> (node.width.value ?: 0f) * ctx.dp
        DimKind.PERCENT -> ctx.canvasRect.width() * ((node.width.value ?: 0f) / 100f)
        else -> 0f
    }

    private fun childHeight(node: LayoutNode, ctx: DrawContext): Float = when (node.height.kind) {
        DimKind.DP -> (node.height.value ?: 0f) * ctx.dp
        DimKind.PERCENT -> ctx.canvasRect.height() * ((node.height.value ?: 0f) / 100f)
        else -> 0f
    }

    private fun drawText(node: LayoutNode, rect: RectF, ctx: DrawContext) {
        val text = resolveText(node, ctx) ?: return
        if (text.isBlank()) return
        val font = node.fontRef?.let { ctx.spec.fonts[it] }
        val size = (node.textSize ?: font?.size ?: 20f) * ctx.dp
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            val colorStr = node.textColor
                ?: font?.colorIn(ctx.theme?.name, ctx.theme?.foregroundAt(0) ?: "#FF000000")
                ?: ctx.theme?.foregroundAt(0)
                ?: "#FF000000"
            color = parseColor(colorStr) ?: Color.BLACK
            letterSpacing = (font?.letterSpacing ?: 0f)
            typeface(font?.fontFamily ?: "default")?.let { typeface = it }
            if ((font?.strokeWidth ?: 0f) > 0f) {
                style = Paint.Style.STROKE
                strokeWidth = (font?.strokeWidth ?: 0f) * ctx.dp
            }
        }
        val saved = ctx.canvas.save()
        if (abs(node.degree) > 0.5f) {
            ctx.canvas.rotate(node.degree, rect.centerX(), rect.centerY())
        }
        // 文本基线：垂直居中
        val metrics = paint.fontMetrics
        val baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2f
        ctx.canvas.drawText(text, rect.left, baseline, paint)
        ctx.canvas.restoreToCount(saved)
    }

    private fun drawImage(node: LayoutNode, rect: RectF, ctx: DrawContext) {
        // 品牌 logo：按设备品牌选
        val resName = node.logoCandidates?.let { candidates ->
            candidates[brandKey()] ?: candidates["_fallback"]
        } ?: node.imageRes
        if (resName.isNullOrBlank()) return
        val id = resId(resName)
        if (id == 0) return
        try {
            val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, id) ?: return
            // 保持宽高比，高度贴合 rect
            val scale = rect.height() / bmp.height
            val w = bmp.width * scale
            val dst = RectF(rect.left, rect.top, rect.left + w, rect.bottom)
            ctx.canvas.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            bmp.recycle()
        } catch (e: Exception) {
            DebugLog.log("Watermark", "图片绘制失败 $resName: ${e.message}")
        }
    }

    // ---------------------------------------------------------------- 文本解析

    private fun resolveText(node: LayoutNode, ctx: DrawContext): String? {
        val tp = node.textParsed
        // 无 textParsed 时按 id 猜
        if (tp == null) {
            return when (node.id) {
                "model" -> ctx.data.deviceModel
                "time" -> formatTime(ctx.data.timeMillis)
                else -> null
            }
        }
        return when (tp.kind) {
            TextKind.MODEL -> ctx.data.deviceModel
            TextKind.TIME -> formatTime(ctx.data.timeMillis)
            TextKind.LOCATION -> ctx.data.location
            TextKind.FILTER -> ctx.data.filterName
            TextKind.CUSTOM -> ctx.data.appVersion
            TextKind.LITERAL -> tp.template
            TextKind.EXIF -> buildExif(tp, ctx.data)
            TextKind.UNKNOWN -> null
        }
    }

    private fun buildExif(tp: TextSpec, d: RenderData): String? {
        val parts = ArrayList<String>()
        for (f in tp.fields) {
            val v = when (f.lowercase()) {
                "focal" -> d.focalLength
                "aperture" -> d.aperture
                "speed", "shutter" -> d.shutterSpeed
                "iso" -> d.iso
                else -> null
            }
            if (!v.isNullOrBlank()) parts.add(v)
        }
        return if (parts.isEmpty()) null else parts.joinToString("  ")
    }

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.US).format(Date(millis))

    // ---------------------------------------------------------------- 工具

    private fun dpBase(photo: Bitmap): Float =
        min(photo.width, photo.height) / 640f

    private fun brandKey(): String {
        val m = android.os.Build.MANUFACTURER.lowercase(Locale.US)
        return when {
            m.contains("redmi") -> "redmi"
            m.contains("poco") -> "poco"
            m.contains("xiaomi") -> "xiaomi"
            else -> "xiaomi"
        }
    }

    private fun resId(name: String): Int =
        context.resources.getIdentifier(name, "drawable", context.packageName)

    private fun parseColor(s: String?): Int? {
        if (s.isNullOrBlank()) return null
        return try {
            when {
                s.startsWith("#") -> Color.parseColor(s)
                s.equals("white", true) -> Color.WHITE
                s.equals("black", true) -> Color.BLACK
                s.equals("transparent", true) -> Color.TRANSPARENT
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
