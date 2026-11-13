package com.fanhua.camera.ui.camera

import android.net.Uri
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CameraController
import com.fanhua.camera.camera.core.CameraUiState
import com.fanhua.camera.camera.core.CaptureMode
import com.fanhua.camera.camera.core.OutputSpec
import com.fanhua.camera.camera.filters.FilterGrade
import com.fanhua.camera.camera.filters.FilterGroup
import com.fanhua.camera.camera.filters.FilterPreset
import com.fanhua.camera.media.MediaRepository
import com.fanhua.camera.ui.common.FanhuaIcons
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.Leica
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

/**
 * 底部控制区（Google Material 3 风格）。
 *
 * 视觉语言统一为「填充式胶囊」：表面色 + 无描边，选中态一律 primary，
 * 金色只保留给徕卡滤镜的品牌标识。
 *
 * 竖屏（自上而下）：模式专属面板 → 滤镜条 → 快捷工具 → 模式选择 → 快门行 → 变焦条。
 * 横屏（自上而下竖排在右侧）：滤镜条 → 快捷工具 → 模式选择 → 快门 → 相册。
 *
 * 横屏遮挡处理：整列套 [WindowInsets.safeDrawing]，一次性避让状态栏、导航栏、
 * 挖孔与刘海；再叠加 [verticalScroll] 作为极端小屏下的兜底，保证任何按钮都不会被裁掉。
 */
@Composable
fun BottomControls(
    controller: CameraController,
    state: CameraUiState,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier,
    landscape: Boolean = false
) {
    if (landscape) {
        LandscapeControls(
            controller = controller,
            state = state,
            onOpenGallery = onOpenGallery,
            modifier = modifier
        )
    } else {
        PortraitControls(
            controller = controller,
            state = state,
            onOpenGallery = onOpenGallery,
            modifier = modifier
        )
    }
}

/* ------------------------------------------------------------------ 竖屏 */

@Composable
private fun PortraitControls(
    controller: CameraController,
    state: CameraUiState,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(visible = state.mode == CaptureMode.PRO) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ProPanel(controller = controller, state = state)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
        AnimatedVisibility(
            visible = state.mode == CaptureMode.VIDEO && state.availableSpecs.isNotEmpty()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VideoSpecBar(state = state, onSelect = controller::selectSpec)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
        AnimatedVisibility(visible = state.mode == CaptureMode.SUPER_RES) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SuperResInfo(state = state)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        FilterStrip(
            state = state,
            onSelect = controller::setPreset,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))

        // 模式条 + 镜头档位合并到同一行：左侧模式文字，右侧镜头倍数，
        // 省下一整行高度给取景框
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeSelector(state = state, onSelect = controller::setMode)
            Spacer(modifier = Modifier.weight(1f))
            LensSwitcher(state = state, onSelect = controller::setLens, compact = true)
        }
        Spacer(modifier = Modifier.height(8.dp))

        // 快门行：左快捷工具 / 中快门 / 右相册，左右等宽保证快门严格居中
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                // 快捷工具（网格 / 定时 / 前后摄）与实况开关共用左侧空间，
                // 竖屏时优先展示快捷工具，避免再多占一行高度
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnimatedVisibility(visible = state.mode == CaptureMode.PHOTO) {
                        LivePhotoToggle(
                            enabled = state.livePhotoEnabled,
                            onClick = {
                                controller.setLivePhotoEnabled(!state.livePhotoEnabled)
                            }
                        )
                    }
                    QuickTools(state = state, controller = controller, compact = true)
                }
            }
            ShutterButton(state = state, controller = controller)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                GalleryButton(
                    captureCount = state.captureCount,
                    lastCaptureUri = state.lastCaptureUri,
                    size = 52,
                    onClick = onOpenGallery
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        ZoomBar(controller = controller, state = state)
    }
}

/* ------------------------------------------------------------------ 横屏 */

/**
 * 横屏：整列竖排贴右边缘。
 *
 * 关键点是**不被系统区域遮挡**：
 * 1. [WindowInsets.safeDrawing] 同时覆盖状态栏（横屏时在侧边）、导航栏与挖孔；
 * 2. 外层 [verticalScroll] 保证在超矮屏（如分屏）下内容仍可滚动查看，不会硬裁；
 * 3. 滤镜条在横屏用固定宽度，避免在 wrap-content 的列里被拉满屏。
 */
