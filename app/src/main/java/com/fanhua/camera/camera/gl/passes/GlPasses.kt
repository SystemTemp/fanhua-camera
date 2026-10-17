package com.fanhua.camera.camera.gl.passes

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.fanhua.camera.camera.filters.FilterGrade
import com.fanhua.camera.camera.gl.GlProgram
import com.fanhua.camera.camera.gl.GlUtil

internal abstract class GlPass(
    vertexSource: String,
    fragmentSource: String,
    uniformNames: Array<String>
) {
    protected val program: GlProgram = GlUtil.buildProgram(vertexSource, fragmentSource, uniformNames)

    protected fun bindTexture(unit: Int, target: Int, textureId: Int, uniformIndex: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
        GLES20.glBindTexture(target, textureId)
        GLES20.glUniform1i(program.uniforms[uniformIndex], unit)
    }

    protected fun drawQuad() {
        GlUtil.drawQuad(program.position, program.texCoord)
    }

    open fun release() {
        GLES20.glDeleteProgram(program.id)
    }
}

internal class GradePass(private val oes: Boolean) : GlPass(
    GlUtil.VERT_GRADE,
    buildFragment(oes),
    UNIFORMS
) {
    fun draw(
        textureId: Int,
        textureMatrix: FloatArray,
        stabilization: FloatArray,
        crop: Float,
        aspect: Float,
        stabScale: Float,
        grade: FilterGrade,
        intensity: Float
    ) {
        GLES20.glUseProgram(program.id)
        GLES20.glUniformMatrix4fv(program.uniforms[0], 1, false, textureMatrix, 0)
        GLES20.glUniformMatrix3fv(program.uniforms[1], 1, false, stabilization, 0)
        GLES20.glUniform1f(program.uniforms[2], crop)
        GLES20.glUniform1f(program.uniforms[18], aspect)
        GLES20.glUniform1f(program.uniforms[19], stabScale)
        val target = if (oes) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D
        bindTexture(0, target, textureId, 3)
        GLES20.glUniform1f(program.uniforms[4], intensity)
        GLES20.glUniform1f(program.uniforms[5], grade.contrast)
        GLES20.glUniform1f(program.uniforms[6], grade.saturation)
        GLES20.glUniform1f(program.uniforms[7], grade.temperature)
        GLES20.glUniform1f(program.uniforms[8], grade.fade)
        GLES20.glUniform3fv(program.uniforms[9], 1, grade.lift, 0)
        GLES20.glUniform3fv(program.uniforms[10], 1, grade.gain, 0)
        GLES20.glUniform3fv(program.uniforms[11], 1, grade.shadowTint, 0)
        GLES20.glUniform3fv(program.uniforms[12], 1, grade.highlightTint, 0)
        GLES20.glUniform1f(program.uniforms[13], grade.highlights)
        GLES20.glUniform1f(program.uniforms[14], grade.shadows)
        GLES20.glUniform1f(program.uniforms[15], grade.greenHue)
        GLES20.glUniform1f(program.uniforms[16], grade.greenSat)
        GLES20.glUniform1f(program.uniforms[17], grade.greenLum)
        drawQuad()
    }

    private companion object {
        val UNIFORMS = arrayOf(
            "uTexMatrix",
            "uStabMatrix",
            "uCrop",
            "uTexture",
            "uIntensity",
            "uContrast",
            "uSaturation",
            "uTemperature",
            "uFade",
            "uLift",
            "uGain",
            "uShadowTint",
            "uHighlightTint",
            "uHighlights",
            "uShadows",
            "uGreenH",
            "uGreenS",
            "uGreenL",
            "uAspect",
            "uStabScale"
        )

        val BODY = """
            uniform float uIntensity;
            uniform float uContrast;
            uniform float uSaturation;
            uniform float uTemperature;
            uniform float uFade;
            uniform vec3 uLift;
            uniform vec3 uGain;
            uniform vec3 uShadowTint;
            uniform vec3 uHighlightTint;
            uniform float uHighlights;
            uniform float uShadows;
            uniform float uGreenH;
            uniform float uGreenS;
            uniform float uGreenL;
            varying vec2 vTexCoord;
            const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
            vec3 rgb2hsl(vec3 c) {
                float mx = max(max(c.r, c.g), c.b);
                float mn = min(min(c.r, c.g), c.b);
                float h = 0.0;
                float s = 0.0;
                float l = (mx + mn) * 0.5;
                float d = mx - mn;
                if (d > 0.001) {
                    s = d / max(1.0 - abs(2.0 * l - 1.0), 0.0001);
                    if (mx == c.r) {
                        h = fract(((c.g - c.b) / d + (c.g < c.b ? 6.0 : 0.0)) / 6.0);
                    } else if (mx == c.g) {
                        h = fract(((c.b - c.r) / d + 2.0) / 6.0);
                    } else {
                        h = fract(((c.r - c.g) / d + 4.0) / 6.0);
                    }
                }
                return vec3(h, s, l);
            }
            vec3 hsl2rgb(vec3 c) {
                vec3 rgb = clamp(abs(mod(c.x * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
                return c.z + c.y * (rgb - 0.5) * (1.0 - abs(2.0 * c.z - 1.0));
            }
            void main() {
                vec3 src = texture2D(uTexture, vTexCoord).rgb;
                vec3 c = (src - 0.5) * uContrast + 0.5;
                c += uTemperature * vec3(0.050, 0.012, -0.045);
                float l = dot(c, LUMA);
                c += uLift * (1.0 - l);
                c *= uGain;
                c += uShadowTint * (1.0 - smoothstep(0.0, 0.46, l));
                c += uHighlightTint * smoothstep(0.54, 1.0, l);
                // 高光/阴影独立压暗（电影感）
                if (uHighlights != 0.0) {
                    c *= 1.0 + uHighlights * smoothstep(0.55, 1.0, l) * 0.40;
                }
                if (uShadows != 0.0) {
                    c *= 1.0 + uShadows * (1.0 - smoothstep(0.0, 0.45, l)) * 0.50;
                }
                // HSL 绿色选择性调整
                if (abs(uGreenH) > 0.001 || abs(uGreenS) > 0.001 || abs(uGreenL) > 0.001) {
                    vec3 hsl = rgb2hsl(c);
                    float gd = abs(hsl.x - 0.5);
                    float gm = 1.0 - smoothstep(0.10, 0.30, gd);
                    if (gm > 0.001) {
                        hsl.x = mod(hsl.x + uGreenH / 360.0, 1.0);
                        hsl.y *= 1.0 + uGreenS * gm;
                        hsl.z *= 1.0 + uGreenL * gm;
                        c = hsl2rgb(hsl);
                    }
                }
                float l2 = dot(c, LUMA);
                c = mix(vec3(l2), c, uSaturation);
                c = mix(c, vec3(0.5) + (c - 0.5) * 0.82 + 0.055, uFade);
                c = clamp(c, 0.0, 1.0);
                gl_FragColor = vec4(mix(src, c, uIntensity), 1.0);
            }
        """.trimIndent()

        fun buildFragment(oes: Boolean): String {
            val head = if (oes) GlUtil.FRAG_HEAD_OES else GlUtil.FRAG_HEAD_2D
            val sampler = if (oes) GlUtil.SAMPLER_OES else GlUtil.SAMPLER_2D
            return head + sampler + BODY
        }
    }
}

