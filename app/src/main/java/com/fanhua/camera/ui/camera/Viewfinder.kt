package com.fanhua.camera.ui.camera

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CameraController
import com.fanhua.camera.camera.core.CameraUiState
import com.fanhua.camera.camera.core.CompositionHint
import com.fanhua.camera.camera.core.PhotoAspect
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * 取景器。
 *
 * 布局：预览铺满 → 画幅遮罩 → 手势层 → 对焦/网格/水平仪/构图引导 → 变焦提示。
 * 画幅遮罩用「上下（或左右）压暗 + 白色描边」表现裁切范围，让取景与成片一致。
 */
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

        // 画幅遮罩：把超出所选画幅的部分压暗
        if (state.photoAspect != PhotoAspect.FULL) {
            AspectMask(
                aspect = state.photoAspect,
                mirror = state.frontCamera && state.mirrorSelfie
            )
        } else if (state.frontCamera && state.mirrorSelfie) {
            // 无遮罩时仍需镜像预览
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = -1f }
            )
        }

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
                    detectTapGestures(
                        onDoubleTap = { controller.switchCamera() },
                        onTap = { offset ->
                            val nx = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val ny = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                            controller.tapToFocus(nx, ny)
                            focusPoint = offset
                            scope.launch {
                                focusProgress.snapTo(0f)
                                focusProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = 720,
                                        easing = LinearEasing
                                    )
                                )
                                focusPoint = null
                            }
                        }
                    )
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
            CompositionGuide(hint = hint, subjectLabel = state.subjectLabel)
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 120.dp)
        ) {
            ZoomHintOverlay(state = zoomHint)
        }
    }
}

/** 画幅遮罩：在预览上压暗超出目标画幅的区域，并描出取景边框。 */
@Composable
private fun AspectMask(aspect: PhotoAspect, mirror: Boolean) {
    val ratio = aspect.ratio
    if (ratio <= 0f) return
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val screenRatio = w.value / h.value
        // 目标框：以屏幕为界内接目标比例
        val frameW: Float
        val frameH: Float
        if (screenRatio > ratio) {
            frameH = h.value
            frameW = frameH * ratio
        } else {
            frameW = w.value
            frameH = frameW / ratio
        }
        val padX = (w.value - frameW) / 2f
        val padY = (h.value - frameH) / 2f
        Canvas(modifier = Modifier.fillMaxSize()) {
            val wpx = size.width
            val hpx = size.height
            val scale = wpx / w.value
            val left = padX * scale
            val top = padY * scale
            val right = wpx - left
            val bottom = hpx - top
            val scrim = Color(0xC4000000)
            // 上下遮罩
            if (top > 1f) {
                drawRect(scrim, Offset(0f, 0f), Size(wpx, top))
                drawRect(scrim, Offset(0f, bottom), Size(wpx, hpx - bottom))
            }
            // 左右遮罩
            if (left > 1f) {
                drawRect(scrim, Offset(0f, top), Size(left, bottom - top))
                drawRect(scrim, Offset(right, top), Size(wpx - right, bottom - top))
            }
            // 取景框描边
            drawRoundRect(
                color = Color(0x55FFFFFF),
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                cornerRadius = CornerRadius(2f),
                style = Stroke(width = 1.2f)
            )
        }
        if (mirror) {
            // 遮罩本身对称，无需翻转
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
            .size(92.dp)
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
            val arm = 17f
            val radius = 13f
            val stroke = Stroke(width = 3f)
            val color = Color(0xFFFFC53D)
            fun bracketPath(
                startX: Float,
                startY: Float,
                horizontalDir: Float,
                verticalDir: Float
            ): Path {
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
    AnimatedVisibility(
        visible = value != null,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(200))
    ) {
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
            val half = min(size.width, size.height) * 0.32f
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
                .padding(top = 70.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x59000000))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CompositionGuide(hint: CompositionHint, subjectLabel: String?) {
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
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 116.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            subjectLabel?.let { label ->
                Text(
                    text = label,
                    color = FanhuaGold,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x77000000))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
                Spacer(modifier = Modifier.height(5.dp))
            }
            Text(
                text = stringResource(
                    if (hint.aligned) R.string.composition_aligned else hint.suggestionRes
                ),
                color = if (hint.aligned) FanhuaGold else FanhuaText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x59000000))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}
