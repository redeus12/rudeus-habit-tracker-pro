package com.example.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Habit
import com.example.service.TimerService
import com.example.ui.HabitViewModel
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.util.MediaControlHelper

enum class FocusTimerMode(val label: String, val iconName: String) {
    POMODORO("Pomodoro", "25m intervals"),
    STOPWATCH("Stopwatch", "Count up"),
    COUNTDOWN("Countdown", "Custom target")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerTabContent(
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val todayStr = remember { DateUtils.todayStr() }

    // Service state
    val runningHabitId by TimerService.activeHabitId.collectAsStateWithLifecycle()
    val runningHabitName by TimerService.activeHabitName.collectAsStateWithLifecycle()
    val isRunning by TimerService.isRunning.collectAsStateWithLifecycle()
    val isPaused by TimerService.isPaused.collectAsStateWithLifecycle()
    val elapsedSeconds by TimerService.elapsedSeconds.collectAsStateWithLifecycle()
    val activeServiceMode by TimerService.activeMode.collectAsStateWithLifecycle()
    val targetDurationService by TimerService.targetDuration.collectAsStateWithLifecycle()
    val pomodoroRoundService by TimerService.pomodoroRound.collectAsStateWithLifecycle()
    val isPomodoroBreakService by TimerService.isPomodoroBreak.collectAsStateWithLifecycle()
    val pendingSession by TimerService.pendingSession.collectAsStateWithLifecycle()

    val musicSourceMode by viewModel.musicSourceMode.collectAsStateWithLifecycle()
    val autoPlayMusicWithTimer by viewModel.autoPlayMusicWithTimer.collectAsStateWithLifecycle()
    val isMusicPlaying by TimerService.isMusicPlaying.collectAsStateWithLifecycle()

    val backgroundConfig = LocalBackgroundConfig.current
    val isGlass = backgroundConfig.isEnabled && backgroundConfig.imagePath != null

    val accentColor = MaterialTheme.colorScheme.primary
    val onAccentColor = MaterialTheme.colorScheme.onPrimary
    val accentContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)

    var isDeviceMusicActive by remember { mutableStateOf(false) }
    LaunchedEffect(musicSourceMode) {
        if (musicSourceMode == MusicSourceMode.EXTERNAL) {
            while (true) {
                isDeviceMusicActive = MediaControlHelper.isMusicActive(context)
                kotlinx.coroutines.delay(4000)
            }
        }
    }

    val currentTrackUri by TimerService.currentTrackUri.collectAsStateWithLifecycle()
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val currentPlayingTrack = remember(currentTrackUri, playlist) {
        playlist.find { it.uri == currentTrackUri }
    }

    // Active habit selection logic:
    // If the timer is running or paused, the service's habit (TimerService.activeHabitId) always wins.
    // Only when the timer is idle does the picker selection apply, then scheduled today, then trackTimeSpent, then first.
    val selectedTimerHabitId by viewModel.selectedTimerHabitId.collectAsStateWithLifecycle()
    val activeHabit = remember(runningHabitId, selectedTimerHabitId, habits, isRunning, isPaused) {
        if ((isRunning || isPaused) && !runningHabitId.isNullOrBlank()) {
            habits.find { it.id == runningHabitId }
        } else if (!selectedTimerHabitId.isNullOrBlank()) {
            habits.find { it.id == selectedTimerHabitId }
        } else {
            habits.find { !it.isArchived && DateUtils.isScheduled(it, todayStr) }
                ?: habits.find { !it.isArchived && it.trackTimeSpent }
                ?: habits.firstOrNull { !it.isArchived }
        }
    }

    // If pending session habit no longer exists, consume silently and show nothing
    LaunchedEffect(pendingSession, habits) {
        val p = pendingSession
        if (p != null && habits.isNotEmpty() && habits.none { it.id == p.habitId }) {
            TimerService.consumePendingSession(context, p.id)
        }
    }

