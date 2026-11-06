package com.fanhua.camera.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CameraController
import com.fanhua.camera.camera.core.CapabilityReport
import com.fanhua.camera.camera.core.PhotoAspect
import com.fanhua.camera.camera.core.WatermarkMode
import com.fanhua.camera.camera.filters.FilterPreset
import com.fanhua.camera.data.LocaleManager
import com.fanhua.camera.data.SettingsRepository
import com.fanhua.camera.debug.DebugLog
import com.fanhua.camera.lut.LutLoader
import com.fanhua.camera.lut.LutStore
import com.fanhua.camera.ui.common.FanhuaButton
import com.fanhua.camera.ui.common.FanhuaButtonStyle
import com.fanhua.camera.ui.common.FanhuaIcons
import com.fanhua.camera.ui.common.GroupDivider
import com.fanhua.camera.ui.common.HyperConfirmDialog
import com.fanhua.camera.ui.common.HyperGroup
import com.fanhua.camera.ui.common.HyperSheet
import com.fanhua.camera.ui.common.InfoRow
import com.fanhua.camera.ui.common.SectionHeader
import com.fanhua.camera.ui.common.SelectableRow
import com.fanhua.camera.ui.common.SheetHeader
import com.fanhua.camera.ui.common.ToggleRow
import com.fanhua.camera.ui.common.ValueRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 设置页。分组顺序：
 * 界面语言 → 拍摄 → 影像 → AI 自动构图 → 设备能力 → LUT 滤镜 → 调试模式 → 关于 → 恢复默认设置。
 *
 * 视觉为 Google Material 3 设置页风格：
 * - 分组卡片（20dp 圆角、surfaceVariant 底、无描边）
 * - 组内分隔线 outlineVariant，组间留白 24dp
 * - Switch / Slider 用 Material 3 默认样式，强调色 primary
 */
