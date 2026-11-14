package com.fanhua.camera.ui.camera

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CameraController
import com.fanhua.camera.camera.core.CameraUiState
import com.fanhua.camera.camera.core.CompositionHint
import com.fanhua.camera.camera.core.PhotoAspect
import com.fanhua.camera.ui.theme.FanhuaSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * 取景器（Google Material 3 风格）。
 *
 * 布局：预览铺满 → 画幅遮罩 → 手势层 → 对焦框 / 网格 / 水平仪 / 构图引导 → 变焦提示。
 *
 * 配色规范：
 * - 交互强调（对焦框、水平仪、构图括号、裁切遮罩）统一用 [MaterialTheme.colorScheme.primary]；
 * - 水平仪对齐时切换为 [FanhuaSecondary]（绿）表示「已水平」；
 * - 辅助线用白色 12% 透明度、1dp，尽量不干扰取景。
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
    val accent = MaterialTheme.colorScheme.primary

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

        // 自动构图裁切遮罩：压暗裁切框以外的区域
        state.autoCropRect?.let { crop ->
            AutoCropMask(crop = crop, accent = accent)
        }

        // 画幅遮罩：把超出所选画幅的部分压暗
        if (state.photoAspect != PhotoAspect.FULL) {
            AspectMask(
                aspect = state.photoAspect,
                accent = accent,
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
            // 先画扩散涟漪，再画四角括号，形成 Material 3 的对焦反馈层次
            FocusRipple(center = point, accent = accent)
            FocusReticle(
                center = point,
                progress = { focusProgress.value },
                accent = accent
            )
        }

        if (state.gridEnabled) {
            GridOverlay()
        }

        if (state.horizonEnabled) {
            val roll by controller.horizonRoll.collectAsState()
            HorizonLevel(roll = roll, accent = accent)
        }

        val hint = state.compositionHint
        if (state.autoCompositionEnabled && hint != null) {
            CompositionGuide(
                hint = hint,
                subjectLabel = state.subjectLabel,
                accent = accent
            )
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
private fun AspectMask(aspect: PhotoAspect, accent: Color, mirror: Boolean) {
    val ratio = aspect.ratio
    // FULL 不裁切，但仍按 4:3 画取景边框提示实际构图范围
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
            val scrim = Color.Black.copy(alpha = 0.78f)
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
            // 取景框描边：Material 3 风格，白色低透明度细线，不抢画面
            drawRoundRect(
                color = Color.White.copy(alpha = 0.32f),
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                cornerRadius = CornerRadius(2f),
                style = Stroke(width = 1f)
            )
        }
        if (mirror) {
            // 遮罩本身对称，无需翻转
        }
    }
}

/**
 * 对焦框：Material 3 风格的圆形涟漪 + primary 色四角括号。
 *
 * 涟漪环从中心向外扩散并淡出，括号随动画轻微收缩，给到明确的「已对焦此处」反馈。
 */
@Composable
private fun FocusReticle(
    center: Offset,
    progress: () -> Float,
    accent: Color
) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                val p = progress()
                translationX = center.x - size.width / 2f
                translationY = center.y - size.height / 2f
                val s = 1.25f - 0.25f * p
                scaleX = s
                scaleY = s
                alpha = (1f - p) * 0.95f
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = 8f
            val arm = 18f
            val radius = 14f
            val stroke = Stroke(width = 2.5f)
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
            drawPath(bracketPath(left, top, 1f, 1f), accent, style = stroke)
            drawPath(bracketPath(right, top, -1f, 1f), accent, style = stroke)
            drawPath(bracketPath(left, bottom, 1f, -1f), accent, style = stroke)
            drawPath(bracketPath(right, bottom, -1f, -1f), accent, style = stroke)
            // 中心圆点
            drawCircle(
                color = accent.copy(alpha = 0.6f),
                radius = 3f,
                center = Offset(size.width / 2f, size.height / 2f)
            )
        }
    }
}

/** 对焦完成后的圆形涟漪（Material 3 语义）：一圈 primary 环向外扩散淡出。 */
@Composable
private fun FocusRipple(center: Offset, accent: Color) {
    val ripple = remember { Animatable(0f) }
    LaunchedEffect(center) {
        ripple.snapTo(0f)
        ripple.animateTo(1f, tween(durationMillis = 620, easing = LinearEasing))
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer {
                translationX = center.x - size.width / 2f
                translationY = center.y - size.height / 2f
                val p = ripple.value
                scaleX = 0.4f + 0.8f * p
                scaleY = 0.4f + 0.8f * p
                alpha = (1f - p) * 0.6f
            }
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.18f))
    )
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
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/** 网格线：白色 12% 透明度，1dp，Material 3 的克制辅助线。 */
@Composable
private fun GridOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val color = Color.White.copy(alpha = 0.12f)
        for (i in 1..2) {
            val x = size.width * i / 3f
            val y = size.height * i / 3f
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
    }
}

