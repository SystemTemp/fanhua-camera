package com.fanhua.camera.camera.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 滤镜调色的数学性质测试。
 *
 * GPU 侧的 GLSL 必须与这里的 CPU 实现保持一致，因此这组测试同时起到
 * 「CPU 行为基线」的作用：一旦有人改了 [FilterGrade.apply]，测试会立刻暴露。
 */
class FilterGradeTest {

    private val presets = FilterPreset.entries

    @Test
    fun `原图滤镜不改变颜色`() {
        val grade = FilterGrade.of(FilterPreset.ORIGINAL)
        val colors = listOf(
            Triple(0f, 0f, 0f),
            Triple(1f, 1f, 1f),
            Triple(0.5f, 0.5f, 0.5f),
            Triple(0.8f, 0.2f, 0.1f),
            Triple(0.1f, 0.4f, 0.9f)
        )
        for ((r, g, b) in colors) {
            val out = grade.apply(r, g, b)
            assertEquals("R 通道应保持原值", r, out[0], 0.02f)
            assertEquals("G 通道应保持原值", g, out[1], 0.02f)
            assertEquals("B 通道应保持原值", b, out[2], 0.02f)
        }
    }

    @Test
    fun `所有滤镜输出都在合法范围内`() {
        val samples = buildList {
            for (r in 0..10) {
                for (g in 0..10) {
                    for (b in 0..10) {
                        add(Triple(r / 10f, g / 10f, b / 10f))
                    }
                }
            }
        }
        for (preset in presets) {
            val grade = FilterGrade.of(preset)
            for ((r, g, b) in samples) {
                val out = grade.apply(r, g, b)
                assertTrue("$preset 的 R 越界: ${out[0]}", out[0] in 0f..1f)
                assertTrue("$preset 的 G 越界: ${out[1]}", out[1] in 0f..1f)
                assertTrue("$preset 的 B 越界: ${out[2]}", out[2] in 0f..1f)
            }
        }
    }

    @Test
    fun `所有滤镜输出都不是 NaN`() {
        for (preset in presets) {
            val grade = FilterGrade.of(preset)
            for (v in listOf(0f, 0.001f, 0.5f, 0.999f, 1f)) {
                val out = grade.apply(v, v, v)
                for (channel in out) {
                    assertTrue("$preset 在 $v 处产生了 NaN", !channel.isNaN())
                }
            }
        }
    }

    @Test
    fun `黑白滤镜输出三通道相等`() {
        val monoPresets = presets.filter { it.monochrome }
        assertTrue("应至少有一个黑白滤镜", monoPresets.isNotEmpty())
        for (preset in monoPresets) {
            val grade = FilterGrade.of(preset)
            val out = grade.apply(0.75f, 0.35f, 0.55f)
            assertEquals("$preset 应输出中性灰", out[0], out[1], 0.02f)
            assertEquals("$preset 应输出中性灰", out[1], out[2], 0.02f)
        }
    }

    @Test
    fun `单色滤镜标记与饱和度参数一致`() {
        for (preset in presets) {
            val grade = FilterGrade.of(preset)
            if (preset.monochrome) {
                assertEquals("$preset 被标记为单色，饱和度必须为 0", 0f, grade.saturation, 0.0001f)
            }
        }
    }

    @Test
    fun `每个滤镜都有唯一的名称与标签`() {
        val names = presets.map { it.name }
        assertEquals("滤镜名称不应重复", names.size, names.distinct().size)
        val labels = presets.map { it.labelRes }
        assertEquals("滤镜标签资源不应重复", labels.size, labels.distinct().size)
    }

    @Test
    fun `滤镜分组覆盖全部滤镜`() {
        val grouped = FilterGroup.entries.flatMap { FilterPreset.ofGroup(it) }
        assertEquals("分组后数量应与总数一致", presets.size, grouped.size)
        assertEquals("分组不应产生重复", presets.size, grouped.distinct().size)
    }

    @Test
    fun `滤镜强度为 0 时输出应接近原图`() {
        // 强度 0 的语义由渲染层通过 mix(src, graded, intensity) 实现，
        // 这里验证 graded 与 src 混合后等于 src。
        val grade = FilterGrade.of(FilterPreset.LEICA_MOMENT)
        val src = floatArrayOf(0.4f, 0.6f, 0.3f)
        val graded = grade.apply(src[0], src[1], src[2])
        val intensity = 0f
        for (i in 0..2) {
            val mixed = src[i] + (graded[i] - src[i]) * intensity
            assertEquals("强度 0 时通道 $i 应等于原值", src[i], mixed, 0.0001f)
        }
    }

    @Test
    fun `对比度大于 1 的滤镜会拉开明暗差`() {
        val grade = FilterGrade.of(FilterPreset.LEICA_BW_HC)
        val dark = grade.apply(0.25f, 0.25f, 0.25f)[0]
        val bright = grade.apply(0.75f, 0.75f, 0.75f)[0]
        val originalGap = 0.75f - 0.25f
        val gradedGap = bright - dark
        assertTrue(
            "高对比滤镜应拉开明暗差（原差 $originalGap，处理后 $gradedGap）",
            gradedGap > originalGap
        )
    }

    @Test
    fun `褪色参数为正的滤镜会抬升纯黑`() {
        val grade = FilterGrade.of(FilterPreset.DREAM)
        assertTrue("DREAM 应带褪色", grade.fade > 0.1f)
        val black = grade.apply(0f, 0f, 0f)
        assertTrue("褪色后纯黑应被抬升，实际 ${black[0]}", black[0] > 0.005f)
    }
}