@Composable
fun SettingsSheet(
    visible: Boolean,
    controller: CameraController,
    capability: CapabilityReport,
    onBugReport: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var language by remember { mutableStateOf(LocaleManager.storedTag(context)) }
    val updateController = remember { UpdateController(context, scope) }
    val settingsRepository = remember { SettingsRepository(context) }
    val clipboard = LocalClipboardManager.current
    var debugEnabled by remember { mutableStateOf(DebugLog.enabled) }
    var debugNote by remember { mutableStateOf<String?>(null) }
    var resetConfirming by remember { mutableStateOf(false) }
    var resetNote by remember { mutableStateOf<String?>(null) }

    var showWatermarkPicker by remember { mutableStateOf(false) }
    var lutNames by remember { mutableStateOf(LutStore.listNames(context)) }
    var selectedLut by remember { mutableStateOf(LutStore.path) }
    var lutNote by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val savedName = withContext(Dispatchers.IO) { importLut(context, uri) }
                if (savedName != null) {
                    LutStore.select(context, savedName)
                    settingsRepository.setLutPath(savedName)
                    controller.setPreset(FilterPreset.CUSTOM_LUT)
                    lutNames = LutStore.listNames(context)
                    selectedLut = LutStore.path
                    lutNote = context.getString(R.string.lut_imported)
                } else {
                    lutNote = context.getString(R.string.lut_invalid)
                }
            }
        }
    }

    HyperSheet(onDismiss = onDismiss) {
        SheetHeader(
            title = stringResource(R.string.settings_title),
            icon = FanhuaIcons.Settings,
            onDismiss = onDismiss
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // ---------- 1. 界面语言 ----------
            SectionHeader(stringResource(R.string.settings_language))
            HyperGroup {
                SelectableRow(
                    label = stringResource(R.string.settings_language_system),
                    selected = language == LocaleManager.SYSTEM,
                    onClick = {
                        language = LocaleManager.SYSTEM
                        LocaleManager.apply(context, LocaleManager.SYSTEM)
                    }
                )
                LocaleManager.supportedTags.forEach { tag ->
                    GroupDivider()
                    SelectableRow(
                        label = nativeName(tag),
                        selected = language == tag,
                        onClick = {
                            language = tag
                            LocaleManager.apply(context, tag)
                        }
                    )
                }
            }

            // ---------- 2. 拍摄 ----------
            SectionSpacer()
            SectionHeader(stringResource(R.string.settings_capture))
            HyperGroup {
                // 照片画幅：4 个胶囊横向分段控件，比 4 行列表更省空间
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = stringResource(R.string.settings_photo_aspect),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_photo_aspect_desc),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AspectSegment(
                        selected = state.photoAspect,
                        onSelect = controller::setPhotoAspect
                    )
                }
                GroupDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_mirror),
                    description = stringResource(R.string.settings_mirror_desc),
                    checked = state.mirrorSelfie,
                    onCheckedChange = controller::setMirrorSelfie
                )
                GroupDivider()
                // 徕卡水印：三态可切换（关 / 白边画框 / 照片内叠加）
                Text(
                    text = stringResource(R.string.settings_watermark),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)
                )
                WatermarkMode.entries.forEach { mode ->
                    SelectableRow(
                        label = stringResource(mode.labelRes),
                        selected = state.watermarkMode == mode,
                        onClick = { controller.setWatermarkMode(mode) }
                    )
                }
                // 水印样式选择（85 个徕卡原生样式）
                if (state.watermarkMode != WatermarkMode.OFF) {
                    GroupDivider()
                    SelectableRow(
                        label = stringResource(R.string.watermark_picker_title),
                        selected = false,
                        onClick = { showWatermarkPicker = true }
                    )
                }
                GroupDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_shutter_sound),
                    description = stringResource(R.string.settings_shutter_sound_desc),
                    checked = state.shutterSoundEnabled,
                    onCheckedChange = controller::setShutterSoundEnabled
                )
            }

            // ---------- 3. 影像 ----------
            SectionSpacer()
            SectionHeader(stringResource(R.string.settings_image))
            HyperGroup {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.settings_filter_intensity),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(state.filterIntensity * 100).toInt()}%",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    // Material 3 滑块：轨道 primary，滑块白色
                    Slider(
                        value = state.filterIntensity,
                        onValueChange = controller::setFilterIntensity,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
                GroupDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_stabilization),
                    description = stringResource(R.string.settings_stabilization_desc),
                    checked = state.stabilizationEnabled,
                    onCheckedChange = controller::setStabilizationEnabled
                )
                GroupDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_horizon),
                    description = stringResource(R.string.settings_horizon_desc),
                    checked = state.horizonEnabled,
                    onCheckedChange = controller::setHorizonEnabled
                )
                GroupDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_superres),
                    description = stringResource(R.string.settings_superres_desc),
                    checked = state.superResEnabled,
                    onCheckedChange = controller::setSuperResEnabled
                )
                GroupDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_autocomposition),
                    description = stringResource(R.string.settings_autocomposition_desc),
                    checked = state.autoCompositionEnabled,
                    onCheckedChange = controller::setAutoCompositionEnabled
                )
            }

            // ---------- 3.5 AI 自动构图模型（波浪进度条在此，逻辑不动） ----------
            SectionSpacer()
            DetectorSection(controller = controller)

            // ---------- 4. 设备能力 ----------
            SectionSpacer()
            SectionHeader(stringResource(R.string.settings_capability))
            HyperGroup {
                CapabilityRows(capability)
            }
            HintText(stringResource(R.string.settings_capability_desc))

            // ---------- 5. LUT 滤镜 ----------
            SectionSpacer()
            SectionHeader(stringResource(R.string.settings_lut))
            HyperGroup {
                if (lutNames.isEmpty()) {
                    Text(
                        text = stringResource(R.string.lut_none),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                } else {
                    lutNames.forEachIndexed { index, name ->
                        if (index > 0) GroupDivider()
                        LutRow(
                            name = name,
                            selected = name == selectedLut,
                            onClick = {
                                LutStore.select(context, name)
                                scope.launch { settingsRepository.setLutPath(name) }
                                controller.setPreset(FilterPreset.CUSTOM_LUT)
                                selectedLut = LutStore.path
                                lutNote = null
                            },
                            onRemove = {
                                val file = LutStore.fileOf(context, name)
                                file.delete()
                                if (LutStore.path == name) {
                                    LutStore.clear()
                                    scope.launch { settingsRepository.setLutPath("") }
                                }
                                lutNames = LutStore.listNames(context)
                                selectedLut = LutStore.path
                                lutNote = context.getString(R.string.lut_removed)
                            }
                        )
                    }
                }
            }
            HintText(stringResource(R.string.settings_lut_desc))
            Spacer(modifier = Modifier.height(12.dp))
            FanhuaButton(
                text = stringResource(R.string.lut_import),
                onClick = {
                    importLauncher.launch(
                        arrayOf("application/octet-stream", "application/x-cube", "*/*")
                    )
                },
                style = FanhuaButtonStyle.Outline,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = FanhuaIcons.ArrowDown
            )
            lutNote?.let { note -> NoteText(note) }

            // ---------- 6. 调试模式 ----------
            SectionSpacer()
            SectionHeader(stringResource(R.string.settings_debug))
            HyperGroup {
                ToggleRow(
                    title = stringResource(R.string.settings_debug),
                    description = stringResource(R.string.settings_debug_desc),
                    checked = debugEnabled,
                    onCheckedChange = { value ->
                        debugEnabled = value
                        DebugLog.enabled = value
                        debugNote = null
                        scope.launch { settingsRepository.setDebugEnabled(value) }
                    }
                )
            }
            debugNote?.let { note -> NoteText(note) }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FanhuaButton(
                    text = stringResource(R.string.debug_copy_log),
                    onClick = {
                        val log = DebugLog.snapshot()
                        if (log.isBlank()) {
                            debugNote = context.getString(R.string.debug_log_empty)
                        } else {
                            clipboard.setText(AnnotatedString(log))
                            debugNote = context.getString(R.string.debug_log_copied)
                        }
                    },
                    style = FanhuaButtonStyle.Solid,
                    modifier = Modifier.weight(1f)
                )
                FanhuaButton(
                    text = stringResource(R.string.debug_clear_log),
                    onClick = {
                        DebugLog.clear()
                        debugNote = null
                    },
                    style = FanhuaButtonStyle.Outline,
                    modifier = Modifier.weight(1f)
                )
            }

            // ---------- 7. 关于 ----------
            SectionSpacer()
            SectionHeader(stringResource(R.string.settings_about))
            HyperGroup {
                // 版本号显示
                InfoRow(
                    label = stringResource(R.string.app_name),
                    value = "v${updateController.currentVersion}"
                )
                GroupDivider()
                ValueRow(
                    label = stringResource(R.string.author),
                    value = "SystemTemp",
                    onClick = { openUrl(context, "https://github.com/SystemTemp") }
                )
                GroupDivider()
                ValueRow(
                    label = stringResource(R.string.open_source),
                    value = "fanhua-camera",
                    onClick = { openUrl(context, "https://github.com/SystemTemp/fanhua-camera") }
                )
                GroupDivider()
                // 检查更新：点击行本身触发（selected=false 时不显示勾选）
                SelectableRow(
                    label = stringResource(R.string.check_update),
                    selected = false,
                    onClick = { updateController.check() }
                )
            }
            HintText(stringResource(R.string.settings_about_desc))
            Spacer(modifier = Modifier.height(12.dp))
            FanhuaButton(
                text = stringResource(R.string.bug_feedback),
                onClick = onBugReport,
                style = FanhuaButtonStyle.Outline,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = FanhuaIcons.Feedback
            )

            // ---------- 8. 恢复默认设置（危险操作，置底） ----------
            Spacer(modifier = Modifier.height(28.dp))
            DangerButton(
                text = stringResource(R.string.settings_reset),
                onClick = { resetConfirming = true }
            )
            HintText(stringResource(R.string.settings_reset_desc))
            resetNote?.let { note -> NoteText(note) }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (resetConfirming) {
        HyperConfirmDialog(
            title = stringResource(R.string.settings_reset),
            message = stringResource(R.string.settings_reset_desc),
            confirmText = stringResource(R.string.action_reset),
            dismissText = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = {
                resetConfirming = false
                scope.launch {
                    settingsRepository.resetAll()
                    // 立即把默认值同步回相机，无需重启应用
                    controller.setPreset(FilterPreset.DREAM)
                    controller.setFilterIntensity(1f)
                    controller.setStabilizationEnabled(true)
                    controller.setHorizonEnabled(false)
                    controller.setSuperResEnabled(false)
                    controller.setAutoCompositionEnabled(false)
                    controller.setPhotoAspect(PhotoAspect.FULL)
                    controller.setMirrorSelfie(false)
                    controller.setWatermarkMode(WatermarkMode.FRAME)
                    controller.setShutterSoundEnabled(true)
                    // LUT 记录已被清除，同步清掉内存缓存
                    LutStore.clear()
                    selectedLut = null
                    lutNames = LutStore.listNames(context)
                    // 调试开关不属于 DataStore 的默认值，单独归位
                    DebugLog.enabled = false
                    debugEnabled = false
                    debugNote = null
                    resetNote = context.getString(R.string.settings_reset_done)
                }
            },
            onDismiss = { resetConfirming = false }
        )
    }

    UpdateDialog(
        state = updateController.state,
        onDownload = updateController::download,
        onInstall = updateController::install,
        onDismiss = updateController::dismiss
    )

    // 水印样式选择器（85 个徕卡原生样式）
    WatermarkPicker(
        visible = showWatermarkPicker,
        onDismiss = { showWatermarkPicker = false }
    )
}

