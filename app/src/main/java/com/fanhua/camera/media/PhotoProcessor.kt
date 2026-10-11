package com.fanhua.camera.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
            return save(processed, preset)
        } catch (e: Exception) {
            return null
        } finally {
            pipeline.release()
            eglCore?.release()
            bitmap?.recycle()
            processed?.recycle()
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