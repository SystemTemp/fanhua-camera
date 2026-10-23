package com.fanhua.camera.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaStroke
import com.fanhua.camera.ui.theme.FanhuaText
import com.fanhua.camera.ui.theme.FanhuaTextDim
import com.fanhua.camera.ui.theme.FanhuaTextFaint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyperSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121317),
        contentColor = FanhuaText,
        scrimColor = Color(0xB3000000),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { SheetGrabber() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            content = content
        )
    }
}

@Composable
private fun SheetGrabber() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .width(38.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0x33FFFFFF))
    )
}

@Composable
fun SheetHeader(
    title: String,
    icon: ImageVector,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 14.dp, top = 2.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0x1AFFC53D)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = FanhuaGold,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
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
                contentDescription = null,
                tint = FanhuaTextDim,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    buttonSize: Dp = 40.dp,
    iconSize: Dp = 19.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "glassIconPress"
    )
    Box(
        modifier = modifier
            .size(buttonSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (active) Color(0x33FFC53D) else Color(0x14FFFFFF))
            .border(1.dp, if (active) FanhuaGold.copy(alpha = 0.6f) else FanhuaStroke, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) FanhuaGold else FanhuaText,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        color = FanhuaTextFaint,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
    )
}

@Composable
fun HyperGroup(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x0FFFFFFF)))
            )
            .border(1.dp, FanhuaStroke, RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(vertical = contentPadding)) { content() }
    }
}

@Composable
fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        thickness = 1.dp,
        color = Color(0x0FFFFFFF)
    )
}

@Composable
fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = FanhuaText,
                style = MaterialTheme.typography.bodyLarge
            )
            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    color = FanhuaTextDim,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        HyperSwitch(checked = checked)
    }
}

@Composable
fun HyperSwitch(checked: Boolean) {
    val track by animateColorAsState(
        targetValue = if (checked) FanhuaGold else Color(0x1FFFFFFF),
        animationSpec = tween(durationMillis = 180),
        label = "switchTrack"
    )
    val offset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "switchOffset"
    )
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(CircleShape)
            .background(track)
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) Color(0xFF241A00) else Color(0xFFE8E9EB))
        )
    }
}

@Composable
fun SelectableRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (selected) FanhuaGold else FanhuaText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = FanhuaTextDim,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (selected) {
            Icon(
                imageVector = FanhuaIcons.Check,
                contentDescription = null,
                tint = FanhuaGold,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ValueRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = FanhuaText,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = FanhuaTextDim,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = FanhuaIcons.ArrowRight,
            contentDescription = null,
            tint = FanhuaTextFaint,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = FanhuaTextDim,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = FanhuaText,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
fun HyperConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1C1D22), Color(0xFF101115))))
                .border(1.dp, FanhuaStroke, RoundedCornerShape(30.dp))
                .padding(horizontal = 22.dp, vertical = 22.dp)
        ) {
            Text(
                text = title,
                color = FanhuaText,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                color = FanhuaTextDim,
                style = MaterialTheme.typography.bodyMedium
            )
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
