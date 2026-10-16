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
                ?: Typeface.DEFAULT
            Typeface.Builder(base).setFontVariationSettings("'wght' 720").build()
        } catch (e: Exception) {
            Typeface.create("sans-serif", Typeface.BOLD)
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
            if (preset == FilterPreset.LEICA_MOMENT) {
                drawLeicaWatermark(processed, source)
            }
            val elapsed = SystemClock.elapsedRealtime() - startMs
            DebugLog.log("Photo", "GL pipeline done in ${elapsed}ms -> saving")
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

    private fun drawLeicaWatermark(bitmap: Bitmap, source: File?) {
        try {
            val canvas = Canvas(bitmap)
            val width = bitmap.width.toFloat()
            val height = bitmap.height.toFloat()

            // 圆形徕卡徽章（红底白字 LEICA），位于右下角
            val badgeSize = minOf(width, height) * 0.15f
            val margin = width * 0.035f
            val cx = width - badgeSize / 2f - margin
            val cy = height - badgeSize / 2f - margin
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(200, 16, 46)
            }
            canvas.drawCircle(cx, cy, badgeSize / 2f, badgePaint)

            val leicaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = leicaTypeface
                textSize = badgeSize * 0.345f
                letterSpacing = 0.10f
            }
            val baseline = cy - (leicaPaint.descent() + leicaPaint.ascent()) / 2f
            canvas.drawText("LEICA", cx, baseline, leicaPaint)

            // 徽章下方的参数小字（像素/焦距/光圈/设备型号）
            val focal = readFocalLength(source)
            val fnum = readFNumber(source)
            val megapixels = (bitmap.width.toLong() * bitmap.height.toLong() / 1_000_000L).toInt()
            val params = buildList {
                add("${megapixels}MP")
                if (focal.isNotEmpty()) add(focal)
                if (fnum.isNotEmpty()) add(fnum)
            }.joinToString("  ")

            val paramPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(220, 30, 30, 30)
                textSize = badgeSize * 0.145f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(params, cx, cy + badgeSize * 0.72f, paramPaint)

            val modelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, 30, 30, 30)
                textSize = badgeSize * 0.125f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(Build.MANUFACTURER + " " + Build.MODEL, cx, cy + badgeSize * 0.88f, modelPaint)
        } catch (e: Exception) {
        }
    }

    private fun readFocalLength(source: File?): String {
        if (source == null) return ""
        return try {
            val exif = ExifInterface(source.absolutePath)
            val raw = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH) ?: return ""
            val parts = raw.split("/")
            if (parts.size == 2) {
                val numerator = parts[0].toDoubleOrNull() ?: return ""
                val denominator = parts[1].toDoubleOrNull() ?: return ""
                if (denominator != 0.0) {
                    val mm = numerator / denominator
                    return if (mm == mm.toLong().toDouble()) {
                        "${mm.toLong()}mm"
                    } else {
                        "%.1fmm".format(mm)
                    }
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun readFNumber(source: File?): String {
        if (source == null) return ""
        return try {
            val exif = ExifInterface(source.absolutePath)
            val raw = exif.getAttribute(ExifInterface.TAG_F_NUMBER) ?: return ""
            val parts = raw.split("/")
            if (parts.size == 2) {
                val numerator = parts[0].toDoubleOrNull() ?: return ""
                val denominator = parts[1].toDoubleOrNull() ?: return ""
                if (denominator != 0.0) {
                    val f = numerator / denominator
                    return "f/%.1f".format(f)
                }
            }
            ""
        } catch (e: Exception) {
            ""
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
        // 解码长边上限：保留更多原始像素，接近系统相机清晰度
        const val MAX_DECODE_EDGE = 8192
        const val JPEG_QUALITY = 98
    }
}