internal class BrightPass : GlPass(GlUtil.VERT_PLAIN, FRAGMENT, arrayOf("uTexture", "uThreshold")) {
    fun draw(textureId: Int, threshold: Float) {
        GLES20.glUseProgram(program.id)
        bindTexture(0, GLES20.GL_TEXTURE_2D, textureId, 0)
        GLES20.glUniform1f(program.uniforms[1], threshold)
        drawQuad()
    }

    private companion object {
        val FRAGMENT = GlUtil.FRAG_HEAD_2D + GlUtil.SAMPLER_2D + """
            uniform float uThreshold;
            varying vec2 vTexCoord;
            void main() {
                vec3 c = texture2D(uTexture, vTexCoord).rgb;
                float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
                // 柔和高光提取：权重上限 0.72，避免太阳等极亮区域被放大成光球
                float w = smoothstep(uThreshold, min(uThreshold + 0.38, 1.0), l);
                w = min(w, 0.72);
                gl_FragColor = vec4(c * w, 1.0);
            }
        """.trimIndent()
    }
}

internal class BlurPass : GlPass(GlUtil.VERT_PLAIN, FRAGMENT, arrayOf("uTexture", "uDirection")) {
    fun draw(textureId: Int, directionX: Float, directionY: Float) {
        GLES20.glUseProgram(program.id)
        bindTexture(0, GLES20.GL_TEXTURE_2D, textureId, 0)
        GLES20.glUniform2f(program.uniforms[1], directionX, directionY)
        drawQuad()
    }

