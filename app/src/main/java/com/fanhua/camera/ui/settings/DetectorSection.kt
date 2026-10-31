package com.fanhua.camera.ui.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CameraController
import com.fanhua.camera.camera.detect.DetectorModels
import com.fanhua.camera.camera.detect.DetectorStore
import com.fanhua.camera.camera.detect.ModelDefinition
import com.fanhua.camera.ui.common.FanhuaButton
import com.fanhua.camera.ui.common.FanhuaButtonStyle
import com.fanhua.camera.ui.common.FanhuaIcons
import com.fanhua.camera.ui.common.GroupDivider
import com.fanhua.camera.ui.common.HyperGroup
import com.fanhua.camera.ui.common.SectionHeader
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaGoldDeep
import com.fanhua.camera.ui.theme.FanhuaRose
import com.fanhua.camera.ui.theme.FanhuaStroke
import com.fanhua.camera.ui.theme.FanhuaText
import com.fanhua.camera.ui.theme.FanhuaTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI 自动构图模型管理区。
 *
 * 模型不内嵌在安装包里（APK 体积可控），用户点一下按钮从多源下载；
 * 也支持导入自备的 .tflite（例如自己转换的 YOLOv8n）。
 */
@Composable
fun DetectorSection(
    controller: CameraController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by controller.state.collectAsStateWithLifecycle()

    // 本地文件系统状态不是 Compose 可观察的，用版本号强制刷新
    var refreshTick by remember { mutableStateOf(0) }
    val downloaded = remember(refreshTick, state.detectorModelId, state.detectorDownloadPercent) {
        DetectorStore.downloaded(context)
    }
    var note by remember { mutableStateOf<String?>(null) }
    var busyModelId by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null) {
                note = context.getString(R.string.detector_import_invalid)
                return@launch
            }
            val ok = controller.importDetectorModel(bytes)
            note = context.getString(
                if (ok) R.string.detector_import_done else R.string.detector_import_invalid
            )
            refreshTick++
        }
    }

    Column(modifier = modifier) {
        SectionHeader(stringResource(R.string.detector_title))
        HyperGroup {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // 状态行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.detectorReady) FanhuaGold else FanhuaTextDim
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.detectorReady) {
                            stringResource(R.string.detector_ready)
                        } else {
                            stringResource(R.string.detector_need_model)
                        },
                        color = if (state.detectorReady) FanhuaGold else FanhuaTextDim,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.detector_desc),
                    color = FanhuaTextDim,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // 下载进度
            val percent = state.detectorDownloadPercent
            AnimatedVisibility(visible = percent != null) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.detector_downloading),
                            color = FanhuaGold,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${percent ?: 0}%",
                            color = FanhuaGold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val animated by animateFloatAsState(
                        targetValue = (percent ?: 0) / 100f,
                        animationSpec = tween(durationMillis = 260),
                        label = "downloadProgress"
                    )
                    LinearProgressIndicator(
                        progress = { animated },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = FanhuaGold,
                        trackColor = Color(0x22FFFFFF)
                    )
                }
            }

            // 模型列表
            DetectorModels.all.forEach { definition ->
                GroupDivider()
                ModelRow(
                    definition = definition,
                    isDownloaded = downloaded.contains(definition.id),
                    isActive = state.detectorModelId == definition.id,
                    isBusy = busyModelId == definition.id || (
                        percent != null && state.detectorModelId == null &&
                            busyModelId == definition.id
                        ),
                    onDownload = {
                        busyModelId = definition.id
                        scope.launch {
                            val ok = controller.downloadDetectorModel(definition.id)
                            if (!ok) note = context.getString(R.string.detector_download_failed)
                            busyModelId = null
                            refreshTick++
                        }
                    },
                    onUse = {
                        controller.useDetectorModel(definition.id)
                        refreshTick++
                    },
                    onDelete = {
                        controller.deleteDetectorModel(definition.id)
                        refreshTick++
                    }
                )
            }

            // 自定义模型
            if (downloaded.contains(DetectorModels.CUSTOM_ID)) {
                GroupDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { controller.useDetectorModel(DetectorModels.CUSTOM_ID) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "自定义模型（.tflite）",
                        color = if (state.detectorModelId == DetectorModels.CUSTOM_ID) {
                            FanhuaGold
                        } else {
                            FanhuaText
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.detectorModelId == DetectorModels.CUSTOM_ID) {
                        Icon(
                            imageVector = FanhuaIcons.Check,
                            contentDescription = null,
                            tint = FanhuaGold,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FanhuaButton(
                text = stringResource(R.string.detector_import),
                onClick = {
                    importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                },
                style = FanhuaButtonStyle.Outline,
                modifier = Modifier.weight(1f),
                leadingIcon = FanhuaIcons.ArrowDown
            )
            if (!state.detectorReady && downloaded.isEmpty()) {
                FanhuaButton(
                    text = stringResource(R.string.detector_download),
                    onClick = {
                        val target = DetectorModels.default
                        busyModelId = target.id
                        scope.launch {
                            val ok = controller.downloadDetectorModel(target.id)
                            if (!ok) note = context.getString(R.string.detector_download_failed)
                            busyModelId = null
                            refreshTick++
                        }
                    },
                    style = FanhuaButtonStyle.Solid,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        note?.let { text ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = text,
                color = FanhuaGold,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.detector_download_failed_hint),
            color = FanhuaTextDim,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}

@Composable
private fun ModelRow(
    definition: ModelDefinition,
    isDownloaded: Boolean,
    isActive: Boolean,
    isBusy: Boolean,
    onDownload: () -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isDownloaded) { onUse() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(definition.displayNameRes),
                    color = if (isActive) FanhuaGold else FanhuaText,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (isActive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.detector_model_selected),
                        color = FanhuaGold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0x24FFC53D))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = buildString {
                    append(definition.sizeMb)
                    append(" · ")
                    append(definition.inputSize)
                    append("px · ")
                    append(definition.sources.size)
                    append(" 个下载源")
                },
                color = FanhuaTextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        when {
            isBusy -> {
                Text(
                    text = stringResource(R.string.detector_downloading),
                    color = FanhuaGold,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            isDownloaded -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.detector_model_delete),
                        color = FanhuaRose,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0x1FFF6F5E))
                            .clickable { onDelete() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
            else -> {
                Text(
                    text = stringResource(R.string.detector_download),
                    color = Color(0xFF241A00),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(FanhuaGold, FanhuaGoldDeep))
                        )
                        .clickable { onDownload() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
