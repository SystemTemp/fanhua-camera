package com.fanhua.camera.camera.analyzer

import android.graphics.Bitmap
import com.fanhua.camera.camera.detect.NormRect

/**
 * 月亮检测结果。
 *
 * @param norm 月亮圆盘在整帧中的归一化外接矩形（0~1，左上为原点）
 * @param radiusRatio 圆盘半径 / 画面短边，用于判断「月亮有多大」
 * @param confidence 0~1，越高越确定
 */
data class MoonBox(
    val norm: NormRect,
    val radiusRatio: Float,
    val confidence: Float
)

/**
 * 月亮检测器（纯算法，不依赖模型文件）。
 *
 * ## 为什么不用 TFLite
 * 项目现有的目标检测是 COCO 80 类，**不包含「月亮」**；为单个天体特征专门训练
 * 一个模型体积与维护成本都不划算。月亮的视觉特征又非常稳定：
 *
 * 1. 画面里**最亮**的一团（月亮是夜空中的强光源）；
 * 2. 形状**接近正圆**（圆形度、外接矩形填充率、宽高比三重约束）；
 * 3. 颜色**低饱和**（白/灰/微黄，不会是彩色）；
 * 4. 与四周**亮度反差极大**（周围是暗天）；
 * 5. 通常**完整落在画面内**（不贴边）。
 *
 * 这些条件用连通域分析即可稳定判别，且完全离线、零模型体积。
 *
 * ## 性能
 * 内部把图缩到 [maxEdge] 再分析：480px 下约 17 万像素，单次 10~25ms，
 * 足够支撑实时预览节流后的调用；成片处理时用同一套逻辑重新检测，精度更高。
 */
object MoonDetector {

    /** 检测参数，抽出来便于单元测试与调参。 */
    data class Params(
        /** 分析前把图缩到的最长边。 */
        val maxEdge: Int = 480,
        /** 亮点阈值下限（0~1）。低于它的像素不参与连通域。 */
        val minLum: Float = 0.62f,
        /** 相对阈值系数：阈值同时不低于「最亮像素 × 该系数」。 */
        val relLum: Float = 0.86f,
        /** 圆盘面积占全图的最小/最大比例。 */
        val minAreaRatio: Float = 0.00040f,
        val maxAreaRatio: Float = 0.40f,
        /** 外接矩形填充率（圆 ≈ 0.785）。 */
        val minFill: Float = 0.52f,
        /** 宽高比允许区间。 */
        val minAspect: Float = 0.66f,
        val maxAspect: Float = 1.52f,
        /** 平均饱和度上限（月亮接近中性色）。 */
        val maxSat: Float = 0.26f,
        /** 与周边环带的亮度反差下限。 */
        val minContrast: Float = 1.55f,
        /** 圆盘边缘不能贴画面边框（贴边说明可能是窗框/灯光/过曝天空）。 */
        val borderMarginPx: Int = 1,
        /**
         * 「面积 / 外接圆面积」比值区间。
         * 正圆 ≈ 1.0，正方形 ≈ 0.64 —— 这是区分「月亮」与「亮矩形」的关键：
         * 标准圆形度（4πA/P²）对圆和正方形分别是 1.0 与 0.785，几乎分不开。
         */
        val minRoundness: Float = 0.80f,
        val maxRoundness: Float = 1.12f,
        /** 判定为月亮的最低总分。 */
        val minScore: Float = 0.56f
    )

    /**
     * 在 [src] 上检测月亮。
     *
     * @return 命中时返回 [MoonBox]（已换算回 [src] 的归一化坐标）；否则 null
     */
    fun detect(src: Bitmap, params: Params = Params()): MoonBox? {
        if (src.width <= 0 || src.height <= 0) return null

        // 1) 缩放到分析尺寸
        val scale = params.maxEdge.toFloat() / maxOf(src.width, src.height)
        val w = maxOf(1, (src.width * scale).toInt().coerceAtMost(params.maxEdge))
        val h = maxOf(1, (src.height * scale).toInt().coerceAtMost(params.maxEdge))
        val small = if (w == src.width && h == src.height) src
        else Bitmap.createScaledBitmap(src, w, h, true)

        try {
            val px = IntArray(w * h)
            small.getPixels(px, 0, w, 0, 0, w, h)
            return detectPixels(px, w, h, params)
        } finally {
            if (small !== src) small.recycle()
        }
    }

