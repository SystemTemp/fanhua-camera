package com.fanhua.camera.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaOutline
import com.fanhua.camera.ui.theme.FanhuaText

enum class FanhuaButtonStyle { Outline, Solid }

@Composable
fun FanhuaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: FanhuaButtonStyle = FanhuaButtonStyle.Outline,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 620f),
        label = "fanhuaButtonScale"
    )
    val shape = RoundedCornerShape(50)
    val backgroundColor = if (style == FanhuaButtonStyle.Solid) FanhuaGold else Color.Transparent
    val foregroundColor = if (style == FanhuaButtonStyle.Solid) MaterialTheme.colorScheme.onPrimary else FanhuaText
    val outlineColor = if (style == FanhuaButtonStyle.Solid) Color.Transparent else FanhuaOutline
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.35f))
            .border(
                1.dp,
                if (enabled) outlineColor else outlineColor.copy(alpha = 0.45f),
                shape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = foregroundColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = if (enabled) foregroundColor else foregroundColor.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelLarge
        )
    }
}