package com.fanhua.camera.camera.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import com.fanhua.camera.camera.filters.FilterGrade
import com.fanhua.camera.camera.filters.FilterPreset
import com.fanhua.camera.camera.gl.passes.BlurPass
import com.fanhua.camera.camera.gl.passes.BrightPass
import com.fanhua.camera.camera.gl.passes.CopyPass
import com.fanhua.camera.camera.gl.passes.EasuPass
import com.fanhua.camera.camera.gl.passes.FinishPass
import com.fanhua.camera.camera.gl.passes.GradePass
import com.fanhua.camera.camera.gl.passes.LutPass
import com.fanhua.camera.camera.gl.passes.RcasPass
import com.fanhua.camera.debug.DebugLog
import com.fanhua.camera.lut.LutData
import kotlin.math.ceil
import kotlin.math.sqrt

class GlFramePipelineImpl : GlFramePipeline {

    private var gradeOesPass: GradePass? = null
    private var grade2dPass: GradePass? = null
    private var brightPass: BrightPass? = null
    private var blurPass: BlurPass? = null
    private var finishPass: FinishPass? = null
    private var easuPass: EasuPass? = null
    private var rcasPass: RcasPass? = null
    private var copyPass: CopyPass? = null
    private var lutPass: LutPass? = null

    private var gradeBuffer: GlUtil.Framebuffer? = null
    private var easuBuffer: GlUtil.Framebuffer? = null
    private var rcasBuffer: GlUtil.Framebuffer? = null
    private var bloomBufferA: GlUtil.Framebuffer? = null
    private var bloomBufferB: GlUtil.Framebuffer? = null
    private var finishBuffer: GlUtil.Framebuffer? = null
    private var lutBuffer: GlUtil.Framebuffer? = null

    private var lutTextureId = 0
    private var lutActive = false
    private var currentLutSize = 0
    private var currentLutRef: LutData? = null

