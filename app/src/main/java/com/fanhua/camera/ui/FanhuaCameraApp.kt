package com.fanhua.camera.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CameraController
import com.fanhua.camera.camera.core.CameraUiState
import com.fanhua.camera.camera.core.CapabilityProbe
import com.fanhua.camera.camera.core.CaptureMode
import com.fanhua.camera.camera.core.SuperMoonMode
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
import com.fanhua.camera.ui.common.GlassSurface
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
import com.fanhua.camera.ui.theme.Leica
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

    // 徕卡一瞬布局：预览铺满全屏，控件浮在预览之上（逆向自 res/layout/f6x.xml）。
    Box(modifier = Modifier.fillMaxSize().background(FanhuaInk)) {
            // 顶部黑边：放顶栏（液态玻璃）
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(160)),
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
            ) {
                GlassSurface(
                    cornerRadius = 0.dp,
                    tint = Color.Black,
                    tintAlpha = 0.26f,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TopBar(
                        state = state,
                        landscape = isLandscape,
                        onFlashToggle = {
                            if (state.frontCamera) {
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
                        onAutoCompositionToggle = { controller.applyAutoComposition() },
                        onSuperResToggle = { controller.setSuperResEnabled(!state.superResEnabled) },
                        onSettings = { showSettings = true },
                        onHideControls = { controlsVisible = false }
                    )
                }
            }

            // 预览铺满全屏，与徕卡一致
            LeicaViewfinderFrame(
                controller = controller,
                state = state,
                landscape = isLandscape
            )

            // 底部黑边：放控制区（液态玻璃）
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(160)),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            ) {
                GlassSurface(
                    cornerRadius = 0.dp,
                    tint = Color.Black,
                    tintAlpha = 0.32f,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BottomControls(
                        controller = controller,
                        state = state,
                        landscape = isLandscape,
                        onOpenGallery = { showGallery = true }
                    )
                }
            }

        StatusOverlay(
            messageRes = state.statusMessage?.resId,
            isError = state.statusMessage?.isError == true,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 96.dp)
        )

        // 实时水印标识：徕卡红圆标 + FANHUA 字标 + 当前滤镜名。
        // 横屏时旋转 -90° 贴左侧黑边，避免压在取景画面上。
        LeicaBadge(
            filterName = stringResource(state.preset.labelRes),
            modifier = Modifier
                .align(if (isLandscape) Alignment.CenterStart else Alignment.TopStart)
                .then(
                    if (isLandscape) {
                        Modifier
                            .statusBarsPadding()
                            .padding(start = 10.dp)
                            .graphicsLayer { rotationZ = -90f }
                    } else {
                        Modifier
                            .statusBarsPadding()
                            .padding(start = 18.dp, top = 64.dp)
                    }
                )
        )

        // 超级月亮：识别到月亮且模式为自动时点亮
        if (state.moonDetected && state.superMoonMode == SuperMoonMode.AUTO) {
            MoonBadge(
                confidence = state.moonConfidence,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 132.dp)
            )
        }

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
 * 徕卡一瞬式取景框：预览只占中央一块，按画幅比例收缩，框外留黑。
 *
 * 竖屏：取景框宽度撑满（减去左右 8dp），高度由画幅决定，垂直居中。
 * 横屏：高度撑满，宽度由画幅决定，水平居中；画幅文字旋转 90° 贴在框左侧，
 *       这样文字不压在取景画面上。
 */
@Composable
private fun LeicaViewfinderFrame(
    controller: CameraController,
    state: CameraUiState,
    landscape: Boolean
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 逆向自徕卡 res/layout/f6x.xml：主预览 TextureView 是 match_parent × match_parent，
        // 取景时画面铺满屏幕，画幅比例只决定出片裁切，不再做 4:3 内接小框。
        Viewfinder(controller = controller, state = state, modifier = Modifier.fillMaxSize())

        // 画幅角标：左上角小字，不压中心构图区
        Text(
            text = stringResource(state.photoAspect.labelRes),
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 10.dp, top = 8.dp)
        )

        // 横屏：画幅/机型信息旋转 -90° 贴左侧边缘
        if (landscape) {
            Text(
                text = "LEICA  ${stringResource(state.photoAspect.labelRes)}",
                color = com.fanhua.camera.ui.theme.Leica.TextDim,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer { rotationZ = -90f }
                    .padding(end = 14.dp)
            )
        }
    }
}

