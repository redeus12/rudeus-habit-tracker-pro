package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

private data class OnboardingPageData(
    val badge: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color,
    val features: List<String>
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableStateOf(0) }
    val primaryAccent = MaterialTheme.colorScheme.primary
    val onAccentColor = MaterialTheme.colorScheme.onPrimary

    val pages = remember(primaryAccent) {
        listOf(
            OnboardingPageData(
                badge = "CONSISTENCY & STREAKS",
                title = "Build Habits That Last",
                description = "Define your personal routines, schedule flexible days, and build unbroken consistency streaks with clear visual milestone curves.",
                icon = Icons.Default.TrendingUp,
                accentColor = primaryAccent,
                features = listOf(
                    "Flexible weekly schedules with customizable rest days",
                    "Visual consistency heatmaps & discipline field scores",
                    "Customizable goal challenges with daily streak tracking"
                )
            ),
            OnboardingPageData(
                badge = "DECOUPLED FOCUS & AUDIO",
                title = "Focus Stopwatch with Music",
                description = "Deeply concentrate on each habit. Track your elapsed session and control YouTube Music or your external media apps without leaving the app.",
                icon = Icons.Default.Headphones,
                accentColor = primaryAccent,
                features = listOf(
                    "Independent timer: silent focus or with audio",
                    "External media control for YouTube Music & Spotify",
                    "Direct in-app volume tuning and track skipping"
                )
            ),
            OnboardingPageData(
                badge = "WIDGETS & 100% PRIVATE",
                title = "Home Widgets & Zero Cloud",
                description = "Check habits and operate your timer straight from your home screen. Your habit records are 100% offline-first and private.",
                icon = Icons.Default.Shield,
                accentColor = primaryAccent,
                features = listOf(
                    "Interactive home-screen widgets for instant tracking",
                    "Complete JSON data export and import for secure backup",
                    "Zero accounts, zero tracking, total privacy on device"
                )
            )
        )
    }

    val page = pages[currentPage]

    Surface(
        modifier = modifier.fillMaxSize(),
        color = AppTheme.colors.bg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Skip button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage < pages.size - 1) {
                    TextButton(
                        onClick = onFinish,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Skip",
                            style = Typography.labelLarge.copy(
                                color = AppTheme.colors.textMuted,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }

            // Central Animated Slide Content
            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboarding_content",
                modifier = Modifier.weight(1f)
            ) { targetPage ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Emblem / Icon Card
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        targetPage.accentColor.copy(alpha = 0.25f),
                                        targetPage.accentColor.copy(alpha = 0.05f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(76.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = AppTheme.colors.surface,
                            border = BorderStroke(1.5.dp, targetPage.accentColor.copy(alpha = 0.6f)),
                            shadowElevation = 6.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = targetPage.icon,
                                    contentDescription = null,
                                    tint = targetPage.accentColor,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Badge Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = targetPage.accentColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, targetPage.accentColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = targetPage.badge,
                            style = Typography.labelSmall.copy(
                                color = targetPage.accentColor,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Title
                    Text(
                        text = targetPage.title,
                        style = Typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.text,
                            textAlign = TextAlign.Center,
                            fontSize = 26.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description
                    Text(
                        text = targetPage.description,
                        style = Typography.bodyMedium.copy(
                            color = AppTheme.colors.textMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                            fontSize = 14.sp
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Feature highlights Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = AppTheme.colors.surface,
                        border = BorderStroke(1.dp, AppTheme.colors.borderSoft)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            targetPage.features.forEach { feat ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = targetPage.accentColor.copy(alpha = 0.18f),
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = targetPage.accentColor,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = feat,
                                        style = Typography.bodySmall.copy(
                                            color = AppTheme.colors.text,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Navigation Area
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    pages.indices.forEach { index ->
                        val isSelected = index == currentPage
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 28.dp else 8.dp,
                            label = "dot_width"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) page.accentColor else AppTheme.colors.border
                                )
                                .clickable { currentPage = index }
                        )
                    }
                }

                // Action Button
                ModernButton(
                    onClick = {
                        if (currentPage < pages.size - 1) {
                            currentPage++
                        } else {
                            onFinish()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = page.accentColor,
                        contentColor = onAccentColor
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (currentPage < pages.size - 1) "Continue" else "Get Started",
                            style = Typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