    private companion object {
        val FRAGMENT = GlUtil.FRAG_HEAD_2D + GlUtil.SAMPLER_2D + """
            uniform vec2 uDirection;
            varying vec2 vTexCoord;
            void main() {
                vec3 sum = texture2D(uTexture, vTexCoord).rgb * 0.227027;
                sum += texture2D(uTexture, vTexCoord + uDirection * 1.3846).rgb * 0.316216;
                sum += texture2D(uTexture, vTexCoord - uDirection * 1.3846).rgb * 0.316216;
                sum += texture2D(uTexture, vTexCoord + uDirection * 3.2308).rgb * 0.070270;
                sum += texture2D(uTexture, vTexCoord - uDirection * 3.2308).rgb * 0.070270;
                gl_FragColor = vec4(sum, 1.0);
            }
        """.trimIndent()
    }
}

internal class FinishPass : GlPass(GlUtil.VERT_PLAIN, FRAGMENT, UNIFORMS) {
    fun draw(
        baseTextureId: Int,
        bloomTextureId: Int,
        bloomStrength: Float,
        grain: Float,
        vignette: Float,
        seed: Float,
        sdr: Boolean = false
    ) {
        GLES20.glUseProgram(program.id)
        bindTexture(0, GLES20.GL_TEXTURE_2D, baseTextureId, 0)
        bindTexture(1, GLES20.GL_TEXTURE_2D, bloomTextureId, 1)
        GLES20.glUniform1f(program.uniforms[2], bloomStrength)
        GLES20.glUniform1f(program.uniforms[3], grain)
        GLES20.glUniform1f(program.uniforms[4], vignette)
        GLES20.glUniform1f(program.uniforms[5], seed)
        GLES20.glUniform1f(program.uniforms[6], if (sdr) 1f else 0f)
        drawQuad()
    }

    private companion object {
        val UNIFORMS = arrayOf(
            "uTexture",
            "uBloom",
            "uBloomStrength",
            "uGrain",
            "uVignette",
            "uSeed",
            "uSdr"
        )

        val FRAGMENT = GlUtil.FRAG_HEAD_2D_HIGH + GlUtil.SAMPLER_2D + """
            uniform sampler2D uBloom;
            uniform float uBloomStrength;
            uniform float uGrain;
            uniform float uVignette;
            uniform float uSeed;
            uniform float uSdr;
            varying vec2 vTexCoord;
            void main() {
                vec3 base = texture2D(uTexture, vTexCoord).rgb;
                vec3 bloom = texture2D(uBloom, vTexCoord).rgb;
                vec3 c = base + bloom * uBloomStrength;
                if (uGrain > 0.0) {
                    float n = fract(sin(dot(vTexCoord + uSeed, vec2(12.9898, 78.233))) * 43758.5453);
                    c += (n - 0.5) * uGrain;
                }
                if (uVignette > 0.0) {
                    vec2 d = vTexCoord - 0.5;
                    float v = smoothstep(0.18, 0.62, dot(d, d) * 2.2);
                    c *= mix(1.0, 1.0 - uVignette, v);
                }
                if (uSdr > 0.5) {
                    // 标准 SDR（广电/显示）色调：柔和高光压缩 + 轻微伽马，高光不溢出
                    c = clamp(c, 0.0, 1.0);
                    c = 1.0 - exp(-c * 2.6);
                    c = pow(c, vec3(0.97));
                    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
                    c = mix(c, vec3(l), 0.05);
                }
                gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
            }
        """.trimIndent()
    }
}

