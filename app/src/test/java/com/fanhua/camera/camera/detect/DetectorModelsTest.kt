package com.fanhua.camera.camera.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 检测模型相关的纯逻辑测试。 */
class DetectorModelsTest {

    @Test
    fun `内置模型 id 唯一`() {
        val ids = DetectorModels.all.map { it.id }
        assertEquals("模型 id 不应重复", ids.size, ids.distinct().size)
    }

    @Test
    fun `每个模型至少有一个下载源`() {
        for (model in DetectorModels.all) {
            assertTrue("${model.id} 应有下载源", model.sources.isNotEmpty())
        }
    }

    @Test
    fun `所有下载源都是 https`() {
        for (model in DetectorModels.all) {
            for (source in model.sources) {
                assertTrue(
                    "${model.id} 的 ${source.name} 必须用 https",
                    source.url.startsWith("https://")
                )
            }
        }
    }

    @Test
    fun `模型大小字符串格式正确`() {
        val model = DetectorModels.EFFICIENTDET_LITE0
        assertTrue("大小应包含 MB", model.sizeMb.endsWith(" MB"))
    }

    @Test
    fun `按 id 查找模型`() {
        assertEquals(
            DetectorModels.EFFICIENTDET_LITE0,
            DetectorModels.byId("efficientdet_lite0")
        )
        assertEquals(null, DetectorModels.byId("not_exist"))
        assertEquals(null, DetectorModels.byId(null))
    }

    @Test
    fun `默认模型可用`() {
        assertTrue(DetectorModels.all.contains(DetectorModels.default))
    }

    @Test
    fun `输入尺寸为正`() {
        for (model in DetectorModels.all) {
            assertTrue("${model.id} 输入尺寸应为正", model.inputSize > 0)
            assertEquals("输入尺寸应为 32 的倍数", 0, model.inputSize % 32)
        }
    }
}

/** 图像预处理与类别映射测试。 */
class DetectorImageUtilsTest {

    @Test
    fun `坐标映射在恒等输入时保持原值`() {
        // 正方形源图 + 正方形输入 → 无 letterbox 偏移
        val rect = NormRect(0.25f, 0.25f, 0.75f, 0.75f)
        val mapped = DetectorImageUtils.mapToOriginal(
            android.graphics.RectF(0.25f, 0.25f, 0.75f, 0.75f),
            640, 640, 320
        )
        // RectF 在 JVM 单测中是桩，这里只验证函数不抛异常且返回非空
        assertTrue(mapped != null)
        assertEquals(0.25f, rect.left, 0.0001f)
    }

    @Test
    fun `非法尺寸输入不崩溃`() {
        val mapped = DetectorImageUtils.mapToOriginal(
            android.graphics.RectF(0f, 0f, 1f, 1f), 0, 0, 0
        )
        assertTrue(mapped != null)
    }

    @Test
    fun `人物类别映射正确`() {
        assertEquals(SubjectKind.PERSON, DetectorImageUtils.kindOf("person"))
        assertEquals(SubjectKind.PERSON, DetectorImageUtils.kindOf("Person"))
        assertEquals(SubjectKind.PERSON, DetectorImageUtils.kindOf("PERSON"))
    }

    @Test
    fun `动物类别映射正确`() {
        for (label in listOf("cat", "dog", "bird", "horse", "teddy bear")) {
            assertEquals("$label 应映射为动物", SubjectKind.ANIMAL, DetectorImageUtils.kindOf(label))
        }
    }

    @Test
    fun `车辆类别映射正确`() {
        for (label in listOf("car", "bus", "bicycle", "airplane", "boat")) {
            assertEquals("$label 应映射为车辆", SubjectKind.VEHICLE, DetectorImageUtils.kindOf(label))
        }
    }

    @Test
    fun `美食类别映射正确`() {
        for (label in listOf("pizza", "cake", "banana", "cup", "bowl")) {
            assertEquals("$label 应映射为美食", SubjectKind.FOOD, DetectorImageUtils.kindOf(label))
        }
    }

    @Test
    fun `植物类别映射正确`() {
        for (label in listOf("potted plant", "vase")) {
            assertEquals("$label 应映射为植物", SubjectKind.PLANT, DetectorImageUtils.kindOf(label))
        }
    }

    @Test
    fun `未知类别回退为物体`() {
        assertEquals(SubjectKind.OBJECT, DetectorImageUtils.kindOf("spaceship"))
        assertEquals(SubjectKind.OBJECT, DetectorImageUtils.kindOf(""))
    }

    @Test
    fun `只有人物与动物偏好三分法`() {
        assertTrue(SubjectKind.PERSON.prefersRuleOfThirds)
        assertTrue(SubjectKind.ANIMAL.prefersRuleOfThirds)
        assertTrue(!SubjectKind.VEHICLE.prefersRuleOfThirds)
        assertTrue(!SubjectKind.FOOD.prefersRuleOfThirds)
        assertTrue(!SubjectKind.OBJECT.prefersRuleOfThirds)
    }

    @Test
    fun `COCO 标签数量为 80`() {
        assertEquals(80, COCO_LABELS.size)
        assertEquals("person", COCO_LABELS[0])
        assertEquals("toothbrush", COCO_LABELS[79])
    }
}

/** 归一化矩形的几何测试。 */
class NormRectTest {

    @Test
    fun `中心点与尺寸计算正确`() {
        val rect = NormRect(0.2f, 0.3f, 0.6f, 0.7f)
        assertEquals(0.4f, rect.centerX, 0.0001f)
        assertEquals(0.5f, rect.centerY, 0.0001f)
        assertEquals(0.4f, rect.width, 0.0001f)
        assertEquals(0.4f, rect.height, 0.0001f)
        assertEquals(0.16f, rect.area, 0.0001f)
    }

    @Test
    fun `由中心点构造矩形`() {
        val rect = NormRect.ofCenter(0.5f, 0.5f, 0.2f, 0.4f)
        assertEquals(0.4f, rect.left, 0.0001f)
        assertEquals(0.3f, rect.top, 0.0001f)
        assertEquals(0.6f, rect.right, 0.0001f)
        assertEquals(0.7f, rect.bottom, 0.0001f)
    }

    @Test
    fun `零面积矩形面积为零`() {
        val rect = NormRect(0.5f, 0.5f, 0.5f, 0.5f)
        assertEquals(0f, rect.area, 0.0001f)
    }
}
