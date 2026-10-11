package com.fanhua.camera.ui.common

import android.os.Build
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaOutline

@Composable
fun FrostedPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(modifier = modifier.clip(shape)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(20.dp) else Modifier)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xE0101216), Color(0xCC14161A))
                    )
                )
        )
        Column(modifier = Modifier.padding(contentPadding)) { content() }
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color(0x59FFFFFF), Color.Transparent)
                        )
                    )
            )
        }
    }
}

@Composable
fun FrostedChip(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(50)
    val background = if (selected) Color(0x33E8C39E) else Color(0x1AFFFFFF)
    val outline = if (selected) FanhuaGold else FanhuaOutline
    Row(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(1.dp, outline, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
            .height(1.dp)
            .background(Color(0x33FFFFFF))
    )
}