    var showHabitPicker by remember { mutableStateOf(false) }

    // Timer modes state
    var selectedTimerMode by remember { mutableStateOf(FocusTimerMode.POMODORO) }
    var countdownTargetMinutes by remember { mutableIntStateOf(50) }

    // Sync selectedTimerMode with service if running
    LaunchedEffect(activeServiceMode, isRunning) {
        if (isRunning || isPaused) {
            when (activeServiceMode) {
                "POMODORO" -> selectedTimerMode = FocusTimerMode.POMODORO
                "STOPWATCH" -> selectedTimerMode = FocusTimerMode.STOPWATCH
                "COUNTDOWN" -> selectedTimerMode = FocusTimerMode.COUNTDOWN
            }
        }
    }

    val pomodoroFocusDurationSeconds = 25 * 60L
    val pomodoroBreakDurationSeconds = if (pomodoroRoundService < 4) 5 * 60L else 15 * 60L
    val currentPomodoroTarget = if (isPomodoroBreakService) pomodoroBreakDurationSeconds else pomodoroFocusDurationSeconds
    val countdownTargetSeconds = countdownTargetMinutes * 60L

    // Compute display time
    val (displayMinutes, displaySeconds, progressRatio, displaySubLabel) = when (selectedTimerMode) {
        FocusTimerMode.POMODORO -> {
            val target = if (isRunning || isPaused) targetDurationService else currentPomodoroTarget
            val remaining = (target - elapsedSeconds).coerceAtLeast(0L)
            val min = remaining / 60
            val sec = remaining % 60
            val ratio = if (target > 0L) (elapsedSeconds.toFloat() / target.toFloat()).coerceIn(0f, 1f) else 0f
            val label = if (isPomodoroBreakService) "🌿 Rest Break (Round $pomodoroRoundService of 4)" else "🔥 Focus Round $pomodoroRoundService of 4"
            Quadruple(min, sec, ratio, label)
        }
        FocusTimerMode.COUNTDOWN -> {
            val target = if (isRunning || isPaused) targetDurationService else countdownTargetSeconds
            val remaining = (target - elapsedSeconds).coerceAtLeast(0L)
            val min = remaining / 60
            val sec = remaining % 60
            val ratio = if (target > 0L) (elapsedSeconds.toFloat() / target.toFloat()).coerceIn(0f, 1f) else 0f
            val label = "🎯 Target: ${target / 60} minutes"
            Quadruple(min, sec, ratio, label)
        }
        FocusTimerMode.STOPWATCH -> {
            val totalSec = elapsedSeconds
            val min = totalSec / 60
            val sec = totalSec % 60
            val ratio = if (totalSec == 0L) 0f else ((totalSec % 3600).toFloat() / 3600f).coerceIn(0f, 1f)
            val label = "⚡ Continuous Routine Flow"
            Quadruple(min, sec, ratio, label)
        }
    }

