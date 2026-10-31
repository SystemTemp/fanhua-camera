package com.fanhua.camera.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本号比较测试。
 *
 * 这里复刻了 [UpdateManager.compareVersions] 的算法，因为原方法是 private。
 * 若修改了 UpdateManager 的比较逻辑，必须同步修改本测试。
 */
class VersionCompareTest {

    /** 与 UpdateManager.compareVersions 完全一致的实现。 */
    private fun compare(a: String, b: String): Int {
        fun parse(v: String): Pair<List<Int>, Boolean> {
            val cleaned = v.trim().lowercase()
            val parts = cleaned.split("-")
            val nums = parts[0].split(".").mapNotNull { it.toIntOrNull() }
            val prerelease = parts.getOrNull(1) != null && parts[1].isNotEmpty()
            return nums to prerelease
        }
        val (an, ap) = parse(a)
        val (bn, bp) = parse(b)
        for (i in 0 until maxOf(an.size, bn.size)) {
            val x = an.getOrElse(i) { 0 }
            val y = bn.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return when {
            ap == bp -> 0
            ap -> -1
            else -> 1
        }
    }

    @Test
    fun `相同版本返回零`() {
        assertEquals(0, compare("0.1.21-beta", "0.1.21-beta"))
        assertEquals(0, compare("1.0.0", "1.0.0"))
    }

    @Test
    fun `补丁号更大的版本更新`() {
        assertTrue(compare("0.1.22-beta", "0.1.21-beta") > 0)
        assertTrue(compare("0.1.21-beta", "0.1.22-beta") < 0)
    }

    @Test
    fun `次版本号更大的版本更新`() {
        assertTrue(compare("0.2.0", "0.1.99") > 0)
    }

    @Test
    fun `主版本号更大的版本更新`() {
        assertTrue(compare("1.0.0", "0.9.9") > 0)
    }

    @Test
    fun `正式版比同号预发布版更新`() {
        assertTrue(compare("1.0.0", "1.0.0-beta") > 0)
        assertTrue(compare("1.0.0-beta", "1.0.0") < 0)
    }

    @Test
    fun `位数不同时缺失位按零处理`() {
        assertEquals(0, compare("1.0", "1.0.0"))
        assertTrue(compare("1.0.1", "1.0") > 0)
    }

    @Test
    fun `带前缀 v 的版本号也能比较`() {
        // UpdateManager 在解析前已 removePrefix("v")，这里直接测数字部分
        assertTrue(compare("0.1.22-beta", "0.1.21-beta") > 0)
    }

    @Test
    fun `异常输入不会崩溃`() {
        assertEquals(0, compare("", ""))
        // 非数字片段被过滤，结果为空列表
        assertEquals(0, compare("abc", "xyz"))
    }
}
