package com.fanhua.camera.camera.capture

import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import androidx.camera.core.ProcessingException
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import com.fanhua.camera.camera.filters.FilterPreset
import com.fanhua.camera.camera.gl.EglCore
import com.fanhua.camera.camera.gl.GlFramePipeline
import com.fanhua.camera.camera.gl.createGlFramePipeline
import java.util.concurrent.Executor

data class RenderState(
    val preset: FilterPreset,
    val intensity: Float,
    val superResolution: Boolean,
    val stabilization: FloatArray? = null,
    val lut: com.fanhua.camera.lut.LutData? = null
)

class FilterSurfaceProcessor(
    private val executor: Executor,
    private val stateProvider: () -> RenderState,
    private val errorListener: (Throwable) -> Unit
) : SurfaceProcessor {

    private class OutputHandle(
        val output: SurfaceOutput,
        val surface: Surface,
        val eglSurface: EGLSurface
    )

    private var eglCore: EglCore? = null
    private var setupSurface: EGLSurface? = null
    private var pipeline: GlFramePipeline? = null

    private var inputTextureId = 0
    private var inputTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null
    private var inputWidth = 0
    private var inputHeight = 0

    private val outputs = mutableListOf<OutputHandle>()
    private val textureMatrix = FloatArray(16)
    private val outputTransform = FloatArray(16)
    private var rendering = false
    private var released = false

    override fun onInputSurface(request: SurfaceRequest) {
        executor.execute {
            if (released) {
                request.willNotProvideSurface()
                return@execute
            }
            try {
                ensureGl()
                releaseInput()
                val resolution = request.resolution
                inputWidth = resolution.width
                inputHeight = resolution.height
                val textureId = createExternalTexture()
                inputTextureId = textureId
                val surfaceTexture = SurfaceTexture(textureId)
                surfaceTexture.setDefaultBufferSize(inputWidth, inputHeight)
                surfaceTexture.setOnFrameAvailableListener { scheduleRender() }
                inputTexture = surfaceTexture
                val surface = Surface(surfaceTexture)
                inputSurface = surface
                request.provideSurface(surface, executor) {
                    executor.execute {
                        if (inputTexture === surfaceTexture) {
                            releaseInput()
                        }
                    }
                }
            } catch (e: Exception) {
                errorListener(e)
                throw ProcessingException()
            }
        }
    }

    override fun onOutputSurface(output: SurfaceOutput) {
        executor.execute {
            if (released) {
                output.close()
                return@execute
            }
            if (outputs.any { it.output === output }) return@execute
            try {
                ensureGl()
                val core = eglCore ?: return@execute
                val surface = output.getSurface(executor) { event ->
                    if (event.eventCode == SurfaceOutput.Event.EVENT_REQUEST_CLOSE) {
                        executor.execute { closeOutput(output) }
                    }
                }
                val eglSurface = core.createWindowSurface(surface)
                outputs += OutputHandle(output, surface, eglSurface)
            } catch (e: Exception) {
                errorListener(e)
                throw ProcessingException()
            }
        }
    }

    private fun scheduleRender() {
        if (rendering || released) return
        rendering = true
        executor.execute {
            rendering = false
            renderFrame()
        }
    }

    private fun renderFrame() {
        val surfaceTexture = inputTexture ?: return
        val core = eglCore ?: return
        val framePipeline = pipeline ?: return
        try {
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(textureMatrix)
            val timestamp = surfaceTexture.timestamp
            val renderState = stateProvider()
            framePipeline.setPreset(renderState.preset, renderState.intensity)
            framePipeline.setSuperResolutionEnabled(renderState.superResolution)
            framePipeline.setLut(renderState.lut)
            framePipeline.setStabilizationMatrix(renderState.stabilization)
            val snapshot = outputs.toList()
            for (handle in snapshot) {
                handle.output.updateTransformMatrix(outputTransform, textureMatrix)
                core.makeCurrent(handle.eglSurface)
                framePipeline.drawOesFrame(
                    inputTextureId,
                    outputTransform,
                    inputWidth,
                    inputHeight,
                    handle.output.size.width,
                    handle.output.size.height,
                    timestamp
                )
                core.setPresentationTime(handle.eglSurface, timestamp)
                core.swapBuffers(handle.eglSurface)
            }
        } catch (e: Exception) {
            errorListener(e)
        }
    }

    private fun ensureGl() {
        var core = eglCore
        if (core == null) {
            core = EglCore()
            eglCore = core
            val surface = core.createOffscreenSurface(4, 4)
            setupSurface = surface
            core.makeCurrent(surface)
        }
        if (pipeline == null) {
            pipeline = createGlFramePipeline()
        }
    }

    private fun createExternalTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        return ids[0]
    }

    private fun releaseInput() {
        inputTexture?.setOnFrameAvailableListener(null)
        inputTexture?.release()
        inputSurface?.release()
        inputTexture = null
        inputSurface = null
        if (inputTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(inputTextureId), 0)
            inputTextureId = 0
        }
    }

    private fun closeOutput(output: SurfaceOutput) {
        val handle = outputs.firstOrNull { it.output === output } ?: return
        outputs.remove(handle)
        eglCore?.destroySurface(handle.eglSurface)
        handle.output.close()
        handle.surface.release()
    }

    fun release() {
        executor.execute {
            released = true
            releaseInput()
            for (handle in outputs.toList()) {
                eglCore?.destroySurface(handle.eglSurface)
                handle.output.close()
                handle.surface.release()
            }
            outputs.clear()
            pipeline?.release()
            pipeline = null
            setupSurface?.let { eglCore?.destroySurface(it) }
            setupSurface = null
            eglCore?.release()
            eglCore = null
        }
    }
}
