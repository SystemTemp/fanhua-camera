package com.fanhua.camera.camera.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 照片画幅与曝光状态的纯逻辑测试。 */
class CameraModelsTest {

    @Test
    fun `全画幅不返回裁切比例`() {
        assertEquals(0f, PhotoAspect.FULL.ratio, 0.0001f)
    }

    @Test
    fun `各画幅比例正确`() {
        assertEquals(4f / 3f, PhotoAspect.RATIO_4_3.ratio, 0.0001f)
        assertEquals(16f / 9f, PhotoAspect.RATIO_16_9.ratio, 0.0001f)
        assertEquals(1f, PhotoAspect.RATIO_1_1.ratio, 0.0001f)
    }

    @Test
    fun `画幅反序列化遇到未知值回退到全画幅`() {
        assertEquals(PhotoAspect.FULL, PhotoAspect.fromKey(null))
        assertEquals(PhotoAspect.FULL, PhotoAspect.fromKey(""))
        assertEquals(PhotoAspect.FULL, PhotoAspect.fromKey("NOT_EXIST"))
        assertEquals(PhotoAspect.RATIO_1_1, PhotoAspect.fromKey("RATIO_1_1"))
    }

    @Test
    fun `每个画幅都有独立标签资源`() {
        val labels = PhotoAspect.entries.map { it.labelRes }
        assertEquals("画幅标签资源不应重复", labels.size, labels.distinct().size)
    }

    @Test
    fun `曝光状态在区间可用时报告可用`() {
        val state = ExposureState(index = 2, minIndex = -6, maxIndex = 6, stepEv = 1f / 6f)
        assertTrue(state.available)
        assertEquals(2f / 6f, state.ev, 0.0001f)
    }

    @Test
    fun `曝光状态在区间为零时报告不可用`() {
        val state = ExposureState(index = 0, minIndex = 0, maxIndex = 0)
        assertTrue(!state.available)
        assertEquals(0f, state.ev, 0.0001f)
        // 不可用时 fraction 返回 0.5，UI 滑块居中
        assertEquals(0.5f, state.fraction, 0.0001f)
    }

    @Test
    fun `曝光 fraction 在两端取值正确`() {
        val min = ExposureState(index = -6, minIndex = -6, maxIndex = 6)
        assertEquals(0f, min.fraction, 0.0001f)
        val max = ExposureState(index = 6, minIndex = -6, maxIndex = 6)
        assertEquals(1f, max.fraction, 0.0001f)
        val mid = ExposureState(index = 0, minIndex = -6, maxIndex = 6)
        assertEquals(0.5f, mid.fraction, 0.0001f)
    }

    @Test
    fun `输出规格标签包含分辨率与帧率`() {
        val spec = OutputSpec(3840, 2160, 60, false)
        assertEquals("3840x2160 60FPS", spec.label)
        val upscaled = OutputSpec(3840, 2160, 30, true)
        assertEquals("3840x2160 30FPS SR", upscaled.label)
    }

    @Test
    fun `前置摄像头不可用物理闪光灯`() {
        val back = CameraUiState(frontCamera = false)
        val front = CameraUiState(frontCamera = true)
        assertTrue(back.flashAvailable)
        assertTrue(!front.flashAvailable)
    }

    @Test
    fun `状态默认值安全`() {
        val state = CameraUiState()
        assertTrue(!state.ready)
        assertTrue(!state.recording)
        assertTrue(!state.busy)
        assertEquals(PhotoAspect.FULL, state.photoAspect)
        assertEquals(1f, state.zoomRatio, 0.0001f)
        assertTrue(state.statusMessage == null)
    }
}
