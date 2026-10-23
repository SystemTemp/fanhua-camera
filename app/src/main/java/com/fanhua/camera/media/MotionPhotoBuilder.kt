package com.fanhua.camera.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

object MotionPhotoBuilder {

    private fun template(offset: Long, length: Long): String {
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            append("<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n")
            append("<rdf:Description rdf:about=\"\"\n")
            append("xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\"\n")
            append("xmlns:GCameraX=\"http://ns.google.com/photos/1.0/camera/xmp/\"\n")
            append("GCamera:MotionPhoto=\"1\"\n")
            append("GCameraX:Video=\"1\"\n")
            append("GCamera:MicroVideoOffset=\"").append(offset).append("\"\n")
            append("GCamera:MicroVideoPresentationTimestampUs=\"").append(length).append("\"\n")
            append("/>\n")
            append("</rdf:RDF>\n")
        }
    }

    fun build(context: Context, jpeg: File, video: Uri): Uri? {
        val videoBytes = readVideoBytes(context, video) ?: return null
        val videoLength = videoLengthUs(context, video)
        if (videoLength <= 0L) return null
        val prefix = template(0L, videoLength).toByteArray(Charsets.UTF_8)
        val jpegBytes = jpeg.readBytes()
        if (jpegBytes.size <= prefix.size) return null
        if (jpegBytes[0] != 0xFF.toByte() || jpegBytes[1] != 0xD8.toByte()) return null
        val combined = ByteArray(prefix.size + jpegBytes.size + videoBytes.size)
        prefix.copyInto(combined, 0)
        jpegBytes.copyInto(combined, prefix.size)
        videoBytes.copyInto(combined, prefix.size + jpegBytes.size)
        return save(context, combined)
    }

    private fun readVideoBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    private fun videoLengthUs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            (raw?.toLongOrNull() ?: 0L) * 1000L
        } catch (e: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
            }
        }
    }

    private fun save(context: Context, bytes: ByteArray): Uri? {
        val name = "FH_" + System.currentTimeMillis() + "_live.jpg"
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
                output.write(bytes)
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