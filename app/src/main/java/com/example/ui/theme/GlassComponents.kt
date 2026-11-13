package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlin.math.roundToInt

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap

val LocalBackgroundConfig = staticCompositionLocalOf { BackgroundConfig() }
val LocalGlassSettings = LocalBackgroundConfig

@Immutable
data class WallpaperBlurState(
    val blurredBitmap: ImageBitmap? = null
)

val LocalBlurredWallpaper = compositionLocalOf<WallpaperBlurState?> { null }

data class GlassRootSize(
    val width: Dp = 412.dp,
    val height: Dp = 915.dp
)

val LocalGlassRootSize = staticCompositionLocalOf { GlassRootSize() }

/**
 * Standard soft text shadow for primary white glass typography
 */
val GlassTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.50f),
    offset = Offset(0f, 1.5f),
    blurRadius = 4f
)

/**
 * Universal Glassmorphism container Card with Real Backdrop Blur.
 *
 * Sized to root screen dimensions and offset by card's position in root,
 * ensuring 100% of every card at any height is completely covered by blur.
 *
 * Layers (bottom to top):
 * 1) Blurred wallpaper copy drawn via drawBehind using LocalBlurredWallpaper
 * 2) Glass tint: Color.Black at (glassOpacity * 0.6) PLUS Color.White at 0.08
 * 3) 1dp border: Color.White alpha 0.25
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundConfig = LocalGlassSettings.current
    val isGlassEnabled = backgroundConfig.isEnabled && backgroundConfig.imagePath != null
    val rootSize = LocalGlassRootSize.current
    val blurredWallpaper = LocalBlurredWallpaper.current

    val cardPositionInRoot = remember { mutableStateOf(Offset.Zero) }
    val glassOpacity = backgroundConfig.glassOpacity

    val baseModifier = modifier
        .onGloballyPositioned { coordinates ->
            cardPositionInRoot.value = coordinates.positionInRoot()
        }
        .clip(shape)

    val backgroundModifier = if (isGlassEnabled) {
        baseModifier
            .drawBehind {
                val blurred = blurredWallpaper?.blurredBitmap
                val rootW = rootSize.width.toPx()
                val rootH = rootSize.height.toPx()
                if (blurred != null && rootW > 0f && rootH > 0f) {
                    val bw = blurred.width.toFloat()
                    val bh = blurred.height.toFloat()
                    val pos = cardPositionInRoot.value
                    val srcX = (pos.x / rootW) * bw
                    val srcY = (pos.y / rootH) * bh
                    val srcW = (size.width / rootW) * bw
                    val srcH = (size.height / rootH) * bh

                    val clLeft = srcX.coerceIn(0f, bw)
                    val clTop = srcY.coerceIn(0f, bh)
                    val clRight = (srcX + srcW).coerceIn(0f, bw)
                    val clBottom = (srcY + srcH).coerceIn(0f, bh)
                    val fSrcW = maxOf(0f, clRight - clLeft)
                    val fSrcH = maxOf(0f, clBottom - clTop)

                    if (fSrcW > 0f && fSrcH > 0f && srcW > 0f && srcH > 0f) {
                        val dstX = (clLeft - srcX) / srcW * size.width
                        val dstY = (clTop - srcY) / srcH * size.height
                        val dstW = fSrcW / srcW * size.width
                        val dstH = fSrcH / srcH * size.height

                        drawImage(
                            image = blurred,
                            srcOffset = IntOffset(clLeft.roundToInt(), clTop.roundToInt()),
                            srcSize = IntSize(fSrcW.roundToInt(), fSrcH.roundToInt()),
                            dstOffset = IntOffset(dstX.roundToInt(), dstY.roundToInt()),
                            dstSize = IntSize(dstW.roundToInt(), dstH.roundToInt()),
                            filterQuality = FilterQuality.Low
                        )
                    }
                }
                // Tint layers
                drawRect(Color.Black.copy(alpha = glassOpacity * 0.60f))
                drawRect(Color.White.copy(alpha = 0.08f))
            }
            .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
    } else {
        baseModifier
            .background(AppTheme.colors.surface)
            .border(1.dp, AppTheme.colors.borderSoft, shape)
    }

    val interactiveModifier = if (onClick != null) {
        backgroundModifier.clickable(onClick = onClick)
    } else {
        backgroundModifier
    }

    Box(
        modifier = interactiveModifier
    ) {
        content()
    }
}

/**
 * Universal Glassmorphism small Pill / Badge component for chips, counters, and filters.
 */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    accentColor: Color? = null,
    glassAlpha: Float = 0.15f,
    borderAlpha: Float = 0.25f,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val isGlassEnabled = AppTheme.colors.isGlassEnabled
    
    val pillBg = if (isSelected && accentColor != null) {
        accentColor
    } else if (accentColor != null) {
        accentColor.copy(alpha = 0.22f)
    } else if (isGlassEnabled) {
        Color.White.copy(alpha = glassAlpha.coerceIn(0.12f, 0.20f))
    } else {
        AppTheme.colors.surface2
    }

    val pillBorder = if (isSelected && accentColor != null) {
        accentColor
    } else if (accentColor != null) {
        accentColor.copy(alpha = 0.45f)
    } else if (isGlassEnabled) {
        Color.White.copy(alpha = borderAlpha)
    } else {
        AppTheme.colors.borderSoft
    }

    val clickModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Surface(
        modifier = clickModifier,
        shape = shape,
        color = pillBg,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, pillBorder)
    ) {
        content()
    }
}

