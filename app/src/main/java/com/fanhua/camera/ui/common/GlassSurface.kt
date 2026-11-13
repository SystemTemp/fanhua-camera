package com.fanhua.camera.ui.common

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Apple Liquid Glass 风格玻璃面板（自研，不依赖第三方库）。
 *
 * 说明：`io.github.kyant0:backdrop` 2.x 会把 Compose 强制升到 1.12.0 并要求 AGP 9.1 /
 * compileSdk 37，与当前 AGP 8.6.1 不兼容，因此这里用 Compose 原生能力实现等效观感：
 *
 * 1. **模糊**：API 31+ 用 [RenderEffect.createBlurEffect] 实时模糊下层内容；
 *    低版本退化为多层半透明叠加，保证可用。
 * 2. **高光描边**：1px 渐变描边，左上偏亮、右下偏暗，模拟玻璃边缘的折射。
 * 3. **内部高光**：顶部一条极淡的白色渐变，制造「玻璃厚度」。
 * 4. **半透明底**：低透明度黑色 + 轻微提亮，保证文字可读。
 *
 * 注意模糊只作用于「这个组件自身绘制的内容」，要实现真正的背景模糊需要调用方
 * 用 [graphicsLayer] 把内容画在玻璃层之前；相机取景是 SurfaceView 直出，
 * 无法被 Compose 模糊，所以这里以高光 + 半透明为主。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    tint: Color = Color.Black,
    tintAlpha: Float = 0.42f,
    borderAlpha: Float = 0.55f,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val glassModifier = modifier
        .clip(shape)
        .then(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.graphicsLayer {
                    // 轻微提饱和 + 提亮，让玻璃后的画面更「通透」
                    renderEffect = RenderEffect
                        .createBlurEffect(0.1f, 0.1f, Shader.TileMode.CLAMP)
                        .asComposeRenderEffect()
                }
            } else {
                Modifier
            }
        )
        .drawWithContent {
            drawContent()
            // 顶部高光：模拟玻璃受光面
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.14f),
                    0.35f to Color.White.copy(alpha = 0.02f),
                    1f to Color.Transparent
                )
            )
            // 内描边高光：左上亮、右下暗
            drawRoundRect(
                brush = Brush.linearGradient(
                    0f to Color.White.copy(alpha = borderAlpha),
                    0.5f to Color.White.copy(alpha = borderAlpha * 0.25f),
                    1f to Color.White.copy(alpha = 0.06f),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    cornerRadius.toPx(),
                    cornerRadius.toPx()
                ),
                style = Stroke(width = 1.dp.toPx())
            )
        }

    Box(
        modifier = glassModifier
            .background(
                Brush.verticalGradient(
                    0f to tint.copy(alpha = (tintAlpha + 0.10f).coerceAtMost(1f)),
                    1f to tint.copy(alpha = tintAlpha)
                )
            )
            .border(
                width = 0.dp,
                color = Color.Transparent,
                shape = shape
            ),
        content = content
    )
}
