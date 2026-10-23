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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CameraController
import com.fanhua.camera.camera.core.CapabilityProbe
import com.fanhua.camera.camera.core.CaptureMode
import com.fanhua.camera.camera.core.FlashMode
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

        if (state.permissionGranted && !state.ready && state.cameraAvailable) {
            CameraStartingOverlay(modifier = Modifier.fillMaxSize())
        }

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
                    val next = when (state.flashMode) {
                        FlashMode.AUTO -> FlashMode.ON
                        FlashMode.ON -> FlashMode.OFF
                        FlashMode.OFF -> FlashMode.AUTO
                    }
                    controller.setFlashMode(next)
                },
                onAutoCompositionToggle = {
                    controller.setAutoCompositionEnabled(!state.autoCompositionEnabled)
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

        CaptureFlash(
            captureCount = state.captureCount,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(160)),
            modifier = if (isLandscape) {
                Modifier.align(Alignment.CenterEnd)
            } else {
                Modifier.align(Alignment.BottomCenter)
            }
        ) {
            BottomControls(
                controller = controller,
                state = state,
                capability = capability,
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

@Composable
private fun CameraStartingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0xA6000000)),
        contentAlignment = Alignment.Center
    ) {
        FrostedPanel(
            cornerRadius = 28.dp,
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 22.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = FanhuaGold,
                    trackColor = FanhuaOutline,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.camera_starting),
                    color = FanhuaText,
                    style = MaterialTheme.typography.labelMedium
                )
            }
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
