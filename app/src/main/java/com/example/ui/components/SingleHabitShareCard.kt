package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChartBucket
import com.example.model.ChartGranularity
import com.example.model.Habit
import com.example.util.DateUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Data bundle for Single Habit share card.
 * Every value is computed from real domain models.
 */
data class SingleHabitShareData(
    val todayStr: String,
    val datePill: String,
    val habit: Habit,
    val habitColor: Color,
    val activeStreak: Int,
    val bestStreak: Int,
    val accuracy30d: Int,
    val totalLogged: Int,
    val granularity: ChartGranularity,
    val buckets: List<ChartBucket>,
    val latestBucket: ChartBucket?,
    val performancePercentage: Int,
    val momentumInfo: MomentumTierInfo,
    val statusText: String,
    val statusColor: Color,
    val scheduleText: String,
    val goalText: String,
    val reminderText: String?,
    val latestBucketRangeText: String,
    val latestBucketDoneScheduledText: String,
    val captionText: String,
    val categoryEmoji: String
)

data class MomentumTierInfo(
    val tierName: String,
    val description: String,
    val color: Color
)

/**
 * Momentum tier computed from ONE function with exact thresholds:
 * >= 80%: ELITE CONSISTENCY
 * 60%..79%: STRONG MOMENTUM
 * 40%..59%: BUILDING HABIT
 * < 40%: GETTING STARTED
 */
fun getMomentumTier(percentage: Int): MomentumTierInfo {
    return when {
        percentage >= 80 -> MomentumTierInfo(
            tierName = "ELITE CONSISTENCY",
            description = "Unstoppable rhythm and exceptional discipline.",
            color = Color(0xFF10B981)
        )
        percentage >= 60 -> MomentumTierInfo(
            tierName = "STRONG MOMENTUM",
            description = "Consistent execution with great staying power.",
            color = Color(0xFF3B82F6)
        )
        percentage >= 40 -> MomentumTierInfo(
            tierName = "BUILDING HABIT",
            description = "Solid foundation in progress, keep pushing daily.",
            color = Color(0xFFF59E0B)
        )
        else -> MomentumTierInfo(
            tierName = "GETTING STARTED",
            description = "Early strides in forming an enduring daily routine.",
            color = Color(0xFFEC4899)
        )
    }
}

private fun extractCategoryEmoji(category: String): String {
    val trimmed = category.trim()
    if (trimmed.isNotEmpty() && (Character.isSurrogate(trimmed[0]) || trimmed.codePointAt(0) > 0x2000)) {
        return trimmed.take(2)
    }
    return when (trimmed.lowercase()) {
        "health", "fitness", "workout", "exercise" -> "💪"
        "mindfulness", "meditation", "mental", "peace" -> "🧘"
        "study", "learning", "read", "reading", "education" -> "📚"
        "work", "productivity", "career", "business" -> "💼"
        "morning", "wake", "routine" -> "🌅"
        "night", "sleep", "evening" -> "🌙"
        "water", "hydration" -> "💧"
        "finance", "money", "savings" -> "💰"
        "diet", "nutrition", "food" -> "🥗"
        "art", "creative", "creativity", "writing" -> "🎨"
        else -> "⭐"
    }
}

/**
 * Computes all numbers, labels, and chart buckets for a single habit.
 */
