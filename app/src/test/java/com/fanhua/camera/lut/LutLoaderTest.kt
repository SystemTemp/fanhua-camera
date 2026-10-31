package com.fanhua.camera.lut

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

/** .cube LUT 解析器测试。 */
class LutLoaderTest {

    private fun parse(text: String): LutData =
        LutLoader.parse(ByteArrayInputStream(text.toByteArray()))

    @Test
    fun `解析 2 阶单位 LUT`() {
        val text = buildString {
            appendLine("TITLE \"identity\"")
            appendLine("LUT_3D_SIZE 2")
            // 2x2x2 = 8 个条目，顺序为 b 外层、g 中层、r 内层
            for (b in 0..1) {
                for (g in 0..1) {
                    for (r in 0..1) {
                        appendLine("$r $g $b")
                    }
                }
            }
        }
        val lut = parse(text)
        assertEquals(2, lut.size)
        assertEquals(2 * 2 * 2 * 3, lut.data.size)
        // 第一个条目应为黑
        assertEquals(0f, lut.data[0], 0.0001f)
        // 最后一个条目应为白
        assertEquals(1f, lut.data[lut.data.size - 1], 0.0001f)
    }

    @Test
    fun `忽略注释与空行`() {
        val text = buildString {
            appendLine("# 这是注释")
            appendLine("// 这也是注释")
            appendLine()
            appendLine("LUT_3D_SIZE 2")
            for (b in 0..1) {
                for (g in 0..1) {
                    for (r in 0..1) {
                        appendLine("$r $g $b")
                    }
                }
            }
        }
        val lut = parse(text)
        assertEquals(2, lut.size)
    }

    @Test
    fun `缺少 LUT_3D_SIZE 时抛异常`() {
        try {
            parse("0 0 0\n1 1 1\n")
            fail("应当抛出异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("LUT_3D_SIZE") == true)
        }
    }

    @Test
    fun `数据不完整时抛异常`() {
        try {
            parse("LUT_3D_SIZE 4\n0 0 0\n1 1 1\n")
            fail("应当抛出异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("incomplete") == true)
        }
    }

    @Test
    fun `非法尺寸时抛异常`() {
        try {
            parse("LUT_3D_SIZE 1\n0 0 0\n")
            fail("应当抛出异常")
        } catch (e: IllegalArgumentException) {
            // 尺寸 1 不在 2..256 范围内
            assertTrue(true)
        }
    }

    @Test
    fun `domain 归一化生效`() {
        // domain 0..2 的数据应被归一化到 0..1
        val text = buildString {
            appendLine("LUT_3D_SIZE 2")
            appendLine("DOMAIN_MIN 0 0 0")
            appendLine("DOMAIN_MAX 2 2 2")
            for (b in 0..1) {
                for (g in 0..1) {
                    for (r in 0..1) {
                        appendLine("${r * 2} ${g * 2} ${b * 2}")
                    }
                }
            }
        }
        val lut = parse(text)
        assertEquals(0f, lut.data[0], 0.0001f)
        assertEquals(1f, lut.data[lut.data.size - 1], 0.0001f)
    }

    @Test
    fun `非法 domain 回退到默认`() {
        val text = buildString {
            appendLine("LUT_3D_SIZE 2")
            appendLine("DOMAIN_MIN 1 1 1")
            appendLine("DOMAIN_MAX 0 0 0")
            for (b in 0..1) {
                for (g in 0..1) {
                    for (r in 0..1) {
                        appendLine("$r $g $b")
                    }
                }
            }
        }
        val lut = parse(text)
        assertEquals(0f, lut.data[0], 0.0001f)
        assertEquals(1f, lut.data[lut.data.size - 1], 0.0001f)
    }

    @Test
    fun `常见 17 阶 LUT 能正确解析`() {
        val n = 17
        val text = buildString {
            appendLine("LUT_3D_SIZE $n")
            for (b in 0 until n) {
                for (g in 0 until n) {
                    for (r in 0 until n) {
                        appendLine(
                            "${r.toFloat() / (n - 1)} ${g.toFloat() / (n - 1)} ${b.toFloat() / (n - 1)}"
                        )
                    }
                }
            }
        }
        val lut = parse(text)
        assertEquals(n, lut.size)
        assertEquals(n * n * n * 3, lut.data.size)
    }
}
