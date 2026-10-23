package com.fanhua.camera.ui.camera

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CameraController
import com.fanhua.camera.camera.core.CameraUiState
import com.fanhua.camera.camera.core.CapabilityReport
import com.fanhua.camera.camera.core.CaptureMode
import com.fanhua.camera.camera.core.OutputSpec
import com.fanhua.camera.camera.core.RecordingSpeed
import com.fanhua.camera.camera.filters.FilterGrade
import com.fanhua.camera.camera.filters.FilterPreset
import com.fanhua.camera.ui.common.FanhuaIcons
import com.fanhua.camera.ui.common.FrostedChip
import com.fanhua.camera.ui.common.FrostedPanel
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaGoldDeep
import com.fanhua.camera.ui.theme.FanhuaStroke
import com.fanhua.camera.ui.theme.FanhuaText
import com.fanhua.camera.ui.theme.FanhuaTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            .then(if (landscape) Modifier.fillMaxHeight() else Modifier)
            .navigationBarsPadding()
            .padding(bottom = if (landscape) 0.dp else 14.dp, end = if (landscape) 12.dp else 0.dp),
        horizontalAlignment = if (landscape) Alignment.End else Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(visible = !landscape) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.mode == CaptureMode.PRO) {
                    ProExtras(controller = controller, state = state)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                if (state.mode == CaptureMode.VIDEO && state.availableSpecs.isNotEmpty()) {
                    VideoSpecBar(state = state, onSelect = controller::selectSpec)
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
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        if (landscape) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                ModeSelector(state = state, onSelect = controller::setMode, compact = true)
                Spacer(modifier = Modifier.height(16.dp))
                ShutterButton(state = state, controller = controller)
                Spacer(modifier = Modifier.height(14.dp))
                GalleryButton(captureCount = state.captureCount, size = 50, onClick = onOpenGallery)
            }
        } else {
            ModeSelector(state = state, onSelect = controller::setMode)
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
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
                    GalleryButton(captureCount = state.captureCount, size = 50, onClick = onOpenGallery)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            ZoomBar(controller = controller, state = state)
        }
    }
}

