package com.fanhua.camera.camera.analyzer

import android.graphics.RectF
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CompositionHint
import com.fanhua.camera.camera.core.FaceBox
import kotlin.math.abs

object CompositionEngine {

    private val powerPoints = listOf(
        0.382f to 0.382f,
        0.618f to 0.382f,
        0.382f to 0.618f,
        0.618f to 0.618f
    )

    private const val ALIGN_THRESHOLD = 0.055f

    fun evaluate(faces: List<FaceBox>): CompositionHint? {
        if (faces.isEmpty()) return null
        val subject = faces.maxByOrNull { it.rect.width() * it.rect.height() } ?: return null
        val centerX = subject.rect.centerX()
        val centerY = subject.rect.centerY()

        var bestX = powerPoints[0].first
        var bestY = powerPoints[0].second
        var bestDistance = Float.MAX_VALUE
        for ((px, py) in powerPoints) {
            val distance = (px - centerX) * (px - centerX) + (py - centerY) * (py - centerY)
            if (distance < bestDistance) {
                bestDistance = distance
                bestX = px
                bestY = py
            }
        }

        val offsetX = bestX - centerX
        val offsetY = bestY - centerY
        val aligned = abs(offsetX) < ALIGN_THRESHOLD && abs(offsetY) < ALIGN_THRESHOLD

        val suggestion = when {
            aligned -> R.string.composition_aligned
            abs(offsetX) >= abs(offsetY) && offsetX < 0 -> R.string.composition_hint_left
            abs(offsetX) >= abs(offsetY) -> R.string.composition_hint_right
            offsetY < 0 -> R.string.composition_hint_up
            else -> R.string.composition_hint_down
        }

        val cropRect = buildCrop(subject.rect, bestX, bestY)

        return CompositionHint(
            faces = faces,
            offsetX = offsetX,
            offsetY = offsetY,
            aligned = aligned,
            suggestionRes = suggestion,
            cropRect = cropRect
        )
    }

    private fun buildCrop(subject: RectF, targetX: Float, targetY: Float): RectF? {
        val width = (subject.width() * 3.2f).coerceIn(0.35f, 1f)
        val height = (subject.height() * 3.2f).coerceIn(0.35f, 1f)
        val left = (subject.centerX() + (targetX - subject.centerX()) - width / 2f).coerceIn(0f, 1f - width)
        val top = (subject.centerY() + (targetY - subject.centerY()) - height / 2f).coerceIn(0f, 1f - height)
        if (width >= 0.99f && height >= 0.99f) return null
        return RectF(left, top, left + width, top + height)
    }
}