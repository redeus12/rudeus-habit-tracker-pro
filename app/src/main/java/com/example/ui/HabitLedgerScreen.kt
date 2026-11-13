package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import com.example.util.NotificationHelper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import android.widget.Toast

@Composable
fun HabitLedgerScreen(
    modifier: Modifier = Modifier,
    viewModel: HabitViewModel = viewModel()
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Notifications are disabled, you won't get habit reminders", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        NotificationHelper.createNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val isModalOpen by viewModel.isModalOpen.collectAsStateWithLifecycle()
    val editingHabit by viewModel.editingHabit.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val confirmDialogState by viewModel.confirmDialogState.collectAsStateWithLifecycle()
    val weeklyTotals by viewModel.weeklyTotals.collectAsStateWithLifecycle()
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsStateWithLifecycle()
    val confettiTrigger by viewModel.confettiTrigger.collectAsStateWithLifecycle()
    val backgroundConfig by viewModel.backgroundConfig.collectAsStateWithLifecycle()

    var showSplash by rememberSaveable { mutableStateOf(true) }
    var currentTab by rememberSaveable { mutableStateOf(0) } // 0 = Today, 1 = Overview, 2 = Timer, 3 = Trends, 4 = Settings

    BackHandler(enabled = !showSplash && isOnboardingCompleted && currentTab != 0) {
        currentTab = 0
    }

    if (showSplash) {
        SplashScreen(onSplashComplete = { showSplash = false })
    } else if (!isOnboardingCompleted) {
        OnboardingScreen(onFinish = { viewModel.setOnboardingCompleted(true) })
    } else {
        val isGlass = backgroundConfig.isEnabled && backgroundConfig.imagePath != null
        val accentColor = remember(backgroundConfig.accentColorHex) { Color(backgroundConfig.accentColorHex) }
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        var rootSizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val glassRootSize = remember(rootSizePx, configuration) {
            if (rootSizePx.width > 0 && rootSizePx.height > 0) {
                with(density) {
                    GlassRootSize(width = rootSizePx.width.toDp(), height = rootSizePx.height.toDp())
                }
            } else {
                GlassRootSize(
                    width = androidx.compose.ui.unit.Dp(configuration.screenWidthDp.toFloat()),
                    height = androidx.compose.ui.unit.Dp(configuration.screenHeightDp.toFloat())
                )
            }
        }

        CompositionLocalProvider(
            LocalBackgroundConfig provides backgroundConfig,
            LocalGlassRootSize provides glassRootSize
        ) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .onSizeChanged { rootSizePx = it }
            ) {
                // Full-screen fixed background behind all screens
                if (isGlass) {
                    CustomBackgroundLayer(config = backgroundConfig)
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = if (isGlass) Color.Transparent else AppTheme.colors.bg,
                    floatingActionButton = {
                        if (currentTab != 4) {
                            ExtendedFloatingActionButton(
                                onClick = { viewModel.openNewModal() },
                                containerColor = accentColor,
                                contentColor = Color.White,
                                shape = RoundedCornerShape(24.dp),
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Habit",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = "New habit",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            )
                        }
                    },
                    bottomBar = {
                        // Glass Bottom Navigation Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val navTabs = listOf(
                                        Triple(0, Icons.Default.Today, "Today"),
                                        Triple(1, Icons.Default.Checklist, "Overview"),
                                        Triple(2, Icons.Default.Timer, "Timer"),
                                        Triple(3, Icons.Default.TrendingUp, "Trends"),
                                        Triple(4, Icons.Default.Settings, "Settings")
                                    )

                                    navTabs.forEach { (tabIndex, icon, label) ->
                                        val isSelected = currentTab == tabIndex

                                        val pillBackground = if (isSelected) {
                                            if (isGlass) Color.White.copy(alpha = 0.85f) else accentColor.copy(alpha = 0.20f)
                                        } else {
                                            Color.Transparent
                                        }

                                        val iconTint = if (isSelected) {
                                            if (isGlass) Color(0xFF161F1B) else accentColor
                                        } else {
                                            if (isGlass) Color.White.copy(alpha = 0.75f) else AppTheme.colors.textMuted
                                        }

                                        val textTint = if (isSelected) {
                                            if (isGlass) Color(0xFF161F1B) else accentColor
                                        } else {
                                            if (isGlass) Color.White.copy(alpha = 0.75f) else AppTheme.colors.textMuted
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(18.dp))
                                                .clickable { currentTab = tabIndex },
                                            shape = RoundedCornerShape(18.dp),
                                            color = pillBackground,
                                            shadowElevation = 0.dp,
                                            tonalElevation = 0.dp
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = label,
                                                    tint = iconTint,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                if (isSelected) {
                                                    Text(
                                                        text = label,
                                                        color = textTint,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(if (isGlass) Color.Transparent else AppTheme.colors.bg)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        val isWide = maxWidth >= 900.dp
                        val maxContentWidth = if (isWide) 1180.dp else maxWidth

                        Column(
                            modifier = Modifier
                                .width(maxContentWidth)
                                .align(Alignment.TopCenter)
                                .padding(bottom = 60.dp)
                        ) {
                            if (currentTab != 4) {
                                val todayStr = com.example.util.DateUtils.todayStr()
                                val scheduledToday = habits.filter { com.example.util.DateUtils.isScheduled(it, todayStr) && !it.isArchived }
                                val doneCount = scheduledToday.count { com.example.util.DateUtils.isDone(it, todayStr) }
                                val tabTitle = when (currentTab) {
                                    0 -> "Today"
                                    1 -> "Overview"
                                    2 -> "Timer"
                                    3 -> "Trends"
                                    else -> "Rudeus Habit"
                                }
                                TopBar(
                                    title = tabTitle,
                                    activeHabitsCount = scheduledToday.size,
                                    completedTodayCount = doneCount,
                                    accentColor = accentColor,
                                    onProfileClick = { currentTab = 4 }
                                )
                            }

                            if (currentTab == 0) {
                                TodayTabContent(
                                    viewModel = viewModel,
                                    onEditHabit = { viewModel.openEditModal(it) }
                                )
                            } else if (currentTab == 2) {
                                TimerTabContent(
                                    viewModel = viewModel
                                )
                            } else if (currentTab == 3) {
                                TrendsTabContent(
                                    viewModel = viewModel,
                                    onNewHabit = { viewModel.openNewModal() },
                                    onLoadSample = { viewModel.loadSampleHabits() }
                                )
                            } else if (currentTab == 4) {
                                SettingsTabContent(
                                    viewModel = viewModel,
                                    onResetClicked = { viewModel.requestResetAll() },
                                    onLoadSampleClicked = { viewModel.loadSampleHabits() }
                                )
                            } else if (isWide) {
                                // Two Column Layout
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // Left column (1.55f)
                                    Column(
                                        modifier = Modifier.weight(1.55f),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        DisciplineFieldCard(viewModel = viewModel)
                                        HabitsListCard(
                                            habits = habits,
                                            weeklyTotals = weeklyTotals,
                                            onToggleHabit = { viewModel.toggleHabitToday(it) },
                                            onToggleHabitDate = { habit, date -> viewModel.toggleHabitDate(habit, date) },
                                            onEditHabit = { viewModel.openEditModal(it) },
                                            onNewHabit = { viewModel.openNewModal() },
                                            onLoadSample = { viewModel.loadSampleHabits() }
                                        )
                                    }

                                    // Right column (1f)
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CategoryPerformanceCard(habits = habits)
                                        ProgressTrendCard(habits = habits)
                                    }
                                }
                            } else {
                                // Single Column Layout
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    DisciplineFieldCard(viewModel = viewModel)
                                    HabitsListCard(
                                        habits = habits,
                                        weeklyTotals = weeklyTotals,
                                        onToggleHabit = { viewModel.toggleHabitToday(it) },
                                        onToggleHabitDate = { habit, date -> viewModel.toggleHabitDate(habit, date) },
                                        onEditHabit = { viewModel.openEditModal(it) },
                                        onNewHabit = { viewModel.openNewModal() },
                                        onLoadSample = { viewModel.loadSampleHabits() }
                                    )
                                    CategoryPerformanceCard(habits = habits)
                                    ProgressTrendCard(habits = habits)
                                }
                            }
                        }
                    }

                    // Dialogs and Toasts
                    HabitModal(
                        isOpen = isModalOpen,
                        editingHabit = editingHabit,
                        onClose = { viewModel.closeModal() },
                        onSave = { name, cat, color, days, duration, reminderTime, trackTimeSpent ->
                            viewModel.saveHabit(name, cat, color, days, duration, reminderTime, trackTimeSpent)
                        },
                        onDelete = { habit ->
                            viewModel.requestDeleteHabit(habit)
                        }
                    )

                    ConfirmDialog(
                        state = confirmDialogState,
                        onDismiss = { viewModel.dismissConfirm() }
                    )

                    ToastOverlay(
                        message = toastMessage,
                        onDismiss = { viewModel.clearToast() }
                    )

                    ConfettiOverlay(triggerKey = confettiTrigger)
                }
            }
        }
    }
}
}