fun computeSingleHabitShareData(
    habit: Habit,
    todayStr: String = DateUtils.todayStr(),
    locale: Locale = Locale.getDefault()
): SingleHabitShareData {
    // Show TODAY's date (java.time LocalDate.now(), device locale, weekday computed)
    val todayDate = LocalDate.now()
    val datePill = todayDate.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", locale))

    val activeStreak = DateUtils.calculateStreak(habit, today = todayStr)
    val bestStreak = DateUtils.calculateLongestStreak(habit, today = todayStr)
    val accuracy30d = (DateUtils.completionRate(habit, 30, today = todayStr) * 100f).roundToInt()
    val totalLogged = habit.completions.size

    val (granularity, buckets) = DateUtils.buildChartBuckets(habit, today = todayStr)
    val latestBucket = buckets.lastOrNull()
    val performancePercentage = if (latestBucket != null) {
        (latestBucket.rate * 100f).roundToInt()
    } else {
        accuracy30d
    }

    val momentumInfo = getMomentumTier(performancePercentage)

    val habitColor = habit.color

    val isDurationEnded = habit.durationDays != null &&
            DateUtils.daysBetween(habit.createdAt, todayStr) >= habit.durationDays

    val (statusText, statusColor) = when {
        isDurationEnded -> Pair("Completed", Color(0xFF10B981))
        DateUtils.isFrozen(habit, todayStr) -> Pair("Frozen", Color(0xFF26C6DA))
        else -> Pair("Active", Color(0xFF10B981))
    }

    val scheduleText = DateUtils.daysText(habit)
    val goalText = if (habit.durationDays != null) {
        "${habit.durationDays}-Day Challenge"
    } else {
        "Ongoing routine"
    }
    val reminderText = habit.reminderTime

    val firstBucket = buckets.firstOrNull()
    val latestBucketRangeText = when (granularity) {
        ChartGranularity.DAY -> {
            if (firstBucket != null && latestBucket != null) {
                if (firstBucket.startDateStr == latestBucket.startDateStr) firstBucket.label
                else "${firstBucket.label} - ${latestBucket.label}"
            } else "All-time"
        }
        ChartGranularity.WEEK, ChartGranularity.MONTH -> {
            latestBucket?.fullRangeLabel ?: "All-time"
        }
    }
    val latestBucketDoneScheduledText = if (latestBucket != null) {
        "${performancePercentage}% (${latestBucket.doneDays}/${latestBucket.scheduledDays}d)"
    } else {
        "$performancePercentage%"
    }

    val captionText = when (granularity) {
        ChartGranularity.DAY -> "So far"
        ChartGranularity.WEEK -> if (latestBucket?.isPartial == true) "This week" else "Latest week"
        ChartGranularity.MONTH -> if (latestBucket?.isPartial == true) "This month" else "Latest month"
    }

    val categoryEmoji = extractCategoryEmoji(habit.category)

    return SingleHabitShareData(
        todayStr = todayStr,
        datePill = datePill,
        habit = habit,
        habitColor = habitColor,
        activeStreak = activeStreak,
        bestStreak = bestStreak,
        accuracy30d = accuracy30d,
        totalLogged = totalLogged,
        granularity = granularity,
        buckets = buckets,
        latestBucket = latestBucket,
        performancePercentage = performancePercentage,
        momentumInfo = momentumInfo,
        statusText = statusText,
        statusColor = statusColor,
        scheduleText = scheduleText,
        goalText = goalText,
        reminderText = reminderText,
        latestBucketRangeText = latestBucketRangeText,
        latestBucketDoneScheduledText = latestBucketDoneScheduledText,
        captionText = captionText,
        categoryEmoji = categoryEmoji
    )
}

/**
 * Pure Jetpack Compose card for Single Habit Spotlight.
 * Rendered at 540dp logical width with LocalDensity = Density(2f, 1f) -> 1080px x 1920px (9:16).
 */
