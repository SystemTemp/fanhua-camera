package com.fanhua.camera.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 按钮样式。
 * - Solid：主按钮，primary 填充（对应 Material 3 Button）
 * - Outline：次按钮，描边（对应 Material 3 OutlinedButton）
 */
enum class FanhuaButtonStyle { Outline, Solid }

/**
 * 通用按钮：Material 3 胶囊按钮。
 *
 * 配色全部取自 colorScheme：主按钮 primary/onPrimary，次按钮 outline 描边 + primary 文字。
 * 原先的金色渐变已移除（金色只留给徕卡滤镜标识）。
 */
@Composable
fun FanhuaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: FanhuaButtonStyle = FanhuaButtonStyle.Outline,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val iconAndText: @Composable () -> Unit = {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
    when (style) {
        FanhuaButtonStyle.Solid -> Button(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            enabled = enabled,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            iconAndText()
        }

        FanhuaButtonStyle.Outline -> OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            enabled = enabled,
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            iconAndText()
        }
    }
}
