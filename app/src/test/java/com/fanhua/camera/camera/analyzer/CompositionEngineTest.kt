package com.fanhua.camera.camera.analyzer

import com.fanhua.camera.camera.core.FaceBox
import com.fanhua.camera.camera.detect.Detection
import com.fanhua.camera.camera.detect.NormRect
import com.fanhua.camera.camera.detect.SubjectKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 构图引擎测试。 */
class CompositionEngineTest {

    private fun face(left: Float, top: Float, right: Float, bottom: Float): FaceBox =
        FaceBox(android.graphics.RectF(left, top, right, bottom), trackingId = 1)

    private fun detection(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        kind: SubjectKind = SubjectKind.PERSON
    ): Detection = Detection(
        kind = kind,
        label = "person",
        confidence = 1f,
        rect = NormRect(left, top, right, bottom)
    )

    @Test
    fun `没有人脸时返回空`() {
        assertNull(CompositionEngine.evaluate(emptyList()))
    }

    @Test
    fun `主体位于三分点附近时判定为构图良好`() {
        // 三分点为 (0.382, 0.382)
        val subject = detection(0.332f, 0.332f, 0.432f, 0.432f)
        val hint = CompositionEngine.evaluateDetections(listOf(subject))
        assertNotNull(hint)
        assertTrue("应判定为对齐", hint!!.aligned)
    }

    @Test
    fun `主体偏右下时给出向左上的建议`() {
        val subject = detection(0.80f, 0.80f, 0.95f, 0.95f)
        val hint = CompositionEngine.evaluateDetections(listOf(subject))!!
        assertTrue("不应判定为对齐", !hint.aligned)
        // 偏移方向应为负（需要往左上方移）
        assertTrue("X 偏移应为负", hint.offsetX < 0f)
        assertTrue("Y 偏移应为负", hint.offsetY < 0f)
    }

    @Test
    fun `多张人脸时以面积最大的为主体`() {
        val small = detection(0.05f, 0.05f, 0.10f, 0.10f)
        val large = detection(0.60f, 0.60f, 0.90f, 0.90f)
        val hint = CompositionEngine.evaluateDetections(listOf(small, large))!!
        // 主体中心在 (0.75, 0.75)，最近的权力点应为 (0.618, 0.618)
        val centerX = 0.75f
        val expectedOffset = 0.618f - centerX
        assertEquals("应以大面积人脸为主体", expectedOffset, hint.offsetX, 0.05f)
    }

    @Test
    fun `裁切建议框在合法范围内`() {
        val subject = detection(0.45f, 0.45f, 0.55f, 0.55f)
        val hint = CompositionEngine.evaluateDetections(listOf(subject))!!
        val crop = hint.cropRect
        if (crop != null) {
            assertTrue("左边不应越界", crop.left >= -0.0001f)
            assertTrue("上边不应越界", crop.top >= -0.0001f)
            assertTrue("右边不应越界", crop.right <= 1.0001f)
            assertTrue("下边不应越界", crop.bottom <= 1.0001f)
            assertTrue("宽度应为正", crop.width > 0f)
            assertTrue("高度应为正", crop.height > 0f)
        }
    }

    @Test
    fun `返回的构图提示携带全部人脸`() {
        val faces = listOf(
            face(0.1f, 0.1f, 0.2f, 0.2f),
            face(0.5f, 0.5f, 0.7f, 0.7f)
        )
        val hint = CompositionEngine.evaluateDetections(
            listOf(detection(0.1f, 0.1f, 0.2f, 0.2f), detection(0.5f, 0.5f, 0.7f, 0.7f)),
            faces
        )!!
        assertEquals(2, hint.faces.size)
    }

    @Test
    fun `构图提示带有建议文案资源`() {
        val subject = detection(0.85f, 0.15f, 0.95f, 0.25f)
        val hint = CompositionEngine.evaluateDetections(listOf(subject))!!
        assertTrue("建议资源应为非零", hint.suggestionRes != 0)
    }
}