@Composable
fun SingleHabitShareCard(
    habit: Habit,
    todayStr: String = DateUtils.todayStr(),
    showFooter: Boolean = true,
    wallpaperBitmap: Bitmap? = null,
    blurredBackgroundBitmap: Bitmap? = null,
    accentColor: Color = Color(0xFF046A38),
    glassOpacity: Float = 0.50f,
    modifier: Modifier = Modifier
) {
    val data = remember(habit, todayStr) {
        computeSingleHabitShareData(habit, todayStr)
    }

    val wallpaperImageBitmap: ImageBitmap? = remember(wallpaperBitmap) {
        wallpaperBitmap?.asImageBitmap()
    }
    val blurredImageBitmap: ImageBitmap? = remember(blurredBackgroundBitmap) {
        blurredBackgroundBitmap?.asImageBitmap()
    }

    val textShadow = remember {
        Shadow(
            color = Color.Black.copy(alpha = 0.65f),
            offset = Offset(0f, 1.5f),
            blurRadius = 3f
        )
    }

    // Measure / estimate content height and adapt chart height (max 200dp, min 140dp)
    // to guarantee that no panel ever leaves the 1920px (960dp logical) export height
    val estimatedHeightWithoutChart = remember(habit, showFooter) {
        var base = 32 + 72 + 120 + 112 + 114 + 62 // padding + 6 gaps + header + stat tiles + perf non-chart + momentum
        if (showFooter) base += 28
        // Info panel variability:
        if (habit.name.length > 20) base += 22
        if (!habit.reminderTime.isNullOrBlank()) base += 18
        if (DateUtils.daysText(habit).length > 25) base += 14
        base
    }

    val dynamicChartHeight = remember(estimatedHeightWithoutChart) {
        val available = 960 - estimatedHeightWithoutChart - 16 // 16dp buffer for spacer
        available.coerceIn(140, 200).dp
    }

    val cardPadding = remember(dynamicChartHeight) {
        if (dynamicChartHeight <= 150.dp) 12.dp else 16.dp
    }

    val itemSpacing = remember(dynamicChartHeight) {
        if (dynamicChartHeight <= 150.dp) 10.dp else 12.dp
    }

    CompositionLocalProvider(
        LocalDensity provides Density(density = 2f, fontScale = 1f)
    ) {
        Box(
            modifier = modifier
                .width(540.dp)
                .height(960.dp)
                .background(Color(0xFF0D1117))
        ) {
            // 1. Wallpaper background ContentScale.Crop
            if (wallpaperImageBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = wallpaperImageBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    accentColor.copy(alpha = 0.70f),
                                    Color(0xFF0F172A),
                                    Color(0xFF020617)
                                )
                            )
                        )
                )
            }

            // Global dim overlay capped at 0.20 for export
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.20f))
            )

            // 2. Foreground Layout Structure (natural height, only 1 spacer before footer)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(cardPadding),
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                // Section A: Header Panel (natural height)
                SingleHabitHeaderPanel(
                    data = data,
                    blurredBitmap = blurredImageBitmap,
                    accentColor = accentColor,
                    glassOpacity = glassOpacity,
                    textShadow = textShadow
                )

                // Section B: Habit Info Panel (natural height)
                SingleHabitInfoPanel(
                    data = data,
                    blurredBitmap = blurredImageBitmap,
                    accentColor = accentColor,
                    glassOpacity = glassOpacity,
                    textShadow = textShadow
                )

                // Section C: Four Stat Tiles in One Row (fixed 112dp height)
                SingleHabitStatTilesRow(
                    data = data,
                    blurredBitmap = blurredImageBitmap,
                    accentColor = accentColor,
                    glassOpacity = glassOpacity,
                    textShadow = textShadow
                )

                // Section D: Performance Panel with Consistency Chart (chart 200dp high, min 140dp)
                SingleHabitPerformancePanel(
                    data = data,
                    blurredBitmap = blurredImageBitmap,
                    accentColor = accentColor,
                    glassOpacity = glassOpacity,
                    textShadow = textShadow,
                    chartHeight = dynamicChartHeight
                )

                // Section E: Momentum Panel (natural height)
                SingleHabitMomentumPanel(
                    data = data,
                    blurredBitmap = blurredImageBitmap,
                    accentColor = accentColor,
                    glassOpacity = glassOpacity,
                    textShadow = textShadow
                )

                // Section F: ONE spacer Modifier.weight(1f) placed right before the footer
                Spacer(modifier = Modifier.weight(1f))

                // Section G: Footer (Optional)
                if (showFooter) {
                    SingleHabitFooter(textShadow = textShadow)
                }
            }
        }
    }
}

