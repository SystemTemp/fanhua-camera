package com.fanhua.camera.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CameraUiState
import com.fanhua.camera.camera.core.CaptureMode
import com.fanhua.camera.camera.core.FlashMode
import com.fanhua.camera.camera.core.PhotoAspect
import com.fanhua.camera.ui.common.FanhuaIcons
import com.fanhua.camera.ui.theme.Leica

/**
 * 顶栏（徕卡一瞬风格）。
 *
 * 真机对照：顶栏没有胶囊/毛玻璃底板，只有一排等距的白色细线图标，
 * 左侧是闪光灯状态，右侧依次是画幅 / 构图 / 超分 / 设置 / 隐藏。
 * 选中态用徕卡红（#E20612）纯色，不做填充底。
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
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
            .padding(
                horizontal = if (landscape) 22.dp else 18.dp,
                vertical = if (landscape) 8.dp else 14.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：徕卡红点标 + 闪光状态（纯文字，无底板）
        LeicaMark(active = state.torchEnabled || state.flashMode != FlashMode.OFF)
        Spacer(modifier = Modifier.width(10.dp))
        FlashLabel(state = state, onClick = onFlashToggle, compact = landscape)

        Spacer(modifier = Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(if (landscape) 14.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = state.mode != CaptureMode.VIDEO,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150))
            ) {
                LeicaIconButton(
                    icon = FanhuaIcons.Aspect,
                    contentDescription = stringResource(R.string.settings_photo_aspect),
                    onClick = onAspectToggle,
                    active = state.photoAspect != PhotoAspect.FULL
                )
            }
            LeicaIconButton(
                icon = FanhuaIcons.Sparkle,
                contentDescription = stringResource(R.string.settings_autocomposition),
                onClick = onAutoCompositionToggle,
                active = state.autoCompositionEnabled
            )
            AnimatedVisibility(
                visible = state.mode == CaptureMode.PHOTO,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150))
            ) {
                LeicaIconButton(
                    icon = FanhuaIcons.Aperture,
                    contentDescription = stringResource(R.string.settings_superres),
                    onClick = onSuperResToggle,
                    active = state.superResEnabled
                )
            }
            LeicaIconButton(
                icon = FanhuaIcons.Settings,
                contentDescription = stringResource(R.string.action_settings),
                onClick = onSettings
            )
            LeicaIconButton(
                icon = FanhuaIcons.VisibilityOff,
                contentDescription = stringResource(R.string.hide_controls),
                onClick = onHideControls
            )
        }
    }
}

/** 徕卡红点标：一个 7dp 实心红圆，激活时带呼吸。 */
@Composable
private fun LeicaMark(active: Boolean) {
    val pulse by rememberInfiniteTransition(label = "leicaMark").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "markPulse"
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(Leica.Red.copy(alpha = if (active) pulse else 0.85f))
    )
}

/**
 * 闪光状态：徕卡风格纯文字 + 图标，无胶囊底、无描边。
 * 激活时文字与图标转为徕卡红，关闭时保持浅灰。
 */
@Composable
private fun FlashLabel(
    state: CameraUiState,
    onClick: () -> Unit,
    compact: Boolean
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "flashScale"
    )
    val active = state.torchEnabled || state.flashMode != FlashMode.OFF
    val tint by animateColorAsState(
        targetValue = if (active) Leica.Red else Leica.TextDim,
        animationSpec = tween(durationMillis = 180),
        label = "flashTint"
    )
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(2.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Crossfade(
            targetState = state.torchEnabled to state.flashMode,
            animationSpec = tween(durationMillis = 180),
            label = "flashIcon"
        ) { target ->
            Icon(
                imageVector = if (target.first) FanhuaIcons.Torch else flashIcon(target.second),
                contentDescription = stringResource(R.string.flash_auto),
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        if (!compact) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (state.torchEnabled) {
                    stringResource(R.string.torch_on)
                } else {
                    stringResource(state.flashMode.labelRes)
                },
                color = tint,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * 徕卡顶栏图标按钮：纯图标，无底色、无描边。
 * 选中态直接把图标染成徕卡红，按下轻微缩放。
 */
@Composable
private fun LeicaIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    buttonSize: Dp = 32.dp,
    iconSize: Dp = 19.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "leicaIconScale"
    )
    val tint by animateColorAsState(
        targetValue = if (active) Leica.Red else Leica.White,
        animationSpec = tween(durationMillis = 180),
        label = "leicaIconTint"
    )
    Box(
        modifier = modifier
            .size(buttonSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

private fun flashIcon(mode: FlashMode): ImageVector = when (mode) {
    FlashMode.AUTO -> FanhuaIcons.FlashAuto
    FlashMode.ON -> FanhuaIcons.FlashOn
    FlashMode.OFF -> FanhuaIcons.FlashOff
}
