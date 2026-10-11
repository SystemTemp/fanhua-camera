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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.fanhua.camera.ui.common.FrostedPanel
import com.fanhua.camera.ui.gallery.GallerySheet
import com.fanhua.camera.ui.settings.SettingsSheet
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaInk
import com.fanhua.camera.ui.theme.FanhuaRose
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
    }

    Box(modifier = Modifier.fillMaxSize().background(FanhuaInk)) {
        Viewfinder(controller = controller, state = state, modifier = Modifier.fillMaxSize())

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
            modifier = Modifier.align(Alignment.TopCenter)
        )

        StatusOverlay(
            messageRes = state.statusMessage?.resId,
            isError = state.statusMessage?.isError == true,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 74.dp)
        )

        if (state.recording) {
            RecordingBadge(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 74.dp)
            )
        }

        CaptureFlash(
            captureCount = state.captureCount,
            modifier = Modifier.fillMaxSize()
        )

        BottomControls(
            controller = controller,
            state = state,
            capability = capability,
            landscape = isLandscape,
            onOpenGallery = { showGallery = true },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (!state.permissionGranted) {
            PermissionGate(
                onRequest = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                    )
                }
            )
        }

        if (state.mode == CaptureMode.PRO && !state.ready) {
            ModeNotice(textRes = R.string.pro_output_spec)
        }
    }

    SettingsSheet(
        visible = showSettings,
        controller = controller,
        capability = capability,
        onDismiss = { showSettings = false }
    )

    GallerySheet(
        visible = showGallery,
        onDismiss = { showGallery = false }
    )
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
        FrostedPanel(
            cornerRadius = 20.dp,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp)
        ) {
            Text(
                text = messageRes?.let { stringResource(it) } ?: "",
                color = if (isError) FanhuaRose else FanhuaGold,
                style = MaterialTheme.typography.labelLarge
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
    FrostedPanel(
        modifier = modifier,
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = FanhuaRose.copy(alpha = 0.35f + 0.65f * pulse.value),
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
            .background(Color(0xF20A0B0D)),
        contentAlignment = Alignment.Center
    ) {
        FrostedPanel(
            cornerRadius = 24.dp,
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.permission_title),
                    color = FanhuaGold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.permission_message),
                    color = FanhuaTextDim,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                FanhuaButton(
                    text = stringResource(R.string.permission_grant),
                    onClick = onRequest,
                    style = FanhuaButtonStyle.Solid
                )
            }
        }
    }
}
