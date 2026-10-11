package com.fanhua.camera.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val FanhuaInk = Color(0xFF08090B)
val FanhuaSurface = Color(0xFF14161A)
val FanhuaSurfaceHigh = Color(0xFF1D2026)
val FanhuaGold = Color(0xFFE8C39E)
val FanhuaGoldDeep = Color(0xFFC79A6B)
val FanhuaRose = Color(0xFFD8A7A0)
val FanhuaText = Color(0xFFF2EDE7)
val FanhuaTextDim = Color(0xFF9A9691)
val FanhuaOutline = Color(0xFF3A3E45)

private val FanhuaColorScheme = darkColorScheme(
    primary = FanhuaGold,
    onPrimary = Color(0xFF1A1408),
    secondary = FanhuaRose,
    onSecondary = Color(0xFF1A1010),
    background = FanhuaInk,
    onBackground = FanhuaText,
    surface = FanhuaSurface,
    onSurface = FanhuaText,
    surfaceVariant = FanhuaSurfaceHigh,
    onSurfaceVariant = FanhuaTextDim,
    outline = FanhuaOutline,
    error = Color(0xFFE57373),
    onError = Color(0xFF1A0C0C)
)

private val FanhuaTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Light, letterSpacing = 3.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.4.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.9.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.7.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.5.sp)
)

@Composable
fun FanhuaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FanhuaColorScheme,
        typography = FanhuaTypography,
        content = content
    )
}