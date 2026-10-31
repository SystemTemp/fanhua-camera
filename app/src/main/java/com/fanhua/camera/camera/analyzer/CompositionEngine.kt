package com.fanhua.camera.camera.analyzer

import android.graphics.RectF
import com.fanhua.camera.camera.detect.NormRect
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CompositionHint
import com.fanhua.camera.camera.core.FaceBox
import com.fanhua.camera.camera.detect.Detection
import com.fanhua.camera.camera.detect.SubjectKind
import kotlin.math.abs

/**
 * 构图评估引擎。
 *
 * 输入：检测到的主体列表（来自 TFLite 目标检测，或 ML Kit 人脸检测）。
 * 输出：构图提示（偏移方向、是否对齐、裁切建议、人脸框）。
 *
 * 规则：
 * - 人像 / 宠物这类主体遵循三分法（放在三分线交点上更自然）；
 * - 车辆 / 静物等主体居中更稳妥，只在明显偏移时才提示；
 * - 多主体时优先人物，其次取面积最大者。
 */
object CompositionEngine {

    private val powerPoints = listOf(
        0.382f to 0.382f,
        0.618f to 0.382f,
        0.382f to 0.618f,
        0.618f to 0.618f
    )

    private const val ALIGN_THRESHOLD = 0.055f

    /** 兼容旧调用：只用 ML Kit 人脸框评估。 */
    fun evaluate(faces: List<FaceBox>): CompositionHint? {
        if (faces.isEmpty()) return null
        val detections = faces.map { face ->
            Detection(
                kind = SubjectKind.PERSON,
                label = "person",
                confidence = 1f,
                rect = NormRect.from(face.rect)
            )
        }
        return evaluateDetections(detections, faces)
    }

    /**
     * 用目标检测结果评估构图。
     *
     * @param detections 检测到的主体（归一化坐标）
     * @param faces 可选的人脸框，用于在画面上额外标注
     */
    fun evaluateDetections(
        detections: List<Detection>,
        faces: List<FaceBox> = emptyList()
    ): CompositionHint? {
        if (detections.isEmpty()) return null

        // 主体选择：优先人物，其次按面积
        val person = detections.filter { it.kind == SubjectKind.PERSON }
            .maxByOrNull { it.area }
        val subject = person ?: detections.maxByOrNull { it.area } ?: return null

        val centerX = subject.centerX
        val centerY = subject.centerY

        // 根据主体类型选择构图目标点
        val target = if (subject.kind.prefersRuleOfThirds) {
            nearestPowerPoint(centerX, centerY)
        } else {
            // 非人像主体：优先居中，若离中心已足够近则不再提示
            val distToCenter = (0.5f - centerX) * (0.5f - centerX) +
                (0.5f - centerY) * (0.5f - centerY)
            val distToThird = nearestPowerPointDistance(centerX, centerY)
            if (distToCenter <= distToThird) 0.5f to 0.5f else nearestPowerPoint(centerX, centerY)
        }

        val offsetX = target.first - centerX
        val offsetY = target.second - centerY
        val aligned = abs(offsetX) < ALIGN_THRESHOLD && abs(offsetY) < ALIGN_THRESHOLD

        val suggestion = when {
            aligned -> R.string.composition_aligned
            abs(offsetX) >= abs(offsetY) && offsetX < 0 -> R.string.composition_hint_left
            abs(offsetX) >= abs(offsetY) -> R.string.composition_hint_right
            offsetY < 0 -> R.string.composition_hint_up
            else -> R.string.composition_hint_down
        }

        val cropRect = buildCrop(subject.rect, target.first, target.second)

        return CompositionHint(
            faces = faces,
            offsetX = offsetX,
            offsetY = offsetY,
            aligned = aligned,
            suggestionRes = suggestion,
            cropRect = cropRect
        )
    }

    private fun nearestPowerPoint(x: Float, y: Float): Pair<Float, Float> {
        var bestX = powerPoints[0].first
        var bestY = powerPoints[0].second
        var bestDistance = Float.MAX_VALUE
        for ((px, py) in powerPoints) {
            val distance = (px - x) * (px - x) + (py - y) * (py - y)
            if (distance < bestDistance) {
                bestDistance = distance
                bestX = px
                bestY = py
            }
        }
        return bestX to bestY
    }

    private fun nearestPowerPointDistance(x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        for ((px, py) in powerPoints) {
            val d = (px - x) * (px - x) + (py - y) * (py - y)
            if (d < best) best = d
        }
        return best
    }

    private fun buildCrop(subject: NormRect, targetX: Float, targetY: Float): NormRect? {
        val width = (subject.width * 3.2f).coerceIn(0.35f, 1f)
        val height = (subject.height * 3.2f).coerceIn(0.35f, 1f)
        val left = (subject.centerX + (targetX - subject.centerX) - width / 2f)
            .coerceIn(0f, 1f - width)
        val top = (subject.centerY + (targetY - subject.centerY) - height / 2f)
            .coerceIn(0f, 1f - height)
        if (width >= 0.99f && height >= 0.99f) return null
        return NormRect(left, top, left + width, top + height)
    }
}