internal class EasuPass : GlPass(GlUtil.VERT_PLAIN, FRAGMENT, arrayOf("uTexture", "uTexel")) {
    fun draw(textureId: Int, texelX: Float, texelY: Float) {
        GLES20.glUseProgram(program.id)
        bindTexture(0, GLES20.GL_TEXTURE_2D, textureId, 0)
        GLES20.glUniform2f(program.uniforms[1], texelX, texelY)
        drawQuad()
    }

    private companion object {
        val FRAGMENT = GlUtil.FRAG_HEAD_2D_HIGH + GlUtil.SAMPLER_2D + """
            uniform vec2 uTexel;
            varying vec2 vTexCoord;
            vec3 tap(vec2 uv) {
                return texture2D(uTexture, clamp(uv, uTexel * 0.5, vec2(1.0) - uTexel * 0.5)).rgb;
            }
            vec4 weights(float t) {
                float t2 = t * t;
                float t3 = t2 * t;
                return vec4(
                    -0.5 * t3 + t2 - 0.5 * t,
                    1.5 * t3 - 2.5 * t2 + 1.0,
                    -1.5 * t3 + 2.0 * t2 + 0.5 * t,
                    0.5 * t3 - 0.5 * t2
                );
            }
            vec3 row(vec2 uv, float dy, vec4 w) {
                vec3 c0 = tap(uv + vec2(-1.0 * uTexel.x, dy));
                vec3 c1 = tap(uv + vec2(0.0, dy));
                vec3 c2 = tap(uv + vec2(1.0 * uTexel.x, dy));
                vec3 c3 = tap(uv + vec2(2.0 * uTexel.x, dy));
                return c0 * w.x + c1 * w.y + c2 * w.z + c3 * w.w;
            }
            void main() {
                vec2 coord = vTexCoord / uTexel - 0.5;
                vec2 base = floor(coord);
                vec2 f = coord - base;
                vec4 wx = weights(f.x);
                vec4 wy = weights(f.y);
                vec2 uv = (base + 0.5) * uTexel;
                vec3 r0 = row(uv, -1.0 * uTexel.y, wx);
                vec3 r1 = row(uv, 0.0, wx);
                vec3 r2 = row(uv, 1.0 * uTexel.y, wx);
                vec3 r3 = row(uv, 2.0 * uTexel.y, wx);
                vec3 c = r0 * wy.x + r1 * wy.y + r2 * wy.z + r3 * wy.w;
                gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
            }
        """.trimIndent()
    }
}

internal class RcasPass : GlPass(
    GlUtil.VERT_PLAIN,
    FRAGMENT,
    arrayOf("uTexture", "uTexel", "uSharpness")
) {
    fun draw(textureId: Int, texelX: Float, texelY: Float, sharpness: Float) {
        GLES20.glUseProgram(program.id)
        bindTexture(0, GLES20.GL_TEXTURE_2D, textureId, 0)
        GLES20.glUniform2f(program.uniforms[1], texelX, texelY)
        GLES20.glUniform1f(program.uniforms[2], sharpness)
        drawQuad()
    }

    private companion object {
        val FRAGMENT = GlUtil.FRAG_HEAD_2D_HIGH + GlUtil.SAMPLER_2D + """
            uniform vec2 uTexel;
            uniform float uSharpness;
            varying vec2 vTexCoord;
            vec3 tap(vec2 uv) {
                return texture2D(uTexture, clamp(uv, uTexel * 0.5, vec2(1.0) - uTexel * 0.5)).rgb;
            }
            void main() {
                vec3 c = tap(vTexCoord);
                vec3 n = tap(vTexCoord + vec2(0.0, -uTexel.y));
                vec3 s = tap(vTexCoord + vec2(0.0, uTexel.y));
                vec3 e = tap(vTexCoord + vec2(uTexel.x, 0.0));
                vec3 w = tap(vTexCoord + vec2(-uTexel.x, 0.0));
                vec3 mn = min(min(min(n, s), min(e, w)), c);
                vec3 mx = max(max(max(n, s), max(e, w)), c);
                vec3 amp = clamp(min(mn, vec3(1.0) - mx) / max(mx - mn, vec3(1.0 / 255.0)), 0.0, 1.0);
                vec3 blur = (n + s + e + w) * 0.25;
                vec3 res = c + (c - blur) * amp * (uSharpness * 4.0);
                gl_FragColor = vec4(clamp(res, 0.0, 1.0), 1.0);
            }
        """.trimIndent()
    }
}

