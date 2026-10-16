package com.fanhua.camera.lut

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

/**
 * 解析 Adobe/Blackmagic 风格 .cube 3D LUT 文件。
 * 支持：LUT_3D_SIZE、DOMAIN_MIN/MAX、TITLE、# 注释，忽略每行多余数值。
 */
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

        // 数据截断到预期数量（多余行忽略）
        val data = FloatArray(expected)
        for (i in 0 until expected) data[i] = values[i]

        // DOMAIN 映射到 0..1
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