@Composable
private fun FilterStrip(
    state: CameraUiState,
    onSelect: (FilterPreset) -> Unit
) {
    val leicaGroup = listOf(
        FilterPreset.LEICA_MOMENT,
        FilterPreset.LEICA_MONO,
        FilterPreset.LEICA_PORTRAIT
    )
    val singles = FilterPreset.entries.filter { it !in leicaGroup }
    var leicaExpanded by rememberSaveable { mutableStateOf(state.preset in leicaGroup) }
    val activeLeica = state.preset in leicaGroup

    FrostedPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        cornerRadius = 26.dp,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Column {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                items(singles) { preset ->
                    FilterCell(
                        preset = preset,
                        selected = state.preset == preset,
                        onSelect = {
                            onSelect(preset)
                            if (preset == FilterPreset.CUSTOM_LUT) leicaExpanded = false
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { leicaExpanded = !leicaExpanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (activeLeica) FanhuaGold else Color(0x33FFFFFF))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (activeLeica) {
                        stringResource(state.preset.labelRes)
                    } else {
                        stringResource(R.string.filter_leica_group)
                    },
                    color = if (activeLeica) FanhuaGold else FanhuaTextDim,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (leicaExpanded) FanhuaIcons.ArrowUp else FanhuaIcons.ArrowDown,
                    contentDescription = null,
                    tint = FanhuaTextDim,
                    modifier = Modifier.size(14.dp)
                )
            }
            AnimatedVisibility(visible = leicaExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    leicaGroup.forEach { preset ->
                        FilterCell(
                            preset = preset,
                            selected = state.preset == preset,
                            onSelect = {
                                onSelect(preset)
                                leicaExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterCell(
    preset: FilterPreset,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 520f),
        label = "filterScale"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(onClick = onSelect)
    ) {
        Box(
            modifier = Modifier
                .size(width = 58.dp, height = 58.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    brush = if (selected) {
                        Brush.linearGradient(listOf(FanhuaGold, FanhuaGoldDeep))
                    } else {
                        Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF)))
                    },
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(if (selected) 3.dp else 2.dp)
                .clip(RoundedCornerShape(15.dp))
        ) {
            FilterSwatch(
                preset = preset,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(FanhuaGold),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = FanhuaIcons.Check,
                        contentDescription = null,
                        tint = Color(0xFF241A00),
                        modifier = Modifier.size(9.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(preset.labelRes),
            color = if (selected) FanhuaText else FanhuaTextDim,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ModeSelector(
    state: CameraUiState,
    onSelect: (CaptureMode) -> Unit,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x0DFFFFFF))))
            .border(1.dp, FanhuaStroke, CircleShape)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        CaptureMode.entries.forEach { mode ->
            val selected = state.mode == mode
            val background by animateColorAsState(
                targetValue = if (selected) Color(0x2EFFFFFF) else Color.Transparent,
                animationSpec = tween(durationMillis = 200),
                label = "modeBackground"
            )
            val color by animateColorAsState(
                targetValue = if (selected) FanhuaText else FanhuaTextDim,
                animationSpec = tween(durationMillis = 200),
                label = "modeColor"
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(background)
                    .clickable { onSelect(mode) }
                    .padding(
                        horizontal = if (compact) 12.dp else 16.dp,
                        vertical = if (compact) 7.dp else 9.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(mode.labelRes),
                    color = color,
                    style = if (compact) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.labelLarge
                    }
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
        targetState = !state.recording && state.mode != CaptureMode.VIDEO,
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
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 620f),
        label = "photoShutterScale"
    )
    val ripple = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.size(86.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .graphicsLayer {
                    val p = ripple.value
                    scaleX = 1f + 0.3f * p
                    scaleY = 1f + 0.3f * p
                    alpha = (1f - p) * 0.55f
                }
                .clip(CircleShape)
                .background(FanhuaGold.copy(alpha = 0.35f))
        )
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .border(
                    width = 3.dp,
                    brush = Brush.linearGradient(listOf(FanhuaGold, FanhuaGoldDeep)),
                    shape = CircleShape
                )
                .padding(7.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(Color(0xFFF7F6F3))
                .clickable(interactionSource = interaction, indication = null) {
                    scope.launch {
                        ripple.snapTo(0f)
                        ripple.animateTo(1f, tween(durationMillis = 480, easing = LinearEasing))
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
        targetValue = if (recording) 24.dp else 9.dp,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "recordCorner"
    )
    val innerSize by animateDpAsState(
        targetValue = if (recording) 34.dp else 46.dp,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "recordSize"
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
        Box(modifier = Modifier.size(86.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        val flash = stopFlash.value
                        val s = 1f + flash * 0.08f
                        scaleX = s
                        scaleY = s
                        alpha = 0.4f + 0.6f * flash
                    }
                    .clip(CircleShape)
                    .border(3.dp, FanhuaGold.copy(alpha = 0.5f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(
                        width = 3.dp,
                        brush = Brush.linearGradient(listOf(FanhuaGold, FanhuaGoldDeep)),
                        shape = CircleShape
                    )
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(innerSize)
                        .clip(RoundedCornerShape(corner))
                        .background(Color(0xFFFF453A))
                )
            }
        }
        Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
            if (recording) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF453A))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatDuration(durationMs),
                        color = FanhuaGold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
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
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x0FFFFFFF))))
            .border(1.dp, FanhuaStroke, CircleShape)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.lensOptions.forEach { lens ->
            val selected = lens.id == state.activeLensId
            val background by animateColorAsState(
                targetValue = if (selected) Color(0x2EFFFFFF) else Color.Transparent,
                animationSpec = tween(durationMillis = 200),
                label = "lensBackground"
            )
            val color by animateColorAsState(
                targetValue = if (selected) FanhuaText else FanhuaTextDim,
                animationSpec = tween(durationMillis = 200),
                label = "lensColor"
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(background)
                    .clickable { onSelect(lens.id) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lens.label,
                    color = color,
                    style = MaterialTheme.typography.labelMedium
                )
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
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (enabled) Color(0x24FFC53D) else Color(0x14FFFFFF))
            .border(1.dp, if (enabled) FanhuaGold.copy(alpha = 0.5f) else FanhuaStroke, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = FanhuaIcons.LivePhoto,
            contentDescription = stringResource(R.string.live_photo),
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.live_photo),
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun VideoSpecBar(
    state: CameraUiState,
    onSelect: (OutputSpec) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 22.dp)
    ) {
        items(state.availableSpecs, key = { "${it.width}x${it.height}@${it.fps}" }) { spec ->
            val selected = state.selectedSpec == spec
            FrostedChip(
                selected = selected,
                modifier = Modifier.clickable { onSelect(spec) }
            ) {
                Text(
                    text = shortLabel(spec),
                    color = if (selected) FanhuaGold else FanhuaTextDim,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun shortLabel(spec: OutputSpec): String {
    val res = when {
        spec.height >= 2160 -> "4K"
        spec.height >= 1080 -> "1080P"
        else -> "720P"
    }
    return "$res${spec.fps}"
}

@Composable
private fun FilterSwatch(
    preset: FilterPreset,
    modifier: Modifier = Modifier
) {
    val grade = remember(preset) { FilterGrade.of(preset) }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val warm = grade.temperature
        val sat = grade.saturation
        val lift = grade.lift
        fun channel(index: Int): Float = (0.5f + lift[index] * 2f).coerceIn(0f, 1f)
        val baseR = channel(0)
        val baseG = channel(1)
        val baseB = channel(2)
        val steps = 5
        val cellW = w / steps
        val cellH = h / 3
        for (row in 0 until 3) {
            for (col in 0 until steps) {
                val t = col.toFloat() / (steps - 1).toFloat()
                val shade = 0.3f + 0.6f * t
                val r = ((baseR + warm * 0.22f) * shade * sat).coerceIn(0f, 1f)
                val g = ((baseG + warm * 0.04f) * shade * sat).coerceIn(0f, 1f)
                val b = ((baseB - warm * 0.16f) * shade * sat).coerceIn(0f, 1f)
                drawRect(
                    color = Color(r, g, b),
                    topLeft = Offset(col * cellW, row * cellH),
                    size = androidx.compose.ui.geometry.Size(cellW + 0.6f, cellH + 0.6f)
                )
            }
        }
        val vig = grade.vignette.coerceIn(0f, 0.6f)
        if (vig > 0.01f) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = vig)),
                    center = Offset(w / 2f, h / 2f),
                    radius = maxOf(w, h) * 0.72f
                ),
                size = androidx.compose.ui.geometry.Size(w, h)
            )
        }
    }
}