/** 分组之间的留白：24dp，Material 3 推荐的组间距。 */
@Composable
private fun SectionSpacer() {
    Spacer(modifier = Modifier.height(24.dp))
}

/** 分组说明文字：12sp、onSurfaceVariant、左内缩对齐卡片内容。 */
@Composable
private fun HintText(text: String) {
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

/** 操作反馈文字：primary 色，替代原先的金色提示。 */
@Composable
private fun NoteText(text: String) {
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

/**
 * 画幅横向分段控件：4 个胶囊，选中态 primary。
 * 用 Material 3 的「filled tonal」观感：选中 16% primary 底 + primary 文字。
 */
@Composable
private fun AspectSegment(
    selected: PhotoAspect,
    onSelect: (PhotoAspect) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PhotoAspect.entries.forEach { aspect ->
            val active = aspect == selected
            val shape = CircleShape
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                    .clickable(role = Role.RadioButton) { onSelect(aspect) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(aspect.labelRes),
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * 危险操作按钮：Material 3 OutlinedButton + error 色描边与文字。
 */
@Composable
private fun DangerButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

private fun importLut(context: android.content.Context, uri: Uri): String? {
    return try {
        val displayName = queryDisplayName(context, uri)
            ?: (uri.lastPathSegment?.substringAfterLast('/') ?: "lut.cube")
        val clean = displayName.ifBlank { "lut.cube" }
        context.contentResolver.openInputStream(uri)?.use { LutLoader.parse(it) }
            ?: return null
        val file = uniqueLutFile(LutStore.dir(context), clean)
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { out -> input.copyTo(out) }
        }
        file.name
    } catch (e: Exception) {
        null
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        }
    } catch (e: Exception) {
        null
    }
}

private fun uniqueLutFile(dir: File, name: String): File {
    val base = name.substringBeforeLast('.')
    val ext = name.substringAfterLast('.', "cube")
    var candidate = File(dir, "$base.$ext")
    var i = 1
    while (candidate.exists()) {
        candidate = File(dir, "${base}_$i.$ext")
        i++
    }
    return candidate
}

/**
 * LUT 行：选中态 primary，移除按钮用 error 色文字按钮（Material 3 TextButton）。
 */
@Composable
private fun LutRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = FanhuaIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        TextButton(onClick = onRemove) {
            Text(
                text = stringResource(R.string.lut_remove),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
    }
}

@Composable
private fun CapabilityRows(capability: CapabilityReport) {
    val rows = buildList {
        add(
            stringResource(R.string.capability_max_video) to
                if (capability.maxVideoWidth > 0) {
                    "${capability.maxVideoWidth}x${capability.maxVideoHeight}"
                } else {
                    stringResource(R.string.value_none)
                }
        )
        add(
            stringResource(R.string.capability_max_photo) to
                if (capability.maxPhotoWidth > 0) {
                    "${capability.maxPhotoWidth}x${capability.maxPhotoHeight}"
                } else {
                    stringResource(R.string.value_none)
                }
        )
        add(stringResource(R.string.capability_encode_4k30) to yesNo(capability.fourK30Encode))
        add(stringResource(R.string.capability_encode_4k60) to yesNo(capability.fourK60Encode))
        add(stringResource(R.string.capability_encode_4k120) to yesNo(capability.fourK120Encode))
        add(stringResource(R.string.capability_hevc) to yesNo(capability.hevcSupported))
        add(
            stringResource(R.string.capability_highspeed) to
                if (capability.highSpeedProfiles.isEmpty()) {
                    stringResource(R.string.value_unsupported)
                } else {
                    stringResource(R.string.value_supported)
                }
        )
        add(
            stringResource(R.string.capability_superres) to
                if (capability.superResTargetWidth > 0) {
                    "${capability.superResTargetWidth}x${capability.superResTargetHeight} @${capability.superResFps}FPS"
                } else {
                    stringResource(R.string.value_none)
                }
        )
        add(
            stringResource(R.string.capability_stabilization) to
                if (capability.stabilizationModes.isEmpty()) {
                    stringResource(R.string.value_none)
                } else {
                    capability.stabilizationModes.joinToString(" / ")
                }
        )
        if (capability.highSpeedProfiles.isNotEmpty()) {
            add(
                stringResource(R.string.capability_slow_motion) to
                    capability.highSpeedProfiles.take(4).joinToString("  ")
            )
        }
    }
    rows.forEachIndexed { index, row ->
        if (index > 0) GroupDivider()
        InfoRow(label = row.first, value = row.second)
    }
    if (capability.notes.isNotEmpty()) {
        GroupDivider()
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.capability_notes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
            capability.notes.forEach { note ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = note,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun yesNo(value: Boolean): String =
    if (value) stringResource(R.string.value_supported) else stringResource(R.string.value_unsupported)

private fun nativeName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    val name = locale.getDisplayName(locale)
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}
