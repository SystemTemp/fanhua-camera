package com.fanhua.camera.camera.analyzer

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.fanhua.camera.camera.core.FaceBox
import com.fanhua.camera.camera.detect.Detection
import com.fanhua.camera.camera.detect.ObjectDetector
import com.fanhua.camera.debug.DebugLog
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 取景分析器：先用 TFLite 做目标检测，可选再用 ML Kit 补充人脸框。
 *
 * 为了控制功耗，采用「跳帧 + 单飞」策略：
 * - 两次分析之间至少间隔 [intervalMs]；
 * - 上一次推理没结束就丢弃新帧（[busy] 标志）；
 * - 输入分辨率限制在 [MAX_ANALYSIS_EDGE]，避免 4K 帧转换耗时。
 */
class SubjectAnalyzer(
    private val detectorProvider: () -> ObjectDetector?,
    private val useFaceDetection: Boolean,
    private val onResult: (detections: List<Detection>, faces: List<FaceBox>) -> Unit
) : ImageAnalysis.Analyzer {

    private var faceDetector: FaceDetector? = null
    private val busy = AtomicBoolean(false)
    private var lastTimestamp = 0L

    var intervalMs: Long = 220L

    private fun faceDetector(): FaceDetector {
        val existing = faceDetector
        if (existing != null) return existing
        val created = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .enableTracking()
                .setMinFaceSize(0.1f)
                .build()
        )
        faceDetector = created
        return created
    }

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastTimestamp < intervalMs || !busy.compareAndSet(false, true)) {
            image.close()
            return
        }
        lastTimestamp = now

        val detector = detectorProvider()
        if (detector == null || !detector.ready) {
            busy.set(false)
            image.close()
            return
        }

        val bitmap = try {
            image.toBitmapSafe()
        } catch (e: Exception) {
            DebugLog.log("Analyzer", "帧转换失败: ${e.message}")
            null
        }
        if (bitmap == null) {
            busy.set(false)
            image.close()
            return
        }

        try {
            val detections = detector.detect(bitmap)
            if (!useFaceDetection) {
                onResult(detections, emptyList())
                busy.set(false)
                bitmap.recycle()
                return
            }
            // 补充人脸：ML Kit 需要 InputImage，用同一帧的位图
            val input = InputImage.fromBitmap(bitmap, image.imageInfo.rotationDegrees)
            faceDetector().process(input)
                .addOnSuccessListener { faces ->
                    val width = bitmap.width
                    val height = bitmap.height
                    val boxes = if (width > 0 && height > 0) {
                        faces.map { face ->
                            val box = face.boundingBox
                            FaceBox(
                                rect = android.graphics.RectF(
                                    box.left.toFloat() / width,
                                    box.top.toFloat() / height,
                                    box.right.toFloat() / width,
                                    box.bottom.toFloat() / height
                                ),
                                trackingId = face.trackingId ?: -1
                            )
                        }
                    } else {
                        emptyList()
                    }
                    onResult(detections, boxes)
                }
                .addOnFailureListener {
                    onResult(detections, emptyList())
                }
                .addOnCompleteListener {
                    busy.set(false)
                    bitmap.recycle()
                }
        } catch (e: Exception) {
            DebugLog.log("Analyzer", "分析失败: ${e.javaClass.simpleName}: ${e.message}")
            busy.set(false)
            bitmap.recycle()
        }
    }

    /** 把 ImageProxy 转成旋转校正后的位图，并把长边限制在 MAX_ANALYSIS_EDGE 内。 */
    private fun ImageProxy.toBitmapSafe(): Bitmap? {
        val mediaImage = image ?: return null
        val rotation = imageInfo.rotationDegrees
        val srcWidth = mediaImage.width
        val srcHeight = mediaImage.height
        if (srcWidth <= 0 || srcHeight <= 0) return null

        val planes = planes
        if (planes.isEmpty()) return null
        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        val pixelStride = yPlane.pixelStride
        val rowStride = yPlane.rowStride

        // YUV_420_888 的 Y 平面就是灰度信息，但我们需要彩色做检测，改用 RGB 转换
        // CameraX 的 ImageAnalysis 默认输出 YUV_420_888，这里用内置转换
        val nv21 = yuv420ToNv21(this)
        if (nv21 == null) {
            // 退化方案：用 Y 平面构造灰度图（检测精度略降但不会崩）
            return grayBitmap(yBuffer, rowStride, pixelStride, srcWidth, srcHeight, rotation)
        }
        return nv21ToBitmap(nv21, srcWidth, srcHeight, rotation)
    }

    private fun yuv420ToNv21(image: ImageProxy): ByteArray? {
        return try {
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]
            val width = image.width
            val height = image.height
            val ySize = width * height
            val uvSize = width * height / 4
            val nv21 = ByteArray(ySize + uvSize * 2)

            val yBuffer = yPlane.buffer
            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            var pos = 0
            for (row in 0 until height) {
                var col = 0
                while (col < width) {
                    val index = row * yRowStride + col * yPixelStride
                    if (index < yBuffer.limit()) {
                        nv21[pos++] = yBuffer.get(index)
                    } else {
                        nv21[pos++] = 0
                    }
                    col++
                }
            }

            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride
            val uvHeight = height / 2
            val uvWidth = width / 2
            var uvPos = ySize
            for (row in 0 until uvHeight) {
                var col = 0
                while (col < uvWidth) {
                    val uIndex = row * uvRowStride + col * uvPixelStride
                    val vIndex = row * uvRowStride + col * uvPixelStride
                    val u = if (uIndex < uBuffer.limit()) uBuffer.get(uIndex) else 0
                    val v = if (vIndex < vBuffer.limit()) vBuffer.get(vIndex) else 0
                    if (uvPos + 1 < nv21.size) {
                        nv21[uvPos++] = v
                        nv21[uvPos++] = u
                    }
                    col++
                }
            }
            nv21
        } catch (e: Exception) {
            DebugLog.log("Analyzer", "YUV 转换失败: ${e.message}")
            null
        }
    }

    private fun nv21ToBitmap(
        nv21: ByteArray,
        width: Int,
        height: Int,
        rotation: Int
    ): Bitmap? {
        return try {
            val yuvImage = android.graphics.YuvImage(
                nv21, android.graphics.ImageFormat.NV21, width, height, null
            )
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 85, out)
            val bytes = out.toByteArray()
            val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return null
            val scaled = scaleIfNeeded(decoded)
            if (rotation == 0) scaled else rotate(scaled, rotation)
        } catch (e: Exception) {
            DebugLog.log("Analyzer", "NV21 转位图失败: ${e.message}")
            null
        }
    }

    private fun grayBitmap(
        yBuffer: java.nio.ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        rotation: Int
    ): Bitmap? {
        return try {
            val pixels = IntArray(width * height)
            for (row in 0 until height) {
                for (col in 0 until width) {
                    val index = row * rowStride + col * pixelStride
                    val v = if (index < yBuffer.limit()) yBuffer.get(index).toInt() and 0xFF else 0
                    pixels[row * width + col] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            val scaled = scaleIfNeeded(bitmap)
            if (rotation == 0) scaled else rotate(scaled, rotation)
        } catch (e: Exception) {
            DebugLog.log("Analyzer", "灰度位图构造失败: ${e.message}")
            null
        }
    }

    private fun scaleIfNeeded(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= MAX_ANALYSIS_EDGE) return bitmap
        val ratio = MAX_ANALYSIS_EDGE.toFloat() / longEdge
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    fun release() {
        try {
            faceDetector?.close()
        } catch (e: Exception) {
            DebugLog.log("Analyzer", "关闭人脸检测器失败: ${e.message}")
        }
        faceDetector = null
    }

    private companion object {
        /** 分析用帧的长边上限，兼顾精度与速度。 */
        const val MAX_ANALYSIS_EDGE = 640
    }
}
