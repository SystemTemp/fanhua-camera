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
 * 徕卡一瞬取景器主题。
 *
 * 色值全部由 REA / aapt2 从「徕卡一瞬 6.8.001960.0」真实资源反解得到：
 *  - leica_red            #ffe20612
 *  - leica_bg_grey        #ff212121
 *  - leica_bg_white       #fff2f2f2
 *  - shutter_recording    #ffe11b1b
 *  - camera_colorPrimary_select_color  #ffffce16
 *  - top_config_color_mm  #ffffffff
 *
 * 视觉语言（对照真机）：
 *  - 取景界面整体纯黑，控件不用毛玻璃/描边胶囊，而用「纯图标 + 极细分隔线」；
 *  - 交互强调统一为徕卡红（录制点、快门环、模式选中下划线），不用蓝紫；
 *  - 模式/滤镜文字用细体无衬线、大写字距，选中项下方一条 2dp 红色短线；
 *  - 快门是白色实心圆 + 细白环；录制时半径收缩成红色方块；
 *  - 圆角极小（8dp 以内），不做 Material 3 的大圆角胶囊。
 */
object Leica {
    val Red = Color(0xFFE20612)
    val RedBright = Color(0xFFE11B1B)
    val Amber = Color(0xFFFFCE16)
    val Ink = Color(0xFF000000)
    val Grey = Color(0xFF212121)
    val GreyLight = Color(0xFFF2F2F2)
    val White = Color(0xFFFFFFFF)
    val Text = Color(0xFFF5F5F5)
    val TextDim = Color(0xFF9A9A9A)
    val TextFaint = Color(0xFF5C5C5C)
    val Line = Color(0x33FFFFFF)
    val Panel = Color(0xE6101010)
    val Disabled = Color(0xFF666666)
}

/** 保留旧引用名，全部映射到徕卡真实配色，避免大规模改名。 */
val FanhuaInk = Leica.Ink
val FanhuaSurface = Leica.Grey
val FanhuaSurfaceHigh = Color(0xFF2B2B2B)
val FanhuaSurfaceHighest = Color(0xFF3A3A3A)
val FanhuaPrimary = Leica.Red
val FanhuaPrimaryDeep = Leica.RedBright
val FanhuaSecondary = Leica.Amber
val FanhuaTertiary = Leica.GreyLight
val FanhuaGold = Leica.Amber
val FanhuaGoldDeep = Leica.Amber
val FanhuaRose = Leica.RedBright
val FanhuaBlue = Leica.White
val FanhuaText = Leica.Text
val FanhuaTextDim = Leica.TextDim
val FanhuaTextFaint = Leica.TextFaint
val FanhuaOutline = Leica.Line
val FanhuaStroke = Leica.Line
val FanhuaGlass = Leica.Panel
val FanhuaGlassHigh = Leica.Panel
val FanhuaShadow = Color(0x99000000)

val HyperRadiusLarge = 8.dp
val HyperRadiusMedium = 6.dp
val HyperRadiusSmall = 4.dp

private val LeicaColorScheme = darkColorScheme(
    primary = Leica.Red,
    onPrimary = Leica.White,
    primaryContainer = Leica.RedBright,
    onPrimaryContainer = Leica.White,
    secondary = Leica.Amber,
    onSecondary = Leica.Ink,
    tertiary = Leica.GreyLight,
    onTertiary = Leica.Ink,
    background = Leica.Ink,
    onBackground = Leica.Text,
    surface = Leica.Grey,
    onSurface = Leica.Text,
    surfaceVariant = Color(0xFF2B2B2B),
    onSurfaceVariant = Leica.TextDim,
    surfaceContainer = Leica.Grey,
    surfaceContainerHigh = Color(0xFF2B2B2B),
    surfaceContainerHighest = Color(0xFF3A3A3A),
    outline = Leica.Line,
    outlineVariant = Color(0x1AFFFFFF),
    error = Leica.RedBright,
    onError = Leica.White
)

private val LeicaShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp)
)

private val LeicaTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 30.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 4.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 1.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 1.5.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 1.5.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.5.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.8.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.5.sp
    )
)

@Composable
fun FanhuaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LeicaColorScheme,
        typography = LeicaTypography,
        shapes = LeicaShapes,
        content = content
    )
}
