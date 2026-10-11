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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fanhua.camera.R
import com.fanhua.camera.ui.common.FanhuaButton
import com.fanhua.camera.ui.common.FanhuaButtonStyle
import com.fanhua.camera.ui.common.FanhuaDivider
import com.fanhua.camera.ui.common.FanhuaIcons
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaOutline
import com.fanhua.camera.ui.theme.FanhuaSurface
import com.fanhua.camera.ui.theme.FanhuaText
import com.fanhua.camera.ui.theme.FanhuaTextDim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugReportSheet(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val deviceInfo = remember { buildDeviceInfo(context) }
    val logText = remember { readLogcat() }
    var copied by remember { mutableStateOf(false) }

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
                    imageVector = FanhuaIcons.Feedback,
                    contentDescription = null,
                    tint = FanhuaGold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.bug_feedback),
                    color = FanhuaText,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = FanhuaIcons.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = FanhuaTextDim,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDismiss)
                        .padding(7.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            FanhuaDivider(width = 46.dp)
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.bug_feedback_desc),
                color = FanhuaTextDim,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.device_info),
                color = FanhuaGold,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            LogBlock(text = deviceInfo)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.app_log),
                color = FanhuaGold,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            LogBlock(
                text = logText,
                modifier = Modifier.heightIn(max = 240.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
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

            Spacer(modifier = Modifier.height(20.dp))
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
            Spacer(modifier = Modifier.height(28.dp))
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
