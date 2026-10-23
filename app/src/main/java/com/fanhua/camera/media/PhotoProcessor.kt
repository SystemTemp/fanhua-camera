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
import android.opengl.GLES20
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.fanhua.camera.R
import com.fanhua.camera.camera.filters.FilterPreset
import com.fanhua.camera.camera.gl.EglCore
import com.fanhua.camera.camera.gl.createGlFramePipeline
import com.fanhua.camera.debug.DebugLog
import com.fanhua.camera.lut.LutStore
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

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

    fun process(source: File, preset: FilterPreset, intensity: Float, upscale: Boolean): Uri? {
        DebugLog.log("Photo", "process start preset=${preset.name} intensity=$intensity upscale=$upscale")
        if (preset == FilterPreset.ORIGINAL && !upscale) {
            DebugLog.log("Photo", "original + no upscale -> direct copy (no GL pipeline)")
            return saveOriginal(source)
        }
        val lut = if (preset == FilterPreset.CUSTOM_LUT) LutStore.cached else null
        if (preset == FilterPreset.CUSTOM_LUT && lut == null) {
            DebugLog.log("Photo", "CUSTOM_LUT selected but no LUT imported -> save original")
            return saveOriginal(source)
        }
        var bitmap: Bitmap? = null
        var processed: Bitmap? = null
        var eglCore: EglCore? = null
        val startMs = SystemClock.elapsedRealtime()
        val pipeline = createGlFramePipeline()
        try {
            pipeline.setLut(lut)
            bitmap = decode(source) ?: return null
            DebugLog.log("Photo", "decoded ${bitmap.width}x${bitmap.height}")
            val rotation = readRotation(source)
            if (rotation != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotation.toFloat())
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated !== bitmap) {
                    bitmap.recycle()
                    bitmap = rotated
                }
                DebugLog.log("Photo", "applied EXIF rotation=$rotation -> ${bitmap.width}x${bitmap.height}")
            }
            val sourceBitmap = bitmap ?: return null
            val targetWidth: Int
            val targetHeight: Int
            if (upscale) {
                val maxEdge = 4096f
                val longEdge = maxOf(sourceBitmap.width, sourceBitmap.height).toFloat()
                val factor = minOf(2f, maxEdge / longEdge).coerceAtLeast(1f)
                targetWidth = (sourceBitmap.width * factor).toInt()
                targetHeight = (sourceBitmap.height * factor).toInt()
            } else {
                targetWidth = sourceBitmap.width
                targetHeight = sourceBitmap.height
            }
            DebugLog.log("Photo", "render target=${targetWidth}x${targetHeight}")

            val core = EglCore()
            eglCore = core
            DebugLog.log("Photo", "EGL context GLES${core.glVersion}")
            val surface = core.createOffscreenSurface(targetWidth, targetHeight)
            core.makeCurrent(surface)
            val textureId = pipeline.createTextureFromBitmap(sourceBitmap)
            pipeline.setPreset(preset, intensity)
            pipeline.setSuperResolutionEnabled(upscale && targetWidth > sourceBitmap.width)
            pipeline.drawBitmapFrame(textureId, sourceBitmap.width, sourceBitmap.height, targetWidth, targetHeight)
            val byteBuffer = ByteBuffer.allocateDirect(targetWidth * targetHeight * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, targetWidth, targetHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, byteBuffer)
            byteBuffer.rewind()
            val pixels = IntArray(targetWidth * targetHeight)
            val tmp = ByteArray(4)
            for (i in pixels.indices) {
                byteBuffer.get(tmp)
                val r = tmp[0].toInt() and 0xFF
                val g = tmp[1].toInt() and 0xFF
                val b = tmp[2].toInt() and 0xFF
                val a = tmp[3].toInt() and 0xFF
                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            core.destroySurface(surface)
            pipeline.deleteTexture(textureId)

            processed = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            processed.setPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
            DebugLog.log(
                "Photo",
                "readback ok topLeft=0x%08X center=0x%08X".format(
                    processed.getPixel(0, 0),
                    processed.getPixel(targetWidth / 2, targetHeight / 2)
                )
            )
            val elapsed = SystemClock.elapsedRealtime() - startMs
            DebugLog.log("Photo", "GL pipeline done in ${elapsed}ms -> saving")
            if (preset == FilterPreset.LEICA_MOMENT) {
                val framed = try {
                    drawLeicaFrame(processed, readExifInfo(source))
                } catch (e: Exception) {
                    DebugLog.log("Photo", "leica frame failed: ${e.message}")
                    processed
                }
                if (framed !== processed) {
                    processed.recycle()
                    processed = framed
                }
            }
            return save(processed, preset)
        } catch (e: Exception) {
            DebugLog.log("Photo", "GL pipeline FAILED: ${e.javaClass.simpleName}: ${e.message}")
            return saveOriginal(source)
        } finally {
            pipeline.release()
            eglCore?.release()
            bitmap?.recycle()
            processed?.recycle()
        }
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
                m?.let { r -> "${r.groupValues[1]}.${r.groupValues[2]}.${r.groupValues[3]} ${r.groupValues[4]}:${r.groupValues[5]}" }
            }
            ExifInfo(
                focalMm = focal,
                aperture = aperture,
                speed = speed,
                iso = iso,
                timeText = timeText,
                versionName = "${Build.VERSION.RELEASE}"
            )
        } catch (e: Exception) {
            ExifInfo()
        }
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

    private fun decode(source: File): Bitmap? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        DebugLog.log(
            "Photo",
            "decode bounds=${bounds.outWidth}x${bounds.outHeight} mime=${bounds.outMimeType}"
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
        DebugLog.log("Photo", "decode sample=$sample -> ${bitmap?.width}x${bitmap?.height}")
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

    private fun save(bitmap: Bitmap, preset: FilterPreset): Uri? {
        val name = "FH_" + System.currentTimeMillis() + "_" + preset.name + ".jpg"
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
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }
        DebugLog.log("Photo", "saved ${bitmap.width}x${bitmap.height} JPEG q=$JPEG_QUALITY uri=$uri name=$name")
        return uri
    }

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

    private companion object {
        const val MAX_DECODE_EDGE = 8192
        const val JPEG_QUALITY = 98
    }
}