    /**
     * 纯像素版检测核心（不依赖 Android Bitmap，便于单元测试）。
     *
     * @param px ARGB 像素数组，长度必须为 w*h
     */
    fun detectPixels(px: IntArray, w: Int, h: Int, params: Params = Params()): MoonBox? {
        val n = w * h
        if (n <= 0 || px.size < n) return null
        run {
            // 2) 亮度 / 饱和度，并统计最大值
            val lum = FloatArray(n)
            val sat = FloatArray(n)
            var maxLum = 0f
            for (i in 0 until n) {
                val c = px[i]
                val r = ((c shr 16) and 0xFF) / 255f
                val g = ((c shr 8) and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
                lum[i] = l
                val mx = maxOf(r, g, b)
                val mn = minOf(r, g, b)
                sat[i] = if (mx <= 1e-4f) 0f else (mx - mn) / mx
                if (l > maxLum) maxLum = l
            }

            val thr = maxOf(params.minLum, maxLum * params.relLum)
            if (maxLum < 0.55f) return null   // 画面整体偏暗且没有强光源

            // 3) 连通域标记（8 邻域，迭代式洪水填充，避免递归爆栈）
            val label = IntArray(n) { -1 }
            val stack = IntArray(n)
            var best: MoonBox? = null
            var bestScore = 0f
            var nextLabel = 0

            for (start in 0 until n) {
                if (label[start] >= 0 || lum[start] < thr) continue
                var sp = 0
                stack[sp++] = start
                label[start] = nextLabel

                var area = 0
                var sumLum = 0f
                var sumSat = 0f
                var sumX = 0.0
                var sumY = 0.0
                var minX = w; var maxX = -1
                var minY = h; var maxY = -1
                var perimeter = 0

                while (sp > 0) {
                    val idx = stack[--sp]
                    val x = idx % w
                    val y = idx / w
                    area++
                    sumLum += lum[idx]
                    sumSat += sat[idx]
                    sumX += x
                    sumY += y
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y

                    var isEdge = x == 0 || y == 0 || x == w - 1 || y == h - 1
                    for (dy in -1..1) {
                        val ny = y + dy
                        if (ny < 0 || ny >= h) continue
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            val nx = x + dx
                            if (nx < 0 || nx >= w) continue
                            val ni = ny * w + nx
                            if (lum[ni] >= thr) {
                                if (label[ni] < 0) {
                                    label[ni] = nextLabel
                                    stack[sp++] = ni
                                }
                            } else {
                                isEdge = true
                            }
                        }
                    }
                    if (isEdge) perimeter++
                }
                nextLabel++

                // ---- 候选筛选 ----
                if (area <= 0 || perimeter <= 0) continue
                val areaRatio = area.toFloat() / n
                if (areaRatio < params.minAreaRatio || areaRatio > params.maxAreaRatio) continue

                val bw = maxX - minX + 1
                val bh = maxY - minY + 1
                val fill = area.toFloat() / (bw * bh)
                if (fill < params.minFill) continue

                val aspect = bw.toFloat() / bh
                if (aspect < params.minAspect || aspect > params.maxAspect) continue

                val meanLum = sumLum / area
                val meanSat = sumSat / area
                if (meanSat > params.maxSat) continue

                // 圆形度：4πA / P²（正圆 = 1，数字圆盘一般 0.7~0.9）
                val circularity = (4f * Math.PI.toFloat() * area) / (perimeter * perimeter).toFloat()
                if (circularity < 0.45f) continue

                // 贴边判定：月亮应完整在画面内
                val m = params.borderMarginPx
                if (minX <= m || minY <= m || maxX >= w - 1 - m || maxY >= h - 1 - m) continue

                // 面积 / 外接圆面积：排除方形/条形亮块
                val centroidX = (sumX / area).toFloat()
                val centroidY = (sumY / area).toFloat()
                val lbl = label[minY * w + minX].let { if (it >= 0) it else nextLabel - 1 }
                var rMax2 = 0f
                for (yy in minY..maxY) {
                    val rowOff = yy * w
                    for (xx in minX..maxX) {
                        if (label[rowOff + xx] != lbl) continue
                        val ddx = xx - centroidX
                        val ddy = yy - centroidY
                        val d2 = ddx * ddx + ddy * ddy
                        if (d2 > rMax2) rMax2 = d2
                    }
                }
                if (rMax2 <= 0f) continue
                val circleArea = Math.PI.toFloat() * rMax2
                val roundness = area / circleArea
                if (roundness < params.minRoundness || roundness > params.maxRoundness) continue

                // 与周边环带的亮度反差
                val ringLum = ringMeanLum(lum, w, h, minX, minY, maxX, maxY, bw, bh)
                if (ringLum <= 0f) continue
                val contrast = meanLum / maxOf(ringLum, 0.02f)
                if (contrast < params.minContrast) continue

                // ---- 打分 ----
                val sFill = normalize(fill, params.minFill, 0.785f)
                val sCirc = normalize(circularity, 0.45f, 0.85f)
                val sRound = 1f - (Math.abs(roundness - 1f) / 0.25f).coerceIn(0f, 1f)
                val sAspect = 1f - (Math.abs(Math.log(aspect.toDouble())).toFloat() /
                    Math.abs(Math.log(params.maxAspect.toDouble())).toFloat()).coerceIn(0f, 1f)
                val sSat = 1f - (meanSat / params.maxSat).coerceIn(0f, 1f)
                val sLum = normalize(meanLum, params.minLum, 0.92f)
                val sContrast = normalize(contrast, params.minContrast, 3.2f)
                // 面积：太小像灯光噪点，太大像过曝天空；0.6%~8% 最像月亮
                val sArea = when {
                    areaRatio < 0.006f -> normalize(areaRatio, params.minAreaRatio, 0.006f)
                    areaRatio > 0.08f -> 1f - normalize(areaRatio, 0.08f, params.maxAreaRatio)
                    else -> 1f
                }

                val score = 0.14f * sFill + 0.10f * sCirc + 0.26f * sRound +
                    0.11f * sAspect + 0.10f * sSat + 0.08f * sLum +
                    0.13f * sContrast + 0.08f * sArea

                if (score > bestScore && score >= params.minScore) {
                    bestScore = score
                    val radiusPx = (bw + bh) / 4f
                    best = MoonBox(
                        norm = NormRect(
                            left = minX / w.toFloat(),
                            top = minY / h.toFloat(),
                            right = (maxX + 1) / w.toFloat(),
                            bottom = (maxY + 1) / h.toFloat()
                        ),
                        radiusRatio = (radiusPx / minOf(w, h)).coerceIn(0f, 1f),
                        confidence = score.coerceIn(0f, 1f)
                    )
                }
            }
            return best
        }
    }

