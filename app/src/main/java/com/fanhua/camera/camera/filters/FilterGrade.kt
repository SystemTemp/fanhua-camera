package com.fanhua.camera.camera.filters

import kotlin.math.max
import kotlin.math.min

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
    val greenLum: Float = 0f
) {

    fun apply(r: Float, g: Float, b: Float): FloatArray {
        var cr = (r - 0.5f) * contrast + 0.5f
        var cg = (g - 0.5f) * contrast + 0.5f
        var cb = (b - 0.5f) * contrast + 0.5f

        cr *= 1f + temperature * 0.12f
        cg *= 1f + temperature * 0.02f
        cb *= 1f - temperature * 0.12f

        val luma = 0.2126f * cr + 0.7152f * cg + 0.0722f * cb
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

        val fadedR = 0.5f + (cr - 0.5f) * 0.82f + 0.055f
        val fadedG = 0.5f + (cg - 0.5f) * 0.82f + 0.055f
        val fadedB = 0.5f + (cb - 0.5f) * 0.82f + 0.055f

        return floatArrayOf(
            clamp01(cr + (fadedR - cr) * fade),
            clamp01(cg + (fadedG - cg) * fade),
            clamp01(cb + (fadedB - cb) * fade)
        )
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

            FilterPreset.DREAM -> FilterGrade(
                contrast = 0.93f,
                saturation = 1.08f,
                temperature = 1f,
                fade = 0.32f,
                lift = floatArrayOf(0.038f, 0.019f, 0.030f),
                gain = floatArrayOf(1.030f, 1.0f, 1.020f),
                shadowTint = floatArrayOf(0.022f, -0.004f, 0.036f),
                highlightTint = floatArrayOf(0.058f, 0.021f, 0.012f),
                bloomStrength = 0.24f,
                bloomThreshold = 0.82f,
                grain = 0.030f,
                vignette = 0.20f
            )

            FilterPreset.LEICA_MOMENT -> FilterGrade(
                contrast = 1.19f,
                saturation = 1.02f,
                temperature = 0.30f,
                fade = 0.03f,
                lift = floatArrayOf(-0.012f, -0.010f, -0.006f),
                gain = floatArrayOf(1.036f, 1.012f, 0.982f),
                shadowTint = floatArrayOf(-0.006f, -0.004f, 0.016f),
                highlightTint = floatArrayOf(0.040f, 0.026f, 0.008f),
                bloomStrength = 0.07f,
                bloomThreshold = 0.88f,
                grain = 0.018f,
                vignette = 0.34f
            )

            FilterPreset.LEICA_VIVID -> FilterGrade(
                contrast = 1.14f,
                saturation = 1.30f,
                temperature = 0.12f,
                fade = 0f,
                lift = floatArrayOf(-0.012f, -0.010f, -0.008f),
                gain = floatArrayOf(1.032f, 1.010f, 0.978f),
                shadowTint = floatArrayOf(0f, 0.002f, 0.014f),
                highlightTint = floatArrayOf(0.032f, 0.016f, 0f),
                bloomStrength = 0.06f,
                bloomThreshold = 0.90f,
                grain = 0.012f,
                vignette = 0.18f,
                greenSat = 0.10f
            )

            FilterPreset.LEICA_NATURAL -> FilterGrade(
                contrast = 1.06f,
                saturation = 1.06f,
                temperature = 0.04f,
                fade = 0.02f,
                lift = floatArrayOf(-0.004f, -0.004f, -0.002f),
                gain = floatArrayOf(1.010f, 1.004f, 0.996f),
                shadowTint = floatArrayOf(0f, 0.001f, 0.008f),
                highlightTint = floatArrayOf(0.014f, 0.010f, 0.004f),
                bloomStrength = 0.04f,
                bloomThreshold = 0.92f,
                grain = 0.008f,
                vignette = 0.10f
            )

            FilterPreset.LEICA_MONO -> FilterGrade(
                contrast = 1.22f,
                saturation = 0f,
                temperature = 0f,
                fade = 0.04f,
                lift = floatArrayOf(-0.008f, -0.008f, -0.008f),
                gain = floatArrayOf(1.030f, 1.030f, 1.030f),
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0.04f,
                bloomThreshold = 0.92f,
                grain = 0.040f,
                vignette = 0.34f
            )

            FilterPreset.LEICA_BW_HC -> FilterGrade(
                contrast = 1.46f,
                saturation = 0f,
                temperature = 0f,
                fade = 0f,
                lift = floatArrayOf(-0.020f, -0.020f, -0.020f),
                gain = floatArrayOf(1.060f, 1.060f, 1.060f),
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0.03f,
                bloomThreshold = 0.94f,
                grain = 0.062f,
                vignette = 0.42f,
                highlights = -0.16f
            )

            FilterPreset.LEICA_BROWN -> FilterGrade(
                contrast = 1.18f,
                saturation = 0f,
                temperature = 0f,
                fade = 0.08f,
                lift = floatArrayOf(-0.010f, -0.010f, -0.012f),
                gain = floatArrayOf(1.024f, 1.020f, 1.010f),
                shadowTint = floatArrayOf(0.052f, 0.030f, 0.008f),
                highlightTint = floatArrayOf(0.062f, 0.046f, 0.022f),
                bloomStrength = 0.05f,
                bloomThreshold = 0.90f,
                grain = 0.048f,
                vignette = 0.34f
            )

            FilterPreset.LEICA_BW_BLUE -> FilterGrade(
                contrast = 1.26f,
                saturation = 0f,
                temperature = 0f,
                fade = 0.06f,
                lift = floatArrayOf(-0.008f, -0.006f, -0.004f),
                gain = floatArrayOf(1.020f, 1.028f, 1.036f),
                shadowTint = floatArrayOf(0.002f, 0.022f, 0.052f),
                highlightTint = floatArrayOf(0.010f, 0.030f, 0.058f),
                bloomStrength = 0.04f,
                bloomThreshold = 0.92f,
                grain = 0.045f,
                vignette = 0.32f
            )

            FilterPreset.LEICA_PORTRAIT -> FilterGrade(
                contrast = 0.92f,
                saturation = 0.88f,
                temperature = 0.22f,
                fade = 0.18f,
                lift = floatArrayOf(0.020f, 0.010f, 0.004f),
                gain = floatArrayOf(1.018f, 1.008f, 1.002f),
                shadowTint = floatArrayOf(-0.002f, 0.004f, 0.014f),
                highlightTint = floatArrayOf(0.030f, 0.024f, 0.016f),
                bloomStrength = 0.14f,
                bloomThreshold = 0.84f,
                grain = 0.016f,
                vignette = 0.16f
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