@Composable
private fun SingleHabitHeaderPanel(
    data: SingleHabitShareData,
    blurredBitmap: ImageBitmap?,
    accentColor: Color,
    glassOpacity: Float,
    textShadow: Shadow
) {
    SingleHabitGlassPanel(
        blurredBitmap = blurredBitmap,
        accentColor = accentColor,
        glassOpacity = glassOpacity
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top branding row with date pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(accentColor.copy(alpha = 0.35f), CircleShape)
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Rudeus Habit",
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                shadow = textShadow
                            )
                        )
                        Text(
                            text = "HABIT SPOTLIGHT",
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.70f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                                shadow = textShadow
                            )
                        )
                    }
                }

                // Date Pill on ONE line (softWrap = false)
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = data.datePill,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            shadow = textShadow
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Title and subtitle
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Habit Mastery & Deep Dive",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        shadow = textShadow
                    )
                )
                Text(
                    text = "Consistency, streaks and progress analysis",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        shadow = textShadow
                    )
                )
            }

            // Subtle quote line
            Text(
                text = "“We are what we repeatedly do. Excellence, then, is not an act, but a habit.”",
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    shadow = textShadow
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SingleHabitInfoPanel(
    data: SingleHabitShareData,
    blurredBitmap: ImageBitmap?,
    accentColor: Color,
    glassOpacity: Float,
    textShadow: Shadow
) {
    SingleHabitGlassPanel(
        blurredBitmap = blurredBitmap,
        accentColor = accentColor,
        glassOpacity = glassOpacity
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Habit color stripe
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(84.dp)
                    .background(data.habitColor, RoundedCornerShape(3.dp))
            )

            // Category Emoji Chip
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = data.categoryEmoji,
                    fontSize = 22.sp
                )
            }

            // Habit details: Name + Status Chip + Info lines
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = data.habit.name,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textDirection = TextDirection.Content,
                            shadow = textShadow
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Status Chip ("Active", "Frozen", or "Completed")
                    Box(
                        modifier = Modifier
                            .background(data.statusColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, data.statusColor.copy(alpha = 0.60f)), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = data.statusText,
                            style = TextStyle(
                                color = data.statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Info lines: Schedule, Goal, Reminder
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.70f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Schedule: ${data.scheduleText}",
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            shadow = textShadow
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.70f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Goal: ${data.goalText}",
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            shadow = textShadow
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (data.reminderText != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.70f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Reminder: ${data.reminderText}",
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                shadow = textShadow
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleHabitStatTilesRow(
    data: SingleHabitShareData,
    blurredBitmap: ImageBitmap?,
    accentColor: Color,
    glassOpacity: Float,
    textShadow: Shadow
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatTile(
            icon = Icons.Default.Whatshot,
            accentTint = Color(0xFFFF9800),
            value = "${data.activeStreak}d",
            label = "Active\nStreak",
            blurredBitmap = blurredBitmap,
            accentColor = accentColor,
            glassOpacity = glassOpacity,
            textShadow = textShadow,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        StatTile(
            icon = Icons.Default.EmojiEvents,
            accentTint = Color(0xFFFFC107),
            value = "${data.bestStreak}d",
            label = "Best\nStreak",
            blurredBitmap = blurredBitmap,
            accentColor = accentColor,
            glassOpacity = glassOpacity,
            textShadow = textShadow,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        StatTile(
            icon = Icons.Default.BarChart,
            accentTint = Color(0xFF03A9F4),
            value = "${data.accuracy30d}%",
            label = "30-Day\nAccuracy",
            blurredBitmap = blurredBitmap,
            accentColor = accentColor,
            glassOpacity = glassOpacity,
            textShadow = textShadow,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        StatTile(
            icon = Icons.Default.CheckCircle,
            accentTint = Color(0xFF10B981),
            value = "${data.totalLogged}",
            label = "Total\nLogged",
            blurredBitmap = blurredBitmap,
            accentColor = accentColor,
            glassOpacity = glassOpacity,
            textShadow = textShadow,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun StatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentTint: Color,
    value: String,
    label: String,
    blurredBitmap: ImageBitmap?,
    accentColor: Color,
    glassOpacity: Float,
    textShadow: Shadow,
    modifier: Modifier = Modifier
) {
    SingleHabitGlassPanel(
        blurredBitmap = blurredBitmap,
        accentColor = accentColor,
        glassOpacity = glassOpacity,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        // Subtle individual accent tint overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(accentTint.copy(alpha = 0.08f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentTint,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = value,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    shadow = textShadow
                ),
                maxLines = 1,
                overflow = TextOverflow.Clip
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                    shadow = textShadow
                ),
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun SingleHabitPerformancePanel(
    data: SingleHabitShareData,
    blurredBitmap: ImageBitmap?,
    accentColor: Color,
    glassOpacity: Float,
    textShadow: Shadow,
    chartHeight: androidx.compose.ui.unit.Dp = 200.dp,
    modifier: Modifier = Modifier
) {
    SingleHabitGlassPanel(
        blurredBitmap = blurredBitmap,
        accentColor = accentColor,
        glassOpacity = glassOpacity,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Title & Subtitle on left, percentage and caption on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Habit Performance",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            shadow = textShadow
                        )
                    )
                    Text(
                        text = "Consistency over time",
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            shadow = textShadow
                        )
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = "${data.performancePercentage}%",
                        style = TextStyle(
                            color = data.habitColor,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            shadow = textShadow
                        )
                    )
                    Text(
                        text = data.captionText,
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            shadow = textShadow
                        )
                    )
                }
            }

            // Info row: true range of latest bucket + "done/scheduled d"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = data.latestBucketRangeText,
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.90f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            shadow = textShadow
                        )
                    )
                    Text(
                        text = data.latestBucketDoneScheduledText,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            shadow = textShadow
                        )
                    )
                }
            }

            // Consistency Curve Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
            ) {
                SingleHabitConsistencyChart(
                    buckets = data.buckets,
                    habitColor = data.habitColor,
                    textShadow = textShadow,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun SingleHabitConsistencyChart(
    buckets: List<ChartBucket>,
    habitColor: Color,
    textShadow: Shadow,
    modifier: Modifier = Modifier
) {
    if (buckets.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No data yet",
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    shadow = textShadow
                )
            )
        }
        return
    }

    val rates = remember(buckets) {
        buckets.map { (it.rate * 100f).coerceIn(0f, 100f) }
    }

    Column(modifier = modifier) {
        // Chart Area: Y-Axis labels (100, 50, 0) on left + Canvas for curves and dots
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Y-Axis labels
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "100%",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        shadow = textShadow
                    )
                )
                Text(
                    text = "50%",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        shadow = textShadow
                    )
                )
                Text(
                    text = "0%",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        shadow = textShadow
                    )
                )
            }

            // Canvas for gridlines, monotone curve, gradient fill, and dots
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 32.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
            ) {
                val chartW = size.width
                val chartH = size.height

                // Gridlines at 0%, 50%, 100%
                val gridColor = Color.White.copy(alpha = 0.12f)
                drawLine(gridColor, Offset(0f, 0f), Offset(chartW, 0f), strokeWidth = 1f)
                drawLine(gridColor, Offset(0f, chartH * 0.5f), Offset(chartW, chartH * 0.5f), strokeWidth = 1f)
                drawLine(gridColor, Offset(0f, chartH), Offset(chartW, chartH), strokeWidth = 1f)

                if (rates.size == 1) {
                    val ptX = chartW * 0.5f
                    val ptY = chartH - (rates[0] / 100f * chartH)
                    val isPartial = buckets[0].isPartial
                    if (isPartial) {
                        drawCircle(
                            color = Color(0xFF0D1117),
                            radius = 4.dp.toPx(),
                            center = Offset(ptX, ptY)
                        )
                        drawCircle(
                            color = habitColor,
                            radius = 4.dp.toPx(),
                            center = Offset(ptX, ptY),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    } else {
                        drawCircle(color = habitColor, radius = 4.5.dp.toPx(), center = Offset(ptX, ptY))
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(ptX, ptY))
                    }
                    return@Canvas
                }

                val stepX = chartW / (rates.size - 1)
                val points = rates.mapIndexed { idx, rate ->
                    val px = idx * stepX
                    val py = chartH - (rate / 100f * chartH)
                    Offset(px, py)
                }

                // Monotone cubic path
                val strokePath = Path().apply {
                    moveTo(points[0].x, points[0].y)
                }
                val fillPath = Path().apply {
                    moveTo(points[0].x, chartH)
                    lineTo(points[0].x, points[0].y)
                }

                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val dx = p1.x - p0.x
                    // Monotone control points clamped within [0, chartH]
                    val c1Y = p0.y.coerceIn(0f, chartH)
                    val c2Y = p1.y.coerceIn(0f, chartH)
                    val c1 = Offset(p0.x + dx * 0.45f, c1Y)
                    val c2 = Offset(p1.x - dx * 0.45f, c2Y)
                    strokePath.cubicTo(c1.x, c1.y, c2.x, c2.y, p1.x, p1.y)
                    fillPath.cubicTo(c1.x, c1.y, c2.x, c2.y, p1.x, p1.y)
                }

                fillPath.lineTo(points.last().x, chartH)
                fillPath.close()

                // Gradient fill alpha 0.18 down to 0.02
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            habitColor.copy(alpha = 0.20f),
                            habitColor.copy(alpha = 0.02f)
                        ),
                        startY = 0f,
                        endY = chartH
                    )
                )

                // 2dp Stroke curve line
                drawPath(
                    path = strokePath,
                    color = habitColor,
                    style = Stroke(width = 2.5.dp.toPx())
                )

                // Dots at points (5dp diameter = 2.5dp radius)
                points.forEachIndexed { idx, pt ->
                    val isLast = (idx == points.size - 1)
                    val isPartial = isLast && buckets[idx].isPartial

                    if (isPartial) {
                        // Hollow last dot for ongoing bucket
                        drawCircle(
                            color = Color(0xFF0D1117),
                            radius = 3.5.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = habitColor,
                            radius = 3.5.dp.toPx(),
                            center = pt,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    } else {
                        drawCircle(
                            color = habitColor,
                            radius = 3.5.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 1.5.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }

            // If only 1 point, show "Building your curve" caption in plot area
            if (rates.size == 1) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 32.dp, end = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Building your curve",
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.60f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            shadow = textShadow
                        )
                    )
                }
            }
        }

        // X-Axis Date Labels Row: aligned with chart dots
        SingleHabitAlignedXLabelsRow(
            buckets = buckets,
            maxLabels = 6,
            textShadow = textShadow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 8.dp, top = 4.dp)
        )
    }
}

