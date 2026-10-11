package com.fanhua.camera.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CameraController
import com.fanhua.camera.camera.core.CameraUiState
import com.fanhua.camera.camera.core.CapabilityReport
import com.fanhua.camera.camera.core.CaptureMode
import com.fanhua.camera.camera.core.RecordingSpeed
import com.fanhua.camera.camera.filters.FilterPreset
import com.fanhua.camera.ui.common.FanhuaIcons
import com.fanhua.camera.ui.common.FrostedChip
import com.fanhua.camera.ui.common.FrostedPanel
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaOutline
import com.fanhua.camera.ui.theme.FanhuaText
import com.fanhua.camera.ui.theme.FanhuaTextDim
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@Composable
fun BottomControls(
    controller: CameraController,
    state: CameraUiState,
    capability: CapabilityReport,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier,
    landscape: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = if (landscape) 10.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(visible = !landscape) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.mode == CaptureMode.PRO) {
                    ProExtras(controller = controller, state = state)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                if (state.mode == CaptureMode.SUPER_RES) {
                    SuperResInfo(state = state, capability = capability)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
        AnimatedVisibility(visible = !landscape) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilterStrip(state = state, onSelect = controller::setPreset)
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        ModeCarousel(state = state, onSelect = controller::setMode)
        Spacer(modifier = Modifier.height(if (landscape) 10.dp else 18.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (landscape) 18.dp else 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LensSwitcher(state = state, onSelect = controller::setLens)
                    AnimatedVisibility(visible = state.mode == CaptureMode.PHOTO) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LivePhotoToggle(
                                enabled = state.livePhotoEnabled,
                                onClick = { controller.setLivePhotoEnabled(!state.livePhotoEnabled) }
                            )
                        }
                    }
                }
            }
            ShutterButton(state = state, controller = controller)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                GalleryButton(onClick = onOpenGallery)
            }
        }

        AnimatedVisibility(visible = !landscape) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(10.dp))
                ZoomSlider(controller = controller, state = state)
            }
        }
    }
}

