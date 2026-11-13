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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import com.example.model.ChartBucket
import com.example.model.ChartGranularity
import com.example.model.HabitWeekTrend
import com.example.ui.HabitWithAnalytics
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

@Composable
fun AllHabitsShareCard(
    habitsWithAnalytics: List<HabitWithAnalytics>,
    showFooter: Boolean = true,
    backgroundImage: Bitmap? = null,
    blurredBackground: Bitmap? = null,
    accentColor: Color = Color(0xFFE11D48),
    glassOpacity: Float = 0.25f,
    modifier: Modifier = Modifier
) {
    val activeHabits = remember(habitsWithAnalytics) {
        habitsWithAnalytics.filter { !it.habit.isArchived }
    }
    val todayStr = remember { DateUtils.todayStr() }

    val todayDisplayDate = remember {
        try {
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US))
        } catch (e: Exception) {
            todayStr
        }
    }

    val activeCount = activeHabits.size
    val avgConsistency = remember(activeHabits) {
        if (activeHabits.isNotEmpty()) {
            (activeHabits.map { it.completionRate30d.toDouble() }.average() * 100.0).roundToInt()
        } else 0
    }

    val bestStreak = remember(activeHabits, todayStr) {
        if (activeHabits.isNotEmpty()) {
            activeHabits.maxOf { DateUtils.calculateStreak(it.habit, today = todayStr) }
        } else 0
    }

    val totalCompletions = remember(activeHabits) {
        activeHabits.sumOf { it.habit.completions.size }
    }

    val displayedHabits = remember(activeHabits) {
        activeHabits.take(30)
    }
    val overflowHabitsCount = activeHabits.size - displayedHabits.size

    val headerChipText = remember(displayedHabits, todayStr) {
        if (displayedHabits.isEmpty()) {
            "Since start"
        } else {
            val results = displayedHabits.map { DateUtils.buildChartBuckets(it.habit, today = todayStr) }
            val distinctG = results.map { it.first }.distinct()
            if (distinctG.size > 1) {
                "Mixed ranges"
            } else {
                when (distinctG.firstOrNull() ?: ChartGranularity.DAY) {
                    ChartGranularity.DAY -> "Since start"
                    ChartGranularity.WEEK -> {
                        val maxWeeks = results.maxOfOrNull { it.second.size } ?: 1
                        if (maxWeeks == 1) "Last 1 week" else "Last $maxWeeks weeks"
                    }
                    ChartGranularity.MONTH -> {
                        val maxMonths = results.maxOfOrNull { it.second.size } ?: 1
                        if (maxMonths == 1) "Last 1 month" else "Last $maxMonths months"
                    }
                }
            }
        }
    }

    // Odd habit count logic: place Streaks panel in the empty last grid cell
    val isOddCount = displayedHabits.size % 2 != 0

    val blurredImageBitmap = remember(blurredBackground) {
        blurredBackground?.asImageBitmap()
    }
    val baseImageBitmap = remember(backgroundImage) {
        backgroundImage?.asImageBitmap()
    }

    // Fix A: Logical width 540dp, Density(2f, 1f) -> exports at exactly 1080px
    CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
        Box(
            modifier = modifier
                .width(540.dp)
                .wrapContentHeight()
        ) {
            // Background Layer: Vivid wallpaper with ContentScale.Crop over whole height
            if (baseImageBitmap != null) {
                Canvas(modifier = Modifier.matchParentSize()) {
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
                        .matchParentSize()
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

            // Main Content: Outer padding 16dp, Gaps 12dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // -------------------------------------------------------------
                // A) HEADER GLASS PANEL
                // -------------------------------------------------------------
                ExportGlassPanel(
                    blurredBitmap = blurredImageBitmap,
                    accentColor = accentColor,
                    glassOpacity = glassOpacity,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header Top Row: Brand on left, Date pill on right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            // Left Brand & Slogan
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(accentColor.copy(alpha = 0.25f), CircleShape)
                                        .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.50f)), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = "Bolt",
                                        tint = accentColor,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "RUDEUS HABIT",
                                        style = TextStyle(
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 22.sp,
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

                            // Right Date pill (single line, softWrap=false) & subtext
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.90f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = todayDisplayDate,
                                            style = TextStyle(
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                shadow = textShadow
                                            ),
                                            softWrap = false,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Text(
                                    text = "“Better habits. A better you.”",
                                    style = TextStyle(
                                        color = Color.White.copy(alpha = 0.80f),
                                        fontStyle = FontStyle.Italic,
                                        fontSize = 12.sp,
                                        shadow = textShadow,
                                        textAlign = TextAlign.End
                                    )
                                )
                            }
                        }

                        // One-line quote under brand
                        Text(
                            text = "“Small daily disciplines build extraordinary lifelong results.”",
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.85f),
                                fontStyle = FontStyle.Italic,
                                fontSize = 12.sp,
                                shadow = textShadow,
                                textDirection = TextDirection.Content
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // 4 Equal-width summary tiles in one row (minHeight 84dp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Tile 1: Avg Consistency with ring
                            SummaryRingTile(
                                modifier = Modifier.weight(1f),
                                percentage = avgConsistency,
                                label = "Avg Consistency",
                                accentColor = accentColor
                            )

                            // Tile 2: Active Habits
                            SummaryMetricTile(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.FormatListBulleted,
                                value = "$activeCount",
                                label = "Active Habits",
                                accentColor = accentColor
                            )

                            // Tile 3: Best Streak
                            SummaryMetricTile(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.LocalFireDepartment,
                                value = "${bestStreak}d",
                                label = "Best Streak",
                                accentColor = accentColor
                            )

                            // Tile 4: Total Completions
                            SummaryMetricTile(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.BarChart,
                                value = "$totalCompletions",
                                label = "Completions",
                                accentColor = accentColor
                            )
                        }
                    }
                }

                // -------------------------------------------------------------
                // B) PROGRESS CURVES 2-COLUMN GRID PANEL
                // -------------------------------------------------------------
                ExportGlassPanel(
                    blurredBitmap = blurredImageBitmap,
                    accentColor = accentColor,
                    glassOpacity = glassOpacity,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Section Header Row
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
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "All Habit Progress Curves",
                                        style = TextStyle(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            shadow = textShadow,
                                            textDirection = TextDirection.Content
                                        )
                                    )
                                    Text(
                                        text = "Track your consistency over time",
                                        style = TextStyle(
                                            color = Color.White.copy(alpha = 0.80f),
                                            fontSize = 12.sp,
                                            shadow = textShadow
                                        )
                                    )
                                }
                            }

                            // Chip with dynamic granularity range
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = headerChipText,
                                        style = TextStyle(
                                            color = Color.White.copy(alpha = 0.90f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            shadow = textShadow
                                        )
                                    )
                                }
                            }
                        }

                        // 2-Column Grid
                        if (displayedHabits.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No active habits tracked yet",
                                    style = TextStyle(
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 13.sp,
                                        shadow = textShadow
                                    )
                                )
                            }
                        } else {
                            val totalSlots = if (isOddCount) displayedHabits.size + 1 else displayedHabits.size
                            val rows = (totalSlots + 1) / 2

                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (r in 0 until rows) {
                                    val leftIdx = r * 2
                                    val rightIdx = r * 2 + 1
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Left Cell
                                        if (leftIdx < displayedHabits.size) {
                                            HabitCurveMiniCard(
                                                habitWithAnalytics = displayedHabits[leftIdx],
                                                index = leftIdx + 1,
                                                todayStr = todayStr,
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }

                                        // Right Cell
                                        if (rightIdx < displayedHabits.size) {
                                            HabitCurveMiniCard(
                                                habitWithAnalytics = displayedHabits[rightIdx],
                                                index = rightIdx + 1,
                                                todayStr = todayStr,
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else if (isOddCount && rightIdx == displayedHabits.size) {
                                            // Odd habit count: place streaks panel in the empty last grid cell!
                                            InlineStreaksMiniPanel(
                                                habits = displayedHabits,
                                                todayStr = todayStr,
                                                accentColor = accentColor,
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }

                                if (overflowHabitsCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterHorizontally)
                                            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "+$overflowHabitsCount more habits in Rudeus Habit",
                                            style = TextStyle(
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                shadow = textShadow
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // -------------------------------------------------------------
                // C) ROUTINE CONSISTENCY & STREAKS (FULL-WIDTH PANEL FOR EVEN COUNT)
                // -------------------------------------------------------------
                if (!isOddCount && displayedHabits.isNotEmpty()) {
                    ExportGlassPanel(
                        blurredBitmap = blurredImageBitmap,
                        accentColor = accentColor,
                        glassOpacity = glassOpacity,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Section Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = "Trophy",
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "Routine Consistency & Streaks",
                                        style = TextStyle(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            shadow = textShadow
                                        )
                                    )
                                    Text(
                                        text = "Current streak and completion rate for each habit",
                                        style = TextStyle(
                                            color = Color.White.copy(alpha = 0.80f),
                                            fontSize = 11.sp,
                                            shadow = textShadow
                                        )
                                    )
                                }
                            }

                            // Layout rows: room for up to 12 habits, with overflow indicator if more
                            val shownHabits = displayedHabits.take(12)
                            val streaksOverflowCount = displayedHabits.size - shownHabits.size

                            if (shownHabits.size > 4) {
                                val half = (shownHabits.size + 1) / 2
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (i in 0 until half) {
                                            HabitStreakRow(
                                                habitWithAnalytics = shownHabits[i],
                                                index = i + 1,
                                                todayStr = todayStr
                                            )
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (i in half until shownHabits.size) {
                                            HabitStreakRow(
                                                habitWithAnalytics = shownHabits[i],
                                                index = i + 1,
                                                todayStr = todayStr
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (i in shownHabits.indices) {
                                        HabitStreakRow(
                                            habitWithAnalytics = shownHabits[i],
                                            index = i + 1,
                                            todayStr = todayStr
                                        )
                                    }
                                }
                            }

                            if (streaksOverflowCount > 0) {
                                Text(
                                    text = "+$streaksOverflowCount more",
                                    style = TextStyle(
                                        color = Color.White.copy(alpha = 0.70f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        shadow = textShadow
                                    ),
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                }

                // -------------------------------------------------------------
                // D) FOOTER PANEL (Toggleable, no Google Play badge, no rating)
                // -------------------------------------------------------------
                if (showFooter) {
                    ExportGlassPanel(
                        blurredBitmap = blurredImageBitmap,
                        accentColor = accentColor,
                        glassOpacity = glassOpacity,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(accentColor.copy(alpha = 0.25f), CircleShape)
                                        .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.50f)), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Rudeus Habit",
                                        style = TextStyle(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            shadow = textShadow,
                                            textDirection = TextDirection.Content
                                        )
                                    )
                                    Text(
                                        text = "Build a better you, one habit at a time.",
                                        style = TextStyle(
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 12.sp,
                                            shadow = textShadow,
                                            textDirection = TextDirection.Content
                                        )
                                    )
                                }
                            }

                            Text(
                                text = "Discipline & Consistency",
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.80f),
                                    fontSize = 12.sp,
                                    fontStyle = FontStyle.Italic,
                                    shadow = textShadow
                                )
                            )
                        }
                    }

                    // Bottom Quote Line
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚡ Discipline today, a better tomorrow.",
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                shadow = textShadow
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRingTile(
    percentage: Int,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 84.dp)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)), RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 3.5.dp.toPx()
                    drawArc(
                        color = Color.White.copy(alpha = 0.15f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(strokeWidth)
                    )
                    drawArc(
                        color = accentColor,
                        startAngle = -90f,
                        sweepAngle = (percentage * 3.6f).coerceIn(0f, 360f),
                        useCenter = false,
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "$percentage%",
                    style = TextStyle(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        shadow = textShadow
                    )
                )
            }
            Text(
                text = label,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    shadow = textShadow
                ),
                maxLines = 2,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun SummaryMetricTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 84.dp)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)), RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                style = TextStyle(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    shadow = textShadow
                )
            )
            Text(
                text = label,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    shadow = textShadow
                ),
                maxLines = 2,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun ExportGlassPanel(
    blurredBitmap: ImageBitmap?,
    accentColor: Color,
    glassOpacity: Float,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit
) {
    var panelPos by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                panelPos = coordinates.positionInRoot()
            }
            .clip(shape)
    ) {
        // 1) Pre-computed blurred wallpaper matching this panel's exact region
        if (blurredBitmap != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val srcX = panelPos.x.roundToInt().coerceIn(0, blurredBitmap.width - 1)
                val srcY = panelPos.y.roundToInt().coerceIn(0, blurredBitmap.height - 1)
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

        // 2) Light and colored glass tint (Fix B: accent color alpha 0.20 + White alpha 0.10 + Black alpha 0.12)
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

@Composable
private fun HabitCurveMiniCard(
    habitWithAnalytics: HabitWithAnalytics,
    index: Int,
    todayStr: String = DateUtils.todayStr(),
    modifier: Modifier = Modifier
) {
    val habit = habitWithAnalytics.habit
    val habitColor = remember(habit.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(habit.colorHex))
        } catch (e: Exception) {
            Color(0xFF3B82F6)
        }
    }

    val (granularity, buckets) = remember(habit, todayStr) {
        DateUtils.buildChartBuckets(habit, today = todayStr)
    }

    val latestBucket = buckets.lastOrNull()
    val latestRate = remember(buckets, habitWithAnalytics.completionRate30d) {
        if (latestBucket != null) {
            (latestBucket.rate * 100f).roundToInt()
        } else {
            (habitWithAnalytics.completionRate30d * 100f).roundToInt()
        }
    }

    val captionText = when (granularity) {
        ChartGranularity.DAY -> "So far"
        ChartGranularity.WEEK -> if (latestBucket?.isPartial == true) "This week" else "Latest week"
        ChartGranularity.MONTH -> if (latestBucket?.isPartial == true) "This month" else "Latest month"
    }

    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Row 1: Dot (10dp) + habit name (15sp semibold, weight(1f), maxLines 2) + category emoji (22dp)
            // Right: % (18sp bold) and caption under it
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(10.dp)
                            .background(habitColor, CircleShape)
                    )
                    Text(
                        text = "$index. ${habit.name}",
                        style = TextStyle(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            shadow = textShadow,
                            textDirection = TextDirection.Content
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    val catEmoji = remember(habit.category) { getHabitCategoryEmoji(habit.category) }
                    if (catEmoji.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = catEmoji,
                                style = TextStyle(fontSize = 12.sp)
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = "$latestRate%",
                        style = TextStyle(
                            color = habitColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            shadow = textShadow
                        )
                    )
                    Text(
                        text = captionText,
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.80f),
                            fontSize = 11.sp,
                            shadow = textShadow
                        )
                    )
                }
            }

            // Mini chart
            HabitCurveCanvas(
                buckets = buckets,
                habitColor = habitColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
            )
        }
    }
}

