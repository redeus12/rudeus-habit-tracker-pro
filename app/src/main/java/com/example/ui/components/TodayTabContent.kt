package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Habit
import com.example.model.Task
import com.example.ui.HabitViewModel
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.util.ShareHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatDisplayDate(dateStr: String): String {
    val today = DateUtils.todayStr()
    val yesterday = DateUtils.addDays(today, -1)
    val tomorrow = DateUtils.addDays(today, 1)
    
    return when (dateStr) {
        today -> "Today"
        yesterday -> "Yesterday"
        tomorrow -> "Tomorrow"
        else -> {
            try {
                val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
                val outputFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)
                val date = LocalDate.parse(dateStr, inputFormatter)
                date.format(outputFormatter)
            } catch (e: Exception) {
                dateStr
            }
        }
    }
}

@Composable
fun TodayTabContent(
    viewModel: HabitViewModel,
    onEditHabit: (Habit) -> Unit
) {
    val context = LocalContext.current
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val subtasks by viewModel.subtasks.collectAsStateWithLifecycle()
    val weeklyTotals by viewModel.weeklyTotals.collectAsStateWithLifecycle()

    val backgroundConfig = LocalBackgroundConfig.current
    val isGlass = backgroundConfig.isEnabled && backgroundConfig.imagePath != null

    val accentColor = MaterialTheme.colorScheme.primary
    val onAccentColor = MaterialTheme.colorScheme.onPrimary
    val accentContainer = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)

    var selectedDate by remember { mutableStateOf(DateUtils.todayStr()) }
    var selectedCategory by remember { mutableStateOf("All") }
    var isHabitsSectionExpanded by remember { mutableStateOf(false) }
    var isTasksSectionExpanded by remember { mutableStateOf(false) }
    var isFreezeModalOpen by remember { mutableStateOf(false) }
    var freezeModalHabit by remember { mutableStateOf<Habit?>(null) }
    
    // Filter habits scheduled for the selected date and not archived
    val habitsToday = habits.filter { DateUtils.isScheduled(it, selectedDate) && !it.isArchived }
    
    val completedHabitsCount = habitsToday.count { DateUtils.isDone(it, selectedDate) }
    val frozenHabitsCount = habitsToday.count { DateUtils.isFrozen(it, selectedDate) }
    val totalHabitsCount = habitsToday.size
    val habitsProgress = if (totalHabitsCount > 0) completedHabitsCount.toFloat() / totalHabitsCount else 0f

    // Available categories from today's scheduled habits
    val availableCategories = remember(habitsToday) {
        val cats = habitsToday.map { it.category.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        if (cats.size > 1) listOf("All") + cats else emptyList()
    }

    val displayedHabits = remember(habitsToday, selectedCategory) {
        if (selectedCategory == "All") habitsToday
        else habitsToday.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        
        // Date Navigator Row
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { selectedDate = DateUtils.addDays(selectedDate, -1) }
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous Day",
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatDisplayDate(selectedDate),
                        style = Typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isGlass) Color.White else AppTheme.colors.text,
                            fontSize = 16.sp
                        )
                    )
                    if (selectedDate != DateUtils.todayStr()) {
                        Text(
                            text = selectedDate,
                            style = Typography.labelSmall.copy(
                                color = if (isGlass) Color.White.copy(alpha = 0.80f) else AppTheme.colors.textFaint,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
                
                IconButton(
                    onClick = { selectedDate = DateUtils.addDays(selectedDate, 1) },
                    enabled = selectedDate < DateUtils.todayStr()
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next Day",
                        tint = if (selectedDate < DateUtils.todayStr()) accentColor else AppTheme.colors.textFaint.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        
        // Habits Today Card Section
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Clickable Header to Collapse/Expand the Whole Habits Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isHabitsSectionExpanded = !isHabitsSectionExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedDate == DateUtils.todayStr()) "Habits Today" else "Habits for ${formatDisplayDate(selectedDate)}",
                            style = Typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = if (isGlass) Color.White else AppTheme.colors.text
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (totalHabitsCount > 0) "$completedHabitsCount of $totalHabitsCount completed" else "No routines scheduled",
                            style = Typography.bodyMedium.copy(
                                color = if (isGlass) Color.White.copy(alpha = 0.80f) else AppTheme.colors.textMuted,
                                fontSize = 13.sp
                            )
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (totalHabitsCount > 0) {
                            GlassPill(
                                shape = RoundedCornerShape(20.dp),
                                accentColor = accentColor,
                                glassAlpha = 0.28f,
                                borderAlpha = 0.30f
                            ) {
                                Text(
                                    text = "${(habitsProgress * 100).toInt()}% Done",
                                    style = Typography.labelMedium.copy(
                                        color = accentColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (frozenHabitsCount > 0) {
                            GlassPill(
                                shape = RoundedCornerShape(20.dp),
                                accentColor = Color(0xFF26C6DA),
                                glassAlpha = 0.28f,
                                borderAlpha = 0.30f
                            ) {
                                Text(
                                    text = "❄️ $frozenHabitsCount Frozen",
                                    style = Typography.labelMedium.copy(
                                        color = Color(0xFF26C6DA),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                freezeModalHabit = null
                                isFreezeModalOpen = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AcUnit,
                                contentDescription = "Streak Freeze & Rest Day",
                                tint = Color(0xFF26C6DA),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                ShareHelper.shareDailyProgress(context, selectedDate, habitsToday, tasks)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Daily Progress Image",
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Chevron Collapse / Expand Toggle
                        Surface(
                            shape = CircleShape,
                            color = AppTheme.colors.surface2,
                            border = BorderStroke(1.dp, AppTheme.colors.borderSoft),
                            modifier = Modifier.size(32.dp)
                        ) {
                            IconButton(
                                onClick = { isHabitsSectionExpanded = !isHabitsSectionExpanded },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = if (isHabitsSectionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isHabitsSectionExpanded) "Collapse Habits Section" else "Expand Habits Section",
                                    tint = AppTheme.colors.textMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Progress Bar (Always visible when there are habits)
                if (totalHabitsCount > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { habitsProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = accentColor,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )

                    // Category Filter Chips (Always visible when there are categories)
                    if (availableCategories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableCategories.forEach { cat ->
                                val isSelected = selectedCategory == cat
                                GlassPill(
                                    shape = RoundedCornerShape(16.dp),
                                    glassAlpha = 0.16f,
                                    borderAlpha = 0.25f,
                                    accentColor = if (isSelected) accentColor else null,
                                    isSelected = isSelected,
                                    onClick = { selectedCategory = cat }
                                ) {
                                    Text(
                                        text = cat,
                                        style = Typography.labelMedium.copy(
                                            color = if (isSelected) {
                                                Color.White
                                            } else {
                                                AppTheme.colors.textMuted
                                            },
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Section Body - Habits list or empty state (Collapsible)
                AnimatedVisibility(
                    visible = isHabitsSectionExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))

                        if (totalHabitsCount > 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                displayedHabits.forEach { habit ->
                                    val habitSubtasks = subtasks.filter { it.habitId == habit.id && it.dateStr == selectedDate }
                                    val totalSeconds = weeklyTotals[habit.id] ?: 0L
                                    val weeklyTimeString = if (habit.trackTimeSpent || totalSeconds > 0L) {
                                        "${DateUtils.formatSecondsToHoursMinutes(totalSeconds)} this week"
                                    } else {
                                        null
                                    }
                                    TodayHabitItem(
                                        habit = habit,
                                        selectedDate = selectedDate,
                                        subtasks = habitSubtasks,
                                        weeklyTimeString = weeklyTimeString,
                                        onToggle = { viewModel.toggleHabitDate(habit, selectedDate) },
                                        onFreezeClick = {
                                            freezeModalHabit = habit
                                            isFreezeModalOpen = true
                                        },
                                        onEdit = { onEditHabit(habit) },
                                        onDelete = { viewModel.requestDeleteHabit(habit) },
                                        onAddSubtask = { text -> viewModel.addSubtask(habit.id, selectedDate, text) },
                                        onToggleSubtask = { subtask -> viewModel.toggleSubtask(subtask) },
                                        onDeleteSubtask = { subtask -> viewModel.deleteSubtask(subtask) }
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .background(accentContainer, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Checklist,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "All clear for this date",
                                        style = Typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = AppTheme.colors.text,
                                            fontSize = 16.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "No habits are scheduled. Take a break or add a new routine.",
                                        style = Typography.bodySmall.copy(
                                            color = AppTheme.colors.textMuted,
                                            fontSize = 12.sp
                                        ),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Other Tasks Section Card (Only shown for today)
        if (selectedDate == DateUtils.todayStr()) {
            val completedCount = tasks.count { it.isCompleted }
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isTasksSectionExpanded = !isTasksSectionExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Other Tasks",
                                style = Typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = AppTheme.colors.text
                                )
                            )
                            Text(
                                text = if (tasks.isNotEmpty()) "$completedCount of ${tasks.size} done" else "Quick to-dos",
                                style = Typography.bodyMedium.copy(
                                    color = AppTheme.colors.textMuted,
                                    fontSize = 13.sp
                                )
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (tasks.any { it.isCompleted }) {
                                TextButton(
                                    onClick = { viewModel.clearCompletedTasks() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MutedCrimson)
                                ) {
                                    Text(
                                        text = "Clear completed",
                                        style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }

                            // Chevron Collapse / Expand Toggle
                            Surface(
                                shape = CircleShape,
                                color = AppTheme.colors.surface2,
                                border = BorderStroke(1.dp, AppTheme.colors.borderSoft),
                                modifier = Modifier.size(32.dp)
                            ) {
                                IconButton(
                                    onClick = { isTasksSectionExpanded = !isTasksSectionExpanded },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = if (isTasksSectionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isTasksSectionExpanded) "Collapse Tasks Section" else "Expand Tasks Section",
                                        tint = AppTheme.colors.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isTasksSectionExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(18.dp))

                            // Add Task TextField
                            var newTaskText by remember { mutableStateOf("") }
                            val keyboardController = LocalSoftwareKeyboardController.current

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextField(
                                    value = newTaskText,
                                    onValueChange = { newTaskText = it },
                                    placeholder = {
                                        Text(
                                            text = "Add a quick task (e.g., Pay bills)",
                                            style = Typography.bodyMedium.copy(color = AppTheme.colors.textFaint)
                                        )
                                    },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = AppTheme.colors.surface2,
                                        unfocusedContainerColor = AppTheme.colors.surface2,
                                        focusedIndicatorColor = accentColor,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = AppTheme.colors.text,
                                        unfocusedTextColor = AppTheme.colors.text
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        if (newTaskText.isNotBlank()) {
                                            viewModel.addTask(newTaskText)
                                            newTaskText = ""
                                            keyboardController?.hide()
                                        }
                                    })
                                )

                                IconButton(
                                    onClick = {
                                        if (newTaskText.isNotBlank()) {
                                            viewModel.addTask(newTaskText)
                                            newTaskText = ""
                                            keyboardController?.hide()
                                        }
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = accentColor,
                                        contentColor = onAccentColor
                                    ),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Task",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            if (tasks.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    tasks.forEachIndexed { index, task ->
                                        TodayTaskItem(
                                            task = task,
                                            onToggle = { viewModel.toggleTask(task) },
                                            onDelete = { viewModel.deleteTask(task.id) }
                                        )
                                        if (index < tasks.size - 1) {
                                            HorizontalDivider(color = AppTheme.colors.borderSoft, thickness = 1.dp)
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .background(accentContainer, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = accentColor,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = "No pending quick tasks",
                                            style = Typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = AppTheme.colors.text,
                                                fontSize = 16.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Add one-time checklist items or reminders using the field above.",
                                            style = Typography.bodySmall.copy(
                                                color = AppTheme.colors.textMuted,
                                                fontSize = 12.sp
                                            ),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        StreakFreezeModal(
            isOpen = isFreezeModalOpen,
            habit = freezeModalHabit,
            selectedDate = selectedDate,
            onClose = { isFreezeModalOpen = false },
            onConfirmFreeze = { targetId, dateStr, reason ->
                if (targetId == null) {
                    viewModel.freezeAllHabitsToday(reason)
                } else {
                    val targetHabit = freezeModalHabit ?: return@StreakFreezeModal
                    viewModel.toggleFreezeDate(targetHabit, dateStr, reason)
                }
            },
            onUnfreeze = { targetId, dateStr ->
                if (targetId == null) {
                    viewModel.unfreezeAllHabitsToday()
                } else {
                    val targetHabit = freezeModalHabit ?: return@StreakFreezeModal
                    viewModel.toggleFreezeDate(targetHabit, dateStr)
                }
            }
        )
    }
}

@Composable
fun TodayHabitItem(
    habit: Habit,
    selectedDate: String = DateUtils.todayStr(),
    subtasks: List<com.example.model.HabitSubtask>,
    weeklyTimeString: String?,
    onToggle: () -> Unit,
    onFreezeClick: () -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddSubtask: (String) -> Unit,
    onToggleSubtask: (com.example.model.HabitSubtask) -> Unit,
    onDeleteSubtask: (com.example.model.HabitSubtask) -> Unit
) {
    val context = LocalContext.current
    val isDone = habit.completions.contains(selectedDate)
    val isFrozen = habit.frozenDates.contains(selectedDate)
    var isExpanded by remember { mutableStateOf(false) }

    val bounceScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDone) 1.05f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "bounce"
    )

    val scheduleText = remember(habit.days) {
        if (habit.days.size == 7) "Every day"
        else {
            val names = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            habit.days.sorted().mapNotNull { if (it in 0..6) names[it] else null }.joinToString(", ")
        }
    }

    val targetGoalText = if (habit.durationDays != null) "${habit.durationDays} Days challenge" else "Ongoing routine"
    val longestStreak = remember(habit.completions, habit.frozenDates) { DateUtils.longestStreak(habit) }
    val currentStreak = remember(habit.completions, habit.frozenDates, selectedDate) { DateUtils.currentStreakAsOf(habit, selectedDate) }
    val totalCompletions = habit.completions.size

    val backgroundConfig = LocalBackgroundConfig.current
    val isGlass = backgroundConfig.isEnabled && backgroundConfig.imagePath != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Main Top Row (always visible)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left accent bar
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(42.dp)
                        .background(if (isFrozen) Color(0xFF26C6DA) else habit.color, RoundedCornerShape(2.dp))
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { isExpanded = !isExpanded }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = habit.name,
                            style = Typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isGlass) (if (isDone) Color.White.copy(alpha = 0.60f) else Color.White) else (if (isDone) AppTheme.colors.textMuted else AppTheme.colors.text),
                                textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                            )
                        )
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = habit.color.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = habit.category,
                                style = Typography.labelSmall.copy(
                                    color = habit.color,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (isFrozen) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF26C6DA).copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "❄️ Rest Day",
                                    style = Typography.labelSmall.copy(
                                        color = Color(0xFF26C6DA),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isFrozen) "Protected: ❄️ Streak Safe" else "Streak: 🔥 $currentStreak",
                            style = Typography.labelSmall.copy(
                                color = if (isGlass) (if (isFrozen) Color(0xFF80DEEA) else Color.White.copy(alpha = 0.80f)) else (if (isFrozen) Color(0xFF26C6DA) else AppTheme.colors.textFaint),
                                fontWeight = if (isFrozen) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        )
                        if (weeklyTimeString != null) {
                            Text(
                                text = "·  ⏱️ $weeklyTimeString",
                                style = Typography.labelSmall.copy(
                                    color = if (isGlass) Color.White.copy(alpha = 0.80f) else AppTheme.colors.textMuted,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        if (subtasks.isNotEmpty()) {
                            val completedSub = subtasks.count { it.isCompleted }
                            Text(
                                text = "·  Subtasks: $completedSub/${subtasks.size}",
                                style = Typography.labelSmall.copy(
                                    color = if (isGlass) Color.White.copy(alpha = 0.80f) else AppTheme.colors.textMuted,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }

                // Expand/Collapse Chevron arrow
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse details" else "Expand details",
                        tint = if (isExpanded) MaterialTheme.colorScheme.primary else AppTheme.colors.textFaint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Toggle button with tactile bounce
                val bgColor = when {
                    isDone -> MossGreen
                    isFrozen -> Color(0xFF26C6DA).copy(alpha = 0.2f)
                    else -> Color.Transparent
                }
                val borderColor = when {
                    isDone -> MossGreen
                    isFrozen -> Color(0xFF26C6DA)
                    else -> AppTheme.colors.border
                }

                Surface(
                    onClick = onToggle,
                    modifier = Modifier
                        .size(34.dp)
                        .scale(bounceScale),
                    shape = CircleShape,
                    color = bgColor,
                    border = BorderStroke(1.8.dp, borderColor)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isDone) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = AppTheme.colors.iconOnAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        } else if (isFrozen) {
                            Icon(
                                imageVector = Icons.Default.AcUnit,
                                contentDescription = "Frozen",
                                tint = Color(0xFF26C6DA),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Expanded Details Section (Accordion Animation)
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    HorizontalDivider(
                        color = if (isGlass) Color.White.copy(alpha = 0.20f) else AppTheme.colors.borderSoft,
                        thickness = 1.dp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Habit Meta Details Grid (Translucent Flat Panel)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "🗓️ Schedule: $scheduleText",
                                    style = Typography.bodySmall.copy(
                                        color = if (isGlass) Color.White else AppTheme.colors.text,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Text(
                                    text = "🎯 Target: $targetGoalText",
                                    style = Typography.bodySmall.copy(
                                        color = if (isGlass) Color.White.copy(alpha = 0.85f) else AppTheme.colors.textMuted,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "⏰ Reminder: ${habit.reminderTime ?: "None"}",
                                    style = Typography.bodySmall.copy(
                                        color = if (isGlass) Color.White else AppTheme.colors.text,
                                        fontSize = 12.sp
                                    )
                                )
                                Text(
                                    text = "📅 Created: ${habit.createdAt}",
                                    style = Typography.bodySmall.copy(
                                        color = if (isGlass) Color.White.copy(alpha = 0.75f) else AppTheme.colors.textFaint,
                                        fontSize = 11.sp
                                    )
                                )
                            }

                            // Stats as two small chips in a row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GlassPill(
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "🏆 Best Streak: $longestStreak days",
                                            style = Typography.labelSmall.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }

                                GlassPill(
                                    accentColor = MossGreen,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "✅ Total logs: $totalCompletions times",
                                            style = Typography.labelSmall.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Subtasks section
                    Text(
                        text = "Subtasks & Steps:",
                        style = Typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isGlass) Color.White else AppTheme.colors.textMuted,
                            fontSize = 12.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (subtasks.isNotEmpty()) {
                        subtasks.forEach { subtask ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val subBgColor = if (subtask.isCompleted) MossGreen else Color.Transparent
                                val subBorderColor = if (subtask.isCompleted) MossGreen else if (isGlass) Color.White.copy(alpha = 0.35f) else AppTheme.colors.border

                                Surface(
                                    onClick = { onToggleSubtask(subtask) },
                                    modifier = Modifier.size(24.dp),
                                    shape = CircleShape,
                                    color = subBgColor,
                                    border = BorderStroke(1.2.dp, subBorderColor)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (subtask.isCompleted) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Completed",
                                                tint = Color.White,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = subtask.text,
                                    style = Typography.bodyMedium.copy(
                                        color = if (subtask.isCompleted) {
                                            if (isGlass) Color.White.copy(alpha = 0.55f) else AppTheme.colors.textFaint
                                        } else {
                                            if (isGlass) Color.White else AppTheme.colors.text
                                        },
                                        textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                        fontSize = 13.sp
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onToggleSubtask(subtask) }
                                )

                                IconButton(
                                    onClick = { onDeleteSubtask(subtask) },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = if (isGlass) Color.White.copy(alpha = 0.70f) else AppTheme.colors.textFaint
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete subtask",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Add Subtask input row
                    var newSubtaskText by remember { mutableStateOf("") }
                    val subKeyboardController = LocalSoftwareKeyboardController.current

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = newSubtaskText,
                            onValueChange = { newSubtaskText = it },
                            placeholder = {
                                Text(
                                    text = "Add step (e.g., 20 mins, 3 sets)",
                                    style = Typography.bodyMedium.copy(
                                        color = if (isGlass) Color.White.copy(alpha = 0.65f) else AppTheme.colors.textFaint,
                                        fontSize = 12.sp
                                    )
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = if (isGlass) Color.White.copy(alpha = 0.15f) else AppTheme.colors.surface,
                                unfocusedContainerColor = if (isGlass) Color.White.copy(alpha = 0.10f) else AppTheme.colors.surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = if (isGlass) Color.White else AppTheme.colors.text,
                                unfocusedTextColor = if (isGlass) Color.White else AppTheme.colors.text
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (newSubtaskText.isNotBlank()) {
                                    onAddSubtask(newSubtaskText)
                                    newSubtaskText = ""
                                    subKeyboardController?.hide()
                                }
                            })
                        )

                        Surface(
                            onClick = {
                                if (newSubtaskText.isNotBlank()) {
                                    onAddSubtask(newSubtaskText)
                                    newSubtaskText = ""
                                    subKeyboardController?.hide()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add subtask",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons (Freeze, Edit, Share, Delete - height 56dp)
                    // If the screen is very narrow (< 340dp), switch to a 2x2 grid instead.
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isNarrow = maxWidth < 340.dp
                        if (isNarrow) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    HabitExpandedActionButton(
                                        onClick = onFreezeClick,
                                        icon = Icons.Default.AcUnit,
                                        label = if (isFrozen) "Unfreeze" else "Freeze",
                                        modifier = Modifier.weight(1f)
                                    )
                                    HabitExpandedActionButton(
                                        onClick = onEdit,
                                        icon = Icons.Default.Edit,
                                        label = "Edit",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    HabitExpandedActionButton(
                                        onClick = {
                                            ShareHelper.shareHabitStreak(context, habit)
                                        },
                                        icon = Icons.Default.Share,
                                        label = "Share",
                                        modifier = Modifier.weight(1f)
                                    )
                                    HabitExpandedActionButton(
                                        onClick = onDelete,
                                        icon = Icons.Default.Delete,
                                        label = "Delete",
                                        isDanger = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HabitExpandedActionButton(
                                    onClick = onFreezeClick,
                                    icon = Icons.Default.AcUnit,
                                    label = if (isFrozen) "Unfreeze" else "Freeze",
                                    modifier = Modifier.weight(1f)
                                )
                                HabitExpandedActionButton(
                                    onClick = onEdit,
                                    icon = Icons.Default.Edit,
                                    label = "Edit",
                                    modifier = Modifier.weight(1f)
                                )
                                HabitExpandedActionButton(
                                    onClick = {
                                        ShareHelper.shareHabitStreak(context, habit)
                                    },
                                    icon = Icons.Default.Share,
                                    label = "Share",
                                    modifier = Modifier.weight(1f)
                                )
                                HabitExpandedActionButton(
                                    onClick = onDelete,
                                    icon = Icons.Default.Delete,
                                    label = "Delete",
                                    isDanger = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitExpandedActionButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    isDanger: Boolean = false
) {
    val bgColor = if (isDanger) Color(0xFFE53935).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f)
    val borderColor = if (isDanger) Color(0xFFFF8A80).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.30f)
    val contentColor = if (isDanger) Color(0xFFFFCDD2) else Color.White

    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
fun TodayTaskItem(
    task: Task,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val accentColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Toggle Check circle
                val bgColor = if (task.isCompleted) accentColor else Color.Transparent
                val borderColor = if (task.isCompleted) accentColor else AppTheme.colors.border

                Surface(
                    onClick = onToggle,
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = bgColor,
                    border = BorderStroke(1.5.dp, borderColor)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (task.isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = AppTheme.colors.iconOnAccent,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = task.text,
                    style = Typography.bodyMedium.copy(
                        color = if (task.isCompleted) AppTheme.colors.textFaint else AppTheme.colors.text,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        fontWeight = if (task.isCompleted) FontWeight.Normal else FontWeight.Medium,
                        fontSize = 14.sp
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { isExpanded = !isExpanded }
                )

                // Expand/Collapse Chevron arrow
                val accentColor = MaterialTheme.colorScheme.primary
                val onAccentColor = MaterialTheme.colorScheme.onPrimary
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse task" else "Expand task",
                        tint = if (isExpanded) accentColor else AppTheme.colors.textFaint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = AppTheme.colors.textFaint),
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Task",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expanded task details accordion
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                ) {
                    HorizontalDivider(color = AppTheme.colors.borderSoft, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (task.isCompleted) "Status: Completed ✅" else "Status: In progress ⏳",
                            style = Typography.labelMedium.copy(
                                color = if (task.isCompleted) accentColor else AppTheme.colors.textMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                        Text(
                            text = "📅 Added: ${task.createdAt}",
                            style = Typography.bodySmall.copy(
                                color = AppTheme.colors.textFaint,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onToggle,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, accentColor),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = accentColor
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (task.isCompleted) "Mark as Incomplete" else "Mark as Completed",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onDelete,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MutedCrimson.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MutedCrimson),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.weight(0.7f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
