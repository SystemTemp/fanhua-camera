package com.fanhua.camera.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fanhua.camera.R
import com.fanhua.camera.data.UpdateManager
import com.fanhua.camera.ui.common.FanhuaButton
import com.fanhua.camera.ui.common.FanhuaButtonStyle
import com.fanhua.camera.ui.common.FanhuaIcons
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaOutline
import com.fanhua.camera.ui.theme.FanhuaStroke
import com.fanhua.camera.ui.theme.FanhuaText
import com.fanhua.camera.ui.theme.FanhuaTextDim
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
        is UpdateUiState.Available -> HyperDialog(
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
                    color = FanhuaText,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.progress}%",
                        color = FanhuaGold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = formatSpeed(state.speedBytesPerSec),
                        color = FanhuaTextDim,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    color = FanhuaGold,
                    trackColor = FanhuaOutline,
                    drawStopIndicator = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }

        is UpdateUiState.Ready -> HyperDialog(
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

@Composable
private fun HyperDialog(
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
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.verticalGradient(listOf(FanhuaGold, Color(0xFFFF9A1A)))
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    color = FanhuaText,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                color = FanhuaGold,
                style = MaterialTheme.typography.labelLarge
            )
            if (!details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = details,
                    color = FanhuaTextDim,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                FanhuaButton(
                    text = dismissText,
                    onClick = onDismiss,
                    style = FanhuaButtonStyle.Outline,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                FanhuaButton(
                    text = confirmText,
                    onClick = onConfirm,
                    style = FanhuaButtonStyle.Solid,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DialogCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1C1D22), Color(0xFF101115))))
            .border(1.dp, FanhuaStroke, RoundedCornerShape(30.dp))
            .padding(horizontal = 22.dp, vertical = 22.dp),
        content = content
    )
}

private fun formatSpeed(bytesPerSec: Long): String {
    val mb = bytesPerSec / 1_000_000.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB/s", mb)
    } else {
        String.format(Locale.US, "%.0f KB/s", bytesPerSec / 1024.0)
    }
}
