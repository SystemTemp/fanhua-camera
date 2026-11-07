package com.fanhua.camera.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.fanhua.camera.camera.analyzer.MoonBox
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 超级月亮处理器（直出）。
 *
 * ## 原则：只用真实拍到的像素
 * 不少机型的「超级月亮」实际是**贴一张月亮素材**上去，那不是拍摄。
 * 这里的做法严格只用这张照片里**月亮本身已有的像素**：
 *
 * 1. **细节增强**：对月面做 USM（反锐化掩模），把相机解析力吃掉的环形山纹理
 *    找回来（真实信息，只是被采样/降噪抹平了）；
 * 2. **高光结构恢复**：月亮通常过曝成一块白饼。把接近纯白的部分轻微压回，
 *    并用局部对比把「月海/月陆」的明暗差拉开，让圆盘重新有层次；
 * 3. **临边昏暗**：真实月面边缘比中心暗，按半径做轻微压暗，圆盘才有球体感，
 *    否则像一张贴纸；
 * 4. **适度放大**：把**圆盘像素**按 [Options.zoom] 重采样放大并羽化边缘合成回去
 *    —— 这是「月亮变大」的唯一来源，没有外部素材。
 *
 * 放大后圆盘边缘用径向羽化过渡到原天空，不会出现硬边。
 */
object SuperMoonProcessor {

    data class Options(
        /** 圆盘放大倍数。1.0 表示只增强不放大。 */
        val zoom: Float = 1.34f,
        /** USM 强度（0~2）。 */
        val detail: Float = 0.85f,
        /** 高光压缩强度（0~1）：越大月面层次越多，过大发灰。 */
        val highlightRecover: Float = 0.42f,
        /** 临边昏暗强度（0~0.5）。 */
        val limbDarkening: Float = 0.18f,
        /** 提取圆盘时向外多带的天空余量（相对半径）。 */
        val patchMargin: Float = 0.68f
    )

    /**
     * 对 [src] 做超级月亮处理。
     *
     * @return 处理后的新位图；失败或参数不合理时返回 null，调用方回退原图
     */
    fun enhance(src: Bitmap, moon: MoonBox, options: Options = Options()): Bitmap? {
        val w = src.width
        val h = src.height
        if (w < 64 || h < 64) return null

        val cx = ((moon.norm.left + moon.norm.right) / 2f * w)
        val cy = ((moon.norm.top + moon.norm.bottom) / 2f * h)
        val rx = moon.norm.width / 2f * w
        val ry = moon.norm.height / 2f * h
        val radius = max((rx + ry) / 2f, 6f)

        // 提取范围（含天空余量），裁到画面内
        val margin = radius * options.patchMargin
        val half = radius + margin
        val left = (cx - half).roundToInt().coerceAtLeast(0)
        val top = (cy - half).roundToInt().coerceAtLeast(0)
        val right = (cx + half).roundToInt().coerceAtMost(w)
        val bottom = (cy + half).roundToInt().coerceAtMost(h)
        val pw = right - left
        val ph = bottom - top
        if (pw < 24 || ph < 24) return null

        // 注意：createBitmap(src, x, y, w, h) 返回的是**不可变**位图，
        // 直接 setPixels 会抛 IllegalStateException，必须 copy 成可变副本。
        val patch = try {
            val cropped = Bitmap.createBitmap(src, left, top, pw, ph)
            if (cropped.isMutable) {
                cropped
            } else {
                val mutable = cropped.copy(Bitmap.Config.ARGB_8888, true)
                cropped.recycle()
                mutable
            }
        } catch (e: Exception) {
            null
        } ?: return null

        try {
            // 1) 在 patch 上做月面增强（只影响圆盘内部，圆盘外天空保持原样）
            val discCx = cx - left
            val discCy = cy - top
            enhanceDisc(patch, discCx, discCy, radius, options)

            // 2) 放大后的尺寸
            val zoom = options.zoom.coerceIn(1f, 2.2f)
            val nw = max(1, (pw * zoom).roundToInt())
            val nh = max(1, (ph * zoom).roundToInt())
            val scaled = Bitmap.createScaledBitmap(patch, nw, nh, true)

            try {
                // 3) 径向羽化遮罩：圆盘内全不透明，到 1.22R 淡出
                val feathered = applyRadialFeather(scaled, zoom, radius, pw, ph, options)
                try {
                    val out = src.copy(Bitmap.Config.ARGB_8888, true) ?: return null
                    val canvas = Canvas(out)
                    val left2 = (cx - nw / 2f)
                    val top2 = (cy - nh / 2f)
                    canvas.drawBitmap(
                        feathered, left2, top2,
                        Paint(Paint.FILTER_BITMAP_FLAG).apply { isAntiAlias = true }
                    )
                    return out
                } finally {
                    feathered.recycle()
                }
            } finally {
                scaled.recycle()
            }
        } finally {
            patch.recycle()
        }
    }

