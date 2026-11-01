package com.fanhua.camera.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CameraController
import com.fanhua.camera.camera.core.CapabilityProbe
import com.fanhua.camera.camera.core.CaptureMode
import com.fanhua.camera.camera.core.FlashMode
import com.fanhua.camera.camera.core.PhotoAspect
import com.fanhua.camera.ui.camera.BottomControls
import com.fanhua.camera.ui.camera.TopBar
import com.fanhua.camera.ui.camera.Viewfinder
import com.fanhua.camera.ui.common.FanhuaButton
import com.fanhua.camera.ui.common.FanhuaButtonStyle
import com.fanhua.camera.ui.common.FanhuaIcons
import com.fanhua.camera.ui.common.FrostedPanel
import com.fanhua.camera.ui.common.GlassIconButton
import com.fanhua.camera.ui.gallery.GallerySheet
import com.fanhua.camera.ui.settings.BugReportSheet
import com.fanhua.camera.ui.settings.SettingsSheet
import com.fanhua.camera.ui.settings.UpdateController
import com.fanhua.camera.ui.settings.UpdateDialog
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaInk
import com.fanhua.camera.ui.theme.FanhuaOutline
import com.fanhua.camera.ui.theme.FanhuaRose
import com.fanhua.camera.ui.theme.FanhuaStroke
import com.fanhua.camera.ui.theme.FanhuaText
import com.fanhua.camera.ui.theme.FanhuaTextDim
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import com.fanhua.camera.ui.common.IndeterminateWaveBar
import kotlinx.coroutines.delay

@Composable
fun FanhuaCameraApp(controller: CameraController) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val capability = remember {
        CapabilityProbe(context).probe(CameraSelector.LENS_FACING_BACK)
    }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showGallery by rememberSaveable { mutableStateOf(false) }
    var showBugReport by rememberSaveable { mutableStateOf(false) }
    // 启动动画至少展示 1.2 秒，否则相机 ready 太快会让动画一闪而过甚至看不到
    var splashMinElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1200)
        splashMinElapsed = true
    }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    val updateController = remember { UpdateController(context, scope) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        controller.onPermissionResult(result[Manifest.permission.CAMERA] == true)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            controller.onPermissionResult(true)
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
        updateController.check(silent = true)
    }

    Box(modifier = Modifier.fillMaxSize().background(FanhuaInk)) {
        Viewfinder(controller = controller, state = state, modifier = Modifier.fillMaxSize())



        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(160)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopBar(
                state = state,
                landscape = isLandscape,
                onFlashToggle = {
                    if (state.frontCamera) {
                        // 前置没有闪光灯，直接切常亮补光（屏幕补光）
                        controller.setTorchEnabled(!state.torchEnabled)
                    } else {
                        val next = when (state.flashMode) {
                            FlashMode.AUTO -> FlashMode.ON
                            FlashMode.ON -> FlashMode.OFF
                            FlashMode.OFF -> FlashMode.AUTO
                        }
                        controller.setFlashMode(next)
                    }
                },
                onTorchToggle = { controller.setTorchEnabled(!state.torchEnabled) },
                onAspectToggle = {
                    val next = when (state.photoAspect) {
                        PhotoAspect.FULL -> PhotoAspect.RATIO_4_3
                        PhotoAspect.RATIO_4_3 -> PhotoAspect.RATIO_16_9
                        PhotoAspect.RATIO_16_9 -> PhotoAspect.RATIO_1_1
                        PhotoAspect.RATIO_1_1 -> PhotoAspect.FULL
                    }
                    controller.setPhotoAspect(next)
                },
                onAutoCompositionToggle = {
                    // 点击直接执行自动构图（裁切到建议构图），而不是切换开关状态
                    controller.applyAutoComposition()
                },
                onSuperResToggle = {
                    controller.setSuperResEnabled(!state.superResEnabled)
                },
                onSettings = { showSettings = true },
                onHideControls = { controlsVisible = false }
            )
        }

        StatusOverlay(
            messageRes = state.statusMessage?.resId,
            isError = state.statusMessage?.isError == true,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 80.dp)
        )

        if (state.recording) {
            RecordingBadge(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 80.dp)
            )
        }

        // 启动动画放在最上层（在所有控件之后绘制），避免被滤镜条等控件遮挡
        val showSplash = state.permissionGranted &&
            state.cameraAvailable &&
            (!state.ready || !splashMinElapsed)
        if (showSplash) {
            CameraStartingOverlay(modifier = Modifier.fillMaxSize().zIndex(10f))
        }

        CountdownOverlay(
            remaining = state.countdownRemaining,
            onCancel = controller::cancelCountdown,
            modifier = Modifier.fillMaxSize()
        )

        CaptureFlash(
            captureCount = state.captureCount,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(160)),
            modifier = if (isLandscape) {
                // 横屏：贴右侧竖直排列，同时避让顶部状态栏与底部导航栏，
                // 避免与 TopBar 或系统手势区重叠。
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(top = 64.dp, bottom = 24.dp)
            } else {
                Modifier.align(Alignment.BottomCenter)
            }
        ) {
            BottomControls(
                controller = controller,
                state = state,
                landscape = isLandscape,
                onOpenGallery = { showGallery = true }
            )
        }

        if (!controlsVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) {
                GlassIconButton(
                    icon = FanhuaIcons.Visibility,
                    contentDescription = stringResource(R.string.show_controls),
                    onClick = { controlsVisible = true },
                    buttonSize = 48.dp,
                    iconSize = 21.dp
                )
            }
        }

        if (!state.permissionGranted) {
            PermissionGate(
                onRequest = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                    )
                }
            )
        }
    }

    SettingsSheet(
        visible = showSettings,
        controller = controller,
        capability = capability,
        onBugReport = { showBugReport = true },
        onDismiss = { showSettings = false }
    )

    GallerySheet(
        visible = showGallery,
        controller = controller,
        onDismiss = { showGallery = false }
    )

    BugReportSheet(
        visible = showBugReport,
        onDismiss = { showBugReport = false }
    )

    UpdateDialog(
        state = updateController.state,
        onDownload = updateController::download,
        onInstall = updateController::install,
        onDismiss = updateController::dismiss
    )
}

