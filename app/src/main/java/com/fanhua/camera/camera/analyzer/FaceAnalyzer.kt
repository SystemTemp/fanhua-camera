package com.fanhua.camera.camera.analyzer

import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.fanhua.camera.camera.core.FaceBox
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceAnalyzer(
    private val onFaces: (List<FaceBox>, Int, Int) -> Unit
) : ImageAnalysis.Analyzer {

    private var detector: FaceDetector? = null
    private var lastTimestamp = 0L
    var intervalMs: Long = 250L

    private fun detector(): FaceDetector {
        val existing = detector
        if (existing != null) return existing
        val created = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .enableTracking()
                .setMinFaceSize(0.1f)
                .build()
        )
        detector = created
        return created
    }

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastTimestamp < intervalMs) {
            image.close()
            return
        }
        lastTimestamp = now
        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
        detector().process(input)
            .addOnSuccessListener { faces ->
                val width = input.width
                val height = input.height
                if (width <= 0 || height <= 0) {
                    onFaces(emptyList(), 0, 0)
                    return@addOnSuccessListener
                }
                val boxes = faces.map { face ->
                    val box = face.boundingBox
                    FaceBox(
                        rect = RectF(
                            box.left.toFloat() / width,
                            box.top.toFloat() / height,
                            box.right.toFloat() / width,
                            box.bottom.toFloat() / height
                        ),
                        trackingId = face.trackingId ?: -1
                    )
                }
                onFaces(boxes, width, height)
            }
            .addOnFailureListener { onFaces(emptyList(), 0, 0) }
            .addOnCompleteListener { image.close() }
    }

    fun release() {
        detector?.close()
        detector = null
    }
}