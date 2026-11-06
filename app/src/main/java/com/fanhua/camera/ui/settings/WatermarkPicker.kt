package com.fanhua.camera.ui.settings

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fanhua.camera.R
import com.fanhua.camera.media.watermark.RenderMode
import com.fanhua.camera.media.watermark.WatermarkGroup
import com.fanhua.camera.media.watermark.WatermarkRegistry
import com.fanhua.camera.ui.common.FanhuaIcons
import com.fanhua.camera.ui.common.HyperSheet
import com.fanhua.camera.ui.common.SheetHeader
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaText
import com.fanhua.camera.ui.theme.FanhuaTextDim

/**
 * 水印选择器。
 *
 * 列出从「徕卡一瞬」逆向出的全部 85 个水印，按分类分组展示。
 * 选中的 id 存进 [WatermarkRegistry]，拍照时由 WatermarkRenderer 使用。
 */
@Composable
fun WatermarkPicker(
    visible: Boolean,
    onDismiss: () -> Unit,
    onChanged: () -> Unit = {}
) {
    if (!visible) return
    val context = LocalContext.current
    val groups: List<WatermarkGroup> = remember { WatermarkRegistry.catalog(context) }
    var selectedId by remember { mutableStateOf(WatermarkRegistry.currentId(context)) }
    var expandedGroup by remember {
        mutableStateOf(groups.firstOrNull()?.key ?: "")
    }

    HyperSheet(onDismiss = onDismiss) {
        SheetHeader(
            title = stringResource(R.string.watermark_picker_title),
            icon = FanhuaIcons.Sparkle,
            onDismiss = onDismiss
        )
        Text(
            text = stringResource(R.string.watermark_picker_desc, groups.sumOf { it.items.size }),
            color = FanhuaTextDim,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (group in groups) {
                item(key = "hdr_${group.key}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                expandedGroup =
                                    if (expandedGroup == group.key) "" else group.key
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = group.displayName,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${group.items.size}",
                            color = FanhuaTextDim,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (expandedGroup == group.key) {
                                FanhuaIcons.ArrowUp
                            } else {
                                FanhuaIcons.ArrowDown
                            },
                            contentDescription = null,
                            tint = FanhuaTextDim,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (expandedGroup == group.key) {
                    items(group.items, key = { it.id }) { item ->
                        val selected = item.id == selectedId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    }
                                )
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(12.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable {
                                    selectedId = item.id
                                    WatermarkRegistry.select(context, item.id)
                                    onChanged()
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(modeColor(item.renderMode))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = item.displayName,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    FanhuaText
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = modeLabel(item.renderMode),
                                color = FanhuaTextDim,
                                style = MaterialTheme.typography.labelSmall
                            )
                            if (selected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = FanhuaIcons.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun modeLabel(mode: RenderMode): String = when (mode) {
    RenderMode.FRAME -> stringResource(R.string.wm_mode_frame)
    RenderMode.SIDE -> stringResource(R.string.wm_mode_side)
    RenderMode.OVERLAY -> stringResource(R.string.wm_mode_overlay)
    RenderMode.BORDER -> stringResource(R.string.wm_mode_border)
    RenderMode.FILMSTRIP -> stringResource(R.string.wm_mode_filmstrip)
}

private fun modeColor(mode: RenderMode): androidx.compose.ui.graphics.Color = when (mode) {
    RenderMode.FRAME -> FanhuaGold
    RenderMode.SIDE -> androidx.compose.ui.graphics.Color(0xFF8AB4F8)
    RenderMode.OVERLAY -> androidx.compose.ui.graphics.Color(0xFF81C995)
    RenderMode.BORDER -> androidx.compose.ui.graphics.Color(0xFFFDD663)
    RenderMode.FILMSTRIP -> androidx.compose.ui.graphics.Color(0xFFF28B82)
}
