package com.fanhua.camera.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 配色：Google Material 3 风格。
 *
 * 纯黑为底、中性灰分层、蓝紫为强调色。
 * 金色仅保留给「徕卡」相关元素做品牌识别，其余交互控件统一用 primary。
 */
val FanhuaInk = Color(0xFF000000)
val FanhuaSurface = Color(0xFF131316)
val FanhuaSurfaceHigh = Color(0xFF1E1F23)
val FanhuaSurfaceHighest = Color(0xFF2A2B30)

/** Material 3 强调色：亮蓝紫。 */
val FanhuaPrimary = Color(0xFF8AB4F8)
val FanhuaPrimaryDeep = Color(0xFF669DF6)
val FanhuaSecondary = Color(0xFF81C995)
val FanhuaTertiary = Color(0xFFFDD663)

/** 徕卡专属金色（仅用于徕卡滤镜标识）。 */
val FanhuaGold = Color(0xFFFFC53D)
val FanhuaGoldDeep = Color(0xFFFF9A1A)
val FanhuaRose = Color(0xFFF28B82)
val FanhuaBlue = FanhuaPrimary

val FanhuaText = Color(0xFFE8EAED)
val FanhuaTextDim = Color(0xFF9AA0A6)
val FanhuaTextFaint = Color(0xFF5F6368)
val FanhuaOutline = Color(0xFF3C4043)
val FanhuaStroke = Color(0x1FFFFFFF)
val FanhuaGlass = Color(0x14FFFFFF)
val FanhuaGlassHigh = Color(0x1FFFFFFF)
val FanhuaShadow = Color(0x99000000)

val HyperRadiusLarge = 28.dp
val HyperRadiusMedium = 20.dp
val HyperRadiusSmall = 12.dp

private val FanhuaColorScheme = darkColorScheme(
    primary = FanhuaPrimary,
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF1A3A6B),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = FanhuaSecondary,
    onSecondary = Color(0xFF0A3818),
    tertiary = FanhuaTertiary,
    onTertiary = Color(0xFF3D2E00),
    background = FanhuaInk,
    onBackground = FanhuaText,
    surface = FanhuaSurface,
    onSurface = FanhuaText,
    surfaceVariant = FanhuaSurfaceHigh,
    onSurfaceVariant = FanhuaTextDim,
    surfaceContainer = FanhuaSurfaceHigh,
    surfaceContainerHigh = FanhuaSurfaceHighest,
    outline = FanhuaOutline,
    outlineVariant = Color(0xFF2A2B30),
    error = FanhuaRose,
    onError = Color(0xFF3B0907)
)

private val FanhuaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

private val FanhuaTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 32.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 24.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp
    )
)

@Composable
fun FanhuaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FanhuaColorScheme,
        typography = FanhuaTypography,
        shapes = FanhuaShapes,
        content = content
    )
}