@Composable
private fun SingleHabitAlignedXLabelsRow(
    buckets: List<ChartBucket>,
    maxLabels: Int = 6,
    textShadow: Shadow,
    modifier: Modifier = Modifier
) {
    if (buckets.isEmpty()) return

    if (buckets.size == 1) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = buckets[0].label,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    shadow = textShadow
                )
            )
        }
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        if (totalWidthPx <= 0f) return@BoxWithConstraints

        val density = LocalDensity.current
        val charWidthPx = with(density) { 6.2.dp.toPx() }
        val minGapPx = with(density) { 8.dp.toPx() }

        val n = buckets.size
        val candidateIndices = if (n <= maxLabels) {
            (0 until n).toList()
        } else {
            val step = (n - 1).toDouble() / (maxLabels - 1)
            val list = mutableListOf<Int>()
            list.add(0)
            for (i in 1 until maxLabels - 1) {
                val idx = (i * step).roundToInt().coerceIn(0, n - 1)
                if (!list.contains(idx)) list.add(idx)
            }
            if (!list.contains(n - 1)) list.add(n - 1)
            list.sorted()
        }

        data class Placement(val text: String, val leftPx: Float, val rightPx: Float)

        val firstText = buckets[0].label
        val firstWidthPx = firstText.length * charWidthPx
        val firstPlacement = Placement(firstText, 0f, firstWidthPx)

        val lastText = buckets[n - 1].label
        val lastWidthPx = lastText.length * charWidthPx
        val lastLeftPx = (totalWidthPx - lastWidthPx).coerceAtLeast(0f)
        val lastPlacement = Placement(lastText, lastLeftPx, totalWidthPx)

        val chosenPlacements = mutableListOf<Placement>()
        chosenPlacements.add(firstPlacement)

        var prevRightEdge = firstWidthPx

        for (idx in candidateIndices) {
            if (idx == 0 || idx == n - 1) continue
            val labelText = buckets[idx].label
            val labelWidthPx = labelText.length * charWidthPx
            val dotCenterPx = idx.toFloat() / (n - 1) * totalWidthPx
            val leftPx = (dotCenterPx - labelWidthPx / 2f).coerceIn(0f, (totalWidthPx - labelWidthPx).coerceAtLeast(0f))
            val rightPx = leftPx + labelWidthPx

            if (leftPx >= prevRightEdge + minGapPx && rightPx <= lastLeftPx - minGapPx) {
                chosenPlacements.add(Placement(labelText, leftPx, rightPx))
                prevRightEdge = rightPx
            }
        }

        chosenPlacements.add(lastPlacement)

        for (placement in chosenPlacements) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(placement.leftPx.roundToInt(), 0) }
            ) {
                Text(
                    text = placement.text,
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        shadow = textShadow
                    )
                )
            }
        }
    }
}