/**
 * 实时取景水印标识：真实徕卡红圆标 + 一轮参数小字。
 *
 * 徕卡圆标用 APK 内提取的原图 `wm_leica.webp`（240×240，红底白字），
 * 下方是当前滤镜 / 画幅 / 机型信息，模拟徕卡一瞬取景时左下的品牌标识区。
 * 该标识只做视觉提示，不写入照片；照片水印由 WatermarkRenderer 处理。
 */
@Composable
private fun LeicaBadge(
    modifier: Modifier = Modifier,
    filterName: String? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 真实徕卡红圆标
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.wm_leica),
            contentDescription = "Leica",
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(9.dp))
        Column {
            // FANHUA 矢量字标（细笔画、宽字距）
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.wordmark_fanhua),
                contentDescription = "FANHUA",
                modifier = Modifier
                    .height(11.dp)
                    .width(73.dp)
            )
            if (!filterName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = filterName,
                    color = com.fanhua.camera.ui.theme.Leica.TextDim,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 启动动画（徕卡一瞬风格）。
 *
 * 参照徕卡一瞬的开机表现：**纯黑底、极简、无花哨动效**。
 * 秩序上只做三件事：
 *   1. 徕卡红圆标从 0.9 轻微放大浮现（250ms）；
 *   2. FANHUA 矢量字标随后淡入，字距由宽收紧（克制、不夸张）；
 *   3. 底部一条 0.5dp 的红色进度线从左向右匀速走满，代替转圈。
 *
 * 去掉了旧版的衬线字体、白底、以及"无限进步"标语——
 * 那些元素让启动页看起来像海报，而不是相机 App。
 */
@Composable
private fun CameraStartingOverlay(modifier: Modifier = Modifier) {
    // 圆标浮现
    val markIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        markIn.animateTo(1f, tween(durationMillis = 260, easing = FastOutSlowInEasing))
    }
    // 字标淡入
    val wordIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(180)
        wordIn.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
    }
    // 进度线：循环匀速推进
    val transition = rememberInfiniteTransition(label = "starting")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Box(
        modifier = modifier.background(Leica.Ink),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 徕卡红圆标
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.wm_leica),
                contentDescription = null,
                modifier = Modifier
                    .size(54.dp)
                    .graphicsLayer {
                        val p = markIn.value
                        scaleX = 0.90f + 0.10f * p
                        scaleY = 0.90f + 0.10f * p
                        alpha = p
                    }
            )
            Spacer(modifier = Modifier.height(18.dp))
            // FANHUA 矢量字标
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.wordmark_fanhua),
                contentDescription = null,
                modifier = Modifier
                    .height(13.dp)
                    .width(86.dp)
                    .graphicsLayer {
                        alpha = wordIn.value
                        translationY = (1f - wordIn.value) * 4f
                    }
            )
        }

        // 底部进度线：细、克制，只占屏幕 84dp 宽
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 56.dp)
                .width(84.dp)
                .height(1.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White.copy(alpha = 0.10f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(84.dp * progress)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Leica.Red)
            )
        }
    }
}

/**
 * 超级月亮标识。
 *
 * 只在「模式=自动 且 实时检测到月亮」时出现，告诉用户这一张会走月亮增强直出。
 * 用琥珀色描边 + 细进度条表示置信度，避免遮挡取景。
 */
@Composable
private fun MoonBadge(
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xCC0B0B0D))
            .border(1.dp, FanhuaGold.copy(alpha = 0.85f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = FanhuaIcons.Sparkle,
                contentDescription = null,
                tint = FanhuaGold,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = stringResource(R.string.supermoon_badge),
                color = FanhuaGold,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.2.sp
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .width(74.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(50))
                .background(scheme.onSurface.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(confidence.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(FanhuaGold)
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
