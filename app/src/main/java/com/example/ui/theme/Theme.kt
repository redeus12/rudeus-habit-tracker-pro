package com.example.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

object AppTheme {
    val colors: AppColors
        @Composable
        get() = LocalAppColors.current

    val accent: Color
        @Composable
        get() = MaterialTheme.colorScheme.primary

    val accentContainer: Color
        @Composable
        get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)

    val onAccent: Color
        @Composable
        get() = MaterialTheme.colorScheme.onPrimary
}

val ColorScheme.accent: Color
    get() = primary

val ColorScheme.accentContainer: Color
    get() = primary.copy(alpha = 0.25f)

val ColorScheme.onAccent: Color
    get() = onPrimary

fun adjustAccentForContrast(color: Color): Color {
    val lum = color.luminance()
    return when {
        lum > 0.60f -> lerp(color, Color.Black, 0.35f)
        lum < 0.15f -> lerp(color, Color.White, 0.45f)
        else -> color
    }
}

@Composable
fun rememberAnimatedAppColors(target: AppColors): AppColors {
    val spec = tween<Color>(durationMillis = 400, easing = FastOutSlowInEasing)
    val dpSpec = tween<androidx.compose.ui.unit.Dp>(durationMillis = 400, easing = FastOutSlowInEasing)

    val bg by animateColorAsState(target.bg, spec, label = "bg")
    val surface by animateColorAsState(target.surface, spec, label = "surface")
    val surface2 by animateColorAsState(target.surface2, spec, label = "surface2")
    val surface3 by animateColorAsState(target.surface3, spec, label = "surface3")
    val border by animateColorAsState(target.border, spec, label = "border")
    val borderSoft by animateColorAsState(target.borderSoft, spec, label = "borderSoft")
    val text by animateColorAsState(target.text, spec, label = "text")
    val textMuted by animateColorAsState(target.textMuted, spec, label = "textMuted")
    val textFaint by animateColorAsState(target.textFaint, spec, label = "textFaint")
    val iconOnAccent by animateColorAsState(target.iconOnAccent, spec, label = "iconOnAccent")
    val cardElevation by animateDpAsState(target.cardElevation, dpSpec, label = "cardElevation")

    return remember(bg, surface, surface2, surface3, border, borderSoft, text, textMuted, textFaint, iconOnAccent, cardElevation, target.isDark, target.isGlassEnabled) {
        AppColors(
            bg = bg,
            surface = surface,
            surface2 = surface2,
            surface3 = surface3,
            border = border,
            borderSoft = borderSoft,
            text = text,
            textMuted = textMuted,
            textFaint = textFaint,
            iconOnAccent = iconOnAccent,
            cardElevation = cardElevation,
            isDark = target.isDark,
            isGlassEnabled = target.isGlassEnabled
        )
    }
}

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = false,
    isGlassEnabled: Boolean = false,
    accentColor: Color = AmberGold,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val isDynamicSupported = useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val baseColors = if (isDynamicSupported) {
        val dynamicScheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        AppColors(
            bg = dynamicScheme.background,
            surface = dynamicScheme.surface,
            surface2 = dynamicScheme.surfaceVariant,
            surface3 = lerp(
                dynamicScheme.surfaceVariant,
                if (isDark) Color.White else Color.Black,
                0.05f
            ),
            border = dynamicScheme.outline,
            borderSoft = dynamicScheme.outlineVariant,
            text = dynamicScheme.onBackground,
            textMuted = dynamicScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textFaint = dynamicScheme.onSurfaceVariant.copy(alpha = 0.45f),
            iconOnAccent = dynamicScheme.onPrimary,
            cardElevation = if (isDark) 1.dp else 3.dp,
            isDark = isDark
        )
    } else {
        if (isDark) DarkAppColors else LightAppColors
    }

    val adjustedAccent = adjustAccentForContrast(accentColor)
    val onAccent = if (adjustedAccent.luminance() > 0.50f) Color(0xDE000000) else Color.White
    val accentContainer = adjustedAccent.copy(alpha = 0.25f)

    val targetColors = if (isGlassEnabled) {
        baseColors.copy(
            bg = Color.Transparent,
            surface = Color.White.copy(alpha = 0.14f),
            surface2 = Color.White.copy(alpha = 0.10f),
            surface3 = Color.White.copy(alpha = 0.08f),
            border = Color.White.copy(alpha = 0.25f),
            borderSoft = Color.White.copy(alpha = 0.20f),
            text = Color.White,
            textMuted = Color.White.copy(alpha = 0.75f),
            textFaint = Color.White.copy(alpha = 0.60f),
            iconOnAccent = onAccent,
            cardElevation = 0.dp,
            isGlassEnabled = true
        )
    } else {
        baseColors.copy(iconOnAccent = onAccent)
    }

    val animatedColors = rememberAnimatedAppColors(targetColors)

    val colorScheme = when {
        isGlassEnabled -> {
            darkColorScheme(
                primary = adjustedAccent,
                onPrimary = onAccent,
                primaryContainer = accentContainer,
                onPrimaryContainer = onAccent,
                secondary = adjustedAccent,
                onSecondary = onAccent,
                secondaryContainer = accentContainer,
                onSecondaryContainer = onAccent,
                tertiary = adjustedAccent,
                background = Color.Transparent,
                onBackground = Color.White,
                surface = Color.White.copy(alpha = 0.14f),
                onSurface = Color.White,
                surfaceVariant = Color.White.copy(alpha = 0.10f),
                onSurfaceVariant = Color.White.copy(alpha = 0.75f),
                outline = Color.White.copy(alpha = 0.25f),
                outlineVariant = Color.White.copy(alpha = 0.20f),
                error = ClayOrange,
                onError = Color.White
            )
        }
        isDynamicSupported -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> {
            darkColorScheme(
                primary = adjustedAccent,
                onPrimary = onAccent,
                primaryContainer = accentContainer,
                onPrimaryContainer = onAccent,
                secondary = adjustedAccent,
                onSecondary = onAccent,
                secondaryContainer = accentContainer,
                onSecondaryContainer = onAccent,
                tertiary = adjustedAccent,
                background = animatedColors.bg,
                onBackground = animatedColors.text,
                surface = animatedColors.surface,
                onSurface = animatedColors.text,
                surfaceVariant = animatedColors.surface2,
                onSurfaceVariant = animatedColors.textMuted,
                outline = animatedColors.border,
                outlineVariant = animatedColors.borderSoft,
                error = ClayOrange,
                onError = Color.White
            )
        }
        else -> {
            lightColorScheme(
                primary = adjustedAccent,
                onPrimary = onAccent,
                primaryContainer = accentContainer,
                onPrimaryContainer = onAccent,
                secondary = adjustedAccent,
                onSecondary = onAccent,
                secondaryContainer = accentContainer,
                onSecondaryContainer = onAccent,
                tertiary = adjustedAccent,
                background = animatedColors.bg,
                onBackground = animatedColors.text,
                surface = animatedColors.surface,
                onSurface = animatedColors.text,
                surfaceVariant = animatedColors.surface2,
                onSurfaceVariant = animatedColors.textMuted,
                outline = animatedColors.border,
                outlineVariant = animatedColors.borderSoft,
                error = ClayOrange,
                onError = Color.White
            )
        }
    }

    CompositionLocalProvider(LocalAppColors provides animatedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