    /** 外接矩形向外扩一圈环带的平均亮度（用亮度图近似，跳过全黑像素）。 */
    private fun ringMeanLum(
        lum: FloatArray, w: Int, h: Int,
        minX: Int, minY: Int, maxX: Int, maxY: Int,
        bw: Int, bh: Int
    ): Float {
        val pad = maxOf(2, ((bw + bh) / 4 * 0.55f).toInt())
        val x0 = (minX - pad).coerceAtLeast(0)
        val y0 = (minY - pad).coerceAtLeast(0)
        val x1 = (maxX + pad).coerceAtMost(w - 1)
        val y1 = (maxY + pad).coerceAtMost(h - 1)
        var sum = 0f
        var cnt = 0
        for (y in y0..y1) {
            val row = y * w
            for (x in x0..x1) {
                // 只取环带：落在内接椭圆内的跳过
                val dx = (x - (minX + maxX) / 2f) / (bw / 2f)
                val dy = (y - (minY + maxY) / 2f) / (bh / 2f)
                if (dx * dx + dy * dy <= 1f) continue
                sum += lum[row + x]
                cnt++
            }
        }
        return if (cnt == 0) 0f else sum / cnt
    }

    private fun normalize(v: Float, lo: Float, hi: Float): Float {
        if (hi <= lo) return 0f
        return ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
    }
}
