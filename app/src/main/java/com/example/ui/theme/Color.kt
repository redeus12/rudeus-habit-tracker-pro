package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val BgDark = Color(0xFF0F1512)
val SurfaceDark = Color(0xFF161F1B)
val Surface2Dark = Color(0xFF1D2822)
val Surface3Dark = Color(0xFF212C27)
val BorderDark = Color(0xFF2A3733)
val BorderSoftDark = Color(0xFF222D28)
val TextLight = Color(0xFFEDEAE2)
val TextMuted = Color(0xFF8CA098)
val TextFaint = Color(0xFF5C6B64)

val AmberGold = Color(0xFFB45309)
val ClayOrange = Color(0xFFD97A5F)
val TealBlue = Color(0xFF6FA8B0)
val VioletPurple = Color(0xFF9B8FC4)
val MossGreen = Color(0xFF7FA872)
val RosePink = Color(0xFFC77D9E)
val SlateBlue = Color(0xFF5B82A6)
val MutedCrimson = Color(0xFFBA5A5A)
val EmeraldSeafoam = Color(0xFF4D9E80)

val HabitPalette = listOf(
    "#D97A5F", "#6FA8B0", "#9B8FC4", "#E2A857", "#7FA872", "#C77D9E",
    "#5B82A6", "#BA5A5A", "#4D9E80"
)

val WarmIvoryBg = Color(0xFFFAF7F2)
val WarmCardSurface = Color(0xFFFFFFFF)
val WarmCardSurface2 = Color(0xFFF5EFE6)
val WarmCardSurface3 = Color(0xFFEFEAE0)
val WarmBorder = Color(0xFFDDD5C7)
val WarmBorderSoft = Color(0xFFEBE5DB)
val WarmTextDark = Color(0xFF1C1917)
val WarmTextMuted = Color(0xFF57534E)
val WarmTextFaint = Color(0xFF78716C)

val ForestGreenPrimary = Color(0xFF046A38)
val WarmAmberCaramel = Color(0xFFB45309)
val DeepWarmBrown = Color(0xFF78350F)
val WarmPeachPill = Color(0xFFFDE8D4)
val CyanTealAccent = Color(0xFF0D9488)
val CyanTealPill = Color(0xFFCCFBF1)

data class AppColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val border: Color,
    val borderSoft: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val iconOnAccent: Color,
    val cardElevation: Dp,
    val isDark: Boolean,
    val isGlassEnabled: Boolean = false
)

val DarkAppColors = AppColors(
    bg = BgDark,
    surface = SurfaceDark,
    surface2 = Surface2Dark,
    surface3 = Surface3Dark,
    border = BorderDark,
    borderSoft = BorderSoftDark,
    text = TextLight,
    textMuted = TextMuted,
    textFaint = TextFaint,
    iconOnAccent = Color(0xFF181410),
    cardElevation = 1.dp,
    isDark = true
)

val LightAppColors = AppColors(
    bg = WarmIvoryBg,
    surface = WarmCardSurface,
    surface2 = WarmCardSurface2,
    surface3 = WarmCardSurface3,
    border = WarmBorder,
    borderSoft = WarmBorderSoft,
    text = WarmTextDark,
    textMuted = WarmTextMuted,
    textFaint = WarmTextFaint,
    iconOnAccent = Color(0xFFFFFFFF),
    cardElevation = 2.dp,
    isDark = false
)

fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        AmberGold
    }
}

