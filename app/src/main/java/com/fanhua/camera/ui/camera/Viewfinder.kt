package com.fanhua.camera.ui.camera

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CameraController
import com.fanhua.camera.camera.core.CameraUiState
import com.fanhua.camera.camera.core.CompositionHint
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

@Composable
fun Viewfinder(
    controller: CameraController,
    state: CameraUiState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val focusProgress = remember { Animatable(0f) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    val zoomHint = remember { mutableStateOf<Float?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    controller.attachCameraXPreview(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.minZoomRatio, state.maxZoomRatio) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val target = (controller.state.value.zoomRatio * zoom)
                            .coerceIn(state.minZoomRatio, state.maxZoomRatio)
                        controller.setZoomRatio(target)
                        zoomHint.value = target
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val nx = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val ny = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                        controller.tapToFocus(nx, ny)
                        focusPoint = offset
                        scope.launch {
                            focusProgress.snapTo(0f)
                            focusProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 720, easing = LinearEasing)
                            )
                            focusPoint = null
                        }
                    }
                }
        )

        focusPoint?.let { point ->
            FocusReticle(center = point, progress = { focusProgress.value })
        }

        if (state.gridEnabled) {
            GridOverlay()
        }

        if (state.horizonEnabled) {
            val roll by controller.horizonRoll.collectAsState()
            HorizonLevel(roll = roll)
        }

        val hint = state.compositionHint
        if (state.autoCompositionEnabled && hint != null) {
            CompositionGuide(hint = hint)
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 130.dp)
        ) {
            ZoomHintOverlay(state = zoomHint)
        }
    }
}

@Composable
private fun FocusReticle(
    center: Offset,
    progress: () -> Float
) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                val p = progress()
                translationX = center.x - size.width / 2f
                translationY = center.y - size.height / 2f
                val s = 1.3f - 0.3f * p
                scaleX = s
                scaleY = s
                alpha = (1f - p) * 0.95f
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = 6f
            val arm = 18f
            val radius = 14f
            val stroke = Stroke(width = 3f)
            val color = Color(0xFFFFC53D)
            fun bracketPath(startX: Float, startY: Float, horizontalDir: Float, verticalDir: Float): Path {
                return Path().apply {
                    moveTo(startX + horizontalDir * (arm - radius), startY)
                    lineTo(startX + horizontalDir * radius, startY)
                    quadraticTo(startX, startY, startX, startY + verticalDir * radius)
                    lineTo(startX, startY + verticalDir * (arm - radius))
                }
            }
            val left = inset
            val top = inset
            val right = size.width - inset
            val bottom = size.height - inset
            drawPath(bracketPath(left, top, 1f, 1f), color, style = stroke)
            drawPath(bracketPath(right, top, -1f, 1f), color, style = stroke)
            drawPath(bracketPath(left, bottom, 1f, -1f), color, style = stroke)
            drawPath(bracketPath(right, bottom, -1f, -1f), color, style = stroke)
            drawCircle(
                color = Color(0x66FFC53D),
                radius = 2.5f,
                center = Offset(size.width / 2f, size.height / 2f)
            )
        }
    }
}

@Composable
private fun ZoomHintOverlay(state: MutableState<Float?>) {
    val value = state.value
    LaunchedEffect(value) {
        if (value != null) {
            delay(900)
            state.value = null
        }
    }
    AnimatedVisibility(visible = value != null) {
        Text(
            text = stringResource(R.string.zoom_label) + " " +
                String.format(Locale.US, "%.1fx", value ?: 1f),
            color = FanhuaText,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x66000000))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun GridOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val color = Color(0x2EFFFFFF)
        for (i in 1..2) {
            val x = size.width * i / 3f
            val y = size.height * i / 3f
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
    }
}

@Composable
private fun HorizonLevel(roll: Float) {
    val aligned = abs(roll) <= 0.6f
    val color = if (aligned) FanhuaGold else Color(0xCCFFFFFF)
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val half = min(size.width, size.height) * 0.34f
            val gap = 22f
            rotate(degrees = roll, pivot = Offset(cx, cy)) {
                drawLine(color, Offset(cx - half, cy), Offset(cx - gap, cy), strokeWidth = 2.5f)
                drawLine(color, Offset(cx + gap, cy), Offset(cx + half, cy), strokeWidth = 2.5f)
                drawLine(color, Offset(cx, cy - 9f), Offset(cx, cy + 9f), strokeWidth = 2.5f)
                for (tick in 1..4) {
                    val dx = half * tick / 4f
                    val h = 5f + tick * 2.5f
                    drawLine(color, Offset(cx - dx, cy - h), Offset(cx - dx, cy + h), strokeWidth = 1.5f)
                    drawLine(color, Offset(cx + dx, cy - h), Offset(cx + dx, cy + h), strokeWidth = 1.5f)
                }
            }
        }
        Text(
            text = String.format(Locale.US, "%+.1f", roll),
            color = if (aligned) FanhuaGold else FanhuaText,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x59000000))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CompositionGuide(hint: CompositionHint) {
    val gridColor = Color(0x24FFFFFF)
    val guideColor = if (hint.aligned) FanhuaGold else Color(0xCCFFFFFF)
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            for (i in 1..2) {
                val x = width * i / 3f
                val y = height * i / 3f
                drawLine(gridColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1f)
                drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
            }
            hint.faces.forEach { face ->
                val rect = Rect(
                    left = face.rect.left * width,
                    top = face.rect.top * height,
                    right = face.rect.right * width,
                    bottom = face.rect.bottom * height
                )
                drawRoundRect(
                    color = guideColor,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    cornerRadius = CornerRadius(22f),
                    style = Stroke(width = 2.5f)
                )
            }
        }
        Text(
            text = stringResource(
                if (hint.aligned) R.string.composition_aligned else hint.suggestionRes
            ),
            color = if (hint.aligned) FanhuaGold else FanhuaText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x59000000))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}
