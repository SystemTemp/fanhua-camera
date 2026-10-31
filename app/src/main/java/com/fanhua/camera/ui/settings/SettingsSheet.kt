package com.fanhua.camera.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CameraController
import com.fanhua.camera.camera.core.CapabilityReport
import com.fanhua.camera.camera.core.PhotoAspect
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
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaRose
import com.fanhua.camera.ui.theme.FanhuaStroke
import com.fanhua.camera.ui.theme.FanhuaText
import com.fanhua.camera.ui.theme.FanhuaTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 设置页。分组顺序：
 * 界面语言 → 拍摄 → 影像 → 设备能力 → LUT 滤镜 → 调试模式 → 关于 → 恢复默认设置。
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
                .padding(horizontal = 18.dp)
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
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(stringResource(R.string.settings_capture))
            HyperGroup {
                // 照片画幅：4 个胶囊横向分段控件，比 4 行列表更省空间
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_photo_aspect),
                        color = FanhuaText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = stringResource(R.string.settings_photo_aspect_desc),
                        color = FanhuaTextDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
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
                ToggleRow(
                    title = stringResource(R.string.settings_watermark),
                    description = stringResource(R.string.settings_watermark_desc),
                    checked = state.watermarkEnabled,
                    onCheckedChange = controller::setWatermarkEnabled
                )
                GroupDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_shutter_sound),
                    description = stringResource(R.string.settings_shutter_sound_desc),
                    checked = state.shutterSoundEnabled,
                    onCheckedChange = controller::setShutterSoundEnabled
                )
            }

            // ---------- 3. 影像 ----------
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(stringResource(R.string.settings_image))
            HyperGroup {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        text = stringResource(R.string.settings_filter_intensity),
                        color = FanhuaText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            value = state.filterIntensity,
                            onValueChange = controller::setFilterIntensity,
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${(state.filterIntensity * 100).toInt()}%",
                            color = FanhuaGold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
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

            // ---------- 3.5 AI 自动构图模型 ----------
            Spacer(modifier = Modifier.height(20.dp))
            DetectorSection(controller = controller)

            // ---------- 4. 设备能力 ----------
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(stringResource(R.string.settings_capability))
            HyperGroup {
                CapabilityRows(capability)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_capability_desc),
                color = FanhuaTextDim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            // ---------- 5. LUT 滤镜 ----------
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(stringResource(R.string.settings_lut))
            HyperGroup {
                if (lutNames.isEmpty()) {
                    Text(
                        text = stringResource(R.string.lut_none),
                        color = FanhuaTextDim,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
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
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_lut_desc),
                color = FanhuaTextDim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
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
            lutNote?.let { note ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = note,
                    color = FanhuaGold,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            // ---------- 6. 调试模式 ----------
            Spacer(modifier = Modifier.height(20.dp))
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
            debugNote?.let { note ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = note,
                    color = FanhuaGold,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Spacer(modifier = Modifier.height(20.dp))
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
                // 检查更新：点击行本身触发（selected=false 时不显示勾选与箭头）
                SelectableRow(
                    label = stringResource(R.string.check_update),
                    selected = false,
                    onClick = { updateController.check() }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_about_desc),
                color = FanhuaTextDim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            FanhuaButton(
                text = stringResource(R.string.bug_feedback),
                onClick = onBugReport,
                style = FanhuaButtonStyle.Outline,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = FanhuaIcons.Feedback
            )

            // ---------- 8. 恢复默认设置（危险操作，置底） ----------
            Spacer(modifier = Modifier.height(24.dp))
            DangerButton(
                text = stringResource(R.string.settings_reset),
                onClick = { resetConfirming = true }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_reset_desc),
                color = FanhuaTextDim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            resetNote?.let { note ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = note,
                    color = FanhuaGold,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (resetConfirming) {
        HyperConfirmDialog(
            title = stringResource(R.string.settings_reset),
            message = stringResource(R.string.settings_reset_desc),
            confirmText = stringResource(R.string.action_reset),
            dismissText = stringResource(R.string.action_cancel),
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
                    controller.setWatermarkEnabled(true)
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
}

/**
 * 画幅横向分段控件：4 个胶囊，选中态金色。
 */
@Composable
private fun AspectSegment(
    selected: PhotoAspect,
    onSelect: (PhotoAspect) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PhotoAspect.entries.forEach { aspect ->
            val active = aspect == selected
            val shape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (active) Color(0x33FFC53D) else Color(0x14FFFFFF))
                    .border(1.dp, if (active) FanhuaGold else FanhuaStroke, shape)
                    .clickable { onSelect(aspect) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(aspect.labelRes),
                    color = if (active) FanhuaGold else FanhuaTextDim,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * 危险操作按钮：沿用 Outline 描边样式，文字与描边为警示红。
 */
@Composable
private fun DangerButton(
    text: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x1FFF6F5E))
            .border(1.dp, FanhuaRose.copy(alpha = 0.55f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            color = FanhuaRose,
            style = MaterialTheme.typography.labelLarge
        )
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
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = if (selected) FanhuaGold else FanhuaText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = FanhuaIcons.Check,
                contentDescription = null,
                tint = FanhuaGold,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = stringResource(R.string.lut_remove),
            color = FanhuaRose,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x1FFF6F5E))
                .clickable { onRemove() }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        )
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
                color = FanhuaTextDim,
                style = MaterialTheme.typography.labelSmall
            )
            capability.notes.forEach { note ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = note,
                    color = FanhuaRose,
                    style = MaterialTheme.typography.labelSmall
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
