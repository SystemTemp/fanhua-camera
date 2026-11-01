package com.fanhua.camera.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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

/**
 * 顶栏（Google Material 3 风格）。
 *
 * 左侧：闪光 / 常亮补光的填充式胶囊。
 * 右侧：一组独立的圆形图标按钮（画幅、自动构图、超分、设置、隐藏）。
 *
 * 视觉规范：
 * - 底色用 [MaterialTheme.colorScheme.surfaceContainer] 半透明，叠在取景画面上有足够对比；
 * - 选中态用 primary 的 18% 透明底 + primary 前景，全栏不再出现金色；
 * - 通过 [WindowInsets.safeDrawing] 避让状态栏、挖孔与横屏时的侧边系统栏。
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
            // 横屏时状态栏在侧边、挖孔也可能在左右，这里统一用 safeDrawing 的顶部与水平方向
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
            .padding(
                horizontal = if (landscape) 20.dp else 16.dp,
                vertical = if (landscape) 10.dp else 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlashPill(state = state, onClick = onFlashToggle, compact = landscape)

        Spacer(modifier = Modifier.weight(1f))

        // 右侧：独立圆形图标按钮，间距 8dp 保持 Material 3 的宽松节奏
        // 间距 6dp + 40dp 按钮：360dp 窄屏下 5 个按钮刚好放得下，不溢出
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = state.mode != CaptureMode.VIDEO,
                enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.8f),
                exit = fadeOut(tween(150)) + scaleOut(tween(180), targetScale = 0.8f)
            ) {
                M3IconButton(
                    icon = FanhuaIcons.Aspect,
                    contentDescription = stringResource(R.string.settings_photo_aspect),
                    onClick = onAspectToggle,
                    active = state.photoAspect != PhotoAspect.FULL
                )
            }
            M3IconButton(
                icon = FanhuaIcons.Sparkle,
                contentDescription = stringResource(R.string.settings_autocomposition),
                onClick = onAutoCompositionToggle,
                active = state.autoCompositionEnabled
            )
            AnimatedVisibility(
                visible = state.mode == CaptureMode.PHOTO,
                enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.8f),
                exit = fadeOut(tween(150)) + scaleOut(tween(180), targetScale = 0.8f)
            ) {
                M3IconButton(
                    icon = FanhuaIcons.Aperture,
                    contentDescription = stringResource(R.string.settings_superres),
                    onClick = onSuperResToggle,
                    active = state.superResEnabled
                )
            }
            M3IconButton(
                icon = FanhuaIcons.Settings,
                contentDescription = stringResource(R.string.action_settings),
                onClick = onSettings
            )
            M3IconButton(
                icon = FanhuaIcons.VisibilityOff,
                contentDescription = stringResource(R.string.hide_controls),
                onClick = onHideControls
            )
        }
    }
}

/**
 * 闪光胶囊。
 *
 * 点击在「自动 → 开启 → 关闭」之间循环（逻辑由上层回调决定），
 * 补光常亮时同样高亮为 primary。横屏时只留图标，省出横向空间。
 */
@Composable
private fun FlashPill(
    state: CameraUiState,
    onClick: () -> Unit,
    compact: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "flashPillScale"
    )
    // 只要不是「关闭」状态都算激活，用 primary 高亮
    val active = state.torchEnabled || state.flashMode != FlashMode.OFF
    val background by animateColorAsState(
        targetValue = if (active) {
            scheme.primary.copy(alpha = 0.18f)
        } else {
            scheme.surfaceContainer.copy(alpha = 0.86f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "flashPillBg"
    )
    val tint by animateColorAsState(
        targetValue = if (active) scheme.primary else scheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 200),
        label = "flashPillTint"
    )

    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(background)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
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
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        if (!compact) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (state.torchEnabled) {
                    stringResource(R.string.torch_on)
                } else {
                    stringResource(state.flashMode.labelRes)
                },
                color = tint,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * Material 3 圆形图标按钮。
 *
 * 未选中：surfaceContainer 填充、无描边、onSurfaceVariant 图标；
 * 选中：primary 18% 填充、primary 图标。
 * 按下时缩放 0.92 作为触觉反馈（相机界面不用水波纹，避免遮挡取景）。
 *
 * 注意：这里不复用 `ui/common/GlassIconButton`，因为该组件属于「玻璃 + 描边」旧风格，
 * 且由其它重构任务维护；顶栏需要的是纯 Material 3 填充式按钮。
 */
@Composable
private fun M3IconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    buttonSize: Dp = 40.dp,
    iconSize: Dp = 20.dp
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "m3IconScale"
    )
    val background by animateColorAsState(
        targetValue = if (active) {
            scheme.primary.copy(alpha = 0.18f)
        } else {
            scheme.surfaceContainer.copy(alpha = 0.86f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "m3IconBg"
    )
    val tint by animateColorAsState(
        targetValue = if (active) scheme.primary else scheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 200),
        label = "m3IconTint"
    )
    Box(
        modifier = modifier
            .size(buttonSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(background)
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
