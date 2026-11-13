package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Habit
import com.example.ui.theme.*
import com.example.util.DateUtils
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitsListCard(
    habits: List<Habit>,
    weeklyTotals: Map<String, Long> = emptyMap(),
    onToggleHabit: (Habit) -> Unit,
    onToggleHabitDate: (Habit, String) -> Unit = { _, _ -> },
    onEditHabit: (Habit) -> Unit,
    onNewHabit: () -> Unit,
    onLoadSample: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val onAccentColor = MaterialTheme.colorScheme.onPrimary

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Your habits",
                        style = Typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = AppTheme.colors.text
                        )
                    )
                    Text(
                        text = "(${habits.size})",
                        style = Typography.bodyMedium.copy(
                            color = AppTheme.colors.textFaint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                if (habits.isNotEmpty()) {
                    ModernButton(
                        onClick = onNewHabit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = onAccentColor
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New habit",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Add",
                            style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (habits.isEmpty()) {
                // Empty State
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No habits yet",
                        style = Typography.titleMedium.copy(
                            fontSize = 17.sp,
                            color = AppTheme.colors.textMuted
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add your first habit to start building your ledger.",
                        style = Typography.bodyMedium.copy(
                            color = AppTheme.colors.textFaint,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModernButton(
                            onClick = onNewHabit,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = onAccentColor
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("+ New habit", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        ModernOutlinedButton(
                            onClick = onLoadSample,
                            border = BorderStroke(1.dp, accentColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Load Examples", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                // List of habits
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    habits.forEachIndexed { index, habit ->
                        val totalSeconds = weeklyTotals[habit.id] ?: 0L
                        val weeklyTimeString = if (habit.trackTimeSpent || totalSeconds > 0L) {
                            DateUtils.formatSecondsToHoursMinutes(totalSeconds)
                        } else {
                            null
                        }
                        CompactHabitRowItem(
                            habit = habit,
                            weeklyTimeString = weeklyTimeString,
                            onToggle = { onToggleHabit(habit) },
                            onToggleDate = { date -> onToggleHabitDate(habit, date) },
                            onEdit = { onEditHabit(habit) }
                        )
                        if (index < habits.size - 1) {
                            HorizontalDivider(
                                color = AppTheme.colors.borderSoft,
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }

                // Legend for 7-day mini heatmap
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = AppTheme.colors.borderSoft, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "KEY:",
                        style = Typography.labelSmall.copy(color = AppTheme.colors.textFaint, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(
                            modifier = Modifier.size(9.dp).background(MossGreen, RoundedCornerShape(1.5.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = AppTheme.colors.iconOnAccent, modifier = Modifier.size(6.dp))
                        }
                        Text("Done", style = Typography.labelSmall.copy(color = AppTheme.colors.textMuted, fontSize = 9.5.sp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(MutedCrimson.copy(alpha = 0.3f), RoundedCornerShape(1.5.dp))
                                .border(0.8.dp, MutedCrimson, RoundedCornerShape(1.5.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = MutedCrimson, modifier = Modifier.size(6.dp))
                        }
                        Text("Missed", style = Typography.labelSmall.copy(color = AppTheme.colors.textMuted, fontSize = 9.5.sp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(accentColor.copy(alpha = 0.2f), RoundedCornerShape(1.5.dp))
                                .border(0.8.dp, accentColor, RoundedCornerShape(1.5.dp))
                        )
                        Text("Pending", style = Typography.labelSmall.copy(color = AppTheme.colors.textMuted, fontSize = 9.5.sp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(
                            modifier = Modifier.size(9.dp).background(AppTheme.colors.surface3, RoundedCornerShape(1.5.dp))
                        )
                        Text("Rest", style = Typography.labelSmall.copy(color = AppTheme.colors.textMuted, fontSize = 9.5.sp))
                    }
                }
            }
        }
    }
}

/**
 * Compact Habit Item
 * Structure: [color dot] [habit name] [category] [time badge] [streak flame] ---> [7-day dots] [checkmark]
 */
@Composable
fun CompactHabitRowItem(
    habit: Habit,
    weeklyTimeString: String? = null,
    onToggle: () -> Unit,
    onToggleDate: (String) -> Unit = {},
    onEdit: () -> Unit
) {
    val today = DateUtils.todayStr()
    val isDone = DateUtils.isDone(habit, today)
    val isScheduledToday = DateUtils.isScheduled(habit, today)
    val curStreak = remember(habit.completions, habit.frozenDates, today) { DateUtils.currentStreak(habit) }
    val durInfo = remember(habit.createdAt, habit.durationDays, today) { DateUtils.durationInfo(habit) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Line 1: [Color Dot] [Name] [Category] [Time] [Streak] ----- [7-Day Heatmap] [Checkmark]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(habit.color, CircleShape)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Habit Name + Category + Time + Streak (Single line)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEdit() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = habit.name,
                    style = Typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = AppTheme.colors.text
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Category pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = habit.color.copy(alpha = 0.2f),
                    border = BorderStroke(0.6.dp, habit.color)
                ) {
                    Text(
                        text = habit.category,
                        style = Typography.labelSmall.copy(
                            color = AppTheme.colors.text,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp
                        ),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        maxLines = 1
                    )
                }

                // Time spent (if tracked)
                if (habit.trackTimeSpent || weeklyTimeString != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("⏱️", fontSize = 10.sp)
                        if (weeklyTimeString != null) {
                            Text(
                                text = weeklyTimeString,
                                style = Typography.labelSmall.copy(
                                    color = AppTheme.colors.textFaint,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }

                // Streak flame count
                val accentColor = MaterialTheme.colorScheme.primary
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text("🔥", fontSize = 11.sp)
                    Text(
                        text = "$curStreak",
                        style = Typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (curStreak > 0) accentColor else AppTheme.colors.text
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 7-day single-row heatmap dots
            SingleRow7DayHeatmap(
                habit = habit,
                onToggleDate = onToggleDate
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Toggle checkmark button
            val buttonModifier = if (isScheduledToday) Modifier else Modifier.alpha(0.35f)
            val bgColor = if (isDone) MossGreen else Color.Transparent
            val borderColor = if (isDone) MossGreen else AppTheme.colors.border

            Surface(
                onClick = onToggle,
                modifier = buttonModifier.size(30.dp),
                shape = CircleShape,
                color = bgColor,
                border = BorderStroke(1.5.dp, borderColor)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isScheduledToday) {
                        if (isDone) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = AppTheme.colors.iconOnAccent,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Rest day",
                            tint = AppTheme.colors.textFaint,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 7-Day Mini Heatmap in a single horizontal row
 */
@Composable
fun SingleRow7DayHeatmap(
    habit: Habit,
    onToggleDate: (String) -> Unit = {}
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val last7Dates = DateUtils.lastNDates(7) // 7 days ending today

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        last7Dates.forEach { date ->
            val isDone = DateUtils.isDone(habit, date)
            val isMissed = DateUtils.isMissed(habit, date)
            val isScheduled = DateUtils.isScheduled(habit, date)
            val isToday = date == DateUtils.todayStr()

            val cellColor = when {
                !isScheduled -> AppTheme.colors.surface3
                isDone -> habit.color
                isMissed -> MutedCrimson.copy(alpha = 0.35f)
                isToday -> accentColor.copy(alpha = 0.2f)
                else -> ClayOrange.copy(alpha = 0.35f)
            }
            val borderColor = when {
                isMissed -> MutedCrimson
                isToday && !isDone -> accentColor
                else -> Color.Transparent
            }

            Surface(
                onClick = { if (isScheduled) onToggleDate(date) },
                modifier = Modifier.size(15.dp),
                shape = RoundedCornerShape(3.dp),
                color = cellColor,
                border = if (borderColor != Color.Transparent) BorderStroke(0.8.dp, borderColor) else null
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isDone) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Done $date",
                            tint = AppTheme.colors.iconOnAccent,
                            modifier = Modifier.size(9.dp)
                        )
                    } else if (isMissed) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Missed $date",
                            tint = MutedCrimson,
                            modifier = Modifier.size(8.dp)
                        )
                    }
                }
            }
        }
    }
}

// Keep backward compatibility if called elsewhere
@Composable
fun HabitRowItem(
    habit: Habit,
    weeklyTimeString: String? = null,
    onToggle: () -> Unit,
    onToggleDate: (String) -> Unit = {},
    onEdit: () -> Unit
) {
    CompactHabitRowItem(
        habit = habit,
        weeklyTimeString = weeklyTimeString,
        onToggle = onToggle,
        onToggleDate = onToggleDate,
        onEdit = onEdit
    )
}

@Composable
fun MiniHabitHeatmap(
    habit: Habit,
    onToggleDate: (String) -> Unit = {}
) {
    SingleRow7DayHeatmap(habit = habit, onToggleDate = onToggleDate)
}