@Composable
private fun HabitCurveCanvas(
    buckets: List<ChartBucket>,
    habitColor: Color,
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
                    fontSize = 11.sp,
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
        // Chart Area: Y labels on left + Canvas for gridlines, fill, curve, dots
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Y-Axis labels (100, 50, 0)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "100",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        shadow = textShadow
                    )
                )
                Text(
                    text = "50",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        shadow = textShadow
                    )
                )
                Text(
                    text = "0",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        shadow = textShadow
                    )
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp)
            ) {
                val chartW = size.width
                val chartH = size.height

                // Subtle gridlines at 0, 50, 100
                val gridColor = Color.White.copy(alpha = 0.12f)
                drawLine(gridColor, Offset(0f, 0f), Offset(chartW, 0f), strokeWidth = 1f)
                drawLine(gridColor, Offset(0f, chartH * 0.5f), Offset(chartW, chartH * 0.5f), strokeWidth = 1f)
                drawLine(gridColor, Offset(0f, chartH), Offset(chartW, chartH), strokeWidth = 1f)

                if (rates.size == 1) {
                    val ptX = chartW * 0.5f
                    val ptY = chartH - (rates[0] / 100f * chartH)
                    val isPartial = buckets[0].isPartial
                    if (isPartial) {
                        drawCircle(Color(0xFF0D1117), radius = 3.dp.toPx(), center = Offset(ptX, ptY))
                        drawCircle(habitColor, radius = 3.dp.toPx(), center = Offset(ptX, ptY), style = Stroke(width = 1.5.dp.toPx()))
                    } else {
                        drawCircle(habitColor, radius = 3.5.dp.toPx(), center = Offset(ptX, ptY))
                        drawCircle(Color.White, radius = 1.5.dp.toPx(), center = Offset(ptX, ptY))
                    }
                    return@Canvas
                }

                val stepX = chartW / (rates.size - 1)
                val points = rates.mapIndexed { idx, rate ->
                    val px = idx * stepX
                    val py = chartH - (rate / 100f * chartH)
                    Offset(px, py)
                }

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
                    val c1 = Offset(p0.x + dx * 0.45f, p0.y.coerceIn(0f, chartH))
                    val c2 = Offset(p1.x - dx * 0.45f, p1.y.coerceIn(0f, chartH))
                    strokePath.cubicTo(c1.x, c1.y, c2.x, c2.y, p1.x, p1.y)
                    fillPath.cubicTo(c1.x, c1.y, c2.x, c2.y, p1.x, p1.y)
                }

                fillPath.lineTo(points.last().x, chartH)
                fillPath.close()

                // Draw gradient fill (alpha 0.18f to 0.02f)
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        listOf(
                            habitColor.copy(alpha = 0.18f),
                            habitColor.copy(alpha = 0.02f)
                        )
                    )
                )

                // Draw 2dp stroke
                drawPath(
                    path = strokePath,
                    color = habitColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Draw dots (hollow if last and partial)
                points.forEachIndexed { idx, pt ->
                    val isLast = (idx == points.size - 1)
                    val isPartial = isLast && buckets[idx].isPartial

                    if (isPartial) {
                        drawCircle(Color(0xFF0D1117), radius = 3.dp.toPx(), center = pt)
                        drawCircle(habitColor, radius = 3.dp.toPx(), center = pt, style = Stroke(width = 1.5.dp.toPx()))
                    } else {
                        drawCircle(habitColor, radius = 3.dp.toPx(), center = pt)
                        drawCircle(Color.White, radius = 1.2.dp.toPx(), center = pt)
                    }
                }
            }

            // If 1 point, display "Building your curve"
            if (rates.size == 1) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Building your curve",
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.60f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            shadow = textShadow
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Aligned X-Labels (Max 4 labels for mini cards)
        MiniCardAlignedXLabelsRow(
            buckets = buckets,
            maxLabels = 4,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp)
        )
    }
}

