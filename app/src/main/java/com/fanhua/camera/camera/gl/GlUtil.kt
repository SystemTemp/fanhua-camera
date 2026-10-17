package com.fanhua.camera.camera.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import com.fanhua.camera.lut.LutData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.sqrt

class GlProgram(
    val id: Int,
    val position: Int,
    val texCoord: Int,
    val uniforms: IntArray
)

object GlUtil {

    val VERT_PLAIN = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    val VERT_TEX = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        uniform mat4 uTexMatrix;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
        }
    """.trimIndent()

    val VERT_GRADE = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        uniform mat4 uTexMatrix;
        uniform mat3 uStabMatrix;
        uniform float uCrop;
        uniform float uAspect;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vec2 c = (aTexCoord - 0.5) * uCrop;
            float ang = atan(uStabMatrix[0][1], uStabMatrix[0][0]);
            float ca = cos(ang);
            float sa = sin(ang);
            vec2 p = vec2(c.x * uAspect, c.y);
            vec2 r = mat2(ca, sa, -sa, ca) * p;
            vec2 q = vec2(r.x / uAspect, r.y) + vec2(uStabMatrix[2][0], uStabMatrix[2][1]);
            vTexCoord = (uTexMatrix * vec4(q + 0.5, 0.0, 1.0)).xy;
        }
    """.trimIndent()

    const val FRAG_HEAD_2D = "precision mediump float;\n"
    const val FRAG_HEAD_2D_HIGH = "precision highp float;\n"
    const val FRAG_HEAD_OES = "#extension GL_OES_EGL_image_external : require\nprecision mediump float;\n"
    const val FRAG_HEAD_OES_HIGH = "#extension GL_OES_EGL_image_external : require\nprecision highp float;\n"
    const val SAMPLER_2D = "uniform sampler2D uTexture;\n"
    const val SAMPLER_OES = "uniform samplerExternalOES uTexture;\n"

    val IDENTITY_4X4 = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )

    val IDENTITY_3X3 = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f
    )

    val FLIP_Y_4X4 = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, -1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 1f, 0f, 1f
    )

    private val QUAD_DATA = floatArrayOf(
        -1f, -1f, 0f, 0f,
        1f, -1f, 1f, 0f,
        -1f, 1f, 0f, 1f,
        1f, 1f, 1f, 1f
    )

    private const val QUAD_STRIDE = 16

    private val quad: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD_DATA.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD_DATA)
            position(0)
        }

    fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            throw RuntimeException("glCreateShader failed for type " + type)
        }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("shader compile failed: " + log)
        }
        return shader
    }

    fun linkProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        if (program == 0) {
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            throw RuntimeException("glCreateProgram failed")
        }
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("program link failed: " + log)
        }
        return program
    }

    fun buildProgram(vertexSource: String, fragmentSource: String, uniformNames: Array<String>): GlProgram {
        val id = linkProgram(vertexSource, fragmentSource)
        val position = GLES20.glGetAttribLocation(id, "aPosition")
        val texCoord = GLES20.glGetAttribLocation(id, "aTexCoord")
        val uniforms = IntArray(uniformNames.size)
        for (i in uniformNames.indices) {
            uniforms[i] = GLES20.glGetUniformLocation(id, uniformNames[i])
        }
        return GlProgram(id, position, texCoord, uniforms)
    }

    fun drawQuad(positionLocation: Int, texCoordLocation: Int) {
        quad.position(0)
        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, QUAD_STRIDE, quad)
        quad.position(2)
        GLES20.glEnableVertexAttribArray(texCoordLocation)
        GLES20.glVertexAttribPointer(texCoordLocation, 2, GLES20.GL_FLOAT, false, QUAD_STRIDE, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionLocation)
        GLES20.glDisableVertexAttribArray(texCoordLocation)
    }

    fun createTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return textures[0]
    }

    fun createTextureFromBitmap(bitmap: Bitmap): Int {
        val texture = createTexture()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return texture
    }

    /**
     * 把 3D LUT 编码为 2D 网格纹理（GLES2 兼容）。
     * 网格布局：grid = ceil(sqrt(size))，纹理尺寸 = grid*size 见方。
     * 每个网格 = 一个 B 层（NxN），网格内 X=R、Y=G。
     */
    fun createLutTexture(lut: LutData): Int {
        val grid = ceil(sqrt(lut.size.toFloat())).toInt()
        val side = grid * lut.size
        val bytes = ByteBuffer.allocateDirect(side * side * 4).order(ByteOrder.nativeOrder())
        val n1 = (lut.size - 1).coerceAtLeast(1)
        for (gy in 0 until grid) {
            for (gx in 0 until grid) {
                val b = gy * grid + gx
                if (b >= lut.size) continue
                for (y in 0 until lut.size) {
                    val g = y.toDouble() / n1
                    for (x in 0 until lut.size) {
                        val r = x.toDouble() / n1
                        val idx = ((b * lut.size + y) * lut.size + x) * 3
                        val rv = (lut.data[idx].toDouble().coerceIn(0.0, 1.0) * 255.0).toInt()
                        val gv = (lut.data[idx + 1].toDouble().coerceIn(0.0, 1.0) * 255.0).toInt()
                        val bv = (lut.data[idx + 2].toDouble().coerceIn(0.0, 1.0) * 255.0).toInt()
                        bytes.put(rv.toByte())
                        bytes.put(gv.toByte())
                        bytes.put(bv.toByte())
                        bytes.put((-1).toByte()) // A=255
                    }
                }
            }
        }
        bytes.rewind()

        val texture = createTexture()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            side, side, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bytes
        )
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return texture
    }

    fun deleteTexture(textureId: Int) {
        if (textureId == 0) return
        val textures = intArrayOf(textureId)
        GLES20.glDeleteTextures(1, textures, 0)
    }

    class Framebuffer(val textureId: Int, val framebufferId: Int, var width: Int, var height: Int)

    fun createFramebuffer(): Framebuffer {
        val texture = createTexture()
        val framebuffers = IntArray(1)
        GLES20.glGenFramebuffers(1, framebuffers, 0)
        return Framebuffer(texture, framebuffers[0], 0, 0)
    }

    fun ensureFramebuffer(existing: Framebuffer?, width: Int, height: Int): Framebuffer {
        val target = existing ?: createFramebuffer()
        resizeFramebuffer(target, width, height)
        return target
    }

    fun resizeFramebuffer(framebuffer: Framebuffer, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (framebuffer.width == width && framebuffer.height == height) return
        val previous = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, previous, 0)
        framebuffer.width = width
        framebuffer.height = height
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, framebuffer.textureId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer.framebufferId)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, framebuffer.textureId, 0
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, previous[0])
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    fun deleteFramebuffer(framebuffer: Framebuffer?) {
        if (framebuffer == null) return
        deleteTexture(framebuffer.textureId)
        val framebuffers = intArrayOf(framebuffer.framebufferId)
        GLES20.glDeleteFramebuffers(1, framebuffers, 0)
    }

    fun toMat4(source: FloatArray, out: FloatArray) {
        if (source.size >= 16) {
            for (i in 0 until 16) out[i] = source[i]
            return
        }
        out[0] = source[0]
        out[1] = source[1]
        out[2] = source[2]
        out[3] = 0f
        out[4] = source[3]
        out[5] = source[4]
        out[6] = source[5]
        out[7] = 0f
        out[8] = source[6]
        out[9] = source[7]
        out[10] = source[8]
        out[11] = 0f
        out[12] = 0f
        out[13] = 0f
        out[14] = 0f
        out[15] = 1f
    }

    fun invert3x3(source: FloatArray, out: FloatArray) {
        val r00 = source[0]
        val r10 = source[1]
        val r20 = source[2]
        val r01 = source[3]
        val r11 = source[4]
        val r21 = source[5]
        val r02 = source[6]
        val r12 = source[7]
        val r22 = source[8]
        val det = r00 * (r11 * r22 - r12 * r21) -
            r01 * (r10 * r22 - r12 * r20) +
            r02 * (r10 * r21 - r11 * r20)
        if (det > -1e-8f && det < 1e-8f) {
            for (i in 0 until 9) out[i] = IDENTITY_3X3[i]
            return
        }
        val inv = 1f / det
        out[0] = (r11 * r22 - r12 * r21) * inv
        out[1] = (r12 * r20 - r10 * r22) * inv
        out[2] = (r10 * r21 - r11 * r20) * inv
        out[3] = (r02 * r21 - r01 * r22) * inv
        out[4] = (r00 * r22 - r02 * r20) * inv
        out[5] = (r01 * r20 - r00 * r21) * inv
        out[6] = (r01 * r12 - r02 * r11) * inv
        out[7] = (r02 * r10 - r00 * r12) * inv
        out[8] = (r00 * r11 - r01 * r10) * inv
    }
}