@Composable
private fun FilterStrip(
    state: CameraUiState,
    onSelect: (FilterPreset) -> Unit
) {
    FrostedPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(FilterPreset.entries) { preset ->
                val selected = state.preset == preset
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onSelect(preset) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 66.dp, height = 44.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) Color(0x33E8C39E) else Color(0x14FFFFFF))
                            .border(
                                width = 1.dp,
                                color = if (selected) FanhuaGold else FanhuaOutline,
                                shape = RoundedCornerShape(9.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = FanhuaIcons.Aperture,
                            contentDescription = null,
                            tint = if (selected) FanhuaGold else FanhuaTextDim,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(preset.labelRes),
                        color = if (selected) FanhuaGold else FanhuaTextDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .width(if (selected) 20.dp else 0.dp)
                            .height(2.dp)
                            .background(FanhuaGold, RoundedCornerShape(1.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeCarousel(
    state: CameraUiState,
    onSelect: (CaptureMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CaptureMode.entries.forEach { mode ->
            val selected = state.mode == mode
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.14f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 520f),
                label = "modeScale"
            )
            val color by animateColorAsState(
                targetValue = if (selected) FanhuaGold else FanhuaTextDim,
                label = "modeColor"
            )
            val underline by animateDpAsState(
                targetValue = if (selected) 20.dp else 0.dp,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                label = "modeUnderline"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(mode.labelRes),
                    color = color,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(underline)
                        .height(2.dp)
                        .background(FanhuaGold, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

@Composable
private fun ShutterButton(
    state: CameraUiState,
    controller: CameraController
) {
    val haptic = LocalHapticFeedback.current
    Crossfade(
        targetState = (state.mode == CaptureMode.PHOTO || state.mode == CaptureMode.SUPER_RES) &&
            !state.recording,
        animationSpec = tween(durationMillis = 240),
        label = "shutterMode"
    ) { isPhoto ->
        if (isPhoto) {
            PhotoShutter(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    controller.takePhoto()
                }
            )
        } else {
            RecordButton(
                recording = state.recording,
                durationMs = state.recordingDurationMs,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (state.recording) controller.stopRecording() else controller.startRecording()
                }
            )
        }
    }
}

@Composable
private fun PhotoShutter(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f),
        label = "photoShutterScale"
    )
    val ripple = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .graphicsLayer {
                    val p = ripple.value
                    scaleX = 1f + 0.55f * p
                    scaleY = 1f + 0.55f * p
                    alpha = (1f - p) * 0.7f
                }
                .clip(CircleShape)
                .border(2.dp, FanhuaGold, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(66.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .border(2.5.dp, FanhuaGold, CircleShape)
                .padding(6.dp)
                .background(Color(0xFFF2EDE7), CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null
                ) {
                    scope.launch {
                        ripple.snapTo(0f)
                        ripple.animateTo(1f, tween(durationMillis = 520, easing = LinearEasing))
                        ripple.snapTo(0f)
                    }
                    onClick()
                }
        )
    }
}

@Composable
private fun RecordButton(
    recording: Boolean,
    durationMs: Long,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val corner by animateDpAsState(
        targetValue = if (recording) 27.dp else 8.dp,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "recordCorner"
    )
    val infinite = rememberInfiniteTransition(label = "recordPulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 880, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recordPulseValue"
    )
    var previousRecording by remember { mutableStateOf(recording) }
    val stopFlash = remember { Animatable(0f) }
    LaunchedEffect(recording) {
        if (previousRecording && !recording) {
            stopFlash.snapTo(1f)
            stopFlash.animateTo(0f, tween(durationMillis = 460))
        }
        previousRecording = recording
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .graphicsLayer {
                        val flash = stopFlash.value
                        val pulseScale = if (recording) pulse else 1f
                        val s = pulseScale + flash * 0.25f
                        scaleX = s
                        scaleY = s
                        alpha = if (recording) 0.9f else flash
                    }
                    .clip(CircleShape)
                    .border(2.5.dp, FanhuaGold, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(corner))
                    .background(Color(0xFFE57373))
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            )
        }
        Box(modifier = Modifier.height(18.dp), contentAlignment = Alignment.Center) {
            if (recording) {
                Text(
                    text = formatDuration(durationMs),
                    color = FanhuaGold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.graphicsLayer {
                        alpha = 0.55f + (pulse - 1f) / 0.14f * 0.45f
                    }
                )
            }
        }
    }
}

@Composable
private fun LensSwitcher(
    state: CameraUiState,
    onSelect: (String) -> Unit
) {
    if (state.lensOptions.isEmpty()) return
    FrostedPanel(
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            state.lensOptions.forEach { lens ->
                val selected = lens.id == state.activeLensId
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.9f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 520f),
                    label = "lensScale"
                )
                val background by animateColorAsState(
                    targetValue = if (selected) Color(0x33E8C39E) else Color.Transparent,
                    label = "lensBackground"
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(background)
                        .clickable { onSelect(lens.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lens.label,
                        color = if (selected) FanhuaGold else FanhuaTextDim,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun LivePhotoToggle(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        targetValue = if (enabled) FanhuaGold else FanhuaTextDim,
        animationSpec = tween(durationMillis = 220),
        label = "livePhotoColor"
    )
    FrostedPanel(
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Icon(
                imageVector = FanhuaIcons.LivePhoto,
                contentDescription = stringResource(R.string.live_photo),
                tint = color,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.live_photo),
                color = color,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun GalleryButton(onClick: () -> Unit) {
    FrostedPanel(
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(12.dp)
    ) {
        Icon(
            imageVector = FanhuaIcons.Gallery,
            contentDescription = stringResource(R.string.gallery_title),
            tint = FanhuaText,
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onClick)
        )
    }
}

@Composable
private fun ZoomSlider(
    controller: CameraController,
    state: CameraUiState
) {
    val range = state.maxZoomRatio - state.minZoomRatio
    if (range <= 0.01f) return
    val ticks = remember(state.lensOptions, state.minZoomRatio, state.maxZoomRatio) {
        val fromLenses = state.lensOptions
            .map { it.zoomRatio }
            .filter { it >= state.minZoomRatio && it <= state.maxZoomRatio }
        if (fromLenses.size >= 2) fromLenses.sorted()
        else listOf(state.minZoomRatio, (state.minZoomRatio + state.maxZoomRatio) / 2f, state.maxZoomRatio)
    }
    fun fraction(ratio: Float): Float = ((ratio - state.minZoomRatio) / range).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.zoom_label) + "  " + formatZoom(state.zoomRatio),
            color = FanhuaTextDim,
            style = MaterialTheme.typography.labelSmall
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(state.minZoomRatio, state.maxZoomRatio) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val current = controller.state.value.zoomRatio
                            val nearest = ticks.minByOrNull { abs(it - current) }
                            if (nearest != null) controller.setZoomRatio(nearest)
                        }
                    ) { change, _ ->
                        val x = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        controller.setZoomRatio(state.minZoomRatio + x * range)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val y = size.height / 2f
                drawLine(
                    color = Color(0x4DFFFFFF),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                ticks.forEach { tick ->
                    val x = fraction(tick) * size.width
                    drawLine(
                        color = Color(0x66F2EDE7),
                        start = Offset(x, y - 8f),
                        end = Offset(x, y + 8f),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round
                    )
                }
                val cx = fraction(state.zoomRatio) * size.width
                drawCircle(color = FanhuaGold, radius = 9f, center = Offset(cx, y))
                drawCircle(color = Color(0xFF14161A), radius = 5.5f, center = Offset(cx, y))
            }
        }
    }
}

@Composable
private fun ProExtras(
    controller: CameraController,
    state: CameraUiState
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RecordingSpeed.entries.forEach { speed ->
                val selected = state.recordingSpeed == speed
                FrostedChip(
                    selected = selected,
                    modifier = Modifier.clickable { controller.setRecordingSpeed(speed) }
                ) {
                    Text(
                        text = stringResource(speed.labelRes),
                        color = if (selected) FanhuaGold else FanhuaTextDim,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        if (state.availableSpecs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                items(state.availableSpecs) { spec ->
                    val selected = state.selectedSpec == spec
                    FrostedChip(
                        selected = selected,
                        modifier = Modifier.clickable { controller.selectSpec(spec) }
                    ) {
                        Text(
                            text = spec.label,
                            color = if (selected) FanhuaGold else FanhuaTextDim,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.pro_manual_focus),
                color = FanhuaTextDim,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.width(12.dp))
            Slider(
                value = state.manualFocusPosition,
                onValueChange = controller::setManualFocus,
                modifier = Modifier.width(180.dp)
            )
        }
    }
}

@Composable
private fun SuperResInfo(
    state: CameraUiState,
    capability: CapabilityReport
) {
    val value = state.selectedSpec?.label
        ?: if (capability.superResTargetWidth > 0) {
            "${capability.superResTargetWidth}x${capability.superResTargetHeight}"
        } else {
            stringResource(R.string.value_none)
        }
    FrostedPanel(
        modifier = Modifier.padding(horizontal = 24.dp),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = stringResource(R.string.capability_superres),
            color = FanhuaTextDim,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = FanhuaGold,
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_superres_desc),
            color = FanhuaTextDim,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun formatZoom(ratio: Float): String = String.format(Locale.US, "%.1fx", ratio)

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}