/**
 * 相机启动动画。
 *
 * Google 风格：光圈环缓慢旋转 + 品牌名淡入 + 波浪进度条。
 * 动画分三段落：0~300ms 图标放大淡入，300ms 后文字与进度条浮现。
 */
@Composable
private fun CameraStartingOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "starting")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    // 入场动画：图标先出现，文字稍后
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(220)
        revealed = true
    }
    val iconAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(420, easing = LinearEasing),
        label = "iconAlpha"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(520, easing = LinearEasing),
        label = "textAlpha"
    )

    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .background(Color(0xF2000000))
            .graphicsLayer { alpha = iconAlpha },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    },
                contentAlignment = Alignment.Center
            ) {
                // 旋转的光圈环
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f - 4f
                    val rect = androidx.compose.ui.geometry.Size(
                        radius * 2f, radius * 2f
                    )
                    val topLeft = androidx.compose.ui.geometry.Offset(
                        (size.width - radius * 2f) / 2f,
                        (size.height - radius * 2f) / 2f
                    )
                    // 底环
                    drawArc(
                        color = Color(0x1FFFFFFF),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = rect,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3.5f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                    // 旋转的渐变弧
                    rotate(spin) {
                        drawArc(
                            brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    accent.copy(alpha = 0.25f),
                                    accent,
                                    accent.copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            ),
                            startAngle = 0f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = rect,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 3.5f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )
                    }
                }
                Icon(
                    imageVector = FanhuaIcons.Aperture,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = FanhuaText,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.graphicsLayer { alpha = textAlpha }
            )
            Spacer(modifier = Modifier.height(14.dp))
            Box(modifier = Modifier.graphicsLayer { alpha = textAlpha }) {
                IndeterminateWaveBar(
                    modifier = Modifier.width(120.dp),
                    height = 3.dp,
                    color = accent
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.camera_starting),
                color = FanhuaTextDim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.graphicsLayer { alpha = textAlpha }
            )
        }
    }
}

@Composable
private fun StatusOverlay(
    messageRes: Int?,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = messageRes != null,
        enter = fadeIn(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(320)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xCC15161A))
                .border(1.dp, FanhuaStroke, CircleShape)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isError) FanhuaRose else FanhuaGold)
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = messageRes?.let { stringResource(it) } ?: "",
                color = FanhuaText,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun RecordingBadge(modifier: Modifier = Modifier) {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            pulse.animateTo(0.25f, tween(durationMillis = 620, easing = LinearEasing))
            pulse.animateTo(1f, tween(durationMillis = 620, easing = LinearEasing))
        }
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xCC15161A))
            .border(1.dp, FanhuaStroke, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = Color(0xFFFF453A).copy(alpha = 0.35f + 0.65f * pulse.value),
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.message_recording_started),
            color = FanhuaText,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun CountdownOverlay(
    remaining: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (remaining <= 0) return
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .background(Color(0x40000000))
            .clickable(interactionSource = interaction, indication = null, onClick = onCancel),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = remaining.toString(),
                color = FanhuaGold,
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.countdown_cancel),
                color = FanhuaTextDim,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun CaptureFlash(
    captureCount: Int,
    modifier: Modifier = Modifier
) {
    val flash = remember { Animatable(0f) }
    var previous by remember { mutableStateOf(captureCount) }
    LaunchedEffect(captureCount) {
        if (captureCount > previous) {
            flash.snapTo(0.62f)
            flash.animateTo(0f, tween(durationMillis = 260, easing = LinearEasing))
        }
        previous = captureCount
    }
    if (flash.value > 0.01f) {
        Box(modifier = modifier.background(Color.White.copy(alpha = flash.value)))
    }
}

@Composable
private fun ModeNotice(textRes: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(textRes),
            color = FanhuaTextDim,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 30.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF1B1C21), Color(0xFF0E0F12)))
                )
                .border(1.dp, FanhuaStroke, RoundedCornerShape(32.dp))
                .padding(horizontal = 28.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0x1FFFFC53D)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = FanhuaIcons.Aperture,
                    contentDescription = null,
                    tint = FanhuaGold,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.permission_title),
                color = FanhuaText,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.permission_message),
                color = FanhuaTextDim,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            FanhuaButton(
                text = stringResource(R.string.permission_grant),
                onClick = onRequest,
                style = FanhuaButtonStyle.Solid
            )
        }
    }
}