    val timeString = String.format("%02d:%02d", displayMinutes, displaySeconds)
    val animatedProgress by animateFloatAsState(targetValue = progressRatio, label = "timerProgress")
    val isDark = AppTheme.colors.isDark

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Banner Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentContainerColor,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Focus & Stopwatch",
                        style = Typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (isGlass) Color.White else AppTheme.colors.text
                        )
                    )
                    Text(
                        text = "Track your time with Pomodoro, Stopwatch, or Countdown",
                        style = Typography.bodySmall.copy(
                            color = if (isGlass) Color.White.copy(alpha = 0.80f) else AppTheme.colors.textMuted,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 2. ACTIVE TARGET HABIT Section
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE TARGET ROUTINE",
                    style = Typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = AppTheme.colors.textMuted,
                        letterSpacing = 0.5.sp
                    )
                )

                val isScheduledToday = activeHabit != null && DateUtils.isScheduled(activeHabit, todayStr)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isScheduledToday) accentContainerColor else AppTheme.colors.surface2
                ) {
                    Text(
                        text = if (isScheduledToday) "⭐ SCHEDULED TODAY" else (activeHabit?.category?.uppercase() ?: "ROUTINE"),
                        style = Typography.labelSmall.copy(
                            color = if (isScheduledToday) accentColor else AppTheme.colors.textMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showHabitPicker = true
                    },
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Habit color dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(activeHabit?.color ?: accentColor)
                    )
                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeHabit?.name ?: "Select a routine to focus",
                            style = Typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (isGlass) Color.White else AppTheme.colors.text
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val streakDays = remember(activeHabit) {
                            if (activeHabit != null) DateUtils.currentStreak(activeHabit) else 0
                        }
                        val isDoneToday = activeHabit != null && DateUtils.isDone(activeHabit, todayStr)
                        Text(
                            text = if (isDoneToday) "Completed today • Streak: $streakDays days" else "Streak: $streakDays days • Tap to switch routine",
                            style = Typography.bodySmall.copy(
                                color = if (isGlass) Color.White.copy(alpha = 0.80f) else (if (isDoneToday) accentColor else AppTheme.colors.textMuted),
                                fontSize = 12.5.sp,
                                fontWeight = if (isDoneToday) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }

                    // Selector badge button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isGlass) Color.White.copy(alpha = 0.15f) else AppTheme.colors.surface3,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Change",
                                style = Typography.labelSmall.copy(
                                    color = if (isGlass) Color.White else AppTheme.colors.text,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.UnfoldMore,
                                contentDescription = "Change Habit",
                                tint = if (isGlass) Color.White.copy(alpha = 0.85f) else AppTheme.colors.text,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. MAIN FOCUS TIMER CARD
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Segmented Mode Selector: Pomodoro, Stopwatch, Countdown
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isGlass) Color.White.copy(alpha = 0.06f) else AppTheme.colors.surface2,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FocusTimerMode.values().forEach { mode ->
                            val isSelected = selectedTimerMode == mode
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color.White else Color.Transparent,
                                shadowElevation = 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (selectedTimerMode != mode) {
                                            selectedTimerMode = mode
                                            TimerService.resetTimer(context)
                                        }
                                    }
                            ) {
                                Text(
                                    text = mode.label,
                                    style = Typography.labelMedium.copy(
                                        color = if (isSelected) {
                                            Color.Black.copy(alpha = 0.87f)
                                        } else {
                                            if (isGlass) Color.White.copy(alpha = 0.70f) else AppTheme.colors.textMuted
                                        },
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Countdown duration selector chips (if countdown selected and not running)
                if (selectedTimerMode == FocusTimerMode.COUNTDOWN && !isRunning && !isPaused) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(15, 25, 30, 45, 50, 60).forEach { mins ->
                            val isChosen = countdownTargetMinutes == mins
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isChosen) accentColor else if (isGlass) Color.White.copy(alpha = 0.06f) else AppTheme.colors.surface2,
                                border = BorderStroke(1.dp, if (isChosen) accentColor else if (isGlass) Color.White.copy(alpha = 0.12f) else AppTheme.colors.borderSoft),
                                modifier = Modifier.clickable { countdownTargetMinutes = mins }
                            ) {
                                Text(
                                    text = "${mins}m",
                                    style = Typography.labelSmall.copy(
                                        color = if (isChosen) onAccentColor else AppTheme.colors.text,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Central Circular Ring Display
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(240.dp)
                        .padding(8.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 10.dp.toPx()
                        // Track ring
                        drawCircle(
                            color = if (isGlass) Color.White.copy(alpha = 0.20f) else if (isDark) Color(0xFF222F28) else Color(0xFFEFEAE0),
                            style = Stroke(width = strokeWidth)
                        )
                        // Progress ring
                        val sweep = when (selectedTimerMode) {
                            FocusTimerMode.STOPWATCH -> {
                                if (elapsedSeconds == 0L) 360f else (360f * animatedProgress).coerceIn(5f, 360f)
                            }
                            FocusTimerMode.POMODORO -> {
                                (360f * (1f - animatedProgress)).coerceIn(5f, 360f)
                            }
                            FocusTimerMode.COUNTDOWN -> {
                                (360f * (1f - animatedProgress)).coerceIn(5f, 360f)
                            }
                        }
                        val ringColor = accentColor
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val headerLabel = when (selectedTimerMode) {
                            FocusTimerMode.POMODORO -> if (isPomodoroBreakService) "REST INTERVAL" else "POMODORO FOCUS"
                            FocusTimerMode.STOPWATCH -> "STOPWATCH TIME"
                            FocusTimerMode.COUNTDOWN -> "COUNTDOWN"
                        }
                        Text(
                            text = headerLabel,
                            style = Typography.labelMedium.copy(
                                color = if (isGlass) Color.White.copy(alpha = 0.85f) else accentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = timeString,
                            style = Typography.displayMedium.copy(
                                fontSize = 46.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isGlass) Color.White else AppTheme.colors.text
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = displaySubLabel,
                            style = Typography.bodyMedium.copy(
                                color = if (isGlass) Color.White.copy(alpha = 0.80f) else accentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Controls Row: [Reset] [Start/Pause Focus] [Save Session]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset Button
                    Surface(
                        shape = CircleShape,
                        color = AppTheme.colors.surface2,
                        modifier = Modifier
                            .size(52.dp)
                            .clickable {
                                TimerService.resetTimer(context)
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Timer",
                                tint = AppTheme.colors.text,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Primary Start/Pause Button
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = accentColor,
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .height(52.dp)
                            .clickable {
                                if (!isRunning) {
                                    if (activeHabit != null) {
                                        val modeStr = when (selectedTimerMode) {
                                            FocusTimerMode.POMODORO -> "POMODORO"
                                            FocusTimerMode.STOPWATCH -> "STOPWATCH"
                                            FocusTimerMode.COUNTDOWN -> "COUNTDOWN"
                                        }
                                        val targetSec = when (selectedTimerMode) {
                                            FocusTimerMode.POMODORO -> currentPomodoroTarget
                                            FocusTimerMode.COUNTDOWN -> countdownTargetSeconds
                                            FocusTimerMode.STOPWATCH -> 0L
                                        }
                                        TimerService.startTimer(
                                            context = context,
                                            habitId = activeHabit.id,
                                            habitName = activeHabit.name,
                                            mode = modeStr,
                                            targetSeconds = targetSec
                                        )
                                    }
                                } else {
                                    TimerService.pauseTimer(context)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 28.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isRunning) "Pause" else "Start",
                                tint = onAccentColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isRunning -> "Pause"
                                    isPaused -> "Resume"
                                    else -> "Start"
                                },
                                style = Typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = onAccentColor
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Right Save/Log Button
                    Surface(
                        shape = CircleShape,
                        color = if (elapsedSeconds > 0) accentColor else AppTheme.colors.surface2,
                        modifier = Modifier
                            .size(52.dp)
                            .clickable {
                                val sessionTime = elapsedSeconds
                                if (activeHabit != null && sessionTime > 0) {
                                    viewModel.saveTimeSession(activeHabit.id, sessionTime, sessionId = System.currentTimeMillis())
                                    TimerService.stopTimer(context)
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.BookmarkBorder,
                                contentDescription = "Save Session",
                                tint = if (elapsedSeconds > 0) onAccentColor else AppTheme.colors.text,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // If time elapsed > 0, show direct 1-tap save session action bar
                AnimatedVisibility(visible = elapsedSeconds > 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isGlass) Color.White.copy(alpha = 0.16f) else accentContainerColor,
                            border = BorderStroke(1.dp, if (isGlass) Color.White.copy(alpha = 0.35f) else accentColor.copy(alpha = 0.40f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (activeHabit != null && elapsedSeconds > 0) {
                                        viewModel.saveTimeSession(activeHabit.id, elapsedSeconds, sessionId = System.currentTimeMillis())
                                        TimerService.stopTimer(context)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isGlass) Color.White else accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Save ${DateUtils.formatSecondsToHoursMinutes(elapsedSeconds)} to \"${activeHabit?.name ?: "Routine"}\"",
                                    style = Typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = if (isGlass) Color.White else AppTheme.colors.text
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

        // 4. AUDIO & MEDIA FLOW CARD
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header: Icon + "AUDIO & MEDIA FLOW"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = accentContainerColor,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "AUDIO & MEDIA FLOW",
                            style = Typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = AppTheme.colors.textMuted,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isMusicPlaying) accentColor else AppTheme.colors.surface2
                    ) {
                        Text(
                            text = if (isMusicPlaying) "● Playing" else "● Ready",
                            style = Typography.labelSmall.copy(
                                color = if (isMusicPlaying) onAccentColor else AppTheme.colors.textMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // Music Mode Segmented Controls (External YouTube Music / Off)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isGlass) Color.White.copy(alpha = 0.06f) else AppTheme.colors.surface2,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        listOf(MusicSourceMode.EXTERNAL, MusicSourceMode.OFF).forEach { mode ->
                            val isSelected = (musicSourceMode == mode) || (mode == MusicSourceMode.OFF && musicSourceMode == MusicSourceMode.LOCAL)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color.White else Color.Transparent,
                                shadowElevation = 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        viewModel.setMusicSourceMode(mode)
                                    }
                            ) {
                                Text(
                                    text = when (mode) {
                                        MusicSourceMode.EXTERNAL -> "YouTube Music"
                                        MusicSourceMode.OFF -> "Off"
                                        else -> "Off"
                                    },
                                    style = Typography.labelMedium.copy(
                                        color = if (isSelected) Color.Black.copy(alpha = 0.87f) else AppTheme.colors.textMuted,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 7.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                if (musicSourceMode != MusicSourceMode.OFF) {
                    // Audio Playback Bar
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isGlass) Color.White.copy(alpha = 0.06f) else AppTheme.colors.surface2,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { TimerService.previousTrack(context) }) {
                                    Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous Track", tint = AppTheme.colors.text)
                                }
                                IconButton(onClick = { TimerService.toggleMusic(context) }) {
                                    Icon(
                                        imageVector = if (isMusicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Toggle Music",
                                        tint = accentColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                IconButton(onClick = { TimerService.nextTrack(context) }) {
                                    Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next Track", tint = AppTheme.colors.text)
                                }
                            }

                            if (musicSourceMode == MusicSourceMode.EXTERNAL) {
                                Button(
                                    onClick = { MediaControlHelper.openMusicApp(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.surface3),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, tint = AppTheme.colors.text, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open YT Music", color = AppTheme.colors.text, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Switch Row: Auto-play audio with timer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-play audio with timer",
                            style = Typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = AppTheme.colors.text
                            )
                        )
                        Text(
                            text = "Resume sound automatically when you start a session",
                            style = Typography.bodySmall.copy(
                                color = AppTheme.colors.textMuted,
                                fontSize = 11.5.sp
                            )
                        )
                    }

                    Switch(
                        checked = autoPlayMusicWithTimer,
                        onCheckedChange = { viewModel.setAutoPlayMusicWithTimer(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = onAccentColor,
                            checkedTrackColor = accentColor,
                            uncheckedTrackColor = AppTheme.colors.surface3,
                            uncheckedThumbColor = AppTheme.colors.textMuted
                        )
                    )
                }
            }
        }
    }

    // Modal Sheet for choosing active habit
    if (showHabitPicker) {
        val todayScheduledHabits = habits.filter { !it.isArchived && DateUtils.isScheduled(it, todayStr) }
        val otherHabits = habits.filter { !it.isArchived && !DateUtils.isScheduled(it, todayStr) }

        AlertDialog(
            onDismissRequest = { showHabitPicker = false },
            title = {
                Text(
                    text = "Select Routine to Focus",
                    style = Typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = AppTheme.colors.text
                    )
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (todayScheduledHabits.isNotEmpty()) {
                        Text(
                            text = "⭐ SCHEDULED FOR TODAY",
                            style = Typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                fontSize = 11.5.sp,
                                letterSpacing = 0.5.sp
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )

                        todayScheduledHabits.forEach { habit ->
                            val isSelected = habit.id == activeHabit?.id
                            HabitPickerRowItem(
                                habit = habit,
                                isSelected = isSelected,
                                isScheduledToday = true,
                                onSelect = {
                                    viewModel.selectTimerHabit(habit.id)
                                    TimerService.changeActiveHabit(context, habit.id, habit.name)
                                    showHabitPicker = false
                                }
                            )
                        }
                    }

                    if (otherHabits.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ALL OTHER ROUTINES",
                            style = Typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textMuted,
                                fontSize = 11.5.sp,
                                letterSpacing = 0.5.sp
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )

                        otherHabits.forEach { habit ->
                            val isSelected = habit.id == activeHabit?.id
                            HabitPickerRowItem(
                                habit = habit,
                                isSelected = isSelected,
                                isScheduledToday = false,
                                onSelect = {
                                    viewModel.selectTimerHabit(habit.id)
                                    TimerService.changeActiveHabit(context, habit.id, habit.name)
                                    showHabitPicker = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHabitPicker = false }) {
                    Text("Close", color = accentColor, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = AppTheme.colors.surface,
            shape = RoundedCornerShape(18.dp)
        )
    }

    // Modal when target/pomodoro completes
    val currentPending = pendingSession
    if (currentPending != null) {
        val targetHabit = habits.find { it.id == currentPending.habitId }
        val targetHabitName = targetHabit?.name ?: currentPending.habitName
        val formattedTime = DateUtils.formatSecondsToHoursMinutes(currentPending.seconds)

        AlertDialog(
            onDismissRequest = {
                TimerService.consumePendingSession(context, currentPending.id)
            },
            title = {
                Text(
                    text = "🎉 Session Complete!",
                    style = Typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = AppTheme.colors.text
                    )
                )
            },
            text = {
                Text(
                    text = "Great job! Would you like to log $formattedTime of focus time to \"$targetHabitName\"?",
                    style = Typography.bodyMedium.copy(color = AppTheme.colors.text, fontSize = 14.sp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sessionToSave = currentPending
                        TimerService.consumePendingSession(context, sessionToSave.id)
                        viewModel.saveTimeSession(
                            habitId = sessionToSave.habitId,
                            durationSeconds = sessionToSave.seconds,
                            sessionId = sessionToSave.id
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Time Session", color = onAccentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    TimerService.consumePendingSession(context, currentPending.id)
                }) {
                    Text("Dismiss", color = AppTheme.colors.textMuted)
                }
            },
            containerColor = AppTheme.colors.surface,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun HabitPickerRowItem(
    habit: Habit,
    isSelected: Boolean,
    isScheduledToday: Boolean,
    onSelect: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.15f) else AppTheme.colors.surface2,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(habit.color)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = Typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) accentColor else AppTheme.colors.text,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isScheduledToday) "Scheduled today • ${habit.category}" else "${habit.category} • ${DateUtils.daysText(habit)}",
                    style = Typography.bodySmall.copy(
                        color = if (isScheduledToday) accentColor else AppTheme.colors.textMuted,
                        fontSize = 11.5.sp,
                        fontWeight = if (isScheduledToday) FontWeight.SemiBold else FontWeight.Normal
                    )
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
