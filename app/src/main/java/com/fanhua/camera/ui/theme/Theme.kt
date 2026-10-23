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

val FanhuaInk = Color(0xFF000000)
val FanhuaSurface = Color(0xFF0E0F12)
val FanhuaSurfaceHigh = Color(0xFF1A1C20)
val FanhuaGold = Color(0xFFFFC53D)
val FanhuaGoldDeep = Color(0xFFFF9A1A)
val FanhuaRose = Color(0xFFFF6F5E)
val FanhuaBlue = Color(0xFF5AA2FF)
val FanhuaText = Color(0xFFF6F6F7)
val FanhuaTextDim = Color(0xFF9C9DA2)
val FanhuaTextFaint = Color(0xFF63656B)
val FanhuaOutline = Color(0x1FFFFFFF)
val FanhuaStroke = Color(0x26FFFFFF)
val FanhuaGlass = Color(0x14FFFFFF)
val FanhuaGlassHigh = Color(0x22FFFFFF)
val FanhuaShadow = Color(0x99000000)

val HyperRadiusLarge = 30.dp
val HyperRadiusMedium = 22.dp
val HyperRadiusSmall = 14.dp

private val FanhuaColorScheme = darkColorScheme(
    primary = FanhuaGold,
    onPrimary = Color(0xFF241A00),
    secondary = FanhuaBlue,
    onSecondary = Color(0xFF04121F),
    tertiary = FanhuaRose,
    background = FanhuaInk,
    onBackground = FanhuaText,
    surface = FanhuaSurface,
    onSurface = FanhuaText,
    surfaceVariant = FanhuaSurfaceHigh,
    onSurfaceVariant = FanhuaTextDim,
    outline = FanhuaOutline,
    error = FanhuaRose,
    onError = Color(0xFF1F0603)
)

private val FanhuaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

private val FanhuaTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 30.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 1.4.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
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
