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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.model.Habit
import com.example.util.DateUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val textShadow = Shadow(
    color = Color.Black.copy(alpha = 0.55f),
    offset = Offset(0f, 1.5f),
    blurRadius = 3f
)

data class DayReportData(
    val dateStr: String,
    val pillDate: String,
    val fullDate: String,
    val allScheduledHabits: List<Habit>,
    val activeScheduledHabits: List<Habit>,
    val scheduledCount: Int,
    val completedCount: Int,
    val percentage: Int,
    val ringFraction: Float,
    val bestStreak: Int,
    val statusChipText: String?,
    val habitStreaks: Map<String, Int>
)

/**
 * Computes all numbers and labels from real data according to specification.
 */
fun computeDayReportData(
    dateStr: String,
    habits: List<Habit>,
    locale: Locale = Locale.getDefault()
): DayReportData {
    val localDate = try {
        LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US))
    } catch (e: Exception) {
        LocalDate.now()
    }
    val pillDate = localDate.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", locale))
    val fullDate = localDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale))

    // List only the habits SCHEDULED on that date (DateUtils.isScheduled) and not archived
    val allScheduled = habits.filter { DateUtils.isScheduled(it, dateStr) && !it.isArchived }

    // Frozen/rest habits on that date are excluded from counts and percentage
    val activeScheduled = allScheduled.filter { !DateUtils.isFrozen(it, dateStr) }

    val scheduledCount = activeScheduled.size
    val completedCount = activeScheduled.count { DateUtils.isDone(it, dateStr) }

    val percentage = if (scheduledCount > 0) {
        ((completedCount.toDouble() / scheduledCount.toDouble()) * 100.0).roundToInt()
    } else {
        0
    }

    val ringFraction = if (scheduledCount > 0) {
        completedCount.toFloat() / scheduledCount.toFloat()
    } else {
        0f
    }

    // Streak per habit: use the existing DateUtils.calculateStreak(habit, today = dateStr)
    val habitStreaks = allScheduled.associate { habit ->
        habit.id to DateUtils.calculateStreak(habit, today = dateStr)
    }

    val bestStreak = if (allScheduled.isNotEmpty()) {
        allScheduled.maxOfOrNull { habitStreaks[it.id] ?: 0 } ?: 0
    } else {
        0
    }

    val statusChipText = getDailyStatusChip(percentage, scheduledCount)

    return DayReportData(
        dateStr = dateStr,
        pillDate = pillDate,
        fullDate = fullDate,
        allScheduledHabits = allScheduled,
        activeScheduledHabits = activeScheduled,
        scheduledCount = scheduledCount,
        completedCount = completedCount,
        percentage = percentage,
        ringFraction = ringFraction,
        bestStreak = bestStreak,
        statusChipText = statusChipText,
        habitStreaks = habitStreaks
    )
}

/**
 * Status chip from ONE function with thresholds:
 * 80% or more "High Discipline Day", 50% to 79% "Good Progress", below 50% "Keep Going",
 * empty state has no chip.
 */
fun getDailyStatusChip(percentage: Int, scheduledCount: Int): String? {
    if (scheduledCount <= 0) return null
    return when {
        percentage >= 80 -> "High Discipline Day"
        percentage >= 50 -> "Good Progress"
        else -> "Keep Going"
    }
}

/**
 * Extracts or maps emoji for habit categories.
 */