internal class CopyPass : GlPass(
    GlUtil.VERT_PLAIN,
    GlUtil.FRAG_HEAD_2D + GlUtil.SAMPLER_2D + """
        varying vec2 vTexCoord;
        void main() {
            gl_FragColor = vec4(texture2D(uTexture, vTexCoord).rgb, 1.0);
        }
    """.trimIndent(),
    arrayOf("uTexture")
) {
    fun draw(textureId: Int) {
        GLES20.glUseProgram(program.id)
        bindTexture(0, GLES20.GL_TEXTURE_2D, textureId, 0)
        drawQuad()
    }
}

internal class LutPass : GlPass(
    GlUtil.VERT_PLAIN,
    FRAGMENT,
    arrayOf("uTexture", "uLut", "uLutSize", "uGrid", "uInvTotal", "uIntensity")
) {
    fun draw(
        textureId: Int,
        lutTextureId: Int,
        lutSize: Float,
        grid: Float,
        invTotal: Float,
        intensity: Float
    ) {
        GLES20.glUseProgram(program.id)
        bindTexture(0, GLES20.GL_TEXTURE_2D, textureId, 0)
        bindTexture(1, GLES20.GL_TEXTURE_2D, lutTextureId, 1)
        GLES20.glUniform1f(program.uniforms[2], lutSize)
        GLES20.glUniform1f(program.uniforms[3], grid)
        GLES20.glUniform1f(program.uniforms[4], invTotal)
        GLES20.glUniform1f(program.uniforms[5], intensity)
        drawQuad()
    }

    private companion object {
        val FRAGMENT = GlUtil.FRAG_HEAD_2D_HIGH + GlUtil.SAMPLER_2D + """
            uniform sampler2D uLut;
            uniform float uLutSize;
            uniform float uGrid;
            uniform float uInvTotal;
            uniform float uIntensity;
            varying vec2 vTexCoord;
            vec3 lutLookup(vec3 c) {
                float bsel = clamp(c.b, 0.0, 1.0) * (uLutSize - 1.0);
                float gi = floor(bsel);
                float fb = bsel - gi;
                float px = (clamp(c.r, 0.0, 1.0) * (uLutSize - 1.0) + 0.5) * uInvTotal;
                float py = (clamp(c.g, 0.0, 1.0) * (uLutSize - 1.0) + 0.5) * uInvTotal;
                float gx = mod(gi, uGrid);
                float gy = floor(gi / uGrid);
                vec2 base = vec2(gx, gy) * (uLutSize * uInvTotal);
                vec3 a = texture2D(uLut, base + vec2(px, py)).rgb;
                float gx2 = mod(gi + 1.0, uGrid);
                float gy2 = floor((gi + 1.0) / uGrid);
                vec2 base2 = vec2(gx2, gy2) * (uLutSize * uInvTotal);
                vec3 b2 = texture2D(uLut, base2 + vec2(px, py)).rgb;
                return mix(a, b2, fb);
            }
            void main() {
                vec3 src = texture2D(uTexture, vTexCoord).rgb;
                vec3 mapped = lutLookup(src);
                gl_FragColor = vec4(mix(src, mapped, uIntensity), 1.0);
            }
        """.trimIndent()
    }
}
