package com.fanhua.camera.camera.capture

import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
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
    val lut: com.fanhua.camera.lut.LutData? = null,
    val stabilizationCrop: Float = 1f
)

class FilterSurfaceProcessor(
    private val executor: Executor,
    private val stateProvider: () -> RenderState,
    private val errorListener: (Throwable) -> Unit,
    private val outputErrorListener: ((Throwable) -> Unit)? = null
) : SurfaceProcessor {

    private class OutputHandle(
        val output: SurfaceOutput,
        val surface: Surface,
        val eglSurface: EGLSurface,
        @Volatile var closed: Boolean = false
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

    /**
     * 当前输入 SurfaceTexture 是否已经成功产出一帧。
     *
     * 竞态背景：输入 Surface 被重建时（切画幅、切模式、CameraX 重新协商），
     * [inputWidth]/[inputHeight] 会立刻换成新值，但旧 SurfaceTexture 的
     * onFrameAvailable 回调仍可能在新纹理还没出第一帧时触发一次渲染。
     * 那一帧会「新尺寸 + 旧纹理」——表现为画面突然旋转 90°、比例扭曲。
     * 所以在拿到当前纹理的第一帧之前，一律不渲染。
     */
    private var inputFrameReady = false

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
                // 新纹理还没出帧，先挡住渲染，避免旧回调配上新尺寸
                inputFrameReady = false
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
                try {
                    request.willNotProvideSurface()
                } catch (ignore: Exception) {
                }
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
                val listener = outputErrorListener
                if (listener != null) listener.invoke(e) else errorListener(e)
                try {
                    output.close()
                } catch (ignore: Exception) {
                }
            }
        }
    }

    private fun scheduleRender() {
        if (rendering || released) return
        rendering = true
        // 记下触发这次渲染的纹理：回调可能来自已经被替换掉的旧 SurfaceTexture，
        // 那种帧必须丢弃，否则会出现「旧帧 + 新尺寸」的旋转/扭曲。
        val source = inputTexture
        executor.execute {
            rendering = false
            if (source == null || source !== inputTexture) return@execute
            renderFrame()
        }
    }

    private fun renderFrame() {
        val surfaceTexture = inputTexture ?: return
        val core = eglCore ?: return
        val framePipeline = pipeline ?: return
        try {
            // 新纹理还没出帧时 updateTexImage 会拿到无效帧，直接跳过这次渲染。
            surfaceTexture.updateTexImage()
            inputFrameReady = true
            surfaceTexture.getTransformMatrix(textureMatrix)
            val timestamp = surfaceTexture.timestamp
            val renderState = stateProvider()
            framePipeline.setPreset(renderState.preset, renderState.intensity)
            framePipeline.setSuperResolutionEnabled(renderState.superResolution)
            framePipeline.setLut(renderState.lut)
            framePipeline.setStabilizationMatrix(renderState.stabilization, renderState.stabilizationCrop)
            val snapshot = outputs.toList()
            for (handle in snapshot) {
                if (handle.closed) continue
                try {
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
                } catch (e: Exception) {
                    if (!handle.closed) {
                        handle.closed = true
                        outputs.remove(handle)
                        try {
                            eglCore?.makeCurrent()
                        } catch (ignore: Exception) {
                        }
                        try {
                            eglCore?.destroySurface(handle.eglSurface)
                        } catch (ignore: Exception) {
                        }
                    }
                }
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
        if (handle.closed) return
        handle.closed = true
        outputs.remove(handle)
        try {
            eglCore?.makeCurrent()
        } catch (e: Exception) {
        }
        try {
            eglCore?.destroySurface(handle.eglSurface)
        } catch (e: Exception) {
        }
        try {
            handle.output.close()
        } catch (e: Exception) {
        }
        try {
            handle.surface.release()
        } catch (e: Exception) {
        }
    }

    fun release() {
        executor.execute {
            released = true
            releaseInput()
            for (handle in outputs.toList()) {
                handle.closed = true
                try {
                    eglCore?.makeCurrent()
                } catch (e: Exception) {
                }
                try {
                    eglCore?.destroySurface(handle.eglSurface)
                } catch (e: Exception) {
                }
                try {
                    handle.output.close()
                } catch (e: Exception) {
                }
                try {
                    handle.surface.release()
                } catch (e: Exception) {
                }
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
