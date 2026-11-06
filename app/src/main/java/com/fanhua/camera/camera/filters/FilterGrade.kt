package com.fanhua.camera.camera.filters

import kotlin.math.max
import kotlin.math.min

/**
 * 单个滤镜的调色参数。
 *
 * 这套参数被 CPU 侧（滤镜色卡预览、单元测试）与 GPU 侧（[com.fanhua.camera.camera.gl.passes.GradePass]）
 * 共同使用，因此必须保持**纯函数、无 Android 依赖**，两条路径的结果才能一致。
 */
data class FilterGrade(
    val contrast: Float,
    val saturation: Float,
    val temperature: Float,
    val fade: Float,
    val lift: FloatArray,
    val gain: FloatArray,
    val shadowTint: FloatArray,
    val highlightTint: FloatArray,
    val bloomStrength: Float,
    val bloomThreshold: Float,
    val grain: Float,
    val vignette: Float,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val greenHue: Float = 0f,
    val greenSat: Float = 0f,
    val greenLum: Float = 0f,
    /** 局部对比（中间调层次感），正数增强，负数柔化。 */
    val clarity: Float = 0f,
    /** 高光红晕强度，模拟胶片 halation。 */
    val halation: Float = 0f,
    /** S 曲线暗部压缩强度，0 表示不生效。 */
    val curveShadow: Float = 0f,
    /** S 曲线亮部提亮强度，0 表示不生效。 */
    val curveHighlight: Float = 0f
) {

    fun apply(r: Float, g: Float, b: Float): FloatArray {
        var cr = (r - 0.5f) * contrast + 0.5f
        var cg = (g - 0.5f) * contrast + 0.5f
        var cb = (b - 0.5f) * contrast + 0.5f

        cr *= 1f + temperature * 0.12f
        cg *= 1f + temperature * 0.02f
        cb *= 1f - temperature * 0.12f

        var luma = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb
        cr = luma + (cr - luma) * saturation
        cg = luma + (cg - luma) * saturation
        cb = luma + (cb - luma) * saturation

        val shadowMask = 1f - smoothstep(0f, 0.46f, luma)
        val highlightMask = smoothstep(0.54f, 1f, luma)

        cr += lift[0] * (1f - luma) + shadowTint[0] * shadowMask + highlightTint[0] * highlightMask
        cg += lift[1] * (1f - luma) + shadowTint[1] * shadowMask + highlightTint[1] * highlightMask
        cb += lift[2] * (1f - luma) + shadowTint[2] * shadowMask + highlightTint[2] * highlightMask

        cr *= gain[0]
        cg *= gain[1]
        cb *= gain[2]

        // 高光 / 阴影明暗调整（与 GPU 侧 uHighlights / uShadows 一致）
        if (highlights != 0f) {
            val k = 1f + highlights * smoothstep(0.55f, 1f, luma) * 0.40f
            cr *= k
            cg *= k
            cb *= k
        }
        if (shadows != 0f) {
            val k = 1f + shadows * (1f - smoothstep(0f, 0.45f, luma)) * 0.50f
            cr *= k
            cg *= k
            cb *= k
        }

        // 绿色通道 HSL 微调（与 GPU 侧 uGreenH / uGreenS / uGreenL 一致）
        if (greenHue != 0f || greenSat != 0f || greenLum != 0f) {
            val hsl = rgbToHsl(cr, cg, cb)
            val greenDistance = abs(hsl[0] - 0.5f)
            val greenMask = 1f - smoothstep(0.10f, 0.30f, greenDistance)
            if (greenMask > 0.001f) {
                var h = (hsl[0] + greenHue / 360f) % 1f
                if (h < 0f) h += 1f
                val sat = hsl[1] * (1f + greenSat * greenMask)
                val lum = hsl[2] * (1f + greenLum * greenMask)
                val rgb = hslToRgb(h, sat, lum)
                cr = rgb[0]
                cg = rgb[1]
                cb = rgb[2]
                luma = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb
            }
        }

        // 局部对比：把中间调往外推，等效 S 曲线，避免死黑死白。
        if (clarity != 0f) {
            val midMask = 1f - abs(2f * luma - 1f)
            val amount = clarity * midMask
            cr = 0.5f + (cr - 0.5f) * (1f + amount)
            cg = 0.5f + (cg - 0.5f) * (1f + amount)
            cb = 0.5f + (cb - 0.5f) * (1f + amount)
            luma = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb
        }

        // 胶片 halation：高光处叠加少量红光溢出。
        if (halation != 0f) {
            val hot = smoothstep(0.62f, 1f, luma) * halation
            cr += hot * 0.30f
            cg += hot * 0.08f
            cb += hot * 0.03f
        }

        // 双段曲线：分别压暗部、提亮部，模拟胶片的肩趾。
        if (curveShadow != 0f) {
            val m = 1f - smoothstep(0f, 0.5f, luma)
            val k = curveShadow * m
            cr -= k * 0.10f * (1f - cr)
            cg -= k * 0.10f * (1f - cg)
            cb -= k * 0.10f * (1f - cb)
        }
        if (curveHighlight != 0f) {
            val m = smoothstep(0.5f, 1f, luma)
            val k = curveHighlight * m
            cr += k * 0.10f * (1f - cr)
            cg += k * 0.10f * (1f - cg)
            cb += k * 0.10f * (1f - cb)
        }

        val fadedR = 0.5f + (cr - 0.5f) * 0.82f + 0.055f
        val fadedG = 0.5f + (cg - 0.5f) * 0.82f + 0.055f
        val fadedB = 0.5f + (cb - 0.5f) * 0.82f + 0.055f

        return floatArrayOf(
            clamp01(cr + (fadedR - cr) * fade),
            clamp01(cg + (fadedG - cg) * fade),
            clamp01(cb + (fadedB - cb) * fade)
        )
    }

    private fun abs(value: Float): Float = if (value < 0f) -value else value

    /** RGB → HSL，h/s/l 均为 0..1，与 GLSL 的 rgb2hsl 逐行对应。 */
    private fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
        val mx = max(max(r, g), b)
        val mn = min(min(r, g), b)
        val l = (mx + mn) * 0.5f
        val d = mx - mn
        var h = 0f
        var s = 0f
        if (d > 0.001f) {
            s = d / max(1f - abs(2f * l - 1f), 0.0001f)
            h = when (mx) {
                r -> (((g - b) / d + (if (g < b) 6f else 0f)) / 6f)
                g -> (((b - r) / d + 2f) / 6f)
                else -> (((r - g) / d + 4f) / 6f)
            }
            h -= kotlin.math.floor(h)
        }
        return floatArrayOf(h, s, l)
    }

    /** HSL → RGB，与 GLSL 的 hsl2rgb 逐行对应。 */
    private fun hslToRgb(h: Float, s: Float, l: Float): FloatArray {
        val rgb = floatArrayOf(0f, 0f, 0f)
        val mod = floatArrayOf(h * 6f, h * 6f + 4f, h * 6f + 2f)
        for (i in 0..2) {
            var v = mod[i] % 6f
            if (v < 0f) v += 6f
            val raw = abs(v - 3f) - 1f
            val ch = raw.coerceIn(0f, 1f)
            rgb[i] = l + s * (ch - 0.5f) * (1f - abs(2f * l - 1f))
        }
        return rgb
    }

    private fun clamp01(value: Float): Float = min(1f, max(0f, value))

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    companion object {
        private val NEUTRAL = floatArrayOf(0f, 0f, 0f)
        private val UNIT = floatArrayOf(1f, 1f, 1f)

        fun of(preset: FilterPreset): FilterGrade = when (preset) {
            FilterPreset.ORIGINAL -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0.02f,
                bloomThreshold = 0.97f,
                grain = 0f,
                vignette = 0f
            )

            // DREAM 走真实 LUT（lut_effect_fbld），参数部分完全恒等，
            // 避免与 LUT 叠加产生偏差。
            FilterPreset.DREAM -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.LEICA_IA -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.LEICA_CLASSIC -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.LEICA_CHROME -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.LEICA_CONTEMPORARY -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.LEICA_ETERNAL -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.LEICA_BRASS -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.LEICA_TEAL -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // ───────── 徕卡经典机身（无 3D LUT，参数化还原） ─────────
            // M9：CCD 色彩特征 —— 饱和度高、暖调、红色厚重、暗部微绿、高光柔和。
            FilterPreset.LEICA_M9 -> FilterGrade(
                contrast = 1.08f,
                saturation = 1.18f,
                temperature = 0.16f,
                fade = 0.02f,
                lift = floatArrayOf(-0.012f, 0.004f, -0.008f),
                gain = floatArrayOf(1.04f, 1.0f, 0.96f),
                shadowTint = floatArrayOf(-0.010f, 0.012f, -0.006f),
                highlightTint = floatArrayOf(0.018f, 0.006f, -0.012f),
                bloomStrength = 0.05f,
                bloomThreshold = 0.92f,
                grain = 0.04f,
                vignette = 0.10f,
                highlights = -0.05f,
                shadows = 0.03f,
                clarity = 0.10f,
                halation = 0.04f,
                curveShadow = 0.10f,
                curveHighlight = 0.06f
            )

            // M3：早期胶片机身 —— 低饱和、暖调、宽容度高、轻微褪色。
            FilterPreset.LEICA_M3 -> FilterGrade(
                contrast = 0.94f,
                saturation = 0.86f,
                temperature = 0.10f,
                fade = 0.08f,
                lift = floatArrayOf(0.020f, 0.016f, 0.010f),
                gain = floatArrayOf(1.02f, 1.0f, 0.97f),
                shadowTint = floatArrayOf(0.008f, 0.010f, 0.004f),
                highlightTint = floatArrayOf(0.012f, 0.004f, -0.008f),
                bloomStrength = 0.07f,
                bloomThreshold = 0.90f,
                grain = 0.07f,
                vignette = 0.08f,
                highlights = -0.03f,
                shadows = 0.06f,
                clarity = -0.04f,
                halation = 0.06f,
                curveShadow = 0.04f,
                curveHighlight = 0.02f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.FILM_VIVID -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.FILM_CLASSIC -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.FILM_NATURE -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.FILM_NEUTRAL -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.FILM_FRESH -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.FILM_CLEAR -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.FILM_WHITENING -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.FILM_COLD_WHITE -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.FILM_BLACKGOLD -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.FILM_RED -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.FILM_TEAL_BLUE -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.FILM_HIGH_CONTRAST -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.FILM_TEXTURE -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            // 基于真实 3D LUT 的滤镜，调色完全由 LUT 负责
            FilterPreset.FILM_BW -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0f,
                bloomThreshold = 0.99f,
                grain = 0f,
                vignette = 0f
            )

            FilterPreset.FUJI_CLASSIC -> FilterGrade(
                contrast = 1.18f,
                saturation = 0.92f,
                temperature = -0.08f,
                fade = 0.05f,
                lift = floatArrayOf(-0.010f, -0.008f, -0.002f),
                gain = floatArrayOf(1.014f, 1.010f, 1.006f),
                shadowTint = floatArrayOf(0.012f, -0.004f, 0.018f),
                highlightTint = floatArrayOf(0.006f, 0.018f, 0.012f),
                bloomStrength = 0.05f,
                bloomThreshold = 0.90f,
                grain = 0.026f,
                vignette = 0.24f,
                greenHue = 6f,
                greenSat = 0.14f,
                greenLum = -0.04f,
                clarity = 0.10f,
                curveShadow = 0.24f,
                curveHighlight = 0.08f
            )

            // 柯达波特拉：暖肤色、柔和对比、微褪色。
            FilterPreset.KODAK_PORTRA -> FilterGrade(
                contrast = 0.98f,
                saturation = 1.04f,
                temperature = 0.42f,
                fade = 0.12f,
                lift = floatArrayOf(0.014f, 0.008f, 0.004f),
                gain = floatArrayOf(1.026f, 1.006f, 0.978f),
                shadowTint = floatArrayOf(0.010f, 0.006f, 0.020f),
                highlightTint = floatArrayOf(0.038f, 0.026f, 0.010f),
                bloomStrength = 0.10f,
                bloomThreshold = 0.86f,
                grain = 0.030f,
                vignette = 0.20f,
                halation = 0.26f,
                curveHighlight = 0.14f
            )

            // 胶片颗粒：中性色、强颗粒、轻微褪色。
            FilterPreset.FILM_GRAIN -> FilterGrade(
                contrast = 1.10f,
                saturation = 1.00f,
                temperature = 0.06f,
                fade = 0.16f,
                lift = floatArrayOf(0.006f, 0.004f, 0.008f),
                gain = floatArrayOf(1.010f, 1.006f, 1.004f),
                shadowTint = floatArrayOf(0.014f, 0.008f, 0.020f),
                highlightTint = floatArrayOf(0.018f, 0.012f, 0.006f),
                bloomStrength = 0.08f,
                bloomThreshold = 0.88f,
                grain = 0.085f,
                vignette = 0.26f,
                halation = 0.30f,
                curveShadow = 0.18f,
                curveHighlight = 0.12f
            )

            // 青橙电影：暗部压青、高光推橙，对比强化。
            FilterPreset.TEAL_ORANGE -> FilterGrade(
                contrast = 1.22f,
                saturation = 1.10f,
                temperature = 0.14f,
                fade = 0.02f,
                lift = floatArrayOf(-0.016f, -0.006f, 0.006f),
                gain = floatArrayOf(1.024f, 1.004f, 0.986f),
                shadowTint = floatArrayOf(-0.026f, 0.010f, 0.042f),
                highlightTint = floatArrayOf(0.052f, 0.026f, -0.010f),
                bloomStrength = 0.09f,
                bloomThreshold = 0.88f,
                grain = 0.014f,
                vignette = 0.30f,
                clarity = 0.14f,
                curveShadow = 0.28f,
                curveHighlight = 0.06f
            )

            // 电影质感：低饱和、青灰、明显肩部。
            FilterPreset.CINEMATIC -> FilterGrade(
                contrast = 1.12f,
                saturation = 0.82f,
                temperature = -0.06f,
                fade = 0.20f,
                lift = floatArrayOf(0.006f, 0.010f, 0.020f),
                gain = floatArrayOf(0.998f, 1.000f, 1.004f),
                shadowTint = floatArrayOf(-0.012f, 0.006f, 0.030f),
                highlightTint = floatArrayOf(0.024f, 0.020f, 0.010f),
                bloomStrength = 0.12f,
                bloomThreshold = 0.84f,
                grain = 0.020f,
                vignette = 0.36f,
                clarity = 0.08f,
                halation = 0.24f,
                curveShadow = 0.22f,
                curveHighlight = 0.10f
            )

            FilterPreset.APPLE_LOG -> FilterGrade(
                contrast = 0.70f,
                saturation = 0.86f,
                temperature = 0f,
                fade = 0.55f,
                lift = floatArrayOf(0.035f, 0.035f, 0.035f),
                gain = floatArrayOf(0.965f, 0.965f, 0.965f),
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0.02f,
                bloomThreshold = 0.98f,
                grain = 0f,
                vignette = 0.04f
            )

            FilterPreset.SDR -> FilterGrade(
                contrast = 1.05f,
                saturation = 0.98f,
                temperature = 0f,
                fade = 0.03f,
                lift = floatArrayOf(0.008f, 0.008f, 0.008f),
                gain = floatArrayOf(1.005f, 1.005f, 1.005f),
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0.02f,
                bloomThreshold = 0.97f,
                grain = 0.004f,
                vignette = 0.02f
            )

            FilterPreset.CUSTOM_LUT -> FilterGrade(
                contrast = 1f,
                saturation = 1f,
                temperature = 0f,
                fade = 0f,
                lift = NEUTRAL,
                gain = UNIT,
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0.03f,
                bloomThreshold = 0.95f,
                grain = 0f,
                vignette = 0f
            )
        }
    }
}
