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
     * 测量节点得到矩形（相对父容器的坐标）。
     *
     * 对齐规则对齐 Android 的 LinearLayout：
     * - 沿主轴（vertical 的 top/bottom、horizontal 的 left/right）由**父容器排列**决定，
     *   这里只负责给出尺寸，位置在 [layoutChildren] 里排；
     * - 交叉轴由节点的 `gravity` 决定（center_horizontal / right / center_vertical ...）。
     *
     * WRAP 按**实际内容**测量（文本宽度、图片原始比例），不再撑满父容器——
     * 早先直接返回 parent 尺寸，导致所有元素被拉成整块、全部堆在左上角。
     */
    private fun measure(node: LayoutNode, parent: RectF, ctx: DrawContext): RectF {
        val dp = ctx.dp
        val contentW = contentWidth(node, ctx)
        val contentH = contentHeight(node, ctx)

        val w = when (node.width.kind) {
            DimKind.DP -> (node.width.value ?: 0f) * dp
            DimKind.MATCH -> (parent.width() - (node.marginLeft + node.marginRight) * dp)
                .coerceAtLeast(0f)
            DimKind.PERCENT -> parent.width() * ((node.width.value ?: 0f) / 100f)
            DimKind.WRAP -> contentW
        }.coerceAtLeast(0f)
        val h = when (node.height.kind) {
            DimKind.DP -> (node.height.value ?: 0f) * dp
            DimKind.MATCH -> (parent.height() - (node.marginTop + node.marginBottom) * dp)
                .coerceAtLeast(0f)
            DimKind.PERCENT -> parent.height() * ((node.height.value ?: 0f) / 100f)
            DimKind.WRAP -> contentH
        }.coerceAtLeast(0f)

        // 交叉轴按 gravity 对齐；主轴位置由父容器排列决定，这里给 0 起点
        val g = node.gravity.map { it.lowercase() }
        val left = when {
            g.any { it.contains("right") || it.contains("end") } -> parent.right - node.marginRight * dp - w
            g.any { it.contains("center_horizontal") || it == "center" || it == "center_horizontal" } ->
                parent.left + (parent.width() - w) / 2f
            else -> parent.left + node.marginLeft * dp
        }
        val top = when {
            g.any { it.contains("bottom") } -> parent.bottom - node.marginBottom * dp - h
            g.any { it.contains("center_vertical") } -> parent.top + (parent.height() - h) / 2f
            else -> parent.top + node.marginTop * dp
        }
        return RectF(left, top, left + w, top + h)
    }

    /** 按内容测量宽度（WRAP 用）。 */
    private fun contentWidth(node: LayoutNode, ctx: DrawContext): Float {
        return when (node.type) {
            NodeType.TEXT -> {
                val t = measureText(node, ctx)
                if (t == null) 0f else buildTextPaint(node, ctx).measureText(t)
            }
            NodeType.IMAGE -> imageAspectSize(node, ctx)?.first ?: 0f
            else -> layoutContentWidth(node, ctx)
        }
    }

    /** 按内容测量高度（WRAP 用）。 */
    private fun contentHeight(node: LayoutNode, ctx: DrawContext): Float {
        return when (node.type) {
            NodeType.TEXT -> {
                val t = measureText(node, ctx)
                if (t == null) 0f else buildTextPaint(node, ctx).let { it.descent() - it.ascent() }
            }
            NodeType.IMAGE -> imageAspectSize(node, ctx)?.second ?: 0f
            else -> layoutContentHeight(node, ctx)
        }
    }

    /** 容器 WRAP：按子节点在主轴方向的累加、交叉轴的最大值计算。 */
    private fun layoutContentWidth(node: LayoutNode, ctx: DrawContext): Float {
        if (node.children.isEmpty()) return 0f
        return if (node.orientation == "vertical") {
            node.children.maxOfOrNull { contentWidth(it, ctx) + (it.marginLeft + it.marginRight) * ctx.dp } ?: 0f
        } else {
            node.children.sumOf {
                (contentWidth(it, ctx) + (it.marginLeft + it.marginRight) * ctx.dp).toDouble()
            }.toFloat()
        }
    }

    private fun layoutContentHeight(node: LayoutNode, ctx: DrawContext): Float {
        if (node.children.isEmpty()) return 0f
        return if (node.orientation == "vertical") {
            node.children.sumOf {
                (contentHeight(it, ctx) + (it.marginTop + it.marginBottom) * ctx.dp).toDouble()
            }.toFloat()
        } else {
            node.children.maxOfOrNull { contentHeight(it, ctx) + (it.marginTop + it.marginBottom) * ctx.dp } ?: 0f
        }
    }

    /**
     * 图片按 `displayHeight`（徕卡原生字段）与原始宽高比计算尺寸。
     * displayWidth > 0 时优先用它；否则用高度 + 比例推宽度。
     */
    private fun imageAspectSize(node: LayoutNode, ctx: DrawContext): Pair<Float, Float>? {
        val resName = node.logoCandidates?.let { c -> c[brandKey()] ?: c["_fallback"] } ?: node.imageRes
        if (resName.isNullOrBlank()) return null
        val id = resId(resName)
        if (id == 0) return null
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            android.graphics.BitmapFactory.decodeResource(context.resources, id, opts)
        } catch (e: Exception) {
            return null
        }
        val bw = opts.outWidth
        val bh = opts.outHeight
        if (bw <= 0 || bh <= 0) return null
        val wantW = (node.displayWidth ?: 0f) * ctx.dp
        val wantH = (node.displayHeight ?: 0f) * ctx.dp
        return when {
            wantW > 0f && wantH > 0f -> wantW to wantH
            wantH > 0f -> (wantH * bw / bh) to wantH
            wantW > 0f -> wantW to (wantW * bh / bw)
            else -> bw.toFloat() to bh.toFloat()
        }
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

        // 3) 子节点：按 orientation + gravity 排布
        if (node.children.isNotEmpty()) layoutChildren(node, rect, ctx)
    }

    /**
     * 子节点排布。
     *
     * - 主轴（vertical 的纵向 / horizontal 的横向）依次累加，含 margin；
     * - 交叉轴按子节点自身 `gravity` 对齐（靠右/居中/居中垂直等）。
     *
     * 负 margin（徕卡原生用它做「反向对齐」，如 `marginLeft = -48dp` 让右侧组靠右）
     * 在这里自然生效：位置 = 游标 + marginLeft，负值即左移，从而贴到右侧。
     */
    private fun layoutChildren(node: LayoutNode, rect: RectF, ctx: DrawContext) {
        val dp = ctx.dp
        val vertical = node.orientation == "vertical"
        var cursor = if (vertical) rect.top else rect.left

        for (child in node.children) {
            val cw = resolveSize(child.width, rect.width(), contentWidth(child, ctx), dp)
            val ch = resolveSize(child.height, rect.height(), contentHeight(child, ctx), dp)
            val g = child.gravity.map { it.lowercase() }

            val left: Float
            val top: Float
            if (vertical) {
                // 纵向排列：主轴 = top，交叉轴 = left
                left = when {
                    g.any { it.contains("right") || it.contains("end") } ->
                        rect.right - child.marginRight * dp - cw
                    g.any { it.contains("center_horizontal") || it == "center" } ->
                        rect.left + (rect.width() - cw) / 2f
                    else -> rect.left + child.marginLeft * dp
                }
                top = cursor + child.marginTop * dp
                cursor = top + ch + child.marginBottom * dp
            } else {
                // 横向排列：主轴 = left，交叉轴 = top
                left = cursor + child.marginLeft * dp
                top = when {
                    g.any { it.contains("bottom") } -> rect.bottom - child.marginBottom * dp - ch
                    g.any { it.contains("center_vertical") } -> rect.top + (rect.height() - ch) / 2f
                    else -> rect.top + child.marginTop * dp
                }
                cursor = left + cw + child.marginRight * dp
            }
            drawNode(child, RectF(left, top, left + cw, top + ch), ctx)
        }
    }

    /** 把 Dim 解析成具体像素值。 */
    private fun resolveSize(dim: Dim, parentSize: Float, contentSize: Float, dp: Float): Float {
        return when (dim.kind) {
            DimKind.DP -> (dim.value ?: 0f) * dp
            // 父容器本身是 wrap 时，match_parent 没有确定的参照宽度，
            // 直接退化成 wrap（按内容），否则会形成「父宽依赖子宽、子宽又等于父宽」的循环。
            DimKind.MATCH -> if (parentSize > 0f) parentSize else contentSize
            DimKind.PERCENT -> if (parentSize > 0f) parentSize * ((dim.value ?: 0f) / 100f) else contentSize
            DimKind.WRAP -> contentSize
        }.coerceAtLeast(0f)
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

    /** 解析节点文本（供测量与绘制共用）。 */
    private fun measureText(node: LayoutNode, ctx: DrawContext): String? {
        val t = resolveText(node, ctx)
        return if (t.isNullOrBlank()) null else t
    }

    /** 构建节点文本的画笔（供测量与绘制共用，保证两者度量一致）。 */
    private fun buildTextPaint(node: LayoutNode, ctx: DrawContext): TextPaint {
        val font = node.fontRef?.let { ctx.spec.fonts[it] }
        val size = (node.textSize ?: font?.size ?: 20f) * ctx.dp
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
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
    }

    private fun drawText(node: LayoutNode, rect: RectF, ctx: DrawContext) {
        val text = measureText(node, ctx) ?: return
        val paint = buildTextPaint(node, ctx)
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