private fun getHabitCategoryEmoji(category: String): String {
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

@Composable
fun DayReportShareCard(
    dateStr: String,
    habits: List<Habit>,
    showFooter: Boolean = true,
    backgroundImage: Bitmap? = null,
    blurredBackground: Bitmap? = null,
    accentColor: Color = Color(0xFFE11D48),
    glassOpacity: Float = 0.25f,
    modifier: Modifier = Modifier
) {
    val data = remember(dateStr, habits) {
        computeDayReportData(dateStr, habits)
    }

    val blurredImageBitmap = remember(blurredBackground) {
        blurredBackground?.asImageBitmap()
    }
    val baseImageBitmap = remember(backgroundImage) {
        backgroundImage?.asImageBitmap()
    }

    // Exact 9:16 layout: 540dp logical width x 960dp logical height at Density(2f, 1f) -> 1080px x 1920px
    CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
        Box(
            modifier = modifier
                .size(width = 540.dp, height = 960.dp)
        ) {
            // 1. Background layer: Vivid wallpaper with ContentScale.Crop over full 1080x1920
            if (baseImageBitmap != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val bmpW = baseImageBitmap.width.toFloat()
                    val bmpH = baseImageBitmap.height.toFloat()
                    val dstW = size.width
                    val dstH = size.height

                    val scale = maxOf(dstW / bmpW, dstH / bmpH)
                    val scaledW = bmpW * scale
                    val scaledH = bmpH * scale
                    val dx = (dstW - scaledW) / 2f
                    val dy = (dstH - scaledH) / 2f

                    drawImage(
                        image = baseImageBitmap,
                        dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
                        dstSize = IntSize(scaledW.roundToInt(), scaledH.roundToInt())
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF2A080C),
                                    Color(0xFF1A0608),
                                    Color(0xFF0F0406)
                                )
                            )
                        )
                )
            }

            // 2. Content Stack: Outer padding 16dp, inter-panel gap 10dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // =============================================================
                // A) HEADER & HERO PANEL
                // =============================================================
                DayReportGlassPanel(
                    blurredBitmap = blurredImageBitmap,
                    accentColor = accentColor,
                    glassOpacity = glassOpacity,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Top Header: Brand on left, Date pill on right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Brand
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(accentColor.copy(alpha = 0.25f), CircleShape)
                                        .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.50f)), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = "Bolt",
                                        tint = accentColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(
                                        text = "RUDEUS HABIT",
                                        style = TextStyle(
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 22.sp,
                                            letterSpacing = 0.05.sp,
                                            shadow = textShadow,
                                            textDirection = TextDirection.Content
                                        )
                                    )
                                    Text(
                                        text = "Daily Discipline & Habit Tracker",
                                        style = TextStyle(
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 13.sp,
                                            shadow = textShadow,
                                            textDirection = TextDirection.Content
                                        )
                                    )
                                }
                            }

                            // Right Date Pill (Strictly one line, softWrap = false)
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = data.pillDate,
                                        style = TextStyle(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            shadow = textShadow
                                        ),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // Hero Section: Progress ring on left, Details on right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            // Progress Ring
                            Box(
                                modifier = Modifier.size(96.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokeWidth = 9.dp.toPx()
                                    // Ring Track
                                    drawArc(
                                        color = Color.White.copy(alpha = 0.16f),
                                        startAngle = -90f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = Stroke(strokeWidth)
                                    )
                                    // Ring Progress Arc
                                    val sweep = (data.ringFraction * 360f).coerceIn(0f, 360f)
                                    val arcColor = if (data.percentage == 100) Color(0xFF10B981) else accentColor
                                    if (sweep > 0f) {
                                        drawArc(
                                            color = arcColor,
                                            startAngle = -90f,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            style = Stroke(strokeWidth, cap = StrokeCap.Round)
                                        )
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "${data.percentage}%",
                                        style = TextStyle(
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 25.sp,
                                            shadow = textShadow
                                        )
                                    )
                                    Text(
                                        text = "${data.completedCount} / ${data.scheduledCount}",
                                        style = TextStyle(
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            shadow = textShadow
                                        )
                                    )
                                }
                            }

                            // Right Details
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "Daily Progress Report",
                                    style = TextStyle(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        shadow = textShadow
                                    )
                                )

                                val subText = if (data.scheduledCount > 0) {
                                    "${data.completedCount} of ${data.scheduledCount} habits completed today"
                                } else {
                                    "No habits scheduled for this day"
                                }
                                Text(
                                    text = subText,
                                    style = TextStyle(
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 14.sp,
                                        shadow = textShadow
                                    )
                                )

                                // Status Chip (from getDailyStatusChip)
                                if (data.statusChipText != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(accentColor.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                                            .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)), RoundedCornerShape(14.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Bolt,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = data.statusChipText,
                                                style = TextStyle(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    shadow = textShadow
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // Optional short quote on the top right
                            Text(
                                text = "“A little progress every day makes a big difference.”",
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.80f),
                                    fontSize = 11.5.sp,
                                    fontStyle = FontStyle.Italic,
                                    textAlign = TextAlign.End,
                                    shadow = textShadow
                                ),
                                modifier = Modifier
                                    .widthIn(max = 115.dp)
                                    .align(Alignment.Top)
                            )
                        }

                        // Divider
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.16f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        // Bottom Row: Best Active Streak on left, Full Date on right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "🔥",
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Best Active Streak: ${data.bestStreak} Days",
                                    style = TextStyle(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        shadow = textShadow
                                    )
                                )
                            }

                            Text(
                                text = data.fullDate,
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    shadow = textShadow
                                )
                            )
                        }
                    }
                }

                // =============================================================
                // B) TODAY'S ROUTINES & HABITS PANEL
                // =============================================================
                DayReportGlassPanel(
                    blurredBitmap = blurredImageBitmap,
                    accentColor = accentColor,
                    glassOpacity = glassOpacity,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Section Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "TODAY'S ROUTINES & HABITS",
                                    style = TextStyle(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        letterSpacing = 0.04.sp,
                                        shadow = textShadow
                                    )
                                )
                            }

                            Text(
                                text = "${data.completedCount}/${data.scheduledCount} Completed",
                                style = TextStyle(
                                    color = if (data.scheduledCount > 0 && data.completedCount == data.scheduledCount) Color(0xFF10B981) else accentColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    shadow = textShadow
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Habit Rows (up to 7 rows)
                        if (data.allScheduledHabits.isEmpty()) {
                            // Empty State
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EventBusy,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.60f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = "No habits scheduled for this day",
                                        style = TextStyle(
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            shadow = textShadow
                                        )
                                    )
                                }
                            }
                        } else {
                            val displayHabits = data.allScheduledHabits.take(7)
                            val overflowCount = data.allScheduledHabits.size - displayHabits.size

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                displayHabits.forEach { habit ->
                                    val isDone = DateUtils.isDone(habit, data.dateStr)
                                    val isFrozen = DateUtils.isFrozen(habit, data.dateStr)
                                    val streak = data.habitStreaks[habit.id] ?: 0

                                    HabitReportRow(
                                        habit = habit,
                                        isDone = isDone,
                                        isFrozen = isFrozen,
                                        streak = streak
                                    )
                                }
                            }

                            if (overflowCount > 0) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "+ $overflowCount more routines tracked in Rudeus Habit ∨",
                                    style = TextStyle(
                                        color = Color.White.copy(alpha = 0.80f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        shadow = textShadow
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // =============================================================
                // C) QUOTE PANEL (Toggleable with footer)
                // =============================================================
                if (showFooter) {
                    DayReportGlassPanel(
                        blurredBitmap = blurredImageBitmap,
                        accentColor = accentColor,
                        glassOpacity = glassOpacity,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "✨",
                                fontSize = 26.sp
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "“Small daily disciplines build extraordinary lifelong results.”",
                                    style = TextStyle(
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.Medium,
                                        shadow = textShadow
                                    )
                                )
                                Text(
                                    text = "📱 Track your habits with Rudeus Habit · Daily Discipline",
                                    style = TextStyle(
                                        color = Color.White.copy(alpha = 0.80f),
                                        fontSize = 11.5.sp,
                                        shadow = textShadow
                                    )
                                )
                            }
                        }
                    }

                    // =============================================================
                    // D) FOOTER (No Google Play badge, no rating, no download button)
                    // =============================================================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "RUDEUS HABIT",
                                style = TextStyle(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    shadow = textShadow
                                )
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.30f))
                            )
                            Text(
                                text = "“Discipline today, a better tomorrow.”",
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    fontStyle = FontStyle.Italic,
                                    shadow = textShadow
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual habit row (64dp target / 50dp content):
 * Colored habit stripe, check circle (green when done, snowflake if frozen, empty ring when pending),
 * habit name (16sp, weight 1f, maxLines 1, ellipsis, Content textDirection for Arabic/RTL),
 * category emoji chip, streak "Nd" with flame icon, chevron.
 */
@Composable
private fun HabitReportRow(
    habit: Habit,
    isDone: Boolean,
    isFrozen: Boolean,
    streak: Int,
    modifier: Modifier = Modifier
) {
    val habitColor = remember(habit.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(habit.colorHex))
        } catch (e: Exception) {
            Color(0xFF3B82F6)
        }
    }

    val categoryEmoji = remember(habit.category) {
        getHabitCategoryEmoji(habit.category)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Colored habit stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(26.dp)
                    .background(habitColor, RoundedCornerShape(2.dp))
            )

            // Check Circle (green when done, snowflake if frozen, empty ring when not)
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isFrozen -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF26C6DA).copy(alpha = 0.25f), CircleShape)
                                .border(BorderStroke(1.5.dp, Color(0xFF26C6DA)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "❄",
                                style = TextStyle(
                                    color = Color(0xFF26C6DA),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                    isDone -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF10B981), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Done",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.50f)), CircleShape)
                        )
                    }
                }
            }

            // Habit Name: 16sp, weight(1f), 1 line, ellipsis only if longer, RTL support
            Text(
                text = habit.name,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDirection = TextDirection.Content,
                    shadow = textShadow
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Category Emoji Chip
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = categoryEmoji,
                    fontSize = 14.sp
                )
            }

            // Streak "Nd" with flame icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "🔥",
                    fontSize = 13.sp
                )
                Text(
                    text = "${streak}d",
                    style = TextStyle(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        shadow = textShadow
                    )
                )
            }

            // Muted Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.40f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Glass panel rendering for Day Report:
 * Pre-computed blurred background translated by panel position,
 * followed by one flat tint: accent alpha 0.20 + White alpha 0.10 + Black alpha 0.12,
 * and a 1dp White alpha 0.28 border. Corner radius 20dp. No nested blur.
 */
@Composable
fun DayReportGlassPanel(
    blurredBitmap: ImageBitmap?,
    accentColor: Color,
    glassOpacity: Float,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var panelPos by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
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