@Composable
private fun LandscapeControls(
    controller: CameraController,
    state: CameraUiState,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center
    ) {
        FilterStrip(
            state = state,
            onSelect = controller::setPreset,
            modifier = Modifier.widthIn(max = 300.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        QuickTools(state = state, controller = controller, compact = true)
        Spacer(modifier = Modifier.height(10.dp))
        ModeSelector(state = state, onSelect = controller::setMode, compact = true)
        Spacer(modifier = Modifier.height(14.dp))
        ShutterButton(state = state, controller = controller)
        Spacer(modifier = Modifier.height(14.dp))
        GalleryButton(
            captureCount = state.captureCount,
            lastCaptureUri = state.lastCaptureUri,
            size = 48,
            onClick = onOpenGallery
        )
    }
}

/* ------------------------------------------------------------------ 通用表面容器 */

/**
 * Material 3 填充式面板：表面色 + 无描边 + 大圆角。
 *
 * 相机界面底衬是实时画面，纯 surface 色会和亮部糊在一起，
 * 因此用 92% 不透明的 surfaceContainer，既保留 Material 3 的「填充式」观感，
 * 又保证任何背景下都能看清控件。
 */
@Composable
private fun M3Panel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(4.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
    content: @Composable () -> Unit
) {
    // 徕卡一瞬：控件区是半透明纯黑底 + 一条极细顶部分隔线，不用毛玻璃胶囊。
    Box(
        modifier = modifier
            .clip(shape)
            .background(Leica.Ink.copy(alpha = 0.62f))
            .padding(contentPadding)
    ) {
        content()
    }
}

/** 通用胶囊按钮：选中 primary 18% 底 + primary 前景，未选中透明。 */
@Composable
private fun PillButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    compact: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "pillScale"
    )
    val background by animateColorAsState(
        targetValue = if (selected) scheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "pillBg"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 180),
        label = "pillTint"
    )
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(background)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(
                horizontal = if (compact) 11.dp else 14.dp,
                vertical = if (compact) 7.dp else 9.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(if (compact) 15.dp else 17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = tint,
            style = if (compact) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.labelLarge
            },
            maxLines = 1
        )
    }
}

/* ------------------------------------------------------------------ 快捷工具 */

@Composable
private fun QuickTools(
    state: CameraUiState,
    controller: CameraController,
    compact: Boolean = false
) {
    // 徕卡一瞬：快捷工具是纯图标，选中为徕卡红，无胶囊底
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
    ) {
        LeicaQuickIcon(
            icon = FanhuaIcons.Grid,
            selected = state.gridEnabled,
            description = stringResource(R.string.grid),
            onClick = { controller.setGridEnabled(!state.gridEnabled) }
        )
        LeicaQuickIcon(
            icon = FanhuaIcons.Timer,
            selected = state.timerSeconds > 0,
            description = timerLabel(state.timerSeconds),
            onClick = { controller.setTimerSeconds(nextTimerSeconds(state.timerSeconds)) }
        )
        LeicaQuickIcon(
            icon = FanhuaIcons.SwitchCamera,
            selected = state.frontCamera,
            description = stringResource(R.string.flip_camera),
            onClick = { controller.switchCamera() }
        )
    }
}

/** 徕卡风格快捷图标：无底板，选中染红。 */
@Composable
private fun LeicaQuickIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    description: String,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        targetValue = if (selected) Leica.Red else Leica.TextDim,
        animationSpec = tween(durationMillis = 180),
        label = "quickIconColor"
    )
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun timerLabel(seconds: Int): String =
    if (seconds == 0) stringResource(R.string.timer_off) else "${seconds}s"

private fun nextTimerSeconds(seconds: Int): Int = when (seconds) {
    0 -> 3
    3 -> 5
    5 -> 10
    else -> 0
}

/* ------------------------------------------------------------------ 滤镜条 */

/**
 * 滤镜条。
 *
 * 收起态只占一行（色卡 + 当前滤镜名 + 展开提示），展开后出现分组切换与滤镜卡片列表。
 * 色卡用 [FilterGrade] 参数化调色或真实 LUT 采样渲染（见 [FilterSwatch]），
 * 保证卡片颜色与实际出片一致。
 */
