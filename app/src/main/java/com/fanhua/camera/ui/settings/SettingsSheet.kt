package com.fanhua.camera.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.fanhua.camera.camera.filters.FilterPreset
import com.fanhua.camera.data.LocaleManager
import com.fanhua.camera.data.SettingsRepository
import com.fanhua.camera.debug.DebugLog
import com.fanhua.camera.lut.LutLoader
import com.fanhua.camera.lut.LutStore
import com.fanhua.camera.ui.common.FanhuaButton
import com.fanhua.camera.ui.common.FanhuaButtonStyle
import com.fanhua.camera.ui.common.FanhuaDivider
import com.fanhua.camera.ui.common.FanhuaIcons
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaOutline
import com.fanhua.camera.ui.theme.FanhuaRose
import com.fanhua.camera.ui.theme.FanhuaSurface
import com.fanhua.camera.ui.theme.FanhuaText
import com.fanhua.camera.ui.theme.FanhuaTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var language by remember { mutableStateOf(LocaleManager.storedTag(context)) }
    val updateController = remember { UpdateController(context, scope) }
    val settingsRepository = remember { SettingsRepository(context) }
    val clipboard = LocalClipboardManager.current
    var debugEnabled by remember { mutableStateOf(DebugLog.enabled) }
    var debugNote by remember { mutableStateOf<String?>(null) }

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
                    // 若当前正使用自定义 LUT，刷新实时预览
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FanhuaSurface,
        scrimColor = Color(0xB3000000),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = FanhuaIcons.Settings,
                    contentDescription = null,
                    tint = FanhuaGold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.settings_title),
                    color = FanhuaText,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = FanhuaIcons.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = FanhuaTextDim,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            FanhuaDivider(width = 46.dp)
            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle(stringResource(R.string.settings_language))
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                LanguageRow(
                    label = stringResource(R.string.settings_language_system),
                    selected = language == LocaleManager.SYSTEM
                ) {
                    language = LocaleManager.SYSTEM
                    LocaleManager.apply(context, LocaleManager.SYSTEM)
                }
                LocaleManager.supportedTags.forEach { tag ->
                    LanguageRow(
                        label = nativeName(tag),
                        selected = language == tag
                    ) {
                        language = tag
                        LocaleManager.apply(context, tag)
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            SectionTitle(stringResource(R.string.settings_filter_intensity))
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = state.filterIntensity,
                onValueChange = controller::setFilterIntensity,
                valueRange = 0f..1f
            )

            Spacer(modifier = Modifier.height(14.dp))
            ToggleRow(
                title = stringResource(R.string.settings_stabilization),
                description = stringResource(R.string.settings_stabilization_desc),
                checked = state.stabilizationEnabled,
                onCheckedChange = controller::setStabilizationEnabled
            )
            ToggleRow(
                title = stringResource(R.string.settings_horizon),
                description = stringResource(R.string.settings_horizon_desc),
                checked = state.horizonEnabled,
                onCheckedChange = controller::setHorizonEnabled
            )
            ToggleRow(
                title = stringResource(R.string.settings_superres),
                description = stringResource(R.string.settings_superres_desc),
                checked = state.superResEnabled,
                onCheckedChange = controller::setSuperResEnabled
            )
            ToggleRow(
                title = stringResource(R.string.settings_autocomposition),
                description = stringResource(R.string.settings_autocomposition_desc),
                checked = state.autoCompositionEnabled,
                onCheckedChange = controller::setAutoCompositionEnabled
            )

            Spacer(modifier = Modifier.height(22.dp))
            SectionTitle(stringResource(R.string.settings_capability))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_capability_desc),
                color = FanhuaTextDim,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(10.dp))
            CapabilityBlock(capability)

            Spacer(modifier = Modifier.height(22.dp))
            SectionTitle(stringResource(R.string.settings_lut))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_lut_desc),
                color = FanhuaTextDim,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (lutNames.isEmpty()) {
                Text(
                    text = stringResource(R.string.lut_none),
                    color = FanhuaTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    lutNames.forEach { name ->
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
            FanhuaButton(
                text = stringResource(R.string.lut_import),
                onClick = {
                    importLauncher.launch(
                        arrayOf("application/octet-stream", "application/x-cube", "*/*")
                    )
                },
                style = FanhuaButtonStyle.Outline,
                modifier = Modifier.fillMaxWidth()
            )
            lutNote?.let { note ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = note,
                    color = FanhuaGold,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.lut_use_tip),
                color = FanhuaTextDim,
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(modifier = Modifier.height(22.dp))
            SectionTitle(stringResource(R.string.settings_debug))
            Spacer(modifier = Modifier.height(4.dp))
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
            debugNote?.let { note ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = note,
                    color = FanhuaGold,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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

            Spacer(modifier = Modifier.height(22.dp))
            SectionTitle(stringResource(R.string.settings_about))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_about_desc),
                color = FanhuaTextDim,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(14.dp))
            AboutRow(
                label = stringResource(R.string.author),
                value = "SystemTemp",
                onClick = { openUrl(context, "https://github.com/SystemTemp") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            AboutRow(
                label = stringResource(R.string.open_source),
                value = "fanhua-camera",
                onClick = { openUrl(context, "https://github.com/SystemTemp/fanhua-camera") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            AboutRow(
                label = stringResource(R.string.check_update),
                value = updateController.currentVersion,
                onClick = { updateController.check() }
            )

            Spacer(modifier = Modifier.height(14.dp))
            FanhuaButton(
                text = stringResource(R.string.bug_feedback),
                onClick = onBugReport,
                style = FanhuaButtonStyle.Solid,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    UpdateDialog(
        state = updateController.state,
        onDownload = updateController::download,
        onInstall = updateController::install,
        onDismiss = updateController::dismiss
    )
}

@Composable
private fun AboutRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x14FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = FanhuaTextDim,
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            color = FanhuaGold,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = FanhuaIcons.ArrowRight,
            contentDescription = null,
            tint = FanhuaTextDim,
            modifier = Modifier.size(14.dp)
        )
    }
}

private fun importLut(context: android.content.Context, uri: Uri): String? {
    return try {
        val displayName = queryDisplayName(context, uri)
            ?: (uri.lastPathSegment?.substringAfterLast('/') ?: "lut.cube")
        val clean = displayName.ifBlank { "lut.cube" }
        // 先验证文件可解析
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
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0x22E8C39E) else Color(0x0FFFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.lut_remove),
            color = FanhuaRose,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onRemove() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = FanhuaGold,
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun LanguageRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Color(0x22E8C39E) else Color(0x0FFFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
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
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = FanhuaText,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = FanhuaTextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = FanhuaGold,
                uncheckedThumbColor = FanhuaTextDim,
                uncheckedTrackColor = FanhuaOutline
            )
        )
    }
}

@Composable
private fun CapabilityBlock(capability: CapabilityReport) {
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
            stringResource(R.string.capability_encode_4k30) to yesNo(capability.fourK30Encode)
        )
        add(
            stringResource(R.string.capability_encode_4k60) to yesNo(capability.fourK60Encode)
        )
        add(
            stringResource(R.string.capability_encode_4k120) to yesNo(capability.fourK120Encode)
        )
        add(
            stringResource(R.string.capability_hevc) to yesNo(capability.hevcSupported)
        )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.first,
                    color = FanhuaTextDim,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = row.second,
                    color = FanhuaGold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (index != rows.lastIndex) {
                HorizontalDivider(color = Color(0x1AFFFFFF))
            }
        }
        if (capability.notes.isNotEmpty()) {
            HorizontalDivider(color = Color(0x1AFFFFFF))
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.capability_notes),
                    color = FanhuaTextDim,
                    style = MaterialTheme.typography.labelSmall
                )
                capability.notes.forEach { note ->
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = note,
                        color = FanhuaRose,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
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
