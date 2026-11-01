package com.fanhua.camera.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fanhua.camera.R
import com.fanhua.camera.data.UpdateManager
import com.fanhua.camera.ui.common.FanhuaIcons
import com.fanhua.camera.ui.common.WaveProgressBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val info: UpdateManager.UpdateInfo) : UpdateUiState
    data object Latest : UpdateUiState
    data object Failed : UpdateUiState
    data class Downloading(val progress: Int, val speedBytesPerSec: Long) : UpdateUiState
    data class Ready(val file: File) : UpdateUiState
}

class UpdateController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val manager = UpdateManager(context)
    var state by mutableStateOf<UpdateUiState>(UpdateUiState.Idle)
        private set

    val currentVersion: String
        get() = manager.currentVersionName()

    fun check(silent: Boolean = false) {
        if (state == UpdateUiState.Checking) return
        scope.launch {
            state = UpdateUiState.Checking
            val info = manager.checkLatest()
            state = when {
                info == null -> {
                    if (!silent) toast(R.string.update_failed)
                    UpdateUiState.Failed
                }
                !manager.hasUpdate(info) -> {
                    if (!silent) toast(R.string.update_latest)
                    UpdateUiState.Latest
                }
                else -> UpdateUiState.Available(info)
            }
        }
    }

    fun download() {
        val info = (state as? UpdateUiState.Available)?.info ?: return
        scope.launch {
            state = UpdateUiState.Downloading(0, 0L)
            var lastBytes = 0L
            var lastTime = 0L
            var avgSpeed = 0L
            var samples = 0
            val file = manager.download(info) { progress ->
                val now = System.currentTimeMillis()
                if (lastTime > 0) {
                    val dt = (now - lastTime).coerceAtLeast(1L)
                    val instant = ((progress.downloadedBytes - lastBytes) * 1000L) / dt
                    avgSpeed = if (samples == 0) instant else (avgSpeed * 3 + instant) / 4
                    samples++
                }
                lastBytes = progress.downloadedBytes
                lastTime = now
                state = UpdateUiState.Downloading(progress.progressPercent, avgSpeed)
            }
            state = if (file != null) {
                UpdateUiState.Ready(file)
            } else {
                toast(R.string.download_failed)
                UpdateUiState.Failed
            }
        }
    }

    fun install() {
        val file = (state as? UpdateUiState.Ready)?.file ?: return
        if (manager.needsInstallPermission()) {
            manager.requestInstallPermission()
            toast(R.string.install_permission_hint)
            return
        }
        if (manager.install(file)) {
            state = UpdateUiState.Idle
        } else {
            toast(R.string.update_failed)
        }
    }

    fun dismiss() {
        state = UpdateUiState.Idle
    }

    private fun toast(resId: Int) {
        try {
            Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
        }
    }
}

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is UpdateUiState.Available -> UpdateCard(
            title = stringResource(R.string.update_available),
            message = state.info.versionName,
            details = state.info.notes.ifBlank { stringResource(R.string.update_available) },
            confirmText = stringResource(R.string.download_update),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = onDownload,
            onDismiss = onDismiss
        )

        is UpdateUiState.Downloading -> Dialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            DialogCard {
                Text(
                    text = stringResource(R.string.downloading),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.progress}%",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // 下载速度：实时显示，速度为 0 时显示占位符
                    Text(
                        text = formatSpeed(state.speedBytesPerSec),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                // 波浪进度条：与 AI 模型下载保持同一套进度语言
                val animated by animateFloatAsState(
                    targetValue = state.progress / 100f,
                    animationSpec = tween(durationMillis = 300),
                    label = "updateProgress"
                )
                WaveProgressBar(
                    progress = animated,
                    modifier = Modifier.fillMaxWidth(),
                    height = 6.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        is UpdateUiState.Ready -> UpdateCard(
            title = stringResource(R.string.download_done),
            message = stringResource(R.string.install_update),
            details = null,
            confirmText = stringResource(R.string.install_update),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = onInstall,
            onDismiss = onDismiss
        )

        else -> {
        }
    }
}

/**
 * Material 3 圆角对话框：标题 + 版本号 + 更新说明 + 文字按钮 / 填充按钮。
 */
@Composable
private fun UpdateCard(
    title: String,
    message: String,
    details: String?,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = FanhuaIcons.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            if (!details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = details,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = dismissText,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(text = confirmText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** 对话框卡片容器：Material 3 surfaceContainerHigh 底 + 28dp 圆角，无描边。 */
@Composable
private fun DialogCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            content = content
        )
    }
}

/** 字节/秒 → 人类可读；速度为 0 时返回占位符「—」。 */
private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return "—"
    val mb = bytesPerSec / 1_000_000.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB/s", mb)
    } else {
        String.format(Locale.US, "%.0f KB/s", bytesPerSec / 1024.0)
    }
}
