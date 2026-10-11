package com.fanhua.camera.camera.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.EGLExt
import android.util.Log
import android.view.Surface

class EglCore(sharedContext: EGLContext? = null) {

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null

    var glVersion: Int = 2
        private set

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("eglGetDisplay failed")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            display = EGL14.EGL_NO_DISPLAY
            throw RuntimeException("eglInitialize failed")
        }
        val chosen = chooseConfig()
        if (chosen == null) {
            EGL14.eglTerminate(display)
            display = EGL14.EGL_NO_DISPLAY
            throw RuntimeException("no suitable EGLConfig")
        }
        config = chosen
        val attributes = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, glVersion,
            EGL14.EGL_NONE
        )
        context = EGL14.eglCreateContext(
            display, chosen, sharedContext ?: EGL14.EGL_NO_CONTEXT, attributes, 0
        )
        if (context == EGL14.EGL_NO_CONTEXT) {
            EGL14.eglTerminate(display)
            display = EGL14.EGL_NO_DISPLAY
            throw RuntimeException("eglCreateContext failed")
        }
    }

    private fun chooseConfig(): EGLConfig? {
        val es3 = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, 0x40,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        if (EGL14.eglChooseConfig(display, es3, 0, configs, 0, 1, count, 0) && count[0] > 0) {
            glVersion = 3
            return configs[0]
        }
        val es2 = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        if (EGL14.eglChooseConfig(display, es2, 0, configs, 0, 1, count, 0) && count[0] > 0) {
            glVersion = 2
            return configs[0]
        }
        return null
    }

    fun createWindowSurface(surface: Surface): EGLSurface {
        val attributes = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, attributes, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("eglCreateWindowSurface failed: " + EGL14.eglGetError())
        }
        return eglSurface
    }

    fun createOffscreenSurface(width: Int, height: Int): EGLSurface {
        val attributes = intArrayOf(
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE
        )
        val eglSurface = EGL14.eglCreatePbufferSurface(display, config, attributes, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("eglCreatePbufferSurface failed: " + EGL14.eglGetError())
        }
        return eglSurface
    }

    fun makeCurrent(surface: EGLSurface) {
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            throw RuntimeException("eglMakeCurrent failed: " + EGL14.eglGetError())
        }
    }

    fun makeCurrent() {
        EGL14.eglMakeCurrent(
            display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
        )
    }

    fun swapBuffers(surface: EGLSurface): Boolean {
        return EGL14.eglSwapBuffers(display, surface)
    }

    fun setPresentationTime(surface: EGLSurface, timestampNs: Long) {
        EGLExt.eglPresentationTimeANDROID(display, surface, timestampNs)
    }

    fun destroySurface(surface: EGLSurface) {
        if (surface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(display, surface)
        }
    }

    fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
            )
            if (context != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, context)
            }
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        config = null
    }

    fun isCurrent(surface: EGLSurface): Boolean {
        return EGL14.eglGetCurrentContext() == context &&
            EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW) == surface
    }

    companion object {
        fun logEglError(tag: String) {
            val error = EGL14.eglGetError()
            if (error != EGL14.EGL_SUCCESS) {
                Log.e(tag, "EGL error 0x" + Integer.toHexString(error))
            }
        }
    }
}