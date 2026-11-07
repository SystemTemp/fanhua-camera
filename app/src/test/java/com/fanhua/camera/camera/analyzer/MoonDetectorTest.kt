package com.fanhua.camera.camera.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 月亮检测器测试。
 *
 * 用合成图验证：明确的月亮要被识别，而「窗框亮条 / 彩色光斑 / 过曝天空 /
 * 贴边光源 / 满屏白」这些相似干扰不能被误判。
 */
class MoonDetectorTest {

    private val W = 320
    private val H = 240

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** 夜空底色（带轻微噪声，模拟真实暗部）。 */
    private fun nightSky(px: IntArray, seed: Int = 1) {
        var s = seed
        for (y in 0 until H) {
            for (x in 0 until W) {
                s = s * 1103515245 + 12345
                val n = (s ushr 16 and 0x1F) - 16
                val v = (6 + n).coerceIn(0, 30)
                px[y * W + x] = argb(v, v + 2, v + 8)
            }
        }
    }

    private fun drawDisc(
        px: IntArray, cx: Float, cy: Float, r: Float,
        core: Int = 250, edge: Int = 205
    ) {
        for (y in 0 until H) {
            for (x in 0 until W) {
                val dx = x - cx
                val dy = y - cy
                val d = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (d <= r) {
                    // 中心亮、边缘略暗，模拟真实月面
                    val t = (d / r).coerceIn(0f, 1f)
                    val v = (core - (core - edge) * t * t).toInt().coerceIn(0, 255)
                    px[y * W + x] = argb(v, v, (v * 0.97f).toInt())
                }
            }
        }
    }

    @Test
    fun `识别画面中的月亮`() {
        val px = IntArray(W * H)
        nightSky(px)
        drawDisc(px, 160f, 100f, 34f)

        val moon = MoonDetector.detectPixels(px, W, H)
        assertNotNull("应当识别到月亮", moon)
        moon!!
        val cx = (moon.norm.left + moon.norm.right) / 2f
        val cy = (moon.norm.top + moon.norm.bottom) / 2f
        assertTrue("中心x偏差过大: $cx", abs(cx - 0.5f) < 0.06f)
        assertTrue("中心y偏差过大: $cy", abs(cy - 100f / H) < 0.08f)
        assertTrue("置信度过低: ${moon.confidence}", moon.confidence > 0.6f)
        // 半径应接近 34/240
        assertTrue("半径比例异常: ${moon.radiusRatio}",
            abs(moon.radiusRatio - 34f / H) < 0.05f)
    }

    @Test
    fun `角落的小月亮也能识别`() {
        val px = IntArray(W * H)
        nightSky(px, seed = 7)
        drawDisc(px, 62f, 58f, 20f)

        val moon = MoonDetector.detectPixels(px, W, H)
        assertNotNull(moon)
        val cx = (moon!!.norm.left + moon.norm.right) / 2f
        assertTrue("应偏左", cx < 0.35f)
    }

    @Test
    fun `彩色光斑不是月亮`() {
        val px = IntArray(W * H)
        nightSky(px)
        // 高饱和橙色圆斑（路灯光晕）
        for (y in 0 until H) {
            for (x in 0 until W) {
                val dx = x - 160
                val dy = y - 100
                if (dx * dx + dy * dy <= 34 * 34) {
                    px[y * W + x] = argb(255, 150, 20)
                }
            }
        }
        assertNull("高饱和光斑不应判为月亮", MoonDetector.detectPixels(px, W, H))
    }

    @Test
    fun `明亮的方框不是月亮`() {
        val px = IntArray(W * H)
        nightSky(px)
        // 亮矩形（窗框 / 屏幕）
        for (y in 70 until 140) {
            for (x in 110 until 210) {
                px[y * W + x] = argb(240, 240, 235)
            }
        }
        assertNull("方形亮块不应判为月亮", MoonDetector.detectPixels(px, W, H))
    }

    @Test
    fun `贴边的强光源不是月亮`() {
        val px = IntArray(W * H)
        nightSky(px)
        // 圆盘一半在画面外
        drawDisc(px, 4f, 100f, 40f)
        assertNull("贴边光源不应判为月亮", MoonDetector.detectPixels(px, W, H))
    }

    @Test
    fun `整片过曝天空不是月亮`() {
        val px = IntArray(W * H)
        for (i in px.indices) px[i] = argb(240, 242, 248)
        assertNull("全白画面不应判为月亮", MoonDetector.detectPixels(px, W, H))
    }

    @Test
    fun `纯黑夜空不报错且返回空`() {
        val px = IntArray(W * H)
        for (i in px.indices) px[i] = argb(0, 0, 0)
        assertNull(MoonDetector.detectPixels(px, W, H))
    }

    @Test
    fun `水平亮带不是月亮`() {
        val px = IntArray(W * H)
        nightSky(px)
        for (y in 110 until 132) {
            for (x in 0 until W) px[y * W + x] = argb(235, 235, 240)
        }
        assertNull("横向亮带不应判为月亮", MoonDetector.detectPixels(px, W, H))
    }
}
