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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CameraUiState
import com.fanhua.camera.camera.core.CaptureMode
import com.fanhua.camera.camera.core.FlashMode
import com.fanhua.camera.camera.core.PhotoAspect
import com.fanhua.camera.ui.common.FanhuaIcons
import com.fanhua.camera.ui.common.GlassIconButton
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaText
import com.fanhua.camera.ui.theme.FanhuaTextDim

/**
 * 顶栏。
 *
 * 左侧：闪光 / 常亮补光胶囊。
 * 右侧：画幅（非视频模式）、自动构图、超分（仅照片）、设置、隐藏控件。
 */
@Composable
fun TopBar(
    state: CameraUiState,
    onFlashToggle: () -> Unit,
    onTorchToggle: () -> Unit,
    onAspectToggle: () -> Unit,
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
            .padding(
                horizontal = if (landscape) 14.dp else 16.dp,
                vertical = if (landscape) 8.dp else 11.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0x26FFFFFF), Color(0x0FFFFFFF))))
                .clickable(onClick = onFlashToggle)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(29.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            state.torchEnabled -> Color(0x3DFFC53D)
                            state.flashMode == FlashMode.OFF -> Color.Transparent
                            else -> Color(0x2EFFC53D)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = state.torchEnabled to state.flashMode,
                    animationSpec = tween(durationMillis = 200),
                    label = "flashIcon"
                ) { target ->
                    val torch = target.first
                    val mode = target.second
                    Icon(
                        imageVector = if (torch) FanhuaIcons.Torch else flashIcon(mode),
                        contentDescription = stringResource(R.string.flash_auto),
                        tint = if (!torch && mode == FlashMode.OFF) FanhuaTextDim else FanhuaGold,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (!landscape) {
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = if (state.torchEnabled) {
                        stringResource(R.string.torch_on)
                    } else {
                        stringResource(state.flashMode.labelRes)
                    },
                    color = if (state.torchEnabled) {
                        FanhuaGold
                    } else if (state.flashMode == FlashMode.OFF) {
                        FanhuaTextDim
                    } else {
                        FanhuaText
                    },
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.width(9.dp))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0x26FFFFFF), Color(0x0FFFFFFF))))
                .padding(horizontal = 5.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AnimatedVisibility(
                visible = state.mode != CaptureMode.VIDEO,
                enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.8f),
                exit = fadeOut(tween(150)) + scaleOut(tween(180), targetScale = 0.8f)
            ) {
                TopAction(
                    icon = FanhuaIcons.Aspect,
                    active = state.photoAspect != PhotoAspect.FULL,
                    contentDescription = stringResource(R.string.settings_photo_aspect),
                    onClick = onAspectToggle
                )
            }
            TopAction(
                icon = FanhuaIcons.Sparkle,
                active = state.autoCompositionEnabled,
                contentDescription = stringResource(R.string.settings_autocomposition),
                onClick = onAutoCompositionToggle
            )
            AnimatedVisibility(
                visible = state.mode == CaptureMode.PHOTO,
                enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.8f),
                exit = fadeOut(tween(150)) + scaleOut(tween(180), targetScale = 0.8f)
            ) {
                TopAction(
                    icon = FanhuaIcons.Aperture,
                    active = state.superResEnabled,
                    contentDescription = stringResource(R.string.settings_superres),
                    onClick = onSuperResToggle
                )
            }
            TopAction(
                icon = FanhuaIcons.Settings,
                active = false,
                contentDescription = stringResource(R.string.action_settings),
                onClick = onSettings
            )
            TopAction(
                icon = FanhuaIcons.VisibilityOff,
                active = false,
                contentDescription = stringResource(R.string.hide_controls),
                onClick = onHideControls
            )
        }
    }
}

@Composable
private fun TopAction(
    icon: ImageVector,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    GlassIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        active = active,
        buttonSize = 33.dp,
        iconSize = 16.dp
    )
}

private fun flashIcon(mode: FlashMode): ImageVector = when (mode) {
    FlashMode.AUTO -> FanhuaIcons.FlashAuto
    FlashMode.ON -> FanhuaIcons.FlashOn
    FlashMode.OFF -> FanhuaIcons.FlashOff
}