    /**
     * 圆盘内的增强：USM 细节 + 高光结构恢复 + 临边昏暗。
     * 圆盘外（距离 > 1.15R）的像素一律不动，保证天空不被连带修改。
     */
    private fun enhanceDisc(
        bmp: Bitmap, cx: Float, cy: Float, radius: Float, options: Options
    ) {
        val w = bmp.width
        val h = bmp.height
        val n = w * h
        val px = IntArray(n)
        bmp.getPixels(px, 0, w, 0, 0, w, h)

        // 亮度图（用于 USM 与遮罩）
        val lum = FloatArray(n)
        for (i in 0 until n) {
            val c = px[i]
            lum[i] = (0.2126f * ((c shr 16) and 0xFF) +
                0.7152f * ((c shr 8) and 0xFF) +
                0.0722f * (c and 0xFF)) / 255f
        }

        // USM：半径随圆盘大小自适应（约 1.1% 直径，至少 2px）
        val blurR = max(2, (radius * 0.022f).roundToInt())
        val blur = boxBlur(lum, w, h, blurR)

        val rIn = radius * 1.15f          // 超过这个距离不动
        val rLim = radius * 1.02f         // 临边昏暗起点
        val detail = options.detail
        val hRec = options.highlightRecover
        val limb = options.limbDarkening

        for (y in 0 until h) {
            val dy = y - cy
            val row = y * w
            for (x in 0 until w) {
                val dx = x - cx
                val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (dist > rIn) continue

                val i = row + x
                val c = px[i]
                var r = ((c shr 16) and 0xFF) / 255f
                var g = ((c shr 8) and 0xFF) / 255f
                var b = (c and 0xFF) / 255f
                val l = lum[i]

                // --- USM 细节：只补回高频，避免整体变亮变脏 ---
                var d = (l - blur[i]) * detail
                // 高光区细节限幅，防止噪声被放大成白点
                if (l > 0.72f) d *= (1f - (l - 0.72f) / 0.28f * 0.55f)
                r += d; g += d; b += d

                // --- 高光结构恢复：把接近纯白的一团压回，露出月海层次 ---
                if (l > 0.86f && hRec > 0f) {
                    val t = ((l - 0.86f) / 0.14f).coerceIn(0f, 1f)
                    val k = 1f - hRec * t * 0.28f
                    r *= k; g *= k; b *= k
                    // 压回后再补一点局部对比，让纹理可见
                    val local = (l - blur[i])
                    val boost = local * hRec * 1.35f
                    r += boost; g += boost; b += boost
                }

                // --- 临边昏暗：r 从 1.02R 到边缘按 cos 曲线压暗 ---
                if (limb > 0f && dist > rLim) {
                    val t = ((dist - rLim) / (radius - rLim)).coerceIn(0f, 1f)
                    // 用椭圆参数近似，边缘过渡更柔
                    val k = 1f - limb * (t * t * (3f - 2f * t))
                    r *= k; g *= k; b *= k
                }

                // 圆盘最外圈向天空淡出 3%，消除放大后的硬边
                if (dist > radius * 0.97f) {
                    val t = ((dist - radius * 0.97f) / (radius * 0.03f)).coerceIn(0f, 1f)
                    val srcR = ((c shr 16) and 0xFF) / 255f
                    val srcG = ((c shr 8) and 0xFF) / 255f
                    val srcB = (c and 0xFF) / 255f
                    r = r + (srcR - r) * t
                    g = g + (srcG - g) * t
                    b = b + (srcB - b) * t
                }

                px[i] = (clamp255(r) shl 16) or (clamp255(g) shl 8) or clamp255(b) or
                    (c and 0xFF000000.toInt())
            }
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    /**
     * 把放大后的 patch 套上径向羽化 alpha：
     * - 距离 ≤ 0.92·R_new：完全不透明
     * - 0.92R ~ 1.22R：平滑淡出
     * - 更远：全透明（保留原天空）
     *
     * @param zoom 放大倍数
     * @param radius 原始圆盘半径（未放大）
     * @param pw/ph 原始 patch 尺寸
     */
    private fun applyRadialFeather(
        scaled: Bitmap, zoom: Float, radius: Float, pw: Int, ph: Int, options: Options
    ): Bitmap {
        val nw = scaled.width
        val nh = scaled.height
        val out = Bitmap.createBitmap(nw, nh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(scaled, 0f, 0f, null)

        // 圆盘中心在放大后 patch 中的位置
        val marginPx = radius * options.patchMargin
        val cx = radius + marginPx
        val cy = radius + marginPx
        val rNew = radius * zoom
        val solid = rNew * 0.92f
        val fadeEnd = (rNew * 1.22f).coerceAtMost(max(nw, nh).toFloat())

        val mask = Paint().apply {
            shader = RadialGradient(
                cx, cy,
                max(fadeEnd, solid + 1f),
                intArrayOf(
                    Color.argb(255, 0, 0, 0),
                    Color.argb(255, 0, 0, 0),
                    Color.argb(0, 0, 0, 0)
                ),
                floatArrayOf(0f, (solid / max(fadeEnd, solid + 1f)).coerceIn(0f, 1f), 1f),
                Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawRect(0f, 0f, nw.toFloat(), nh.toFloat(), mask)
        return out
    }

    /** 可分离盒式模糊（够用且快，patch 尺寸很小）。 */
    private fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        if (r <= 0) return src.copyOf()
        val tmp = FloatArray(w * h)
        val out = FloatArray(w * h)
        val win = r * 2 + 1
        // 横向
        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            for (i in -r..r) sum += src[row + i.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[row + x] = sum / win
                val outIdx = (x - r).coerceIn(0, w - 1)
                val inIdx = (x + r + 1).coerceIn(0, w - 1)
                sum += src[row + inIdx] - src[row + outIdx]
            }
        }
        // 纵向
        for (x in 0 until w) {
            var sum = 0f
            for (i in -r..r) sum += tmp[i.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = sum / win
                val outIdx = (y - r).coerceIn(0, h - 1)
                val inIdx = (y + r + 1).coerceIn(0, h - 1)
                sum += tmp[inIdx * w + x] - tmp[outIdx * w + x]
            }
        }
        return out
    }

    private fun clamp255(v: Float): Int {
        val i = (v * 255f).roundToInt()
        return if (i < 0) 0 else if (i > 255) 255 else i
    }
}
