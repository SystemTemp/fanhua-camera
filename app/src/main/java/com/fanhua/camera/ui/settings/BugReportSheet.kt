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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fanhua.camera.R
import com.fanhua.camera.ui.common.FanhuaButton
import com.fanhua.camera.ui.common.FanhuaButtonStyle
import com.fanhua.camera.ui.common.FanhuaIcons
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaStroke
import com.fanhua.camera.ui.theme.FanhuaText
import com.fanhua.camera.ui.theme.FanhuaTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1C1D22), Color(0xFF101115))))
                .border(1.dp, FanhuaStroke, RoundedCornerShape(30.dp))
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFC53D)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = FanhuaIcons.Feedback,
                        contentDescription = null,
                        tint = FanhuaGold,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.bug_feedback),
                    color = FanhuaText,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x14FFFFFF))
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

            Text(
                text = stringResource(R.string.bug_feedback_desc),
                color = FanhuaTextDim,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.device_info),
                color = FanhuaGold,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            LogBlock(text = deviceInfo)
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.app_log),
                color = FanhuaGold,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            LogBlock(
                text = logText,
                modifier = Modifier.heightIn(max = 220.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable { openUrl(context, "https://github.com/SystemTemp") }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.author),
                    color = FanhuaTextDim,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "SystemTemp",
                    color = FanhuaGold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.open_homepage),
                    color = FanhuaTextDim,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun LogBlock(
    text: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x12000000))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            color = FanhuaTextDim,
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