    private val identityMatrix = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )

    private val stabilizationMatrix = GlUtil.IDENTITY_3X3.copyOf()
    private var stabilizationCrop = 1f

    private var preset = FilterPreset.DREAM
    private var intensity = 1f
    private var superResolution = false
    private var stabilizationEnabled = false
    private var sharpen = false
    private var frameSeed = 0f

    /** 静态照片的重绘计数，用于给 grain 生成递增 seed（避免颗粒图案固定）。 */
    private var photoFrameCounter = 0L

    override fun setPreset(preset: FilterPreset, intensity: Float) {
        this.preset = preset
        this.intensity = intensity.coerceIn(0f, 1f)
    }

    override fun setSuperResolutionEnabled(enabled: Boolean) {
        superResolution = enabled
    }

    override fun setStabilizationMatrix(matrix: FloatArray?, crop: Float) {
        stabilizationCrop = crop.coerceIn(1f, 1.4f)
        if (matrix == null) {
            for (i in 0 until 9) {
                stabilizationMatrix[i] = GlUtil.IDENTITY_3X3[i]
            }
            stabilizationEnabled = false
            stabilizationCrop = 1f
            return
        }
        for (i in 0 until 9) {
            stabilizationMatrix[i] = if (i < matrix.size) matrix[i] else GlUtil.IDENTITY_3X3[i]
        }
        stabilizationEnabled = true
    }

    override fun setLut(lut: LutData?) {
        if (lut === currentLutRef) return
        if (lut == null) {
            if (lutTextureId != 0) {
                GlUtil.deleteTexture(lutTextureId)
                lutTextureId = 0
            }
            lutActive = false
            currentLutSize = 0
            currentLutRef = null
            return
        }
        if (lutTextureId != 0) {
            GlUtil.deleteTexture(lutTextureId)
        }
        lutTextureId = GlUtil.createLutTexture(lut)
        lutActive = true
        currentLutSize = lut.size
        currentLutRef = lut
    }

    override fun drawOesFrame(
        textureId: Int,
        textureMatrix: FloatArray,
        inputWidth: Int,
        inputHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
        timestampNs: Long
    ) {
        if (inputWidth <= 0 || inputHeight <= 0 || outputWidth <= 0 || outputHeight <= 0) return
        frameSeed = (timestampNs % 997L).toFloat() * 0.001f
        sharpen = false
        render(textureId, true, textureMatrix, inputWidth, inputHeight, outputWidth, outputHeight)
    }

    override fun drawBitmapFrame(
        textureId: Int,
        inputWidth: Int,
        inputHeight: Int,
        outputWidth: Int,
        outputHeight: Int
    ) {
        if (inputWidth <= 0 || inputHeight <= 0 || outputWidth <= 0 || outputHeight <= 0) return
        sharpen = true
        // 静态照片没有时间戳驱动，若沿用固定 seed，grain 会变成一张死板的固定噪点图案
        // （放大看能明显看到同样的斑点重复）。这里用递增帧计数生成变化的 seed，
        // 让每次重绘的颗粒分布不同，视觉上更接近真实胶片颗粒。
        photoFrameCounter++
        val seedCycle = (photoFrameCounter % 997) * 0.001f
        frameSeed = seedCycle
        render(textureId, false, identityMatrix, inputWidth, inputHeight, outputWidth, outputHeight)
    }

    private fun render(
        textureId: Int,
        oes: Boolean,
        textureMatrix: FloatArray,
        inputWidth: Int,
        inputHeight: Int,
        outputWidth: Int,
        outputHeight: Int
    ) {
        val grade = FilterGrade.of(preset)

        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        val gradeTarget = GlUtil.ensureFramebuffer(gradeBuffer, inputWidth, inputHeight)
        gradeBuffer = gradeTarget
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, gradeTarget.framebufferId)
        GLES20.glViewport(0, 0, inputWidth, inputHeight)
        gradePass(oes).draw(
            textureId,
            textureMatrix,
            if (stabilizationEnabled) stabilizationMatrix else GlUtil.IDENTITY_3X3,
            if (stabilizationEnabled) stabilizationCrop else 1f,
            if (outputHeight > 0) outputWidth.toFloat() / outputHeight.toFloat() else 1f,
            1f,
            grade,
            intensity
        )

        var baseTexture = gradeTarget.textureId
        var baseWidth = inputWidth
        var baseHeight = inputHeight

        if (lutActive) {
            val lutTarget = GlUtil.ensureFramebuffer(lutBuffer, inputWidth, inputHeight)
            lutBuffer = lutTarget
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, lutTarget.framebufferId)
            GLES20.glViewport(0, 0, inputWidth, inputHeight)
            val lutSize = currentLutSize
            val grid = ceil(sqrt(lutSize.toDouble())).toInt()
            ensureLut().draw(
                gradeTarget.textureId,
                lutTextureId,
                lutSize.toFloat(),
                grid.toFloat(),
                1f / (grid * lutSize),
                intensity
            )
            baseTexture = lutTarget.textureId
        }

        if (superResolution && (outputWidth > inputWidth || outputHeight > inputHeight)) {
            val easuTarget = GlUtil.ensureFramebuffer(easuBuffer, outputWidth, outputHeight)
            easuBuffer = easuTarget
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, easuTarget.framebufferId)
            GLES20.glViewport(0, 0, outputWidth, outputHeight)
            // 用 baseTexture（LUT 之后的结果）做超分：早先用的是 gradeTarget.textureId，
            // 会丢掉自定义 LUT 的调色结果。texel 也按 baseWidth/baseHeight 取，保证与输入尺寸一致。
            ensureEasu().draw(baseTexture, 1f / baseWidth, 1f / baseHeight)

            val rcasTarget = GlUtil.ensureFramebuffer(rcasBuffer, outputWidth, outputHeight)
            rcasBuffer = rcasTarget
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, rcasTarget.framebufferId)
            GLES20.glViewport(0, 0, outputWidth, outputHeight)
            ensureRcas().draw(easuTarget.textureId, 1f / outputWidth, 1f / outputHeight, SHARPNESS)

            baseTexture = rcasTarget.textureId
            baseWidth = outputWidth
            baseHeight = outputHeight
        } else if (sharpen) {
            val rcasTarget = GlUtil.ensureFramebuffer(rcasBuffer, inputWidth, inputHeight)
            rcasBuffer = rcasTarget
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, rcasTarget.framebufferId)
            GLES20.glViewport(0, 0, inputWidth, inputHeight)
            ensureRcas().draw(
                baseTexture,
                1f / inputWidth,
                1f / inputHeight,
                SAVE_SHARPNESS
            )

            baseTexture = rcasTarget.textureId
        }

        // 有效 bloom 强度：ORIGINAL 走固定 0（保持原图纯净），其余用调色参数里的值。
        val effectiveBloom = if (preset == FilterPreset.ORIGINAL) ORIGINAL_BLOOM else grade.bloomStrength
        val bloomActive = needsBloom(preset, effectiveBloom)
        // 跳过 bloom 时绑定 1x1 全黑纹理占位，保证 FinishPass 的 uBloom 采样永远有合法纹理，
        // 不会采到未初始化/已删除的纹理（那样在部分 GPU 上会出黑块或花屏）。
        val bloomTexture = if (bloomActive) renderBloom(baseTexture, baseWidth, baseHeight, grade) else GlUtil.blackTexture()

        val finishTarget = GlUtil.ensureFramebuffer(finishBuffer, outputWidth, outputHeight)
        finishBuffer = finishTarget
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, finishTarget.framebufferId)
        GLES20.glViewport(0, 0, outputWidth, outputHeight)
        ensureFinish().draw(
            baseTexture,
            bloomTexture,
            if (bloomActive) effectiveBloom else 0f,
            grade.grain,
            grade.vignette,
            frameSeed,
            preset == FilterPreset.SDR
        )

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, outputWidth, outputHeight)
        ensureCopy().draw(finishTarget.textureId)

        if (DebugLog.enabled) {
            GlUtil.checkGlError("render ${outputWidth}x${outputHeight}")
        }
    }

    /**
     * 判断是否需要跑 bloom 链路。
     *
     * 跳过条件：
     * 1. ORIGINAL：原图必须纯净，管线里已把强度固定为 0；
     * 2. APPLE_LOG / SDR：Log 与标准视频档是「留给后期」的平坦影像，叠加辉光在语义上就是错的，
     *    而且这两个档的 bloomStrength 只有 0.02，肉眼几乎不可见，跳过可省掉 3~5 个 draw call；
     * 3. 有效强度低于阈值：参数本身就是 0，跑一遍纯属浪费。
     */
    private fun needsBloom(preset: FilterPreset, effectiveStrength: Float): Boolean {
        if (preset == FilterPreset.ORIGINAL) return false
        if (preset == FilterPreset.APPLE_LOG || preset == FilterPreset.SDR) return false
        return effectiveStrength > BLOOM_EPSILON
    }

    /**
     * 执行 bloom 链路（亮度提取 + 两轮可分离高斯模糊），返回最终 bloom 纹理。
     *
     * 两轮模糊的意义：单轮 5 tap 高斯在 1/4 分辨率下扩散半径偏小，大分辨率画面里辉光几乎贴着
     * 高光边缘，看着像「描边」而不是「光晕」。两轮串联等价于高斯核自卷积（sigma 约放大 1.41 倍），
     * 衰减曲线更平滑；因为 bloom 是加法叠加（base + bloom * strength），扩散变宽不会糊掉画面细节。
     * 每轮权重和为 1（0.227027 + 2*0.316216 + 2*0.070270），总能量守恒，所以亮度不会失控。
     */
    private fun renderBloom(
        sourceTexture: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        grade: FilterGrade
    ): Int {
        val bloomWidth = maxOf(8, sourceWidth / 4)
        val bloomHeight = maxOf(8, sourceHeight / 4)

        val bloomA = GlUtil.ensureFramebuffer(bloomBufferA, bloomWidth, bloomHeight)
        bloomBufferA = bloomA
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bloomA.framebufferId)
        GLES20.glViewport(0, 0, bloomWidth, bloomHeight)
        ensureBright().draw(sourceTexture, grade.bloomThreshold)

        val bloomB = GlUtil.ensureFramebuffer(bloomBufferB, bloomWidth, bloomHeight)
        bloomBufferB = bloomB

        // 每轮：先水平（A -> B），再垂直（B -> A），结果始终落回 bloomA
        repeat(BLUR_ITERATIONS) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bloomB.framebufferId)
            GLES20.glViewport(0, 0, bloomWidth, bloomHeight)
            ensureBlur().draw(bloomA.textureId, 1f / bloomWidth, 0f)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bloomA.framebufferId)
            GLES20.glViewport(0, 0, bloomWidth, bloomHeight)
            ensureBlur().draw(bloomB.textureId, 0f, 1f / bloomHeight)
        }
        return bloomA.textureId
    }

    private fun gradePass(oes: Boolean): GradePass {
        return if (oes) {
            gradeOesPass ?: GradePass(true).also { gradeOesPass = it }
        } else {
            grade2dPass ?: GradePass(false).also { grade2dPass = it }
        }
    }

    private fun ensureBright(): BrightPass = brightPass ?: BrightPass().also { brightPass = it }

    private fun ensureBlur(): BlurPass = blurPass ?: BlurPass().also { blurPass = it }

    private fun ensureFinish(): FinishPass = finishPass ?: FinishPass().also { finishPass = it }

    private fun ensureEasu(): EasuPass = easuPass ?: EasuPass().also { easuPass = it }

    private fun ensureRcas(): RcasPass = rcasPass ?: RcasPass().also { rcasPass = it }

    private fun ensureCopy(): CopyPass = copyPass ?: CopyPass().also { copyPass = it }

    private fun ensureLut(): LutPass = lutPass ?: LutPass().also { lutPass = it }

    override fun createTextureFromBitmap(bitmap: Bitmap): Int = GlUtil.createTextureFromBitmap(bitmap)

    override fun deleteTexture(textureId: Int) = GlUtil.deleteTexture(textureId)

    override fun release() {
        GlUtil.deleteFramebuffer(gradeBuffer)
        GlUtil.deleteFramebuffer(easuBuffer)
        GlUtil.deleteFramebuffer(rcasBuffer)
        GlUtil.deleteFramebuffer(bloomBufferA)
        GlUtil.deleteFramebuffer(bloomBufferB)
        GlUtil.deleteFramebuffer(finishBuffer)
        GlUtil.deleteFramebuffer(lutBuffer)
        gradeBuffer = null
        easuBuffer = null
        rcasBuffer = null
        bloomBufferA = null
        bloomBufferB = null
        finishBuffer = null
        lutBuffer = null

        if (lutTextureId != 0) {
            GlUtil.deleteTexture(lutTextureId)
            lutTextureId = 0
        }
        lutActive = false

        gradeOesPass?.release()
        grade2dPass?.release()
        brightPass?.release()
        blurPass?.release()
        finishPass?.release()
        easuPass?.release()
        rcasPass?.release()
        copyPass?.release()
        lutPass?.release()
        gradeOesPass = null
        grade2dPass = null
        brightPass = null
        blurPass = null
        finishPass = null
        easuPass = null
        rcasPass = null
        copyPass = null
        lutPass = null

        // 释放当前 EGL 上下文下的 1x1 黑纹理占位，避免上下文销毁后纹理名泄漏
        GlUtil.releaseBlackTexture()
    }

    private companion object {
        const val SHARPNESS = 0.30f
        const val SAVE_SHARPNESS = 0.22f
        const val ORIGINAL_BLOOM = 0f

        /** bloom 有效强度低于该阈值时视为「无辉光」，直接跳过整条 bloom 链路。 */
        const val BLOOM_EPSILON = 0.001f

        /** 可分离高斯模糊的迭代轮数：2 轮串联让扩散更柔和。 */
        const val BLUR_ITERATIONS = 2
    }
}
