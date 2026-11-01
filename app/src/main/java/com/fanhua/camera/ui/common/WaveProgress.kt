package com.fanhua.camera.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Google 风格波浪进度条。
 *
 * 视觉参照 Material 3 / Google 系应用的「不确定进度」表现：
 * 一条正弦波在轨道内水平流动，波峰随时间起伏，已确定进度时按进度裁剪。
 *
 * @param progress 0..1；传 null 表示不确定进度（无限循环流动）
 * @param amplitude 波浪振幅（相对高度的比例）
 * @param wavelength 波长（相对宽度的比例，越小波越密）
 */
@Composable
fun WaveProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color(0x1FFFFFFF),
    amplitude: Float = 0.45f,
    wavelength: Float = 0.28f,
    animated: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "waveProgress")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveBreathe"
    )
    val p = if (animated) phase else 0f
    val amp = if (animated) amplitude * breathe else amplitude

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        val ampPx = h * amp * 0.5f
        val waveLen = w * wavelength.coerceIn(0.08f, 0.6f)

        // 轨道
        drawLine(
            color = trackColor,
            start = Offset(0f, mid),
            end = Offset(w, mid),
            strokeWidth = h * 0.55f,
            cap = StrokeCap.Round
        )

        // 波浪路径
        fun wavePath(from: Float, to: Float, offsetPhase: Float): Path {
            val path = Path()
            var first = true
            var x = from
            val step = (w / 220f).coerceAtLeast(1.5f)
            while (x <= to) {
                val t = (x / waveLen) * 2f * PI.toFloat() - offsetPhase * 2f * PI.toFloat()
                val y = mid + sin(t) * ampPx
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                x += step
            }
            return path
        }

        val endX = if (progress == null) w else (w * progress.coerceIn(0f, 1f))
        if (endX <= 0f) return@Canvas

        clipRect(right = endX) {
            // 主波浪
            drawPath(
                path = wavePath(-w * 0.1f, w * 1.1f, p),
                color = color,
                style = Stroke(width = h * 0.55f, cap = StrokeCap.Round)
            )
            // 次级波浪：相位偏移，降低透明度，营造层叠流动感
            drawPath(
                path = wavePath(-w * 0.1f, w * 1.1f, (p + 0.5f) % 1f),
                color = color.copy(alpha = 0.45f),
                style = Stroke(width = h * 0.40f, cap = StrokeCap.Round)
            )
            // 进度前端的高光点
            if (progress != null && progress < 1f) {
                drawCircle(
                    color = color,
                    radius = h * 0.42f,
                    center = Offset(endX, mid)
                )
            }
        }
    }
}

/**
 * 不确定进度的波浪条（无限流动），用于「加载中」状态。
 */
@Composable
fun IndeterminateWaveBar(
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    WaveProgressBar(
        progress = null,
        modifier = modifier,
        height = height,
        color = color
    )
}

/**
 * 带渐变填充的波浪进度条，用于下载等需要强调的进度。
 */
@Composable
fun GradientWaveProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    colors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary
    )
) {
    val brush = Brush.horizontalGradient(colors)
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val w = size.width
            val h = size.height
            drawLine(
                color = Color(0x1FFFFFFF),
                start = Offset(0f, h / 2f),
                end = Offset(w, h / 2f),
                strokeWidth = h * 0.6f,
                cap = StrokeCap.Round
            )
            val endX = w * progress.coerceIn(0f, 1f)
            if (endX > 0f) {
                drawLine(
                    brush = brush,
                    start = Offset(0f, h / 2f),
                    end = Offset(endX, h / 2f),
                    strokeWidth = h * 0.6f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
