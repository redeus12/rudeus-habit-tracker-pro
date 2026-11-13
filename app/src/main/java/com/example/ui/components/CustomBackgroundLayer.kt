package com.example.ui.components

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.AppTheme
import com.example.ui.theme.BackgroundConfig
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GlassRootSize
import com.example.ui.theme.GlassTextShadow
import com.example.ui.theme.LocalBackgroundConfig
import com.example.ui.theme.LocalGlassRootSize
import java.io.File

import androidx.compose.ui.unit.IntSize
import com.example.ui.theme.LocalBlurredWallpaper
import com.example.util.rememberBlurredWallpaperState

/**
 * Root Custom Background Layer.
 * Draws the user-selected wallpaper edge-to-edge behind all screens,
 * covered by a global scrim (Color.Black alpha `dim`, adjustable 0-0.60).
 */
@Composable
fun CustomBackgroundLayer(
    config: BackgroundConfig,
    modifier: Modifier = Modifier
) {
    if (!config.isEnabled || config.imagePath == null) return

    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rootSize = LocalGlassRootSize.current
    val rootWidthPx = with(density) { rootSize.width.roundToPx() }
    val rootHeightPx = with(density) { rootSize.height.roundToPx() }

    val animatedDim by animateFloatAsState(
        targetValue = config.dimAmount,
        animationSpec = tween(durationMillis = 600),
        label = "DimAnimation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        // 1. Smooth 600ms crossfade between wallpaper transitions
        Crossfade(
            targetState = config.imagePath,
            animationSpec = tween(durationMillis = 600),
            label = "WallpaperCrossfade"
        ) { currentPath ->
            if (currentPath != null) {
                val file = remember(currentPath) { File(currentPath) }
                if (file.exists()) {
                    val imageRequest = remember(currentPath, rootWidthPx, rootHeightPx) {
                        val builder = ImageRequest.Builder(context)
                            .data(file)
                            .allowHardware(true)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .crossfade(false)
                        if (rootWidthPx > 0 && rootHeightPx > 0) {
                            builder.size(rootWidthPx, rootHeightPx)
                        }
                        builder.build()
                    }

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "Custom App Background",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // 2. Global scrim over wallpaper (Color.Black at alpha = dimAmount, adjusted for bright wallpapers)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = animatedDim))
        )
    }
}

/**
 * Live Preview Card displayed in the Settings Screen.
 * Demonstrates the exact wallpaper, blur radius, glass opacity, and dim settings in real-time.
 */
@Composable
fun BackgroundLivePreview(
    config: BackgroundConfig,
    modifier: Modifier = Modifier
) {
    val isDark = AppTheme.colors.isDark
    val hasImage = config.imagePath != null && File(config.imagePath).exists()
    val accentColor = remember(config.accentColorHex) { Color(config.accentColorHex) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(
                BorderStroke(
                    1.dp,
                    if (config.isEnabled && hasImage) Color.White.copy(alpha = 0.35f) else AppTheme.colors.borderSoft
                ),
                RoundedCornerShape(24.dp)
            )
            .background(if (isDark) Color(0xFF161F1B) else Color(0xFF222825))
    ) {
        if (hasImage) {
            val file = remember(config.imagePath) { File(config.imagePath) }

            // Preview Wallpaper
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(file)
                    .crossfade(false)
                    .build(),
                contentDescription = "Live Background Preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Scrim inside preview
            if (config.isEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = config.dimAmount))
                )
            }
        } else {
            // Empty placeholder
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No custom wallpaper chosen",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Live Simulated Glassmorphism Card (uses GlassCard so it is 100% identical)
        if (hasImage && config.isEnabled) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val previewRootSize = remember { GlassRootSize(width = 380.dp, height = 210.dp) }
            val previewRootSizePx = remember(density) {
                with(density) {
                    IntSize(380.dp.roundToPx(), 210.dp.roundToPx())
                }
            }
            val previewBlurredState = rememberBlurredWallpaperState(config, previewRootSizePx)

            CompositionLocalProvider(
                LocalBackgroundConfig provides config,
                LocalGlassRootSize provides previewRootSize,
                LocalBlurredWallpaper provides previewBlurredState
            ) {
                GlassCard(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.88f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daily Habit Card",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White,
                                style = androidx.compose.ui.text.TextStyle(shadow = GlassTextShadow)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "5 Streak",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(accentColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Morning Meditation (20m)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Status pill top-right
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (config.isEnabled && hasImage) accentColor else Color.Black.copy(alpha = 0.55f),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        ) {
            Text(
                text = if (config.isEnabled && hasImage) "Vivid Glass Active" else "Default Theme",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