/**
 * Glass Surface with full backdrop blur, white alpha 0.18 background, and 1dp white alpha 0.35 border.
 * Sized dynamically to the root viewport to guarantee blur coverage matching GlassCard.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = Color.White.copy(alpha = 0.18f),
    borderColor: Color = Color.White.copy(alpha = 0.35f),
    borderWidth: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundConfig = LocalGlassSettings.current
    val isGlassEnabled = backgroundConfig.isEnabled && backgroundConfig.imagePath != null
    val rootSize = LocalGlassRootSize.current
    val blurredWallpaper = LocalBlurredWallpaper.current

    val positionInRoot = remember { mutableStateOf(Offset.Zero) }

    val baseModifier = modifier
        .onGloballyPositioned { coordinates ->
            positionInRoot.value = coordinates.positionInRoot()
        }
        .clip(shape)

    val backgroundModifier = if (isGlassEnabled) {
        baseModifier
            .drawBehind {
                val blurred = blurredWallpaper?.blurredBitmap
                val rootW = rootSize.width.toPx()
                val rootH = rootSize.height.toPx()
                if (blurred != null && rootW > 0f && rootH > 0f) {
                    val bw = blurred.width.toFloat()
                    val bh = blurred.height.toFloat()
                    val pos = positionInRoot.value
                    val srcX = (pos.x / rootW) * bw
                    val srcY = (pos.y / rootH) * bh
                    val srcW = (size.width / rootW) * bw
                    val srcH = (size.height / rootH) * bh

                    val clLeft = srcX.coerceIn(0f, bw)
                    val clTop = srcY.coerceIn(0f, bh)
                    val clRight = (srcX + srcW).coerceIn(0f, bw)
                    val clBottom = (srcY + srcH).coerceIn(0f, bh)
                    val fSrcW = maxOf(0f, clRight - clLeft)
                    val fSrcH = maxOf(0f, clBottom - clTop)

                    if (fSrcW > 0f && fSrcH > 0f && srcW > 0f && srcH > 0f) {
                        val dstX = (clLeft - srcX) / srcW * size.width
                        val dstY = (clTop - srcY) / srcH * size.height
                        val dstW = fSrcW / srcW * size.width
                        val dstH = fSrcH / srcH * size.height

                        drawImage(
                            image = blurred,
                            srcOffset = IntOffset(clLeft.roundToInt(), clTop.roundToInt()),
                            srcSize = IntSize(fSrcW.roundToInt(), fSrcH.roundToInt()),
                            dstOffset = IntOffset(dstX.roundToInt(), dstY.roundToInt()),
                            dstSize = IntSize(dstW.roundToInt(), dstH.roundToInt()),
                            filterQuality = FilterQuality.Low
                        )
                    }
                }
                drawRect(backgroundColor)
            }
            .border(borderWidth, borderColor, shape)
    } else {
        baseModifier
            .background(AppTheme.colors.surface2)
            .border(borderWidth, AppTheme.colors.borderSoft, shape)
    }

    val interactiveModifier = if (onClick != null) {
        backgroundModifier.clickable(onClick = onClick)
    } else {
        backgroundModifier
    }

    Box(modifier = interactiveModifier) {
        content()
    }
}

/**
 * Glass Icon Button with minimum 40dp height/width, 8dp padding, and root-sized backdrop blur.
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    size: Dp = 40.dp,
    enabled: Boolean = true,
    backgroundColor: Color = Color.White.copy(alpha = 0.18f),
    borderColor: Color = Color.White.copy(alpha = 0.35f),
    content: @Composable () -> Unit
) {
    GlassSurface(
        modifier = modifier
            .size(size)
            .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp),
        shape = shape,
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        onClick = if (enabled) onClick else null
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/**
 * Primary Solid Button
 * Solid accent color, high-contrast text/icon, 16dp radius, min height 48dp, no transparency.
 */
@Composable
fun PrimaryGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val onAccent = MaterialTheme.colorScheme.onPrimary
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor,
            contentColor = onAccent,
            disabledContainerColor = accentColor.copy(alpha = 0.4f),
            disabledContentColor = onAccent.copy(alpha = 0.6f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * Secondary Tonal Glass Button
 * Tonal glass style: White alpha 0.15 background, 1dp white alpha 0.3 border, white text, 40dp height.
 */
@Composable
fun SecondaryGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * Danger Glass Button for Delete/Remove
 * Soft red tint: Red alpha 0.25 background, 1dp red alpha 0.4 border, light red text.
 */
@Composable
fun DangerGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 40.dp,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFE53935).copy(alpha = 0.25f),
        border = BorderStroke(1.dp, Color(0xFFFF8A80).copy(alpha = 0.40f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * Glass Slider Row with stacked layout:
 * Label on left, value on right, slider below (do not overlap).
 * Track: White alpha 0.3, active track and thumb in accent color, thumb 20dp.
 */
@Composable
fun GlassSliderRow(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Text(
                text = valueText,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.30f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        )
    }
}

/**
 * Frosted Progress Bar with White alpha 0.25 track and dynamic Accent Color fill
 */
@Composable
fun GlassProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.White.copy(alpha = 0.25f),
    height: Dp = 6.dp,
    shape: Shape = RoundedCornerShape(4.dp)
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedProgress)
                .fillMaxHeight()
                .background(accentColor)
        )
    }
}
