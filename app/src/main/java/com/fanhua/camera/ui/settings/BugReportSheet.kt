package com.fanhua.camera.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fanhua.camera.R
import com.fanhua.camera.ui.common.FanhuaButton
import com.fanhua.camera.ui.common.FanhuaButtonStyle
import com.fanhua.camera.ui.common.FanhuaIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bug 反馈面板：Material 3 圆角卡片 + 日志块。
 *
 * 视觉要点：
 * - 卡片 28dp 圆角、surfaceContainerHigh 底、无描边；
 * - 标题图标用 primary 12% 圆底；
 * - 日志块用 surface 底 + 12dp 圆角，等宽感弱化，只保留小字。
 */
@Composable
fun BugReportSheet(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val context = LocalContext.current
    val deviceInfo = remember { buildDeviceInfo(context) }
    val logText by produceState(initialValue = "loading", key1 = Unit) {
        value = withContext(Dispatchers.IO) { readLogcat() }
    }
    var copied by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 22.dp)
            ) {
                // 标题行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = FanhuaIcons.Feedback,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.bug_feedback),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Icon(
                            imageVector = FanhuaIcons.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.bug_feedback_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                BlockLabel(text = stringResource(R.string.device_info))
                Spacer(modifier = Modifier.height(8.dp))
                LogBlock(text = deviceInfo)
                Spacer(modifier = Modifier.height(16.dp))

                BlockLabel(text = stringResource(R.string.app_log))
                Spacer(modifier = Modifier.height(8.dp))
                LogBlock(
                    text = logText,
                    modifier = Modifier.heightIn(max = 200.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FanhuaButton(
                        text = if (copied) stringResource(R.string.copied) else stringResource(R.string.copy_log),
                        onClick = {
                            val clip = ClipData.newPlainText("FanhuaCameraLog", buildReport(deviceInfo, logText))
                            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
                            copied = true
                            Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                        },
                        style = FanhuaButtonStyle.Solid,
                        modifier = Modifier.weight(1f)
                    )
                    FanhuaButton(
                        text = stringResource(R.string.report_issue),
                        onClick = {
                            openUrl(context, "https://github.com/SystemTemp/fanhua-camera/issues/new")
                        },
                        style = FanhuaButtonStyle.Outline,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                // 作者信息行：Material 3 列表项观感
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.author),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "SystemTemp",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { openUrl(context, "https://github.com/SystemTemp") }
                    ) {
                        Text(
                            text = stringResource(R.string.open_homepage),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

/** 小节标题：Material 3 labelMedium + primary 色。 */
@Composable
private fun BlockLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelMedium
    )
}

/** 日志块：surface 底 + 12dp 圆角，可纵向滚动。 */
@Composable
private fun LogBlock(
    text: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        )
    }
}

private fun buildDeviceInfo(context: Context): String = buildString {
    appendLine("App: " + context.getString(R.string.app_name))
    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    appendLine("Build: ${Build.DISPLAY}")
}

private fun readLogcat(maxLines: Int = 400): String {
    return try {
        val pid = Process.myPid()
        val process = ProcessBuilder("logcat", "-d", "-t", maxLines.toString(), "--pid=$pid")
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().readText()
        process.destroy()
        if (text.isBlank()) {
            "no log entries"
        } else {
            text
        }
    } catch (e: Exception) {
        "logcat unavailable: ${e.message}"
    }
}

private fun buildReport(deviceInfo: String, logText: String): String = buildString {
    appendLine("=== FanhuaCamera Bug Report ===")
    appendLine(deviceInfo)
    appendLine("--- logcat (this app) ---")
    append(logText)
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, url, Toast.LENGTH_LONG).show()
    }
}
