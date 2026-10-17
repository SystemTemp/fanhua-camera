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

    private var preset = FilterPreset.DREAM
    private var intensity = 1f
    private var superResolution = false
    private var stabilizationEnabled = false
    private var sharpen = false
    private var frameSeed = 0f

    override fun setPreset(preset: FilterPreset, intensity: Float) {
        this.preset = preset
        this.intensity = intensity.coerceIn(0f, 1f)
    }

    override fun setSuperResolutionEnabled(enabled: Boolean) {
        superResolution = enabled
    }

    override fun setStabilizationMatrix(matrix: FloatArray?) {
        if (matrix == null) {
            for (i in 0 until 9) {
                stabilizationMatrix[i] = GlUtil.IDENTITY_3X3[i]
            }
            stabilizationEnabled = false
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
        // 电影感滤镜在预览时也启用锐化，让效果所见即所得
        sharpen = preset == FilterPreset.CINEMATIC
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
            if (stabilizationEnabled) STABILIZATION_CROP else 1f,
            if (inputHeight > 0) inputWidth.toFloat() / inputHeight.toFloat() else 1f,
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
            ensureEasu().draw(gradeTarget.textureId, 1f / inputWidth, 1f / inputHeight)

            val rcasTarget = GlUtil.ensureFramebuffer(rcasBuffer, outputWidth, outputHeight)
            rcasBuffer = rcasTarget
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, rcasTarget.framebufferId)
            GLES20.glViewport(0, 0, outputWidth, outputHeight)
            ensureRcas().draw(easuTarget.textureId, 1f / outputWidth, 1f / outputHeight, SHARPNESS)

            baseTexture = rcasTarget.textureId
            baseWidth = outputWidth
            baseHeight = outputHeight
        } else if (sharpen) {
            // 照片保存路径（未超分时）也做轻锐化，接近系统相机锐度；电影感滤镜锐化更强
            val rcasTarget = GlUtil.ensureFramebuffer(rcasBuffer, inputWidth, inputHeight)
            rcasBuffer = rcasTarget
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, rcasTarget.framebufferId)
            GLES20.glViewport(0, 0, inputWidth, inputHeight)
            ensureRcas().draw(
                baseTexture,
                1f / inputWidth,
                1f / inputHeight,
                if (preset == FilterPreset.CINEMATIC) CINEMATIC_SHARPNESS else SAVE_SHARPNESS
            )

            baseTexture = rcasTarget.textureId
        }

        val bloomWidth = maxOf(8, baseWidth / 4)
        val bloomHeight = maxOf(8, baseHeight / 4)

        val bloomA = GlUtil.ensureFramebuffer(bloomBufferA, bloomWidth, bloomHeight)
        bloomBufferA = bloomA
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bloomA.framebufferId)
        GLES20.glViewport(0, 0, bloomWidth, bloomHeight)
        ensureBright().draw(baseTexture, grade.bloomThreshold)

        val bloomB = GlUtil.ensureFramebuffer(bloomBufferB, bloomWidth, bloomHeight)
        bloomBufferB = bloomB
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bloomB.framebufferId)
        GLES20.glViewport(0, 0, bloomWidth, bloomHeight)
        ensureBlur().draw(bloomA.textureId, 1f / bloomWidth, 0f)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bloomA.framebufferId)
        GLES20.glViewport(0, 0, bloomWidth, bloomHeight)
        ensureBlur().draw(bloomB.textureId, 0f, 1f / bloomHeight)

        val finishTarget = GlUtil.ensureFramebuffer(finishBuffer, outputWidth, outputHeight)
        finishBuffer = finishTarget
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, finishTarget.framebufferId)
        GLES20.glViewport(0, 0, outputWidth, outputHeight)
        ensureFinish().draw(
            baseTexture,
            bloomA.textureId,
            if (preset == FilterPreset.ORIGINAL) ORIGINAL_BLOOM else grade.bloomStrength,
            grade.grain,
            grade.vignette,
            frameSeed,
            preset == FilterPreset.SDR
        )

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, outputWidth, outputHeight)
        ensureCopy().draw(finishTarget.textureId)
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
    }

    private companion object {
        const val STABILIZATION_CROP = 0.85f
        const val SHARPNESS = 0.30f
        const val SAVE_SHARPNESS = 0.22f
        const val CINEMATIC_SHARPNESS = 0.32f
        const val ORIGINAL_BLOOM = 0f
    }
}
