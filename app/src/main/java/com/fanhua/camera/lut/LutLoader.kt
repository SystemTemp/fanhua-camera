package com.fanhua.camera.lut

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

object LutLoader {

    fun parse(input: InputStream): LutData {
        val reader = BufferedReader(InputStreamReader(input))
        var size = 0
        var domainMin = floatArrayOf(0f, 0f, 0f)
        var domainMax = floatArrayOf(1f, 1f, 1f)
        val values = ArrayList<Float>(1024)

        reader.forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) return@forEachLine
            val tokens = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return@forEachLine
            val head = tokens[0].uppercase(Locale.US)
            when (head) {
                "LUT_3D_SIZE", "LUT3DSIZE" -> {
                    size = tokens.getOrNull(1)?.toIntOrNull()?.takeIf { it in 2..256 }
                        ?: throw IllegalArgumentException("bad LUT size: $line")
                }

                "DOMAIN_MIN" -> {
                    tokens.drop(1).take(3).mapNotNull { it.toFloatOrNull() }.toFloatArray().also {
                        if (it.size == 3) domainMin = it
                    }
                }

                "DOMAIN_MAX" -> {
                    tokens.drop(1).take(3).mapNotNull { it.toFloatOrNull() }.toFloatArray().also {
                        if (it.size == 3) domainMax = it
                    }
                }

                "TITLE" -> Unit

                else -> {
                    val nums = tokens.take(3).mapNotNull { it.toFloatOrNull() }
                    if (nums.size == 3) {
                        values.add(nums[0])
                        values.add(nums[1])
                        values.add(nums[2])
                    }
                }
            }
        }

        if (size <= 0) throw IllegalArgumentException("missing LUT_3D_SIZE")
        val expected = size * size * size * 3
        if (values.size < expected) throw IllegalArgumentException(
            "LUT data incomplete: got ${values.size / 3} of ${size * size * size} entries"
        )

        val data = FloatArray(expected)
        for (i in 0 until expected) data[i] = values[i]

        var min = domainMin
        var max = domainMax
        if (min[0] >= max[0] || min[1] >= max[1] || min[2] >= max[2]) {
            min = floatArrayOf(0f, 0f, 0f)
            max = floatArrayOf(1f, 1f, 1f)
        }
        if (min[0] != 0f || min[1] != 0f || min[2] != 0f ||
            max[0] != 1f || max[1] != 1f || max[2] != 1f
        ) {
            for (i in 0 until expected step 3) {
                data[i] = ((data[i] - min[0]) / (max[0] - min[0])).coerceIn(0f, 1f)
                data[i + 1] = ((data[i + 1] - min[1]) / (max[1] - min[1])).coerceIn(0f, 1f)
                data[i + 2] = ((data[i + 2] - min[2]) / (max[2] - min[2])).coerceIn(0f, 1f)
            }
        }

        return LutData(size, data, min, max)
    }
}


/**
 * 二进制 LUT（`.lut`）的解析器。
 *
 * 格式：头部 4 字节大端 int32 = 网格边长 N，其后紧跟 N³ × 3 个字节（RGB888），
 * 索引顺序与 .cube 一致：R 变化最快，其次 G，最后 B。
 *
 * 之所以用二进制而不是 .cube 文本：同样的 33³ LUT，
 * 文本约 750KB（APK 压缩后仍很大），二进制只要 107KB。
 */
object BinaryLutLoader {

    /** 解析二进制 LUT 数据。 */
    fun parse(bytes: ByteArray): LutData? {
        if (bytes.size < 4) return null
        val size = ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
        if (size !in 2..128) return null
        val expected = 4 + size * size * size * 3
        if (bytes.size < expected) return null
        val data = FloatArray(size * size * size * 3)
        var src = 4
        for (i in data.indices) {
            data[i] = (bytes[src].toInt() and 0xFF) / 255f
            src++
        }
        return LutData(size, data)
    }

    /** 生成二进制 LUT（供构建期使用）。 */
    fun encode(size: Int, data: FloatArray): ByteArray {
        val out = ByteArray(4 + size * size * size * 3)
        out[0] = (size ushr 24).toByte()
        out[1] = (size ushr 16).toByte()
        out[2] = (size ushr 8).toByte()
        out[3] = size.toByte()
        var dst = 4
        for (v in data) {
            out[dst++] = (v * 255f).toInt().coerceIn(0, 255).toByte()
        }
        return out
    }
}
