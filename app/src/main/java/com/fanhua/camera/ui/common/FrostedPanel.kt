package com.fanhua.camera.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaStroke

@Composable
fun FrostedPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x26FFFFFF), Color(0x0FFFFFFF), Color(0x14FFFFFF))
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x0AFFFFFF))),
                shape
            )
    ) {
        Column(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

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
                if (selected) Color(0x33FFC53D) else Color(0x14FFFFFF)
            )
            .border(
                1.dp,
                if (selected) FanhuaGold.copy(alpha = 0.85f) else FanhuaStroke,
                shape
            )
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

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
            .background(Color(0x33FFFFFF))
    )
}