@Composable
private fun GalleryButton(
    captureCount: Int,
    size: Int,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, key1 = captureCount) {
        value = withContext(Dispatchers.IO) {
            val uri = LatestMedia.latestPhotoUri(context)
            if (uri != null) {
                runCatching {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(uri, Size(160, 160), null)
                    } else {
                        null
                    }
                    bitmap?.asImageBitmap()
                }.getOrNull()
            } else {
                null
            }
        }
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x14FFFFFF))
            .border(1.dp, FanhuaStroke, RoundedCornerShape(18.dp))
            .padding(3.dp)
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val image = thumbnail
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.gallery_title),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = FanhuaIcons.Gallery,
                contentDescription = stringResource(R.string.gallery_title),
                tint = FanhuaText,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

private object LatestMedia {
    fun latestPhotoUri(context: Context): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = MediaStore.Images.Media.DISPLAY_NAME + " LIKE ?"
        val args = arrayOf("FH_%")
        val sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC LIMIT 1"
        return try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
private fun ZoomBar(
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
            .padding(horizontal = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.zoom_label),
                color = FanhuaTextDim,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatZoom(state.zoomRatio),
                color = FanhuaGold,
                style = MaterialTheme.typography.labelSmall
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
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
                    color = Color(0x33FFFFFF),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
                val cx = fraction(state.zoomRatio) * size.width
                drawLine(
                    color = FanhuaGold,
                    start = Offset(0f, y),
                    end = Offset(cx, y),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
                ticks.forEach { tick ->
                    val x = fraction(tick) * size.width
                    drawCircle(
                        color = Color(0x66FFFFFF),
                        radius = 2.5f,
                        center = Offset(x, y)
                    )
                }
                drawCircle(color = Color(0xFF16171A), radius = 11f, center = Offset(cx, y))
                drawCircle(color = Color(0xFFF7F6F3), radius = 8f, center = Offset(cx, y))
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
                contentPadding = PaddingValues(horizontal = 22.dp)
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
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 22.dp)
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
        modifier = Modifier.padding(horizontal = 20.dp),
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
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
            }
            Text(
                text = stringResource(R.string.settings_superres_desc),
                color = FanhuaTextDim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(150.dp)
            )
        }
    }
}

private fun formatZoom(ratio: Float): String = String.format(Locale.US, "%.1fx", ratio)

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
