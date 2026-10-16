package com.fanhua.camera.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CameraUiState
import com.fanhua.camera.camera.core.CaptureMode
import com.fanhua.camera.camera.core.FlashMode
import com.fanhua.camera.ui.common.FanhuaIcons
import com.fanhua.camera.ui.common.FrostedPanel
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaText
import com.fanhua.camera.ui.theme.FanhuaTextDim

@Composable
fun TopBar(
    state: CameraUiState,
    onFlashToggle: () -> Unit,
    onAutoCompositionToggle: () -> Unit,
    onSuperResToggle: () -> Unit,
    onSettings: () -> Unit,
    onHideControls: () -> Unit,
    modifier: Modifier = Modifier,
    landscape: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = if (landscape) 12.dp else 16.dp, vertical = if (landscape) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FrostedPanel(
            cornerRadius = 24.dp,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onFlashToggle)
            ) {
                Crossfade(
                    targetState = state.flashMode,
                    animationSpec = tween(durationMillis = 220),
                    label = "flashIcon"
                ) { mode ->
                    Icon(
                        imageVector = flashIcon(mode),
                        contentDescription = stringResource(R.string.flash_auto),
                        tint = if (mode == FlashMode.OFF) FanhuaTextDim else FanhuaGold,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (!landscape) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(state.flashMode.labelRes),
                        color = if (state.flashMode == FlashMode.OFF) FanhuaTextDim else FanhuaText,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        FrostedPanel(
            cornerRadius = 24.dp,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TopIconButton(
                    icon = FanhuaIcons.Sparkle,
                    active = state.autoCompositionEnabled,
                    contentDescription = stringResource(R.string.settings_autocomposition),
                    onClick = onAutoCompositionToggle
                )
                AnimatedVisibility(
                    visible = state.mode == CaptureMode.PHOTO,
                    enter = fadeIn(animationSpec = tween(180)) +
                        scaleIn(initialScale = 0.8f, animationSpec = tween(220)),
                    exit = fadeOut(animationSpec = tween(150)) +
                        scaleOut(targetScale = 0.8f, animationSpec = tween(180)),
                    modifier = Modifier
                ) {
                    TopIconButton(
                        icon = FanhuaIcons.Aperture,
                        active = state.superResEnabled,
                        contentDescription = stringResource(R.string.settings_superres),
                        onClick = onSuperResToggle
                    )
                }
                TopIconButton(
                    icon = FanhuaIcons.Settings,
                    active = false,
                    contentDescription = stringResource(R.string.action_settings),
                    onClick = onSettings
                )
                TopIconButton(
                    icon = FanhuaIcons.VisibilityOff,
                    active = false,
                    contentDescription = stringResource(R.string.hide_controls),
                    onClick = onHideControls
                )
            }
        }
    }
}

@Composable
private fun TopIconButton(
    icon: ImageVector,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) Color(0x33E8C39E) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) FanhuaGold else FanhuaText,
            modifier = Modifier.size(19.dp)
        )
    }
}

private fun flashIcon(mode: FlashMode): ImageVector = when (mode) {
    FlashMode.AUTO -> FanhuaIcons.FlashAuto
    FlashMode.ON -> FanhuaIcons.FlashOn
    FlashMode.OFF -> FanhuaIcons.FlashOff
}