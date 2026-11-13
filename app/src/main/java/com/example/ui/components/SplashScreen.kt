package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppTheme
import com.example.ui.theme.Typography
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val infiniteTransition = rememberInfiniteTransition(label = "splash_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val entryProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        entryProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(700, easing = FastOutSlowInEasing)
        )
        delay(650)
        onSplashComplete()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = AppTheme.colors.bg
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Ambient Radial Glow
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .scale(pulseScale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.18f * entryProgress.value),
                                accentColor.copy(alpha = 0.04f * entryProgress.value),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .scale(0.85f + 0.15f * entryProgress.value)
                    .alpha(entryProgress.value)
            ) {
                // Central Logo Emblem
                Surface(
                    modifier = Modifier.size(88.dp),
                    shape = RoundedCornerShape(26.dp),
                    color = AppTheme.colors.surface,
                    border = androidx.compose.foundation.BorderStroke(2.dp, accentColor),
                    shadowElevation = 10.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Habit Ledger Logo",
                            tint = accentColor,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Brand Title
                Text(
                    text = "HABIT LEDGER",
                    style = Typography.headlineMedium.copy(
                        color = AppTheme.colors.text,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                        fontSize = 24.sp
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle
                Text(
                    text = "Forge Consistency, One Day at a Time",
                    style = Typography.bodyMedium.copy(
                        color = AppTheme.colors.textMuted,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        fontSize = 13.sp
                    )
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Minimalist loading bar indicator
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(3.dp)
                        .background(AppTheme.colors.surface2, RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(entryProgress.value)
                            .background(accentColor, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}
