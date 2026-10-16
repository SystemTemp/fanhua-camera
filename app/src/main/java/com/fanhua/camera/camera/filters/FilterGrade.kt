package com.fanhua.camera.camera.filters

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
    val vignette: Float
) {
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
                saturation = 0.87f,
                temperature = 0.35f,
                fade = 0.04f,
                lift = floatArrayOf(-0.014f, -0.011f, -0.007f),
                gain = floatArrayOf(1.042f, 1.018f, 0.976f),
                shadowTint = floatArrayOf(-0.013f, -0.006f, 0.021f),
                highlightTint = floatArrayOf(0.046f, 0.029f, 0.011f),
                bloomStrength = 0.07f,
                bloomThreshold = 0.88f,
                grain = 0.018f,
                vignette = 0.34f
            )

            FilterPreset.LEICA_MONO -> FilterGrade(
                contrast = 1.28f,
                saturation = 0f,
                temperature = 0f,
                fade = 0.10f,
                lift = floatArrayOf(-0.008f, -0.008f, -0.008f),
                gain = floatArrayOf(1.035f, 1.035f, 1.035f),
                shadowTint = NEUTRAL,
                highlightTint = NEUTRAL,
                bloomStrength = 0.04f,
                bloomThreshold = 0.92f,
                grain = 0.050f,
                vignette = 0.36f
            )

            FilterPreset.LEICA_PORTRAIT -> FilterGrade(
                contrast = 0.90f,
                saturation = 0.80f,
                temperature = 0.28f,
                fade = 0.24f,
                lift = floatArrayOf(0.024f, 0.012f, 0.006f),
                gain = floatArrayOf(1.018f, 1.008f, 1.004f),
                shadowTint = floatArrayOf(-0.004f, 0.002f, 0.012f),
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

            // 自定义 LUT：无内置调色，颜色由 3D LUT 决定
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