@Composable
private fun SingleHabitMomentumPanel(
    data: SingleHabitShareData,
    blurredBitmap: ImageBitmap?,
    accentColor: Color,
    glassOpacity: Float,
    textShadow: Shadow
) {
    SingleHabitGlassPanel(
        blurredBitmap = blurredBitmap,
        accentColor = accentColor,
        glassOpacity = glassOpacity
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(data.momentumInfo.color.copy(alpha = 0.25f), CircleShape)
                    .border(BorderStroke(1.dp, data.momentumInfo.color.copy(alpha = 0.60f)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = data.momentumInfo.color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = data.momentumInfo.tierName,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        shadow = textShadow
                    )
                )
                Text(
                    text = data.momentumInfo.description,
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        shadow = textShadow
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SingleHabitFooter(
    textShadow: Shadow
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.90f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Rudeus Habit",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = textShadow
                )
            )
        }

        Text(
            text = "Discipline today, a better tomorrow.",
            style = TextStyle(
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                shadow = textShadow
            )
        )
    }
}

/**
 * Single Habit Glass panel matching target look:
 * Pre-computed blurred background translated by panel position,
 * followed by one flat tint: accent alpha 0.20 + White alpha 0.10 + Black alpha 0.12,
 * and a 1dp White alpha 0.28 border. Corner radius 20dp. No nested blur.
 */