@Composable
private fun FilterStrip(
    state: CameraUiState,
    onSelect: (FilterPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var activeGroup by rememberSaveable { mutableStateOf(state.preset.group.name) }
    val group = remember(activeGroup) {
        FilterGroup.entries.firstOrNull { it.name == activeGroup } ?: FilterGroup.BASIC
    }
    val items = remember(group) { FilterPreset.ofGroup(group) }
    val scheme = MaterialTheme.colorScheme

    M3Panel(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Column {
            // 收起态：色卡 + 当前滤镜名 + 展开提示
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterSwatch(
                    preset = state.preset,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(state.preset.labelRes),
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(
                        if (expanded) R.string.filter_strip_collapse else R.string.filter_strip_expand
                    ),
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) FanhuaIcons.ArrowUp else FanhuaIcons.ArrowDown,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    // 分组切换：徕卡风格，文字 + 选中项下方红色短线，无胶囊底
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        FilterGroup.entries.forEach { entry ->
                            val selected = entry == group
                            val color by animateColorAsState(
                                targetValue = if (selected) Leica.White else Leica.TextDim,
                                animationSpec = tween(durationMillis = 180),
                                label = "groupColor"
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(2.dp))
                                    .clickable { activeGroup = entry.name }
                                    .padding(vertical = 7.dp)
                            ) {
                                Text(
                                    text = stringResource(entry.labelRes),
                                    color = color,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(if (selected) 14.dp else 0.dp)
                                        .height(2.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(if (selected) Leica.Red else Color.Transparent)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(items, key = { it.name }) { preset ->
                            FilterCell(
                                preset = preset,
                                selected = state.preset == preset,
                                onSelect = {
                                    onSelect(preset)
                                    expanded = false
                                }
                            )
                        }
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
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 620f),
        label = "filterScale"
    )
    // 徕卡滤镜是唯一允许出现金色的地方：用金色小圆点做品牌标识
    val isLeica = preset.group == FilterGroup.LEICA

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onSelect)
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .then(
                    if (selected) {
                        Modifier.border(2.dp, scheme.primary, RoundedCornerShape(18.dp))
                    } else {
                        Modifier
                    }
                )
                .padding(if (selected) 3.dp else 0.dp)
                .clip(RoundedCornerShape(if (selected) 15.dp else 18.dp))
        ) {
            FilterSwatch(
                preset = preset,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(if (selected) 15.dp else 18.dp))
            )
            // 选中角标：primary 底 + onPrimary 勾
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = FanhuaIcons.Check,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
            // 徕卡标识：左下角金色小圆点
            if (isLeica) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(FanhuaGold)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(preset.labelRes),
            // 徕卡滤镜名用金色文字，其它一律用 primary / 次要文字色
            color = when {
                selected -> scheme.primary
                isLeica -> FanhuaGold
                else -> scheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

/**
 * 滤镜色卡。
 *
 * 优先使用从「相机整合」徕卡包搬来的真实 LUT 预览图（`res/drawable/lut_preview_<asset>.png`），
 * 让色卡与实际出片观感一致，也更贴近原相机；没有对应预览图时（参数化滤镜 / 自定义 LUT
 * 尚未导入）才回退到原来的参数化色块渲染。
 */
@Composable
private fun FilterSwatch(
    preset: FilterPreset,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    // 预览图优先级：徕卡官方实拍图（look_*） > 本地烘焙的 LUT 预览（lut_preview_*）
    val previewRes = remember(preset, ctx) {
        // 1) 徕卡官方实拍预览图
        preset.previewAsset?.let { name ->
            val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            if (id != 0) return@remember id
        }
        // 2) 本地烘焙的 LUT 预览图
        preset.lutAsset?.let { asset ->
            val id = ctx.resources.getIdentifier("lut_preview_$asset", "drawable", ctx.packageName)
            if (id != 0) return@remember id
        }
        null
    }

    if (previewRes != null) {
        // 真实 LUT 预览图：直接铺满色卡（预览图本身已是该滤镜的典型色彩）
        Image(
            painter = painterResource(previewRes),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier
        )
        return
    }

    // 回退：用滤镜调色参数渲染 3x6 色块
    val grade = remember(preset) { FilterGrade.of(preset) }
    val lut = remember(preset, ctx) {
        preset.lutAsset?.let { com.fanhua.camera.lut.LutStore.parseFromRaw(ctx, it) }
    }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val steps = 6
        val cellW = w / steps
        val cellH = h / 3
        val hues = floatArrayOf(
            0.86f, 0.22f, 0.20f,
            0.92f, 0.58f, 0.16f,
            0.84f, 0.80f, 0.24f,
            0.24f, 0.68f, 0.32f,
            0.20f, 0.64f, 0.78f,
            0.22f, 0.34f, 0.84f
        )
        for (row in 0 until 3) {
            val level = when (row) {
                0 -> 0.16f
                1 -> 0.5f
                else -> 0.86f
            }
            for (col in 0 until steps) {
                val base = col * 3
                val r = hues[base] * level
                val g = hues[base + 1] * level
                val b = hues[base + 2] * level
                val out = if (lut != null) {
                    sampleLut(lut, r, g, b)
                } else {
                    grade.apply(r, g, b)
                }
                drawRect(
                    color = Color(out[0], out[1], out[2]),
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

/* ------------------------------------------------------------------ 模式选择 */

@Composable
private fun ModeSelector(
    state: CameraUiState,
    onSelect: (CaptureMode) -> Unit,
    compact: Boolean = false
) {
    // 徕卡一瞬：模式是一行等宽文字，选中项下方一条 2dp 红色短线，无胶囊底。
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 22.dp)
    ) {
        CaptureMode.entries.forEach { mode ->
            val selected = state.mode == mode
            val color by animateColorAsState(
                targetValue = if (selected) Leica.White else Leica.TextDim,
                animationSpec = tween(durationMillis = 180),
                label = "modeColor"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .clickable { onSelect(mode) }
                    .padding(
                        horizontal = if (compact) 4.dp else 6.dp,
                        vertical = 6.dp
                    )
            ) {
                Text(
                    text = stringResource(mode.labelRes),
                    color = color,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .width(if (selected) 18.dp else 0.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (selected) Leica.Red else Color.Transparent)
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ 快门 */

@Composable
private fun ShutterButton(
    state: CameraUiState,
    controller: CameraController
) {
    val haptic = LocalHapticFeedback.current
    var holding by remember { mutableStateOf(false) }
    val videoMode = state.mode == CaptureMode.VIDEO
    // 视频模式直接显示录像键；照片模式在长按后切换为录像键
    val showRecord = videoMode || state.recording || holding
    Crossfade(
        targetState = showRecord,
        animationSpec = tween(durationMillis = 220),
        label = "shutterMode"
    ) { isRecord ->
        if (isRecord) {
            RecordButton(
                recording = state.recording,
                paused = state.recordingPaused,
                durationMs = state.recordingDurationMs,
                onTap = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (state.recording) controller.stopRecording() else controller.startRecording()
                },
                onPauseToggle = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (state.recordingPaused) controller.resumeRecording() else controller.pauseRecording()
                }
            )
        } else {
            PhotoShutter(
                onTap = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    controller.takePhoto()
                },
                onHoldStart = {
                    holding = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    controller.startRecording()
                },
                onHoldEnd = {
                    holding = false
                    controller.stopRecording()
                }
            )
        }
    }
}

/**
 * 拍照快门：Google Camera 风格。
 *
 * 白色实心圆 + 外圈 primary 描边，按下时外圈放大并淡出（Material 3 的「涟漪」语义）。
 */
@Composable
private fun PhotoShutter(
    onTap: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var pressed by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 620f),
        label = "photoShutterScale"
    )
    val ripple = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .size(88.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        val holdJob = scope.launch {
                            delay(550)
                            holding = true
                            onHoldStart()
                        }
                        tryAwaitRelease()
                        holdJob.cancel()
                        pressed = false
                        if (holding) {
                            holding = false
                            onHoldEnd()
                        } else {
                            scope.launch {
                                ripple.snapTo(0f)
                                ripple.animateTo(
                                    1f,
                                    tween(durationMillis = 460, easing = LinearEasing)
                                )
                                ripple.snapTo(0f)
                            }
                            onTap()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // 按下涟漪：primary 圆环向外扩散
        Box(
            modifier = Modifier
                .size(84.dp)
                .graphicsLayer {
                    val p = ripple.value
                    scaleX = 1f + 0.24f * p
                    scaleY = 1f + 0.24f * p
                    alpha = (1f - p) * 0.55f
                }
                .clip(CircleShape)
                .border(2.dp, scheme.primary, CircleShape)
        )
        // 外圈 primary 描边
        Box(
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .border(3.dp, scheme.primary, CircleShape)
                .padding(8.dp)
                .clip(CircleShape)
                // 白色实心圆
                .background(Color.White)
        )
    }
}

@Composable
private fun RecordButton(
    recording: Boolean,
    paused: Boolean,
    durationMs: Long,
    onTap: () -> Unit,
    onPauseToggle: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val corner by animateDpAsState(
        targetValue = if (recording) 22.dp else 9.dp,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "recordCorner"
    )
    val innerSize by animateDpAsState(
        targetValue = if (recording) 32.dp else 46.dp,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "recordSize"
    )
    var previousRecording by remember { mutableStateOf(recording) }
    val stopFlash = remember { Animatable(0f) }
    LaunchedEffect(recording) {
        if (previousRecording && !recording) {
            stopFlash.snapTo(1f)
            stopFlash.animateTo(0f, tween(durationMillis = 440))
        }
        previousRecording = recording
    }
    val pulse by rememberInfiniteTransition(label = "recPulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(680, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recPulseAlpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 录制中显示暂停 / 继续
            AnimatedVisibility(
                visible = recording,
                enter = fadeIn(tween(180)) + scaleIn(tween(200), initialScale = 0.7f),
                exit = fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.7f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainerHigh.copy(alpha = 0.92f))
                        .clickable(onClick = onPauseToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (paused) FanhuaIcons.Play else FanhuaIcons.Pause,
                        contentDescription = stringResource(
                            if (paused) R.string.video_resume else R.string.video_pause
                        ),
                        tint = scheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
            }

            Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                // 停止时的一次性外扩闪光
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .graphicsLayer {
                            val flash = stopFlash.value
                            val s = 1f + flash * 0.08f
                            scaleX = s
                            scaleY = s
                            alpha = 0.35f + 0.65f * flash
                        }
                        .clip(CircleShape)
                        .border(3.dp, scheme.primary.copy(alpha = 0.5f), CircleShape)
                )
                // 外圈 primary 描边
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(3.dp, scheme.primary, CircleShape)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onTap
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // 录像态：红色圆角方块；待录态：白色实心圆
                    Box(
                        modifier = Modifier
                            .size(innerSize)
                            .graphicsLayer {
                                if (recording && !paused) alpha = 0.6f + 0.4f * pulse
                            }
                            .clip(RoundedCornerShape(corner))
                            .background(if (recording) scheme.error else Color.White)
                    )
                }
            }

            AnimatedVisibility(
                visible = recording,
                enter = fadeIn(tween(180)) + scaleIn(tween(200), initialScale = 0.7f),
                exit = fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.7f)
            ) {
                Spacer(modifier = Modifier.width(16.dp))
                // 占位，保持快门居中
                Box(modifier = Modifier.size(44.dp))
            }
        }
        Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
            if (recording) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .graphicsLayer { alpha = if (paused) 0.4f else pulse }
                            .clip(CircleShape)
                            .background(scheme.error)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = formatDuration(durationMs),
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (paused) {
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = stringResource(R.string.video_pause),
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ 镜头与变焦 */

@Composable
private fun LensSwitcher(
    state: CameraUiState,
    onSelect: (String) -> Unit,
    compact: Boolean = false
) {
    if (state.lensOptions.isEmpty()) return
    // 徕卡一瞬：镜头倍数是一排纯文字，选中项为白色并加一个红点，无胶囊底
    Row(
        modifier = Modifier
            .widthIn(max = if (compact) 150.dp else 200.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
    ) {
        state.lensOptions.forEach { lens ->
            val selected = lens.id == state.activeLensId
            val color by animateColorAsState(
                targetValue = if (selected) Leica.White else Leica.TextFaint,
                animationSpec = tween(durationMillis = 180),
                label = "lensColor"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .clickable { onSelect(lens.id) }
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = lens.label,
                    color = color,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(if (selected) 4.dp else 0.dp)
                        .clip(CircleShape)
                        .background(if (selected) Leica.Red else Color.Transparent)
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
    val scheme = MaterialTheme.colorScheme
    val color by animateColorAsState(
        targetValue = if (enabled) scheme.primary else scheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 220),
        label = "livePhotoColor"
    )
    val background by animateColorAsState(
        targetValue = if (enabled) scheme.primary.copy(alpha = 0.18f) else scheme.surfaceContainer.copy(alpha = 0.92f),
        animationSpec = tween(durationMillis = 220),
        label = "livePhotoBg"
    )
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
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
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

/**
 * 变焦条：细线 + primary 圆点，拖拽结束后吸附到最近的镜头档位。
 */
@Composable
private fun ZoomBar(
    controller: CameraController,
    state: CameraUiState
) {
    val range = state.maxZoomRatio - state.minZoomRatio
    if (range <= 0.01f) return
    val scheme = MaterialTheme.colorScheme
    val ticks = remember(state.lensOptions, state.minZoomRatio, state.maxZoomRatio) {
        val fromLenses = state.lensOptions
            .map { it.zoomRatio }
            .filter { it >= state.minZoomRatio && it <= state.maxZoomRatio }
        if (fromLenses.size >= 2) {
            fromLenses.sorted()
        } else {
            listOf(
                state.minZoomRatio,
                (state.minZoomRatio + state.maxZoomRatio) / 2f,
                state.maxZoomRatio
            )
        }
    }
    fun fraction(ratio: Float): Float = ((ratio - state.minZoomRatio) / range).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.zoom_label),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatZoom(state.zoomRatio),
                color = scheme.primary,
                style = MaterialTheme.typography.labelMedium
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
                // 轨道
                drawLine(
                    color = scheme.outlineVariant,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                // 已选区段
                val cx = fraction(state.zoomRatio) * size.width
                drawLine(
                    color = scheme.primary,
                    start = Offset(0f, y),
                    end = Offset(cx, y),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                // 档位刻度
                ticks.forEach { tick ->
                    drawCircle(
                        color = scheme.onSurfaceVariant.copy(alpha = 0.45f),
                        radius = 2.5f,
                        center = Offset(fraction(tick) * size.width, y)
                    )
                }
                // primary 滑块
                drawCircle(color = scheme.primary, radius = 8f, center = Offset(cx, y))
                drawCircle(color = scheme.background, radius = 3.5f, center = Offset(cx, y))
            }
        }
    }
}

/* ------------------------------------------------------------------ 模式专属面板 */

@Composable
private fun VideoSpecBar(
    state: CameraUiState,
    onSelect: (OutputSpec) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        items(state.availableSpecs, key = { "${it.width}x${it.height}@${it.fps}" }) { spec ->
            val selected = state.selectedSpec == spec
            SpecChip(
                label = shortLabel(spec),
                selected = selected,
                onClick = { onSelect(spec) }
            )
        }
    }
}

/** 规格胶囊：选中 primary 18% 底 + primary 文字。 */
@Composable
private fun SpecChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) scheme.primary.copy(alpha = 0.18f)
                else scheme.surfaceContainer.copy(alpha = 0.92f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
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
private fun ProPanel(
    controller: CameraController,
    state: CameraUiState
) {
    val scheme = MaterialTheme.colorScheme
    M3Panel(
        modifier = Modifier.padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column {
            // 曝光补偿
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.pro_exposure),
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(64.dp)
                )
                if (state.exposure.available) {
                    Slider(
                        value = state.exposure.index.toFloat(),
                        onValueChange = { controller.setExposureCompensation(it.toInt()) },
                        valueRange = state.exposure.minIndex.toFloat()..state.exposure.maxIndex.toFloat(),
                        steps = (state.exposure.maxIndex - state.exposure.minIndex - 1).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = scheme.primary,
                            activeTrackColor = scheme.primary,
                            inactiveTrackColor = scheme.outlineVariant
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatEv(state.exposure.ev),
                        color = scheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(46.dp)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.value_unsupported),
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            // 手动对焦
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.pro_manual_focus),
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(64.dp)
                )
                Slider(
                    value = state.manualFocusPosition,
                    onValueChange = controller::setManualFocus,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = if (state.manualFocusActive) scheme.primary else scheme.onSurfaceVariant,
                        activeTrackColor = scheme.primary,
                        inactiveTrackColor = scheme.outlineVariant
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (state.manualFocusActive) scheme.primary.copy(alpha = 0.18f)
                            else scheme.surfaceContainerHigh
                        )
                        .clickable { controller.resetManualFocus() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pro_focus_auto),
                        color = if (state.manualFocusActive) scheme.primary else scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (state.availableSpecs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(state.availableSpecs) { spec ->
                        val selected = state.selectedSpec == spec
                        SpecChip(
                            label = spec.label,
                            selected = selected,
                            onClick = { controller.selectSpec(spec) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuperResInfo(state: CameraUiState) {
    val scheme = MaterialTheme.colorScheme
    val value = state.selectedSpec?.label
        ?: "${state.photoAspect.labelRes.let { "" }}"
    M3Panel(
        modifier = Modifier.padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.capability_superres),
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = if (value.isBlank()) "4K" else value,
                    color = scheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(
                text = stringResource(R.string.settings_superres_desc),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(148.dp)
            )
        }
    }
}

/* ------------------------------------------------------------------ 相册入口 */

@Composable
private fun GalleryButton(
    captureCount: Int,
    lastCaptureUri: String?,
    size: Int,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    // 复用媒体仓库查询最新一张本 App 照片，避免重复实现 MediaStore 查询
    val repository = remember(context) { MediaRepository(context) }
    val thumbnail by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = captureCount,
        key2 = lastCaptureUri
    ) {
        value = withContext(Dispatchers.IO) {
            val uri = lastCaptureUri?.let { Uri.parse(it) } ?: repository.latestPhoto()
            if (uri != null) {
                runCatching {
                    repository.loadThumbnail(uri, isVideo = false, sizePx = 180)?.asImageBitmap()
                }.getOrNull()
            } else {
                null
            }
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "galleryScale"
    )
    Box(
        modifier = Modifier
            .size(size.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surfaceContainer.copy(alpha = 0.92f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val image = thumbnail
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.gallery_title),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
            )
        } else {
            Icon(
                imageVector = FanhuaIcons.Gallery,
                contentDescription = stringResource(R.string.gallery_title),
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/* ------------------------------------------------------------------ 工具函数 */

private fun formatZoom(ratio: Float): String = String.format(Locale.US, "%.1fx", ratio)

private fun formatEv(ev: Float): String =
    if (abs(ev) < 0.01f) "±0" else String.format(Locale.US, "%+.1f", ev)

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

/**
 * 对内置 3D LUT 做三线性采样，返回 0..1 的 RGB。
 *
 * 数据布局与 .cube 一致：索引 = (b*size + g)*size + r，R 变化最快。
 */
private fun sampleLut(
    lut: com.fanhua.camera.lut.LutData,
    r: Float,
    g: Float,
    b: Float
): FloatArray {
    val n = lut.size
    val last = (n - 1).toFloat()
    val fr = (r.coerceIn(0f, 1f)) * last
    val fg = (g.coerceIn(0f, 1f)) * last
    val fb = (b.coerceIn(0f, 1f)) * last
    val r0 = fr.toInt().coerceIn(0, n - 1)
    val g0 = fg.toInt().coerceIn(0, n - 1)
    val b0 = fb.toInt().coerceIn(0, n - 1)
    val r1 = (r0 + 1).coerceAtMost(n - 1)
    val g1 = (g0 + 1).coerceAtMost(n - 1)
    val b1 = (b0 + 1).coerceAtMost(n - 1)
    val tr = fr - r0
    val tg = fg - g0
    val tb = fb - b0

    fun at(rr: Int, gg: Int, bb: Int): FloatArray {
        val idx = ((bb * n + gg) * n + rr) * 3
        if (idx + 2 >= lut.data.size) return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf(lut.data[idx], lut.data[idx + 1], lut.data[idx + 2])
    }

    val c000 = at(r0, g0, b0); val c100 = at(r1, g0, b0)
    val c010 = at(r0, g1, b0); val c110 = at(r1, g1, b0)
    val c001 = at(r0, g0, b1); val c101 = at(r1, g0, b1)
    val c011 = at(r0, g1, b1); val c111 = at(r1, g1, b1)

    val out = FloatArray(3)
    for (k in 0..2) {
        val c00 = c000[k] + (c100[k] - c000[k]) * tr
        val c10 = c010[k] + (c110[k] - c010[k]) * tr
        val c01 = c001[k] + (c101[k] - c001[k]) * tr
        val c11 = c011[k] + (c111[k] - c011[k]) * tr
        val c0 = c00 + (c10 - c00) * tg
        val c1 = c01 + (c11 - c01) * tg
        out[k] = c0 + (c1 - c0) * tb
    }
    return out
}
