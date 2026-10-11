package com.fanhua.camera.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fanhua.camera.R
import com.fanhua.camera.data.UpdateManager
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaOutline
import com.fanhua.camera.ui.theme.FanhuaSurface
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
            var lastTime = System.currentTimeMillis()
            val file = manager.download(info) { progress ->
                val now = System.currentTimeMillis()
                val dt = (now - lastTime).coerceAtLeast(1L)
                val speed = ((progress.downloadedBytes - lastBytes) * 1000L) / dt
                lastBytes = progress.downloadedBytes
                lastTime = now
                state = UpdateUiState.Downloading(progress.progressPercent, speed)
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
        if (manager.install(file)) {
            state = UpdateUiState.Idle
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
        is UpdateUiState.Available -> {
            val info = state.info
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = FanhuaSurface,
                title = {
                    Text(
                        text = stringResource(R.string.update_available) + " " + info.versionName,
                        color = FanhuaText,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Text(
                        text = info.notes.ifBlank { stringResource(R.string.update_available) },
                        color = FanhuaTextDim,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDownload) {
                        Text(text = stringResource(R.string.download_update), color = FanhuaGold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.action_cancel), color = FanhuaTextDim)
                    }
                }
            )
        }

        is UpdateUiState.Downloading -> {
            AlertDialog(
                onDismissRequest = { },
                containerColor = FanhuaSurface,
                title = {
                    Text(
                        text = stringResource(R.string.downloading),
                        color = FanhuaText,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Column {
                        Text(
                            text = state.progress.toString() + "%  " + formatSpeed(state.speedBytesPerSec),
                            color = FanhuaTextDim,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            color = FanhuaGold,
                            trackColor = FanhuaOutline,
                            stopIndicator = {},
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {}
            )
        }

        is UpdateUiState.Ready -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = FanhuaSurface,
                title = {
                    Text(
                        text = stringResource(R.string.download_done),
                        color = FanhuaText,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.install_update),
                        color = FanhuaTextDim,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = onInstall) {
                        Text(text = stringResource(R.string.install_update), color = FanhuaGold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.action_cancel), color = FanhuaTextDim)
                    }
                }
            )
        }

        else -> {
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    val mb = bytesPerSec / 1_000_000.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB/s", mb)
    } else {
        String.format(Locale.US, "%.0f KB/s", bytesPerSec / 1024.0)
    }
}
