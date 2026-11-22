package com.fanhua.camera.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * 握持方向旋转：界面锁定竖屏后，横握手机时把文字内容绕自身中心旋转，
 * 外层占位按旋转后的包围盒交换宽高——布局不塌陷、不裁切。
 *
 * [deviceAngle] 来自 OrientationTracker：0 竖握 / 90 顶部朝右 /
 * 180 倒握 / 270 顶部朝左。映射到文字旋转角与 LeicaBadge 一致：
 * 90 → -90°，270 → +90°，180 → 180°。
 *
 * 用法：`Modifier.gripRotate(deviceAngle)` 套在 Text / Row 外层。
 */
fun Modifier.gripRotate(deviceAngle: Int): Modifier = composed {
    val degrees = when (deviceAngle) {
        90 -> -90f
        270 -> 90f
        180 -> 180f
        else -> 0f
    }
    if (degrees == 0f) {
        Modifier
    } else {
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(
                constraints.copy(minWidth = 0, minHeight = 0)
            )
            val swapped = degrees % 180f != 0f
            val w = if (swapped) placeable.height else placeable.width
            val h = if (swapped) placeable.width else placeable.height
            layout(w, h) {
                val dx = ((w - placeable.width) / 2f).roundToInt()
                val dy = ((h - placeable.height) / 2f).roundToInt()
                placeable.placeWithLayer(IntOffset(dx, dy)) {
                    rotationZ = degrees
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
            }
        }
    }
}