/**
 * 水平仪：未对齐时 primary，对齐后切到 [FanhuaSecondary] 绿。
 *
 * 对齐阈值 0.6 度，超过阈值仍显示 primary 以示「还需调整」。
 */
@Composable
private fun HorizonLevel(roll: Float, accent: Color) {
    val scheme = MaterialTheme.colorScheme
    val aligned = abs(roll) <= 0.6f
    val color = if (aligned) FanhuaSecondary else accent
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val half = min(size.width, size.height) * 0.32f
            val gap = 24f
            rotate(degrees = roll, pivot = Offset(cx, cy)) {
                drawLine(color, Offset(cx - half, cy), Offset(cx - gap, cy), strokeWidth = 2.5f)
                drawLine(color, Offset(cx + gap, cy), Offset(cx + half, cy), strokeWidth = 2.5f)
                drawLine(color, Offset(cx, cy - 10f), Offset(cx, cy + 10f), strokeWidth = 2.5f)
                for (tick in 1..4) {
                    val dx = half * tick / 4f
                    val h = 5f + tick * 2.5f
                    drawLine(
                        color.copy(alpha = 0.55f),
                        Offset(cx - dx, cy - h),
                        Offset(cx - dx, cy + h),
                        strokeWidth = 1.5f
                    )
                    drawLine(
                        color.copy(alpha = 0.55f),
                        Offset(cx + dx, cy - h),
                        Offset(cx + dx, cy + h),
                        strokeWidth = 1.5f
                    )
                }
            }
        }
        Text(
            text = String.format(Locale.US, "%+.1f", roll),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp)
                .clip(RoundedCornerShape(50))
                .background(scheme.surfaceContainer.copy(alpha = 0.88f))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

/**
 * 构图引导：primary 色括号框出人脸/主体，底部提示文案用 Material 3 胶囊承载。
 * 构图达标时整体切换为绿色（FanhuaSecondary）表示「构图良好」。
 */
@Composable
private fun CompositionGuide(
    hint: CompositionHint,
    subjectLabel: String?,
    accent: Color
) {
    val scheme = MaterialTheme.colorScheme
    val gridColor = Color.White.copy(alpha = 0.12f)
    val guideColor = if (hint.aligned) FanhuaSecondary else accent
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
                    color = if (hint.aligned) FanhuaSecondary else accent,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(scheme.surfaceContainer.copy(alpha = 0.88f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                text = stringResource(
                    if (hint.aligned) R.string.composition_aligned else hint.suggestionRes
                ),
                color = if (hint.aligned) FanhuaSecondary else scheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceContainer.copy(alpha = 0.88f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * 自动构图裁切遮罩。
 *
 * 把裁切框以外的区域压暗，框内保持原样，并在四角画出括号（primary 色呼吸动画），
 * 让用户一眼看出「点了自动构图之后会裁成什么样」。
 */
@Composable
private fun AutoCropMask(
    crop: com.fanhua.camera.camera.detect.NormRect,
    accent: Color
) {
    val transition = rememberInfiniteTransition(label = "autoCrop")
    val breathe by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cropBreathe"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val left = crop.left * w
        val top = crop.top * h
        val right = crop.right * w
        val bottom = crop.bottom * h
        val scrim = Color.Black.copy(alpha = 0.7f)
        // 上、下、左、右四块压暗
        drawRect(scrim, Offset(0f, 0f), Size(w, top))
        drawRect(scrim, Offset(0f, bottom), Size(w, h - bottom))
        drawRect(scrim, Offset(0f, top), Size(left, bottom - top))
        drawRect(scrim, Offset(right, top), Size(w - right, bottom - top))
        // 四角括号：primary 色，呼吸明暗提示「这是建议裁切范围」
        val arm = 28f
        val stroke = 3f
        val color = accent.copy(alpha = breathe)
        fun corner(cx: Float, cy: Float, dx: Float, dy: Float) {
            drawLine(color, Offset(cx, cy), Offset(cx + dx * arm, cy), strokeWidth = stroke)
            drawLine(color, Offset(cx, cy), Offset(cx, cy + dy * arm), strokeWidth = stroke)
        }
        corner(left, top, 1f, 1f)
        corner(right, top, -1f, 1f)
        corner(left, bottom, 1f, -1f)
        corner(right, bottom, -1f, -1f)
    }
}