@Composable
private fun MiniCardAlignedXLabelsRow(
    buckets: List<ChartBucket>,
    maxLabels: Int = 4,
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
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 11.sp,
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
        val charWidthPx = with(density) { 6.5.dp.toPx() }
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
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 11.sp,
                        shadow = textShadow
                    )
                )
            }
        }
    }
}

@Composable
private fun InlineStreaksMiniPanel(
    habits: List<HabitWithAnalytics>,
    todayStr: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val shownHabits = habits.take(5)
    val overflowCount = habits.size - shownHabits.size

    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Streaks & Rate",
                    style = TextStyle(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        shadow = textShadow
                    )
                )
            }

            for ((idx, h) in shownHabits.withIndex()) {
                HabitStreakRow(
                    habitWithAnalytics = h,
                    index = idx + 1,
                    todayStr = todayStr
                )
            }

            if (overflowCount > 0) {
                Text(
                    text = "+$overflowCount more",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        shadow = textShadow
                    ),
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun HabitStreakRow(
    habitWithAnalytics: HabitWithAnalytics,
    index: Int,
    todayStr: String,
    modifier: Modifier = Modifier
) {
    val habit = habitWithAnalytics.habit
    val habitColor = remember(habit.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(habit.colorHex))
        } catch (e: Exception) {
            Color(0xFF3B82F6)
        }
    }
    val currentStreak = remember(habit, todayStr) {
        DateUtils.calculateStreak(habit, today = todayStr)
    }
    val rate30d = habitWithAnalytics.completionRate30d.coerceIn(0f, 1f)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Color square
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(habitColor, RoundedCornerShape(2.dp))
        )

        // Habit Name: 12sp, 45% of the row, ellipsis only if longer
        Text(
            text = "$index. ${habit.name}",
            style = TextStyle(
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                shadow = textShadow,
                textDirection = TextDirection.Content
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.45f)
        )

        // Progress bar: habit's 30-day rate, min 60dp
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 60.dp)
                .weight(0.38f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(rate30d)
                    .background(habitColor)
            )
        }

        // Streak "Nd" (12sp)
        Text(
            text = "${currentStreak}d",
            style = TextStyle(
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                shadow = textShadow
            ),
            modifier = Modifier.widthIn(min = 26.dp)
        )
    }
}

private fun getHabitCategoryEmoji(category: String): String {
    val trimmed = category.trim()
    if (trimmed.isNotEmpty() && Character.isSurrogate(trimmed[0])) {
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
