package com.fanhua.camera.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.fanhua.camera.R
import com.fanhua.camera.camera.analyzer.MoonDetector
import com.fanhua.camera.camera.core.PhotoAspect
import com.fanhua.camera.camera.core.WatermarkMode
import com.fanhua.camera.camera.filters.FilterGroup
import com.fanhua.camera.camera.filters.FilterPreset
import com.fanhua.camera.camera.gl.EglCore
import com.fanhua.camera.camera.gl.createGlFramePipeline
import com.fanhua.camera.debug.DebugLog
import com.fanhua.camera.lut.LutStore
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 照片后处理器：把 CameraX 拍下的原始 JPEG 走一遍 GL 滤镜管线，然后写回媒体库。
 *
 * 处理顺序（很重要，不要随意调换）：
 * 解码 → EXIF 方向旋转 → 前置镜像 → 画幅裁切 → GL 滤镜（含超分）→ 读回 → 徕卡相框 → 保存（含 EXIF 写回）
 */
class PhotoProcessor(private val context: Context) {

    private val leicaTypeface: Typeface by lazy {
        try {
            val base = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.jost_variable)
            if (base != null) {
                Typeface.create(base, Typeface.BOLD)
            } else {
                Typeface.create("sans-serif", Typeface.BOLD)
            }
        } catch (e: Exception) {
            Typeface.create("sans-serif", Typeface.BOLD)
        }
    }

    private val leicaNumberTypeface: Typeface by lazy {
        try {
            androidx.core.content.res.ResourcesCompat.getFont(context, R.font.leica_lg1056)
                ?: Typeface.create("sans-serif", Typeface.NORMAL)
        } catch (e: Exception) {
            Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    private val brandLogo: Bitmap? by lazy {
        try {
            BitmapFactory.decodeResource(context.resources, R.drawable.wm_xiaomi_black)
        } catch (e: Exception) {
            null
        }
    }

    private val leicaLogo: Bitmap? by lazy {
        try {
            BitmapFactory.decodeResource(context.resources, R.drawable.wm_leica)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 应用版本名，用于徕卡相框上的版本号角标。
     *
     * 历史实现用的是 [Build.VERSION.RELEASE]（Android 系统版本，例如 "14"），语义错误：
     * 相框上想表达的是「哪一版 App 拍的」。
     */
    private val appVersionName: String by lazy {
        try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 处理并保存一张照片。
     *
     * @param source CameraX 写出的原始临时文件（带完整 EXIF）
     * @param preset 滤镜预设
     * @param intensity 滤镜强度 0..1
     * @param upscale 是否超分放大（长边上限 [MAX_SUPER_RES_EDGE]）
     * @param aspect 照片画幅，[PhotoAspect.FULL] 表示不裁切
     * @param mirror 前置镜像，true 时水平翻转（在旋转之后、裁切之前）
     * @param watermarkMode 徕卡水印模式（OFF/FRAME/EMBEDDED）；非 LEICA 组滤镜时不生效
     */
    fun process(
        source: File,
        preset: FilterPreset,
        intensity: Float,
        upscale: Boolean,
        aspect: PhotoAspect = PhotoAspect.FULL,
        mirror: Boolean = false,
        watermarkMode: WatermarkMode = WatermarkMode.FRAME,
        /** 自动构图裁切框（归一化坐标）；null 表示不裁切。 */
        autoCrop: com.fanhua.camera.camera.detect.NormRect? = null,
        /** 超级月亮：命中月亮时在成片上增强并适度放大圆盘（直出）。 */
        superMoon: Boolean = false
    ): Uri? {
        DebugLog.log(
            "Photo",
            "开始处理 preset=${preset.name} intensity=$intensity upscale=$upscale " +
                "aspect=${aspect.name} mirror=$mirror watermarkMode=$watermarkMode"
        )
        val needsGeometry = aspect != PhotoAspect.FULL || mirror

        // 原图 + 不超分 + 不需要几何处理：字节级直接复制，连解码都省了（EXIF 天然保留）
        if (preset == FilterPreset.ORIGINAL && !upscale && !needsGeometry) {
            DebugLog.log("Photo", "原图且无需几何处理 -> 直接复制（不走 GL 管线）")
            return saveOriginal(source)
        }

        // 原图 + 需要画幅/镜像：走纯 CPU 几何路径。
        // 不走 GL 是因为 ORIGINAL 预设的 FinishPass 仍会叠加 bloom，会改变「原图」的观感。
        if (preset == FilterPreset.ORIGINAL && !upscale) {
            DebugLog.log("Photo", "原图 + 画幅/镜像 -> 纯 CPU 几何处理")
            return saveGeometryOnly(source, preset, aspect, mirror, autoCrop)
        }

        val lut = if (preset == FilterPreset.CUSTOM_LUT) LutStore.cached else null
        if (preset == FilterPreset.CUSTOM_LUT && lut == null) {
            DebugLog.log("Photo", "CUSTOM_LUT 未导入 LUT")
            // 没有 LUT 时仍要保证画幅/镜像生效，所以需要几何处理时走纯 CPU 路径
            return if (needsGeometry) {
                saveGeometryOnly(source, preset, aspect, mirror, autoCrop)
            } else {
                saveOriginal(source)
            }
        }

        var decoded: Bitmap? = null
        var output: Bitmap? = null
        var eglCore: EglCore? = null
        var eglSurface: EGLSurface? = null
        val pipeline = createGlFramePipeline()
        val startMs = SystemClock.elapsedRealtime()
        try {
            pipeline.setLut(lut)

            // 1) 解码 + EXIF 方向旋转
            var working = loadOrientedBitmap(source) ?: return null
            decoded = working
            DebugLog.log("Photo", "解码旋转后 ${working.width}x${working.height}")

            // 2) 前置镜像（旋转之后）
            if (mirror) {
                working = swapBitmap(working, flipHorizontal(working))
                decoded = working
                DebugLog.log("Photo", "已水平镜像")
            }

            // 3) 画幅裁切（旋转/镜像之后、加徕卡框之前）
            if (aspect != PhotoAspect.FULL) {
                working = swapBitmap(working, cropToAspect(working, aspect))
                // 自动构图裁切：按检测到的主体框裁切画面
                if (autoCrop != null) {
                    working = swapBitmap(working, cropToRect(working, autoCrop))
                }
                decoded = working
                DebugLog.log("Photo", "画幅裁切 ${aspect.name} -> ${working.width}x${working.height}")
            }

            val srcWidth = working.width
            val srcHeight = working.height

            // 4) 目标尺寸（超分）
            val targetWidth: Int
            val targetHeight: Int
            if (upscale) {
                val longEdge = maxOf(srcWidth, srcHeight).toFloat()
                // 大图不再无脑放大 2 倍：源图长边 >= 3000 时插值收益极低，画质反而变差
                val maxFactor = if (longEdge >= LARGE_SOURCE_EDGE) {
                    LARGE_SOURCE_MAX_FACTOR
                } else {
                    DEFAULT_MAX_FACTOR
                }
                val factor = minOf(maxFactor, MAX_SUPER_RES_EDGE / longEdge).coerceAtLeast(1f)
                targetWidth = (srcWidth * factor).toInt()
                targetHeight = (srcHeight * factor).toInt()
            } else {
                targetWidth = srcWidth
                targetHeight = srcHeight
            }
            DebugLog.log("Photo", "渲染目标 ${targetWidth}x$targetHeight")

            // 5) GL 渲染
            val core = EglCore()
            eglCore = core
            DebugLog.log("Photo", "EGL 上下文 GLES${core.glVersion}")
            val surface = core.createOffscreenSurface(targetWidth, targetHeight)
            eglSurface = surface
            core.makeCurrent(surface)
            val textureId = pipeline.createTextureFromBitmap(working)
            pipeline.setPreset(preset, intensity)
            pipeline.setSuperResolutionEnabled(upscale && targetWidth > srcWidth)
            pipeline.drawBitmapFrame(textureId, srcWidth, srcHeight, targetWidth, targetHeight)
            // 必须等 GPU 把这一帧真正画完再读回。
            // glReadPixels 虽然会隐式同步，但部分驱动在 pbuffer + 多 pass 场景下
            // 仍会读到未完成内容（表现为整张照片纯白或纯黑）。
            GLES20.glFinish()

            // 6) 读回像素
            val readback = readPixels(targetWidth, targetHeight)
            pipeline.deleteTexture(textureId)
            output = readback
            DebugLog.log(
                "Photo",
                "读回完成 左上=0x%08X 中心=0x%08X".format(
                    readback.getPixel(0, 0),
                    readback.getPixel(targetWidth / 2, targetHeight / 2)
                )
            )
            val elapsed = SystemClock.elapsedRealtime() - startMs
            DebugLog.log("Photo", "GL 管线耗时 ${elapsed}ms -> 开始保存")

            var result = readback

            // 6.5) 超级月亮（直出）
            // 在**最终成片**上重新检测一次月亮：分析帧与成片之间存在画幅/裁剪差异，
            // 直接复用预览阶段的坐标会错位。检测在缩略图上做，开销很小。
            if (superMoon) {
                val enhanced = try {
                    val moon = MoonDetector.detect(result)
                    if (moon != null) {
                        DebugLog.log(
                            "Photo",
                            "超级月亮 命中 conf=%.2f 半径=%.3f 中心=(%.3f,%.3f)".format(
                                moon.confidence, moon.radiusRatio,
                                (moon.norm.left + moon.norm.right) / 2f,
                                (moon.norm.top + moon.norm.bottom) / 2f
                            )
                        )
                        SuperMoonProcessor.enhance(result, moon) ?: result
                    } else {
                        DebugLog.log("Photo", "超级月亮 未检测到月亮，跳过")
                        result
                    }
                } catch (e: Exception) {
                    DebugLog.log("Photo", "超级月亮处理失败: ${e.javaClass.simpleName}: ${e.message}")
                    result
                }
                if (enhanced !== result) {
                    result.recycle()
                    result = enhanced
                    output = enhanced
                }
            }

            // 7) 水印（受三态模式控制，仅 LEICA 组滤镜生效）
            if (watermarkMode != WatermarkMode.OFF && preset.group == FilterGroup.LEICA) {
                val decorated = try {
                    // 优先用新的通用水印渲染器（支持徕卡全部 85 个水印样式）
                    renderWatermark(result, source, watermarkMode, preset, intensity)
                } catch (e: Exception) {
                    DebugLog.log("Photo", "水印渲染失败，回退旧实现: ${e.message}")
                    try {
                        when (watermarkMode) {
                            WatermarkMode.FRAME -> drawLeicaFrame(result, readExifInfo(source))
                            WatermarkMode.EMBEDDED -> drawEmbeddedWatermark(result, readExifInfo(source))
                            else -> result
                        }
                    } catch (e2: Exception) {
                        DebugLog.log("Photo", "旧水印实现也失败: ${e2.message}")
                        result
                    }
                }
                if (decorated !== result) {
                    result.recycle()
                    result = decorated
                    output = decorated
                }
            }

            // 8) 保存（压缩到 cache 临时文件 -> 写 EXIF -> 复制进 MediaStore）
            return save(result, preset, source)
        } catch (e: Exception) {
            DebugLog.log("Photo", "GL 管线失败: ${e.javaClass.simpleName}: ${e.message}")
            return saveOriginal(source)
        } finally {
            pipeline.release()
            eglSurface?.let { surface -> eglCore?.destroySurface(surface) }
            eglCore?.release()
            decoded?.recycle()
            output?.recycle()
        }
    }

    /** 兼容旧调用方的 4 参数重载，委托到带画幅/镜像/水印的新版本。 */
    fun process(source: File, preset: FilterPreset, intensity: Float, upscale: Boolean): Uri? =
        process(
            source = source,
            preset = preset,
            intensity = intensity,
            upscale = upscale,
            aspect = PhotoAspect.FULL,
            mirror = false,
            watermarkMode = WatermarkMode.FRAME
        )

    /**
     * 用通用水印渲染器绘制水印。
     *
     * 水印规格来自 `assets/watermark_specs.json`（从徕卡一瞬 APK 逆向的 85 个样式），
     * 通过 [WatermarkSettings] 里保存的样式 id 选择。
     */
    private fun renderWatermark(
        photo: Bitmap,
        source: File,
        mode: WatermarkMode,
        preset: FilterPreset,
        intensity: Float
    ): Bitmap {
        val renderer = com.fanhua.camera.media.watermark.WatermarkRenderer(context)
        val repo = com.fanhua.camera.media.watermark.WatermarkRegistry.repository(context)
        val specId = com.fanhua.camera.media.watermark.WatermarkRegistry.currentId(context)
        val spec = repo.spec(specId) ?: return photo

        val exif = readExifInfo(source)
        val data = com.fanhua.camera.media.watermark.WatermarkRenderer.RenderData(
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            appVersion = appVersionName(),
            timeMillis = System.currentTimeMillis(),
            focalLength = exif.focalMm.takeIf { it.isNotBlank() },
            aperture = exif.aperture.takeIf { it.isNotBlank() },
            shutterSpeed = exif.speed.takeIf { it.isNotBlank() },
            iso = exif.iso.takeIf { it.isNotBlank() },
            location = null,
            filterName = null,
            themeName = if (mode == WatermarkMode.EMBEDDED) "black" else "white"
        )
        DebugLog.log("Photo", "水印渲染 spec=$specId mode=$mode theme=${data.themeName}")
        return renderer.apply(photo, spec, data)
    }

    fun drawLeicaFrame(source: Bitmap, exif: ExifInfo): Bitmap {
        val base = minOf(source.width, source.height).toFloat()
        val dp = base / 640f
        val padH = 80f * dp
        val topH = 188f * dp
        val bottomH = 194f * dp

        val outW = (source.width + padH * 2).toInt()
        val outH = (source.height + topH + bottomH).toInt()
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)

        canvas.drawBitmap(source, padH, topH, Paint(Paint.FILTER_BITMAP_FLAG))

        val ink = Color.BLACK
        val inkSecondary = Color.argb(0x80, 0, 0, 0)
        val leicaFont = leicaNumberTypeface
        val sysFont = Typeface.create("sans-serif", Typeface.NORMAL)

        var cursorX = padH
        val logoH = 24f * dp
        brandLogo?.let { logo ->
            val logoW = logoH * logo.width / logo.height.toFloat()
            val dst = android.graphics.RectF(cursorX, topH - 104f * dp - logoH, cursorX + logoW, topH - 104f * dp)
            canvas.drawBitmap(logo, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            cursorX = dst.right
        }

        val modelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            typeface = sysFont
            textSize = 21.6f * dp
            isFakeBoldText = true
        }
        val modelText = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        val modelBaseline = topH - 104f * dp
        canvas.drawText(modelText, cursorX, modelBaseline, modelPaint)
        cursorX += modelPaint.measureText(modelText)

        exif.versionName?.let { ver ->
            val vp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink
                typeface = sysFont
                textSize = 13.2f * dp
                letterSpacing = 0.028f
            }
            val gap = 5f * dp
            val textW = vp.measureText(ver)
            val rectH = 20f * dp
            val rectTop = modelBaseline - rectH * 0.78f
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = inkSecondary
                style = Paint.Style.STROKE
                strokeWidth = 1f * dp
            }
            val rr = android.graphics.RectF(
                cursorX + gap, rectTop,
                cursorX + gap + textW + gap * 2, rectTop + rectH
            )
            canvas.drawRoundRect(rr, 4f * dp, 4f * dp, stroke)
            canvas.drawText(ver, rr.left + gap, modelBaseline, vp)
        }

        exif.timeText?.let { t ->
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = inkSecondary
                typeface = leicaFont
                textSize = 20f * dp
                letterSpacing = 0.15f
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(t, outW - padH, topH - 108f * dp, tp)
        }

        val bottomTop = topH + source.height.toFloat()
        var bx = padH
        val leicaH = 72f * dp
        leicaLogo?.let { logo ->
            val logoW = leicaH * logo.width / logo.height.toFloat()
            val centerY = bottomTop + bottomH / 2f
            val dst = android.graphics.RectF(bx, centerY - leicaH / 2f, bx + logoW, centerY + leicaH / 2f)
            canvas.drawBitmap(logo, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            bx = dst.right + 40f * dp
        }

        val exifPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            typeface = leicaFont
            textSize = 20f * dp
            letterSpacing = 0.15f
        }
        val labelPaint = Paint(exifPaint).apply { color = inkSecondary }
        val line1Y = bottomTop + bottomH / 2f - 4f * dp
        val line2Y = bottomTop + bottomH / 2f + 20f * dp

        if (exif.focalMm.isNotBlank()) {
            canvas.drawText(exif.focalMm, bx, line1Y, exifPaint)
        }

        var sx = bx
        fun label(text: String) {
            canvas.drawText(text, sx, line2Y, labelPaint)
            sx += labelPaint.measureText(text) + 8f * dp
        }

        fun value(text: String) {
            canvas.drawText(text, sx, line2Y, exifPaint)
            sx += exifPaint.measureText(text)
        }

        if (exif.aperture.isNotBlank()) {
            label("F")
            value(exif.aperture)
            sx += 20f * dp
        }
        if (exif.speed.isNotBlank()) {
            label("S")
            value(exif.speed)
            sx += 20f * dp
        }
        if (exif.iso.isNotBlank()) {
            label("ISO")
            value(exif.iso)
        }

        return out
    }

    /**
     * 内嵌式徕卡水印（参照相机整合「标准 7 号」：水印叠在照片内部底部，不在照片外扩边）。
     *
     * 在照片底部绘制一条半透明暗带：左侧徕卡标志（自带 sRGB 原色），右侧时间，
     * 中间机型名；文字与 logo 均为浅色，保证在亮/暗照片上都清晰。
     * 仅 [FilterPreset] 属于 LEICA 组时由调用方传入。
     */
    fun drawEmbeddedWatermark(source: Bitmap, exif: ExifInfo): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

        val base = minOf(source.width, source.height).toFloat()
        val dp = base / 640f
        val bandH = (base * 0.13f).coerceAtLeast(36f * dp)
        val pad = 40f * dp
        val logoH = (base * 0.07f).coerceAtLeast(20f * dp)
        val ink = Color.WHITE
        val inkSoft = Color.argb(0xB3, 0xFF, 0xFF, 0xFF)
        val leicaFont = leicaNumberTypeface
        val sysFont = Typeface.create("sans-serif", Typeface.NORMAL)

        // 底部半透明暗带（上边缘渐隐，避免硬切割）
        val bandTop = (source.height - bandH)
        val grad = android.graphics.LinearGradient(
            0f, bandTop, 0f, source.height.toFloat(),
            Color.argb(0, 0, 0, 0), Color.argb(0x80, 0, 0, 0),
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawRect(
            android.graphics.RectF(0f, bandTop, source.width.toFloat(), source.height.toFloat()),
            Paint().apply { shader = grad }
        )

        val centerY = bandTop + bandH / 2f

        // 左侧：徕卡标志
        leicaLogo?.let { logo ->
            val logoW = logoH * logo.width / logo.height.toFloat()
            val dst = android.graphics.RectF(pad, centerY - logoH / 2f, pad + logoW, centerY + logoH / 2f)
            canvas.drawBitmap(logo, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        // 中间：机型名
        val modelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            typeface = sysFont
            textSize = 20f * dp
            isFakeBoldText = true
        }
        val modelText = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        modelPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(modelText, source.width / 2f, centerY + modelPaint.textSize * 0.35f, modelPaint)

        // 右侧：时间
        exif.timeText?.let { t ->
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = inkSoft
                typeface = leicaFont
                textSize = 18f * dp
                letterSpacing = 0.15f
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(t, source.width - pad, centerY + tp.textSize * 0.35f, tp)
        }

        return out
    }

    /** 当前应用版本名，用于水印里的版本标签。 */
    private fun appVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    data class ExifInfo(
        val focalMm: String = "",
        val aperture: String = "",
        val speed: String = "",
        val iso: String = "",
        val timeText: String? = null,
        val versionName: String? = null
    )

    fun readExifInfo(source: File?): ExifInfo {
        if (source == null) return ExifInfo()
        return try {
            val exif = ExifInterface(source.absolutePath)
            val focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
                ?.let { parseRational(it) }
                ?.let { if (it == it.toLong().toDouble()) "${it.toLong()}mm" else "%.1fmm".format(it) }
                ?: ""
            val aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                ?.let { parseRational(it) }
                ?.let { "%.1f".format(it) }
                ?: ""
            val speed = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                ?.let { parseRational(it) }
                ?.let { s ->
                    if (s < 1.0 && s > 0.0) "1/${Math.round(1.0 / s)}" else "%.1f\"".format(s)
                }
                ?: ""
            val iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                ?: ""
            val date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            val timeText = date?.let {
                val m = Regex("""(\d{4}):(\d{2}):(\d{2})[ T](\d{2}):(\d{2})""").find(it)
                m?.let { r ->
                    "${r.groupValues[1]}.${r.groupValues[2]}.${r.groupValues[3]} " +
                        "${r.groupValues[4]}:${r.groupValues[5]}"
                }
            }
            ExifInfo(
                focalMm = focal,
                aperture = aperture,
                speed = speed,
                iso = iso,
                timeText = timeText,
                // 应用版本名（不是 Android 系统版本）；相框角标空间有限，只取数字部分
                versionName = appVersionBadge()
            )
        } catch (e: Exception) {
            ExifInfo()
        }
    }

    /**
     * 徕卡相框上显示的版本角标。
     *
     * 形如 `v0.1.21-beta` -> `V0.1.21`：相框角标宽度有限，去掉 `-beta` 这类通道后缀，
     * 保留主版本号，语义仍是「App 版本」。
     */
    private fun appVersionBadge(): String? {
        val raw = appVersionName.ifBlank { return null }
        val core = raw.substringBefore('-').ifBlank { raw }
        return "V$core"
    }

    private fun parseRational(raw: String): Double? {
        val parts = raw.split("/")
        return if (parts.size == 2) {
            val n = parts[0].toDoubleOrNull() ?: return null
            val d = parts[1].toDoubleOrNull() ?: return null
            if (d != 0.0) n / d else null
        } else {
            raw.toDoubleOrNull()
        }
    }

    // ---------------------------------------------------------------- 几何变换

    /**
     * 按画幅居中裁切。
     *
     * 竖拍处理：手机竖拍时图片高 > 宽，此时 [PhotoAspect.ratio] 应按「长边/短边」理解，
     * 目标比例取 `1f / ratio`（例如 4:3 的竖拍结果是 3:4 而不是 4:3）。
     */
    /**
     * 按归一化矩形裁切位图。
     *
     * 用于「自动构图」：检测到主体后，把画面裁到建议构图范围内。
     * 裁切结果的长宽比保持不变（按目标框在原图中的相对位置与大小换算）。
     */
    private fun cropToRect(
        bitmap: Bitmap,
        rect: com.fanhua.camera.camera.detect.NormRect
    ): Bitmap {
        val left = (rect.left.coerceIn(0f, 1f) * bitmap.width).toInt()
        val top = (rect.top.coerceIn(0f, 1f) * bitmap.height).toInt()
        val right = (rect.right.coerceIn(0f, 1f) * bitmap.width).toInt()
        val bottom = (rect.bottom.coerceIn(0f, 1f) * bitmap.height).toInt()
        val w = (right - left).coerceAtLeast(1)
        val h = (bottom - top).coerceAtLeast(1)
        // 全画幅或无效框直接跳过
        if (w >= bitmap.width - 2 && h >= bitmap.height - 2) return bitmap
        if (w <= 0 || h <= 0) return bitmap
        return try {
            Bitmap.createBitmap(bitmap, left, top, w, h)
        } catch (e: Exception) {
            DebugLog.log("Photo", "自动构图裁切失败: ${e.message}")
            bitmap
        }
    }

    private fun cropToAspect(bitmap: Bitmap, aspect: PhotoAspect): Bitmap {
        val ratio = aspect.ratio
        if (ratio <= 0f) return bitmap
        val targetRatio = if (bitmap.height > bitmap.width) 1f / ratio else ratio
        val width = bitmap.width
        val height = bitmap.height
        val currentRatio = width.toFloat() / height.toFloat()
        // 比例已经很接近就不裁，避免无意义的位图拷贝
        if (abs(currentRatio - targetRatio) < 0.005f) return bitmap

        val cropWidth: Int
        val cropHeight: Int
        if (currentRatio > targetRatio) {
            // 当前太宽 -> 裁宽
            cropWidth = (height * targetRatio).roundToInt().coerceIn(1, width)
            cropHeight = height
        } else {
            // 当前太高 -> 裁高
            cropWidth = width
            cropHeight = (width / targetRatio).roundToInt().coerceIn(1, height)
        }
        val left = ((width - cropWidth) / 2).coerceAtLeast(0)
        val top = ((height - cropHeight) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
    }

    /** 水平翻转（前置镜像）。 */
    private fun flipHorizontal(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply {
            postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** 用新位图替换旧位图；不是同一个对象时回收旧的，避免 OOM。 */
    private fun swapBitmap(current: Bitmap, next: Bitmap): Bitmap {
        if (next !== current) current.recycle()
        return next
    }

    // ---------------------------------------------------------------- 读回

    /**
     * 从当前 EGL 上下文的默认帧缓冲读回像素并生成 Bitmap。
     *
     * ## 字节序依据（关键）
     * - `glReadPixels(GL_RGBA, GL_UNSIGNED_BYTE)` 输出的内存布局是逐像素 R,G,B,A。
     * - Android 的 `Bitmap.Config.ARGB_8888` 在内存中同样是逐像素 R,G,B,A 字节序
     *   （小端机器上单像素 int 值为 0xAARRGGBB，但字节流顺序是 R,G,B,A）。
     * - 两者字节布局一致，因此 `copyPixelsFromBuffer` 可以直接使用，无需逐像素重排。
     *   旧实现用 `(a shl 24) or (r shl 16) or (g shl 8) or b` 逐个组装 IntArray，
     *   在 4K（约 800 万像素）下是 800 万次循环，实测数百毫秒；直接 copy 是纯 memcpy 级别。
     *
     * ## Y 方向
     * `GLUtils.texImage2D` 上传 Bitmap 时第 0 行对应纹理 v=0，而 `drawQuad` 把 v=0 画在
     * 帧缓冲底部；`glReadPixels` 又从底部第 0 行开始读 —— 两次翻转相互抵消，方向正确，
     * 不需要额外翻转。
     *
     * ## 如何回退
     * 如果实机发现颜色异常（红蓝互换 / 整张照片偏色），把 [USE_DIRECT_COPY] 改成 false，
     * 就会走 [readPixelsWithChannelSwap] 逐像素重排 R/B 的保守路径。
     * 历史背景：release notes 里记录过「修复照片偏色：保存管线 R/B 通道互换」，
     * 说明这里曾经踩过坑，所以保留两条路径以便快速回退。
     */
    private fun readPixels(width: Int, height: Int): Bitmap {
        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        // copyPixelsFromBuffer 从 buffer 的当前位置开始读，必须显式归零
        buffer.position(0)
        return if (USE_DIRECT_COPY) {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                copyPixelsFromBuffer(buffer)
            }
        } else {
            readPixelsWithChannelSwap(buffer, width, height)
        }
    }

    /**
     * 回退路径：不走 `copyPixelsFromBuffer`，改为显式搬运通道再 `setPixels`。
     *
     * ## 通道换算（为什么这么写）
     * `glReadPixels(GL_RGBA)` 的内存字节流是 (R,G,B,A)。在小端机上按 int 读出来是
     * `0xAABBGGRR`（最低字节是 R）。而 [Bitmap.setPixels] 接收的是**颜色值** `0xAARRGGBB`。
     * 所以要把最低字节 R 搬到 16 位、把 16 位的 B 搬到最低字节：
     *
     * ```
     * 0xAABBGGRR  --(R/B 对调)-->  0xAARRGGBB
     * ```
     *
     * ## 为什么这条路径才是「保险」的那条
     * - [Bitmap.copyPixelsFromBuffer] 依赖一个**未写进文档**的实现细节：
     *   ARGB_8888 的原生内存布局恰好是 RGBA 字节序。绝大多数设备如此，所以能用。
     * - [Bitmap.setPixels] 的语义是**文档化的**：参数就是 `0xAARRGGBB` 颜色值，
     *   由系统自己负责转成原生布局。所以这条路径与设备内存布局无关，构造上就是对的。
     *
     * 换句话说：默认走快路径；一旦实机出现红蓝互换/偏色，把 [USE_DIRECT_COPY] 改成
     * `false` 切到本函数即可得到正确颜色 —— **不要**去改下面的掩码。
     *
     * 性能：4K 约 800 万次循环，比直接 copy 慢，但比历史实现逐个
     * `ByteBuffer.get(ByteArray(4))` 快一个数量级（这里用 `asIntBuffer()` 整块取）。
     */
    private fun readPixelsWithChannelSwap(
        buffer: ByteBuffer,
        width: Int,
        height: Int
    ): Bitmap {
        val count = width * height
        val pixels = IntArray(count)
        buffer.asIntBuffer().get(pixels)
        for (i in 0 until count) {
            val v = pixels[i]
            // 保留 A(24-31) 与 G(8-15)，把 R(0-7) 与 B(16-23) 对调
            pixels[i] = (v and 0xFF00FF00.toInt()) or
                ((v and 0x000000FF) shl 16) or
                ((v ushr 16) and 0x000000FF)
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    // ---------------------------------------------------------------- 解码

    private fun decode(source: File): Bitmap? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        DebugLog.log(
            "Photo",
            "解码尺寸=${bounds.outWidth}x${bounds.outHeight} mime=${bounds.outMimeType}"
        )
        var sample = 1
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (longEdge / sample > MAX_DECODE_EDGE) {
            sample *= 2
        }
        val options = BitmapFactory.Options()
        options.inSampleSize = sample
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        val bitmap = BitmapFactory.decodeFile(source.absolutePath, options)
        DebugLog.log("Photo", "解码 sample=$sample -> ${bitmap?.width}x${bitmap?.height}")
        return bitmap
    }

    /** 解码并按 EXIF 方向摆正。 */
    private fun loadOrientedBitmap(source: File): Bitmap? {
        var bitmap = decode(source) ?: return null
        val rotation = readRotation(source)
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) {
                bitmap.recycle()
                bitmap = rotated
            }
            DebugLog.log("Photo", "应用 EXIF 旋转=$rotation -> ${bitmap.width}x${bitmap.height}")
        }
        return bitmap
    }

    private fun readRotation(source: File): Int {
        return try {
            val exif = ExifInterface(source.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    // ---------------------------------------------------------------- 保存

    /**
     * 保存位图到媒体库。
     *
     * 流程：先压缩到 cacheDir 临时 jpg（只有文件才能用 [ExifInterface] 读写），
     * 把源文件的 EXIF 写进临时文件，再把字节复制到 MediaStore 输出流。
     * 这样既保住了 EXIF，又不用直接操作 MediaStore 的 fd。
     */
    private fun save(bitmap: Bitmap, preset: FilterPreset, source: File?): Uri? {
        val name = "FH_" + System.currentTimeMillis() + "_" + preset.name + ".jpg"
        val temp = File(context.cacheDir, "fanhua_save_" + System.currentTimeMillis() + ".jpg")
        try {
            FileOutputStream(temp).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    DebugLog.log("Photo", "JPEG 压缩失败")
                    return null
                }
            }
            if (source != null) {
                writeExif(source, temp)
            }
            return publishToMediaStore(temp, name, bitmap.width, bitmap.height)
        } catch (e: Exception) {
            DebugLog.log("Photo", "保存失败: ${e.javaClass.simpleName}: ${e.message}")
            return null
        } finally {
            temp.delete()
        }
    }

    /** 把源文件的 EXIF 写进目标 JPEG 文件。 */
    private fun writeExif(source: File, target: File) {
        try {
            if (!source.exists()) return
            val src = ExifInterface(source.absolutePath)
            val dst = ExifInterface(target.absolutePath)

            // 相机型号、曝光参数、镜头、时间等常用标签逐个搬运
            val tags = listOf(
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_LENS_MODEL,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                ExifInterface.TAG_ISO_SPEED_RATINGS,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_FLASH,
                ExifInterface.TAG_WHITE_BALANCE,
                ExifInterface.TAG_EXPOSURE_PROGRAM,
                ExifInterface.TAG_METERING_MODE,
                ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
                ExifInterface.TAG_SOFTWARE,
                ExifInterface.TAG_GPS_TIMESTAMP,
                ExifInterface.TAG_GPS_DATESTAMP
            )
            for (tag in tags) {
                val value = runCatching { src.getAttribute(tag) }.getOrNull()
                if (!value.isNullOrBlank()) {
                    runCatching { dst.setAttribute(tag, value) }
                }
            }

            // Make/Model 兜底：源图缺失时写设备信息，保证相册里的元信息不空
            if (runCatching { dst.getAttribute(ExifInterface.TAG_MAKE) }.getOrNull().isNullOrBlank()) {
                runCatching {
                    dst.setAttribute(
                        ExifInterface.TAG_MAKE,
                        Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
                    )
                }
            }
            if (runCatching { dst.getAttribute(ExifInterface.TAG_MODEL) }.getOrNull().isNullOrBlank()) {
                runCatching { dst.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL) }
            }

            // 拍摄时间兜底，避免相册按时间排序时这张照片落到底部
            if (runCatching { dst.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) }
                    .getOrNull().isNullOrBlank()
            ) {
                runCatching {
                    dst.setAttribute(
                        ExifInterface.TAG_DATETIME_ORIGINAL,
                        SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date())
                    )
                }
            }

            // GPS：先搬经纬度，再搬海拔
            val latLong = FloatArray(2)
            if (runCatching { src.getLatLong(latLong) }.getOrDefault(false)) {
                runCatching { dst.setLatLong(latLong[0].toDouble(), latLong[1].toDouble()) }
                val altitude = runCatching { src.getAltitude(Double.NaN) }.getOrDefault(Double.NaN)
                if (!altitude.isNaN()) {
                    runCatching { dst.setAltitude(altitude) }
                }
            }

            // 像素已经按 EXIF 方向旋转过，输出方向必须写 1（NORMAL），否则查看器会二次旋转
            runCatching {
                dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            }
            dst.saveAttributes()
            DebugLog.log("Photo", "EXIF 已写回 -> ${target.name}")
        } catch (e: Exception) {
            // EXIF 写失败不影响出片，只记日志
            DebugLog.log("Photo", "EXIF 写回失败: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** 把临时文件字节复制进 MediaStore。 */
    private fun publishToMediaStore(file: File, name: String, width: Int, height: Int): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FanhuaCamera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            DebugLog.log("Photo", "写入媒体库失败: ${e.message}")
            resolver.delete(uri, null, null)
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }
        DebugLog.log("Photo", "已保存 ${width}x$height JPEG q=$JPEG_QUALITY uri=$uri name=$name")
        return uri
    }

    /**
     * 只做几何处理（画幅裁切 / 镜像）不做滤镜的兜底保存路径。
     * 用于 CUSTOM_LUT 但用户没有导入 LUT 的情况，保证画幅设置仍然生效。
     */
    private fun saveGeometryOnly(
        source: File,
        preset: FilterPreset,
        aspect: PhotoAspect,
        mirror: Boolean,
        autoCrop: com.fanhua.camera.camera.detect.NormRect? = null
    ): Uri? {
        var working: Bitmap? = null
        return try {
            working = loadOrientedBitmap(source) ?: return saveOriginal(source)
            if (mirror) {
                working = swapBitmap(working, flipHorizontal(working))
            }
            if (aspect != PhotoAspect.FULL) {
                working = swapBitmap(working, cropToAspect(working, aspect))
                // 自动构图裁切：按检测到的主体框裁切画面
                if (autoCrop != null) {
                    working = swapBitmap(working, cropToRect(working, autoCrop))
                }
            }
            save(working, preset, source)
        } catch (e: Exception) {
            DebugLog.log("Photo", "几何处理失败: ${e.message}")
            saveOriginal(source)
        } finally {
            working?.recycle()
        }
    }

    /** 原图直接复制：字节级搬运，EXIF 天然完整。 */
    private fun saveOriginal(source: File): Uri? {
        val name = "FH_" + System.currentTimeMillis() + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FanhuaCamera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }
        return uri
    }

    companion object {
        /** JPEG 压缩质量（1-100）。98 是画质与体积的折中，可按需下调。 */
        const val JPEG_QUALITY = 98

        /**
         * 字节序方案开关。
         *
         * `true`：`glReadPixels(GL_RGBA)` 的结果直接 `copyPixelsFromBuffer`（快，默认）。
         * `false`：逐像素交换 R/B 通道（保守回退）。
         * 判断依据见 [readPixels] 的注释；若实机出现红蓝互换/偏色，改成 false 即可。
         */
        private const val USE_DIRECT_COPY = true

        private const val MAX_DECODE_EDGE = 8192

        /** 超分输出长边上限。 */
        private const val MAX_SUPER_RES_EDGE = 4096f

        /** 源图长边达到该值时，放大系数限制到 1.5 倍以内。 */
        private const val LARGE_SOURCE_EDGE = 3000f
        private const val LARGE_SOURCE_MAX_FACTOR = 1.5f
        private const val DEFAULT_MAX_FACTOR = 2f
    }
}