@Composable
fun SingleHabitGlassPanel(
    blurredBitmap: ImageBitmap?,
    accentColor: Color,
    glassOpacity: Float,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable BoxScope.() -> Unit
) {
    var panelPos by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                panelPos = coordinates.positionInRoot()
            }
            .clip(shape)
    ) {
        // 1) Pre-computed blurred wallpaper matching this panel's exact region
        if (blurredBitmap != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val srcX = panelPos.x.roundToInt().coerceIn(0, (blurredBitmap.width - 1).coerceAtLeast(0))
                val srcY = panelPos.y.roundToInt().coerceIn(0, (blurredBitmap.height - 1).coerceAtLeast(0))
                val srcW = size.width.roundToInt().coerceAtMost(blurredBitmap.width - srcX)
                val srcH = size.height.roundToInt().coerceAtMost(blurredBitmap.height - srcY)

                if (srcW > 0 && srcH > 0) {
                    drawImage(
                        image = blurredBitmap,
                        srcOffset = IntOffset(srcX, srcY),
                        srcSize = IntSize(srcW, srcH),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    )
                }
            }
        }

        // 2) Light & colored glass tint: accent alpha 0.20 + White alpha 0.10 + Black alpha 0.12, border 1dp White alpha 0.28
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(accentColor.copy(alpha = 0.20f))
                .background(Color.White.copy(alpha = 0.10f))
                .background(Color.Black.copy(alpha = 0.12f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)), shape)
        )

        // 3) Inner Content
        content()
    }
}
