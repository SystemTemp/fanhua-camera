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
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.fanhua.camera.camera.filters.FilterPreset
import com.fanhua.camera.camera.gl.EglCore
import com.fanhua.camera.camera.gl.createGlFramePipeline
import java.io.File
import java.io.FileOutputStream

class PhotoProcessor(private val context: Context) {

    fun process(source: File, preset: FilterPreset, intensity: Float, upscale: Boolean): Uri? {
        if (preset == FilterPreset.ORIGINAL && !upscale) {
            return saveOriginal(source)
        }
        var bitmap: Bitmap? = null
        var processed: Bitmap? = null
        var eglCore: EglCore? = null
        val pipeline = createGlFramePipeline()
        try {
            bitmap = decode(source) ?: return null
            val rotation = readRotation(source)
            if (rotation != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotation.toFloat())
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated !== bitmap) {
                    bitmap.recycle()
                    bitmap = rotated
                }
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

            val core = EglCore()
            eglCore = core
            val surface = core.createOffscreenSurface(targetWidth, targetHeight)
            core.makeCurrent(surface)
            val textureId = pipeline.createTextureFromBitmap(sourceBitmap)
            pipeline.setPreset(preset, intensity)
            pipeline.setSuperResolutionEnabled(upscale && targetWidth > sourceBitmap.width)
            pipeline.drawBitmapFrame(textureId, sourceBitmap.width, sourceBitmap.height, targetWidth, targetHeight)
            val pixels = IntArray(targetWidth * targetHeight)
            val buffer = java.nio.IntBuffer.wrap(pixels)
            GLES20.glReadPixels(0, 0, targetWidth, targetHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
            core.destroySurface(surface)
            pipeline.deleteTexture(textureId)

            processed = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            buffer.rewind()
            processed.setPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
            if (preset == FilterPreset.LEICA_MOMENT) {
                drawLeicaWatermark(processed, source)
            }
            return save(processed, preset)
        } catch (e: Exception) {
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
            val barHeight = height * 0.062f
            val barTop = height - barHeight
            val background = Paint().apply {
                color = Color.argb(240, 255, 255, 255)
            }
            canvas.drawRect(0f, barTop, width, height, background)

            val margin = width * 0.036f
            val dotRadius = barHeight * 0.20f
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(200, 16, 46)
            }
            val centerY = barTop + barHeight / 2f
            canvas.drawCircle(margin + dotRadius, centerY, dotRadius, dotPaint)

            val leicaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = barHeight * 0.40f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                letterSpacing = 0.07f
            }
            canvas.drawText("LEICA", margin + dotRadius * 2.9f, barTop + barHeight * 0.69f, leicaPaint)

            val focal = readFocalLength(source)
            val fnum = readFNumber(source)
            val megapixels = (bitmap.width.toLong() * bitmap.height.toLong() / 1_000_000L).toInt()
            val params = buildList {
                add("${megapixels}MP")
                if (focal.isNotEmpty()) add(focal)
                if (fnum.isNotEmpty()) add(fnum)
            }.joinToString("  ")

            val modelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = barHeight * 0.24f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val paramPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(220, 30, 30, 30)
                textSize = barHeight * 0.21f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            val right = width - margin
            val deviceModel = Build.MANUFACTURER + " " + Build.MODEL
            canvas.drawText(deviceModel, right, barTop + barHeight * 0.36f, modelPaint)
            canvas.drawText(params, right, barTop + barHeight * 0.66f, paramPaint)
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
        var sample = 1
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (longEdge / sample > 4096) {
            sample *= 2
        }
        val options = BitmapFactory.Options()
        options.inSampleSize = sample
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        return BitmapFactory.decodeFile(source.absolutePath, options)
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
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
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
}