package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.HabitRepository
import com.example.model.Habit
import com.example.model.Task
import com.example.model.HabitSubtask
import com.example.ui.theme.HabitPalette
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePreferences
import com.example.ui.theme.BackgroundPreferences
import com.example.ui.theme.BackgroundConfig
import com.example.ui.theme.RotationMode
import com.example.ui.theme.WallpaperItem
import com.example.ui.theme.PlaylistPreferences
import com.example.ui.theme.PlaylistTrack
import com.example.util.DateUtils
import com.example.util.NotificationHelper
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.data.HabitFreezeEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@Immutable
data class HabitWithAnalytics(
    val habit: Habit,
    val currentStreak: Int,
    val longestStreak: Int,
    val durationInfo: com.example.model.DurationInfo?,
    val daysText: String,
    val completionRate30d: Float,
    val weeklyTimeString: String?,
    val isScheduledToday: Boolean,
    val isDoneToday: Boolean,
    val isFrozenToday: Boolean = false,
    val progressCurve: List<com.example.model.HabitWeekTrend>,
    val chart: Pair<com.example.model.ChartGranularity, List<com.example.model.ChartBucket>> = com.example.model.ChartGranularity.DAY to emptyList()
)

@Immutable
data class HeatmapPoint(
    val dateStr: String,
    val fieldIntensity: Float,
    val isScheduledAny: Boolean,
    val isMissedDay: Boolean,
    val isFrozenAny: Boolean = false,
    val isToday: Boolean
)

@Immutable
data class AnalyticsUIState(
    val todayDone: Int = 0,
    val todayTotal: Int = 0,
    val todayPct: Float = 0f,
    val rate30d: Float = 0f,
    val bestStreakName: String = "—",
    val bestStreakValue: Int = 0,
    val activeHabitsCount: Int = 0,
    val totalFrozenCount: Int = 0,
    val heatmapPoints: List<HeatmapPoint> = emptyList()
)

@Immutable
data class ConfirmDialogState(
    val title: String,
    val message: String,
    val onConfirm: () -> Unit,
    val confirmText: String = "Confirm",
    val cancelText: String = "Cancel"
)

class HabitViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: HabitRepository

    val habits: StateFlow<List<Habit>>
    val archivedHabits: StateFlow<List<Habit>>
    val tasks: StateFlow<List<Task>>
    val subtasks: StateFlow<List<HabitSubtask>>
    val allTimeSessions: StateFlow<List<com.example.model.TimeSession>>
    val allFreezes: StateFlow<List<HabitFreezeEntity>>

    val weeklyTotals: StateFlow<Map<String, Long>>
    val habitsWithAnalytics: StateFlow<List<HabitWithAnalytics>>
    val analyticsState: StateFlow<AnalyticsUIState>
    val categoryPerformance: StateFlow<List<com.example.model.CategoryPerformance>>
    val weeklyTrend: StateFlow<List<com.example.model.TrendPoint>>
    val monthlyTrend: StateFlow<List<com.example.model.TrendPoint>>

    val isModalOpen = MutableStateFlow(false)
    val editingHabit = MutableStateFlow<Habit?>(null) // null = new habit when modal open
    val toastMessage = MutableStateFlow<String?>(null)
    val confirmDialogState = MutableStateFlow<ConfirmDialogState?>(null)

    val themeMode: StateFlow<ThemeMode>
    val useDynamicColor: StateFlow<Boolean>
    val playlist: StateFlow<List<PlaylistTrack>>
    val playlistShuffle: StateFlow<Boolean>
    val playlistFavoritesOnly: StateFlow<Boolean>
    val musicSourceMode: StateFlow<com.example.ui.theme.MusicSourceMode>
    val autoPlayMusicWithTimer: StateFlow<Boolean>

    val isOnboardingCompleted: StateFlow<Boolean>
    val isSoundEffectsEnabled: StateFlow<Boolean>
    val isHapticsEnabled: StateFlow<Boolean>
    val customSoundName: StateFlow<String?>
    val confettiTrigger = MutableStateFlow(0L)
    val selectedTimerHabitId = MutableStateFlow<String?>(null)
    val backgroundConfig: StateFlow<BackgroundConfig>

    init {
        ThemePreferences.init(application)
        themeMode = ThemePreferences.themeMode
        useDynamicColor = ThemePreferences.useDynamicColor

        BackgroundPreferences.init(application)
        backgroundConfig = BackgroundPreferences.config

        com.example.util.AppPreferences.init(application)
        isOnboardingCompleted = com.example.util.AppPreferences.isOnboardingCompleted
        isSoundEffectsEnabled = com.example.util.AppPreferences.isSoundEffectsEnabled
        isHapticsEnabled = com.example.util.AppPreferences.isHapticsEnabled
        customSoundName = com.example.util.AppPreferences.customSoundName

        PlaylistPreferences.init(application)
        playlist = PlaylistPreferences.playlist
        playlistShuffle = PlaylistPreferences.shuffle
        playlistFavoritesOnly = PlaylistPreferences.favoritesOnly
        musicSourceMode = PlaylistPreferences.musicSourceMode
        autoPlayMusicWithTimer = PlaylistPreferences.autoPlayWithTimer

        val db = AppDatabase.getDatabase(application)
        val dao = db.habitDao()
        val taskDao = db.taskDao()
        val subtaskDao = db.habitSubtaskDao()
        val timeSessionDao = db.timeSessionDao()
        val completionDao = db.habitCompletionDao()
        val freezeDao = db.habitFreezeDao()
        repository = HabitRepository(dao, taskDao, subtaskDao, timeSessionDao, completionDao, freezeDao, db)
        
        habits = repository.allHabits.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        allFreezes = repository.allFreezes.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        allTimeSessions = repository.allTimeSessions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        archivedHabits = repository.archivedHabits.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        tasks = repository.allTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        subtasks = repository.allSubtasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        val weekDates = DateUtils.getCurrentWeekDates()
        val weekStart = weekDates.first()
        val weekEnd = weekDates.last()
        weeklyTotals = repository.getWeeklyTotals(weekStart, weekEnd).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

        habitsWithAnalytics = kotlinx.coroutines.flow.combine(habits, weeklyTotals) { habitsList, totalsMap ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                habitsList.map { habit ->
                    val totalSeconds = totalsMap[habit.id] ?: 0L
                    val weeklyTimeString = if (habit.trackTimeSpent || totalSeconds > 0L) {
                        "${DateUtils.formatSecondsToHoursMinutes(totalSeconds)} this week"
                    } else {
                        null
                    }
                    val todayStr = DateUtils.todayStr()
                    HabitWithAnalytics(
                        habit = habit,
                        currentStreak = DateUtils.currentStreak(habit),
                        longestStreak = DateUtils.longestStreak(habit),
                        durationInfo = DateUtils.durationInfo(habit),
                        daysText = DateUtils.daysText(habit),
                        completionRate30d = DateUtils.completionRate(habit, 30),
                        weeklyTimeString = weeklyTimeString,
                        isScheduledToday = DateUtils.isScheduled(habit, todayStr),
                        isDoneToday = DateUtils.isDone(habit, todayStr),
                        isFrozenToday = DateUtils.isFrozen(habit, todayStr),
                        progressCurve = DateUtils.habitProgressCurve(habit),
                        chart = DateUtils.buildChartBuckets(habit, todayStr)
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        analyticsState = habits.map { habitsList ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                if (habitsList.isEmpty()) {
                    AnalyticsUIState()
                } else {
                    val todayPair = DateUtils.overallCompletionToday(habitsList)
                    val todayDone = todayPair.first
                    val todayTotal = todayPair.second
                    val todayPct = if (todayTotal > 0) todayDone.toFloat() / todayTotal else 0f

                    val rate30 = DateUtils.overallRate(habitsList, 30)
                    val bestStreak = DateUtils.bestStreakHabit(habitsList)
                    val totalFrozenCount = habitsList.sumOf { it.frozenDates.size }

                    // Heatmap calculation
                    val daysCount = 126 // 18 weeks
                    val dates = DateUtils.lastNDates(daysCount)
                    val todayStr = DateUtils.todayStr()
                    val heatmapPoints = dates.map { date ->
                        val intensity = DateUtils.fieldIntensity(habitsList, date)
                        val isScheduledAny = habitsList.any { DateUtils.isScheduled(it, date) }
                        val isFrozenAny = habitsList.any { DateUtils.isFrozen(it, date) }
                        val isPast = date < todayStr
                        val isMissedDay = isPast && isScheduledAny && !isFrozenAny && intensity == 0f
                        HeatmapPoint(
                            dateStr = date,
                            fieldIntensity = intensity,
                            isScheduledAny = isScheduledAny,
                            isMissedDay = isMissedDay,
                            isFrozenAny = isFrozenAny,
                            isToday = date == todayStr
                        )
                    }

                    AnalyticsUIState(
                        todayDone = todayDone,
                        todayTotal = todayTotal,
                        todayPct = todayPct,
                        rate30d = rate30,
                        bestStreakName = bestStreak.first,
                        bestStreakValue = bestStreak.second,
                        activeHabitsCount = habitsList.size,
                        totalFrozenCount = totalFrozenCount,
                        heatmapPoints = heatmapPoints
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AnalyticsUIState()
        )

        categoryPerformance = habits.map { habitsList ->
            withContext(Dispatchers.Default) {
                DateUtils.categoryPerformance(habitsList)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        weeklyTrend = habits.map { habitsList ->
            withContext(Dispatchers.Default) {
                DateUtils.weeklyTrend(habitsList, 8)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        monthlyTrend = habits.map { habitsList ->
            withContext(Dispatchers.Default) {
                DateUtils.monthlyTrend(habitsList, 6)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        ThemePreferences.setThemeMode(mode)
    }

    fun setUseDynamicColor(use: Boolean) {
        ThemePreferences.setUseDynamicColor(use)
    }

    fun setBackgroundEnabled(enabled: Boolean) {
        BackgroundPreferences.setEnabled(enabled)
    }

    fun setCustomBackgroundImage(uri: android.net.Uri, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = BackgroundPreferences.saveImagesFromUris(getApplication(), listOf(uri))
            withContext(Dispatchers.Main) {
                onResult(count > 0)
            }
        }
    }

    fun addCustomWallpapers(uris: List<android.net.Uri>, onResult: (Int) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = BackgroundPreferences.saveImagesFromUris(getApplication(), uris)
            withContext(Dispatchers.Main) {
                onResult(count)
            }
        }
    }

    fun removeCustomWallpaper(id: String) {
        BackgroundPreferences.removeWallpaper(getApplication(), id)
    }

    fun removeAllCustomWallpapers() {
        BackgroundPreferences.removeAllWallpapers(getApplication())
    }

    fun setActiveWallpaper(index: Int) {
        BackgroundPreferences.setActiveWallpaperIndex(index)
    }

    fun setWallpaperRotationMode(mode: RotationMode) {
        BackgroundPreferences.setRotationMode(mode)
    }

    fun setWallpaperRotationInterval(minutes: Int) {
        BackgroundPreferences.setRotationInterval(minutes)
    }

    fun setWallpaperShuffle(shuffle: Boolean) {
        BackgroundPreferences.setShuffle(shuffle)
    }

    fun setDayNightWallpapers(dayId: String?, nightId: String?) {
        BackgroundPreferences.setDayNightWallpapers(dayId, nightId)
    }

    fun setBackgroundBlur(blur: Float) {
        BackgroundPreferences.setBlurRadius(blur)
    }

    fun setBackgroundOpacity(opacity: Float) {
        BackgroundPreferences.setGlassOpacity(opacity)
    }

    fun setBackgroundDim(dim: Float) {
        BackgroundPreferences.setDim(dim)
    }

    fun resetBackgroundControls() {
        BackgroundPreferences.resetControlsToDefault()
    }

    fun removeCustomBackground() {
        BackgroundPreferences.removeAllWallpapers(getApplication())
    }

    fun addPlaylistTracks(tracks: List<PlaylistTrack>) {
        PlaylistPreferences.addTracks(tracks)
    }

    fun removePlaylistTrack(uri: String) {
        PlaylistPreferences.removeTrack(uri)
    }

    fun setPlaylistShuffle(shuffle: Boolean) {
        PlaylistPreferences.setShuffle(shuffle)
    }

    fun togglePlaylistTrackFavorite(uri: String) {
        PlaylistPreferences.toggleFavorite(uri)
    }

    fun setPlaylistFavoritesOnly(only: Boolean) {
        PlaylistPreferences.setFavoritesOnly(only)
    }

    fun setMusicSourceMode(mode: com.example.ui.theme.MusicSourceMode) {
        PlaylistPreferences.setMusicSourceMode(mode)
    }

    fun setAutoPlayMusicWithTimer(autoPlay: Boolean) {
        PlaylistPreferences.setAutoPlayWithTimer(autoPlay)
    }

    fun openNewModal() {
        editingHabit.value = null
        isModalOpen.value = true
    }

    fun openEditModal(habit: Habit) {
        editingHabit.value = habit
        isModalOpen.value = true
    }

    fun closeModal() {
        isModalOpen.value = false
        editingHabit.value = null
    }

    fun showToast(msg: String) {
        toastMessage.value = msg
    }

    fun clearToast() {
        toastMessage.value = null
    }

    fun dismissConfirm() {
        confirmDialogState.value = null
    }

    fun toggleHabitToday(habit: Habit) {
        toggleHabitDate(habit, DateUtils.todayStr())
    }

    fun toggleHabitDate(habit: Habit, dateStr: String) {
        if (!DateUtils.isScheduled(habit, dateStr)) {
            showToast("Not scheduled on $dateStr — rest day")
            return
        }
        if (dateStr > DateUtils.todayStr()) {
            showToast("Cannot mark future days")
            return
        }
        viewModelScope.launch {
            val currentSubtasks = repository.getSubtasksForHabitAndDate(habit.id, dateStr)
            val isCurrentlyDone = habit.completions.contains(dateStr)
            val targetCompletedState = !isCurrentlyDone
            val newCompletions = if (targetCompletedState) {
                habit.completions + dateStr
            } else {
                habit.completions - dateStr
            }

            if (currentSubtasks.isNotEmpty()) {
                currentSubtasks.forEach { sub ->
                    repository.insertSubtask(sub.copy(isCompleted = targetCompletedState))
                }
            }

            val updated = habit.copy(completions = newCompletions)
            repository.insertHabit(updated)
            com.example.receiver.WidgetUpdateHelper.updateWidgets(getApplication())

            if (targetCompletedState) {
                com.example.util.AppFeedbackHelper.triggerCompletionFeedback(getApplication())
                confettiTrigger.value = System.currentTimeMillis()
            } else {
                com.example.util.AppFeedbackHelper.vibrateClick(getApplication())
            }

            if (dateStr != DateUtils.todayStr()) {
                showToast(if (targetCompletedState) "Marked completed on $dateStr" else "Marked incomplete on $dateStr")
            }
        }
    }

    fun toggleFreezeToday(habit: Habit, reason: String = "Rest Day") {
        toggleFreezeDate(habit, DateUtils.todayStr(), reason)
    }

    fun toggleFreezeDate(habit: Habit, dateStr: String, reason: String = "Rest Day") {
        viewModelScope.launch {
            val isCurrentlyFrozen = DateUtils.isFrozen(habit, dateStr)
            repository.toggleFreeze(habit.id, dateStr, reason)
            com.example.util.AppFeedbackHelper.vibrateClick(getApplication())
            if (!isCurrentlyFrozen) {
                showToast("❄️ Streak frozen for '${habit.name}' on $dateStr ($reason) — Protected!")
            } else {
                showToast("Unfroze $dateStr for '${habit.name}'")
            }
            com.example.receiver.WidgetUpdateHelper.updateWidgets(getApplication())
        }
    }

    fun freezeAllHabitsToday(reason: String = "Rest Day") {
        freezeAllHabitsForDate(DateUtils.todayStr(), reason)
    }

    fun freezeAllHabitsForDate(dateStr: String, reason: String = "Rest Day") {
        viewModelScope.launch {
            repository.freezeAllHabitsForDate(dateStr, reason)
            com.example.util.AppFeedbackHelper.vibrateClick(getApplication())
            val label = if (dateStr == DateUtils.todayStr()) "today" else dateStr
            showToast("❄️ Protected all habits for $label ($reason)!")
            com.example.receiver.WidgetUpdateHelper.updateWidgets(getApplication())
        }
    }

    fun unfreezeAllHabitsToday() {
        unfreezeAllHabitsForDate(DateUtils.todayStr())
    }

    fun unfreezeAllHabitsForDate(dateStr: String) {
        viewModelScope.launch {
            repository.unfreezeAllHabitsForDate(dateStr)
            com.example.util.AppFeedbackHelper.vibrateClick(getApplication())
            val label = if (dateStr == DateUtils.todayStr()) "today" else dateStr
            showToast("Removed freeze protection for $label.")
            com.example.receiver.WidgetUpdateHelper.updateWidgets(getApplication())
        }
    }

    fun addSubtask(habitId: String, dateStr: String, text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        viewModelScope.launch {
            val newSubtask = HabitSubtask(
                id = "sub_" + UUID.randomUUID().toString().replace("-", "").take(8),
                habitId = habitId,
                dateStr = dateStr,
                text = cleanText,
                isCompleted = false
            )
            repository.insertSubtask(newSubtask)

            val habit = repository.getHabitById(habitId)
            if (habit != null) {
                val newCompletions = habit.completions - dateStr
                repository.insertHabit(habit.copy(completions = newCompletions))
                com.example.receiver.WidgetUpdateHelper.updateWidgets(getApplication())
            }
        }
    }

    fun toggleSubtask(subtask: HabitSubtask) {
        viewModelScope.launch {
            val updated = subtask.copy(isCompleted = !subtask.isCompleted)
            repository.insertSubtask(updated)

            if (updated.isCompleted) {
                com.example.util.AppFeedbackHelper.triggerCompletionFeedback(getApplication())
            } else {
                com.example.util.AppFeedbackHelper.triggerClickFeedback(getApplication())
            }

            val habitId = subtask.habitId
            val dateStr = subtask.dateStr
            val currentSubtasks = repository.getSubtasksForHabitAndDate(habitId, dateStr)
            val habit = repository.getHabitById(habitId)
            if (habit != null && currentSubtasks.isNotEmpty()) {
                val allDone = currentSubtasks.all { it.isCompleted }
                val isCurrentlyDone = habit.completions.contains(dateStr)
                if (allDone != isCurrentlyDone) {
                    val newCompletions = if (allDone) {
                        habit.completions + dateStr
                    } else {
                        habit.completions - dateStr
                    }
                    val updatedHabit = habit.copy(completions = newCompletions)
                    repository.insertHabit(updatedHabit)
                    com.example.receiver.WidgetUpdateHelper.updateWidgets(getApplication())
                }
            }
        }
    }

    fun deleteSubtask(subtask: HabitSubtask) {
        viewModelScope.launch {
            repository.deleteSubtask(subtask.id)
            com.example.util.AppFeedbackHelper.triggerClickFeedback(getApplication())

            val habitId = subtask.habitId
            val dateStr = subtask.dateStr
            val currentSubtasks = repository.getSubtasksForHabitAndDate(habitId, dateStr)
            val habit = repository.getHabitById(habitId)
            if (habit != null) {
                if (currentSubtasks.isNotEmpty()) {
                    val allDone = currentSubtasks.all { it.isCompleted }
                    val isCurrentlyDone = habit.completions.contains(dateStr)
                    if (allDone != isCurrentlyDone) {
                        val newCompletions = if (allDone) {
                            habit.completions + dateStr
                        } else {
                            habit.completions - dateStr
                        }
                        val updatedHabit = habit.copy(completions = newCompletions)
                        repository.insertHabit(updatedHabit)
                        com.example.receiver.WidgetUpdateHelper.updateWidgets(getApplication())
                    }
                }
            }
        }
    }

    fun saveHabit(
        name: String,
        category: String,
        colorHex: String,
        days: List<Int>,
        durationDays: Int?,
        reminderTime: String?,
        trackTimeSpent: Boolean
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            showToast("Give your habit a name")
            return
        }
        val cleanCat = if (category.trim().isBlank()) "General" else category.trim()
        val cleanReminderTime = reminderTime?.takeIf { it.isNotBlank() }

        viewModelScope.launch {
            val current = editingHabit.value
            val updatedHabit = if (current != null) {
                current.copy(
                    name = cleanName,
                    category = cleanCat,
                    colorHex = colorHex,
                    days = days,
                    durationDays = durationDays,
                    reminderTime = cleanReminderTime,
                    trackTimeSpent = trackTimeSpent
                )
            } else {
                val newId = "h_" + UUID.randomUUID().toString().replace("-", "").take(8)
                Habit(
                    id = newId,
                    name = cleanName,
                    category = cleanCat,
                    colorHex = colorHex,
                    days = days,
                    durationDays = durationDays,
                    createdAt = DateUtils.todayStr(),
                    completions = emptySet(),
                    reminderTime = cleanReminderTime,
                    trackTimeSpent = trackTimeSpent
                )
            }
            repository.insertHabit(updatedHabit)
            com.example.receiver.WidgetUpdateHelper.updateWidgets(getApplication())
            NotificationHelper.scheduleHabitReminder(getApplication(), updatedHabit)
            closeModal()
            showToast("Saved")
        }
    }

    fun requestDeleteHabit(habit: Habit) {
        confirmDialogState.value = ConfirmDialogState(
            title = "Are you sure?",
            message = "Delete this habit? This removes all its tracked history.",
            onConfirm = {
                viewModelScope.launch {
                    NotificationHelper.cancelHabitReminder(getApplication(), habit.id)
                    if (com.example.service.TimerService.activeHabitId.value == habit.id) {
                        stopTimer()
                    }
                    repository.deleteHabit(habit.id)
                    com.example.receiver.WidgetUpdateHelper.updateWidgets(getApplication())
                    closeModal()
                    showToast("Habit deleted")
                }
            }
        )
    }

    fun requestResetAll() {
        confirmDialogState.value = ConfirmDialogState(
            title = "Are you sure?",
            message = "Reset all habit data? This clears everything you have tracked.",
            onConfirm = {
                viewModelScope.launch {
                    val allCurrent = habits.value + archivedHabits.value
                    allCurrent.forEach { h ->
                        NotificationHelper.cancelHabitReminder(getApplication(), h.id)
                    }
                    if (com.example.service.TimerService.isRunning.value || com.example.service.TimerService.isPaused.value) {
                        stopTimer()
                    }
                    repository.deleteAllHabits()
                    com.example.receiver.WidgetUpdateHelper.updateWidgets(getApplication())
                    showToast("Data cleared")
                }
            }
        )
    }

    fun restoreArchivedHabit(habit: Habit) {
        viewModelScope.launch {
            val restored = habit.copy(isArchived = false)
            repository.insertHabit(restored)
            NotificationHelper.scheduleHabitReminder(getApplication(), restored)
            com.example.receiver.WidgetUpdateHelper.updateWidgets(getApplication())
            showToast("Restored habit to active ledger")
        }
    }

    fun loadSampleHabits() {
        viewModelScope.launch {
            val today = DateUtils.todayStr()

            // Deterministic sample habits
            val sampleHabits = listOf(
                Habit(
                    id = "h_morning_run",
                    name = "Morning Run",
                    category = "Fitness",
                    colorHex = "#10B981",
                    days = listOf(1, 2, 3, 4, 5),
                    durationDays = null,
                    createdAt = DateUtils.addDays(today, -20),
                    completions = DateUtils.lastNDates(21).filterIndexed { idx, _ -> idx % 2 == 0 }.toSet()
                ),
                Habit(
                    id = "h_read_pages",
                    name = "Read 20 pages",
                    category = "Mind",
                    colorHex = "#6FA8B0",
                    days = listOf(0, 1, 2, 3, 4, 5, 6),
                    durationDays = null,
                    createdAt = DateUtils.addDays(today, -14),
                    completions = DateUtils.lastNDates(14).filterIndexed { idx, _ -> idx != 5 }.toSet()
                ),
                Habit(
                    id = "h_deep_work",
                    name = "Deep Work Session",
                    category = "Focus",
                    colorHex = "#D97A5F",
                    days = listOf(1, 2, 3, 4, 5),
                    durationDays = null,
                    createdAt = DateUtils.addDays(today, -10),
                    completions = DateUtils.lastNDates(10).filterIndexed { idx, _ -> idx !in listOf(2, 6) }.toSet(),
                    trackTimeSpent = true
                ),
                Habit(
                    id = "h_meditation",
                    name = "Mindfulness Meditation",
                    category = "Mind",
                    colorHex = "#A882DD",
                    days = listOf(0, 1, 2, 3, 4, 5, 6),
                    durationDays = 21,
                    createdAt = DateUtils.addDays(today, -12),
                    completions = DateUtils.lastNDates(13).filterIndexed { idx, _ -> idx != 3 }.toSet()
                ),
                Habit(
                    id = "h_sugar_free",
                    name = "30-Day No Sugar Challenge",
                    category = "Health",
                    colorHex = "#E5A93C",
                    days = listOf(0, 1, 2, 3, 4, 5, 6),
                    durationDays = 30,
                    createdAt = DateUtils.addDays(today, -20),
                    completions = DateUtils.lastNDates(21).filterIndexed { idx, _ -> idx != 14 }.toSet()
                )
            )

            sampleHabits.forEach { repository.insertHabit(it) }
            com.example.receiver.WidgetUpdateHelper.updateWidgets(getApplication())
            showToast("Sample habits loaded!")
        }
    }

    fun addTask(text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        val taskId = "t_" + UUID.randomUUID().toString().replace("-", "").take(8)
        viewModelScope.launch {
            repository.insertTask(
                Task(
                    id = taskId,
                    text = cleanText,
                    isCompleted = false,
                    createdAt = DateUtils.todayStr()
                )
            )
            com.example.util.AppFeedbackHelper.triggerClickFeedback(getApplication())
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch {
            val nextCompleted = !task.isCompleted
            repository.insertTask(task.copy(isCompleted = nextCompleted))
            if (nextCompleted) {
                com.example.util.AppFeedbackHelper.triggerCompletionFeedback(getApplication())
                confettiTrigger.value = System.currentTimeMillis()
            } else {
                com.example.util.AppFeedbackHelper.triggerClickFeedback(getApplication())
            }
        }
    }

    fun deleteTask(id: String) {
        viewModelScope.launch {
            repository.deleteTask(id)
            com.example.util.AppFeedbackHelper.triggerClickFeedback(getApplication())
        }
    }

    fun clearCompletedTasks() {
        viewModelScope.launch {
            repository.deleteCompletedTasks()
            com.example.util.AppFeedbackHelper.triggerClickFeedback(getApplication())
            showToast("Cleared completed tasks")
        }
    }

    companion object {
        private const val PREFS_SAVED_SESSIONS = "SavedSessionsPrefs"
        private const val KEY_SAVED_SESSION_IDS = "saved_session_ids"
    }

    fun selectTimerHabit(habitId: String) {
        selectedTimerHabitId.value = habitId
    }

    fun saveTimeSession(habitId: String, durationSeconds: Long, sessionId: Long = System.currentTimeMillis()) {
        if (durationSeconds <= 0L || durationSeconds > 12 * 3600L) return

        val context = getApplication<Application>()
        val prefs = context.getSharedPreferences(PREFS_SAVED_SESSIONS, android.content.Context.MODE_PRIVATE)
        val rawIds = prefs.getString(KEY_SAVED_SESSION_IDS, "") ?: ""
        val idList = if (rawIds.isNotBlank()) {
            rawIds.split(",").mapNotNull { it.trim().toLongOrNull() }.toMutableList()
        } else {
            mutableListOf()
        }

        if (idList.contains(sessionId)) {
            android.util.Log.d("HabitViewModel", "Session id $sessionId already stored. Skipping duplicate save.")
            return
        }

        idList.add(sessionId)
        while (idList.size > 20) {
            idList.removeAt(0)
        }
        prefs.edit().putString(KEY_SAVED_SESSION_IDS, idList.joinToString(",")).apply()

        viewModelScope.launch {
            val today = com.example.util.DateUtils.todayStr()
            repository.insertOrAccumulateSession(habitId, today, durationSeconds)
            com.example.util.AppFeedbackHelper.triggerCompletionFeedback(getApplication())
            confettiTrigger.value = System.currentTimeMillis()
            
            // Get the habit
            val habit = repository.getHabitById(habitId)
            if (habit != null) {
                // Ensure trackTimeSpent is true so time shows up everywhere
                if (!habit.trackTimeSpent) {
                    repository.insertHabit(habit.copy(trackTimeSpent = true))
                }
                
                val formattedTime = com.example.util.DateUtils.formatSecondsToHoursMinutes(durationSeconds)
                if (!com.example.util.DateUtils.isDone(habit, today) && com.example.util.DateUtils.isScheduled(habit, today)) {
                    // Ask user with yes/no confirmation dialog
                    confirmDialogState.value = ConfirmDialogState(
                        title = "Save & Complete?",
                        message = "Logged $formattedTime for \"${habit.name}\". Would you also like to mark it as completed for today?",
                        confirmText = "Mark Completed",
                        cancelText = "Keep Time Only",
                        onConfirm = {
                            toggleHabitDate(habit, today)
                            showToast("Logged $formattedTime & marked completed!")
                        }
                    )
                } else {
                    showToast("Logged $formattedTime to \"${habit.name}\"!")
                }
            } else {
                showToast("Time session saved successfully!")
            }
        }
    }

    fun setOnboardingCompleted(completed: Boolean) = com.example.util.AppPreferences.setOnboardingCompleted(completed)
    fun setSoundEffectsEnabled(enabled: Boolean) = com.example.util.AppPreferences.setSoundEffectsEnabled(enabled)
    fun setHapticsEnabled(enabled: Boolean) = com.example.util.AppPreferences.setHapticsEnabled(enabled)
    
    fun setCustomSound(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<android.app.Application>()
                val contentResolver = context.contentResolver
                
                // Get display name
                var displayName = "custom_completion_sound.audio"
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val name = it.getString(nameIndex)
                            if (!name.isNullOrBlank()) {
                                displayName = name
                            }
                        }
                    }
                }

                // Copy to internal storage
                val soundsDir = java.io.File(context.filesDir, "custom_sounds")
                if (!soundsDir.exists()) {
                    soundsDir.mkdirs()
                } else {
                    soundsDir.listFiles()?.forEach { it.delete() }
                }

                val ext = if (displayName.contains('.')) displayName.substringAfterLast('.') else "audio"
                val targetFile = java.io.File(soundsDir, "completion_sound_${System.currentTimeMillis()}.$ext")
                contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (targetFile.exists() && targetFile.length() > 0) {
                    com.example.util.AppPreferences.setCustomSound(displayName, targetFile.absolutePath)
                    showToast("Custom sound active: $displayName")
                    com.example.util.AppFeedbackHelper.previewSound(context)
                } else {
                    showToast("Could not load selected audio file")
                }
            } catch (e: Exception) {
                android.util.Log.e("HabitViewModel", "Failed to set custom sound", e)
                showToast("Failed to load audio file: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun resetCustomSound() {
        try {
            val context = getApplication<android.app.Application>()
            val soundsDir = java.io.File(context.filesDir, "custom_sounds")
            if (soundsDir.exists()) {
                soundsDir.listFiles()?.forEach { it.delete() }
            }
            com.example.util.AppPreferences.clearCustomSound()
            showToast("Reset to default chime sound")
        } catch (e: Exception) {
            com.example.util.AppPreferences.clearCustomSound()
        }
    }

    fun previewCompletionSound() {
        com.example.util.AppFeedbackHelper.previewSound(getApplication())
    }

    fun triggerCelebration() {
        confettiTrigger.value = System.currentTimeMillis()
        com.example.util.AppFeedbackHelper.triggerCelebrationFeedback(getApplication())
    }

    fun shareHabitsCurves(context: android.content.Context) {
        val list = habitsWithAnalytics.value
        com.example.util.ShareHelper.shareHabitsCurvesInfographic(context, list)
    }

    fun saveHabitsCurvesToGallery(context: android.content.Context) {
        val list = habitsWithAnalytics.value
        val success = com.example.util.ShareHelper.saveHabitsCurvesToGallery(context, list)
        if (success) {
            confettiTrigger.value = System.currentTimeMillis()
            com.example.util.AppFeedbackHelper.triggerCelebrationFeedback(getApplication())
        }
    }

    fun exportBackup(uri: android.net.Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication())
            val res = com.example.util.BackupManager.exportData(getApplication(), db, uri)
            onResult(res.first, res.second)
            showToast(res.second)
        }
    }

    fun importBackup(uri: android.net.Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication())
            val res = com.example.util.BackupManager.importData(getApplication(), db, uri)
            onResult(res.first, res.second)
            showToast(res.second)
            if (res.first) {
                confettiTrigger.value = System.currentTimeMillis()
                com.example.util.AppFeedbackHelper.triggerCelebrationFeedback(getApplication())
            }
        }
    }
}
