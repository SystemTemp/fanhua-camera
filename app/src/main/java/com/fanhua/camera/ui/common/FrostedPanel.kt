package com.fanhua.camera.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 面板容器：Material 3 filled card 观感（surfaceVariant 底、无描边、无玻璃渐变）。
 *
 * 相机底栏仍在引用，保留原签名；配色已从「玻璃 + 白描边」改为 Material 3 填充式。
 */
@Composable
fun FrostedPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

/**
 * 胶囊标签：选中态 primary 16% 底 + primary 文字，未选中 surface 底 + onSurfaceVariant。
 */
@Composable
fun FrostedChip(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** 细分隔条：outlineVariant 色。 */
@Composable
fun FanhuaDivider(
    modifier: Modifier = Modifier,
    width: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .width(width)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
