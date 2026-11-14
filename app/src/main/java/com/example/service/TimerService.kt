package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import android.util.Log
import android.media.MediaPlayer
import android.net.Uri
import com.example.ui.theme.PlaylistPreferences
import com.example.ui.theme.PlaylistTrack
import com.example.ui.theme.MusicSourceMode
import com.example.util.MediaControlHelper
import com.example.receiver.TimerWidgetProvider

data class PendingSession(
    val id: Long,
    val habitId: String,
    val habitName: String,
    val seconds: Long,
    val mode: String,
    val completedAt: Long
)

data class ModeTimerState(
    val mode: String,
    val targetDuration: Long = defaultTargetForMode(mode),
    val baseElapsedSeconds: Long = 0L,
    val startedAtMillis: Long = 0L,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val pomodoroRound: Int = 1,
    val isPomodoroBreak: Boolean = false
) {
    fun computeElapsedSeconds(nowMillis: Long = System.currentTimeMillis()): Long {
        return if (isRunning && startedAtMillis > 0L) {
            baseElapsedSeconds + ((nowMillis - startedAtMillis) / 1000L).coerceAtLeast(0L)
        } else {
            baseElapsedSeconds
        }
    }

    companion object {
        fun defaultTargetForMode(mode: String): Long = when (mode) {
            "POMODORO" -> 25 * 60L
            "COUNTDOWN" -> 50 * 60L
            else -> 0L
        }
    }
}

class TimerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null

    private var mediaPlayer: MediaPlayer? = null
    private var playbackList: List<PlaylistTrack> = emptyList()
    private var currentIndex: Int = 0
    private var consecutiveFailuresCount: Int = 0

    companion object {
        const val CHANNEL_ID = "timer_service_channel"
        const val NOTIFICATION_ID = 2001

        // Intent actions
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESET = "ACTION_RESET"
        const val ACTION_SELECT_MODE = "ACTION_SELECT_MODE"
        const val ACTION_CHANGE_HABIT = "ACTION_CHANGE_HABIT"
        const val ACTION_NEXT_TRACK = "ACTION_NEXT_TRACK"
        const val ACTION_PREVIOUS_TRACK = "ACTION_PREVIOUS_TRACK"
        const val ACTION_TOGGLE_MUSIC = "ACTION_TOGGLE_MUSIC"
        const val ACTION_VOLUME_DOWN = "ACTION_VOLUME_DOWN"
        const val ACTION_VOLUME_UP = "ACTION_VOLUME_UP"

        // Intent extras
        const val EXTRA_HABIT_ID = "EXTRA_HABIT_ID"
        const val EXTRA_HABIT_NAME = "EXTRA_HABIT_NAME"
        const val EXTRA_TIMER_MODE = "EXTRA_TIMER_MODE"
        const val EXTRA_TARGET_SECONDS = "EXTRA_TARGET_SECONDS"
        const val EXTRA_IS_RESTORE = "EXTRA_IS_RESTORE"
        const val EXTRA_SAVE_SESSION = "EXTRA_SAVE_SESSION"

        val ALL_MODES = listOf("POMODORO", "STOPWATCH", "COUNTDOWN")

        fun defaultModeStates(): Map<String, ModeTimerState> = mapOf(
            "POMODORO" to ModeTimerState("POMODORO"),
            "STOPWATCH" to ModeTimerState("STOPWATCH"),
            "COUNTDOWN" to ModeTimerState("COUNTDOWN")
        )

        // Per-mode independent states
        val modeStates = MutableStateFlow<Map<String, ModeTimerState>>(defaultModeStates())

        // State observable from UI (always reflects activeMode's ModeTimerState)
        val activeHabitId = MutableStateFlow<String?>(null)
        val activeHabitName = MutableStateFlow<String?>(null)
        val activeMode = MutableStateFlow("POMODORO") // "POMODORO", "STOPWATCH", "COUNTDOWN"
        val targetDuration = MutableStateFlow(25 * 60L)
        val elapsedSeconds = MutableStateFlow(0L)
        val isRunning = MutableStateFlow(false)
        val isPaused = MutableStateFlow(false)
        val pomodoroRound = MutableStateFlow(1)
        val isPomodoroBreak = MutableStateFlow(false)
        val pendingSession = MutableStateFlow<PendingSession?>(null)

        var baseElapsedSeconds: Long = 0L
        var startedAtMillis: Long = 0L
        var lastCompletedPhaseKey: String? = null
        var restoredThisProcess: Boolean = false

        val isMusicPlaying = MutableStateFlow(false)
        val currentTrackUri = MutableStateFlow<String?>(null)
        val currentTrackName = MutableStateFlow<String?>(null)

        private const val PREFS_NAME = "TimerServicePrefs"
        private const val KEY_HABIT_ID = "habitId"
        private const val KEY_HABIT_NAME = "habitName"
        private const val KEY_MODE = "activeMode"
        private const val KEY_TARGET_DURATION = "targetDuration"
        private const val KEY_POMODORO_ROUND = "pomodoroRound"
        private const val KEY_IS_BREAK = "isPomodoroBreak"
        private const val KEY_BASE_ELAPSED_SECONDS = "baseElapsedSeconds"
        private const val KEY_STARTED_AT_MILLIS = "startedAtMillis"
        private const val KEY_IS_RUNNING = "isRunning"
        private const val KEY_IS_PAUSED = "isPaused"
        private const val KEY_LAST_UPDATED_TIME = "lastUpdatedTime"
        private const val KEY_LAST_COMPLETED_PHASE_KEY = "lastCompletedPhaseKey"
        private const val KEY_PENDING_ID = "pending_id"
        private const val KEY_PENDING_HABIT_ID = "pending_habit_id"
        private const val KEY_PENDING_HABIT_NAME = "pending_habit_name"
        private const val KEY_PENDING_SECONDS = "pending_seconds"
        private const val KEY_PENDING_MODE = "pending_mode"
        private const val KEY_PENDING_COMPLETED_AT = "pending_completed_at"

        private fun modePrefKey(mode: String, field: String): String = "mode_${mode}_$field"

        fun getModeState(mode: String): ModeTimerState {
            return modeStates.value[mode] ?: ModeTimerState(mode)
        }

        fun anyModeRunning(): Boolean = modeStates.value.values.any { it.isRunning }

        fun anyModeActive(): Boolean = modeStates.value.values.any { it.isRunning || it.isPaused }

        fun getDisplayModeState(nowMillis: Long = System.currentTimeMillis()): ModeTimerState {
            val current = getModeState(activeMode.value)
            if (current.isRunning || current.isPaused) return current
            val running = modeStates.value.values.firstOrNull { it.isRunning }
            if (running != null) return running
            val paused = modeStates.value.values.firstOrNull { it.isPaused }
            if (paused != null) return paused
            return current
        }

        fun formatModeTime(state: ModeTimerState, nowMillis: Long = System.currentTimeMillis()): String {
            val seconds = state.computeElapsedSeconds(nowMillis)
            return when (state.mode) {
                "COUNTDOWN", "POMODORO" -> {
                    val rem = (state.targetDuration - seconds).coerceAtLeast(0L)
                    val hours = rem / 3600
                    val minutes = (rem % 3600) / 60
                    val secs = rem % 60
                    if (hours > 0) {
                        String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
                    } else {
                        String.format(java.util.Locale.US, "%02d:%02d", minutes, secs)
                    }
                }
                else -> {
                    val hours = seconds / 3600
                    val minutes = (seconds % 3600) / 60
                    val secs = seconds % 60
                    if (hours > 0) {
                        String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
                    } else {
                        String.format(java.util.Locale.US, "%02d:%02d", minutes, secs)
                    }
                }
            }
        }

        fun formatModeBadge(state: ModeTimerState): String = when (state.mode) {
            "POMODORO" -> if (state.isPomodoroBreak) "Break" else "Pomodoro"
            "COUNTDOWN" -> "Countdown"
            "STOPWATCH" -> "Stopwatch"
            else -> state.mode
        }

        fun syncActiveModeFlows(nowMillis: Long = System.currentTimeMillis()) {
            val mode = activeMode.value
            val state = getModeState(mode)
            targetDuration.value = state.targetDuration
            baseElapsedSeconds = state.baseElapsedSeconds
            startedAtMillis = state.startedAtMillis
            elapsedSeconds.value = state.computeElapsedSeconds(nowMillis)
            isRunning.value = state.isRunning
            isPaused.value = state.isPaused
            pomodoroRound.value = state.pomodoroRound
            isPomodoroBreak.value = state.isPomodoroBreak
        }

        fun computeElapsedSeconds(nowMillis: Long = System.currentTimeMillis()): Long {
            val state = modeStates.value[activeMode.value]
            if (state != null && (state.isRunning || state.isPaused || state.baseElapsedSeconds > 0L || state.startedAtMillis > 0L)) {
                return state.computeElapsedSeconds(nowMillis)
            }
            return if (startedAtMillis > 0L) {
                baseElapsedSeconds + ((nowMillis - startedAtMillis) / 1000L).coerceAtLeast(0L)
            } else {
                baseElapsedSeconds
            }
        }

        fun applySelectMode(mode: String, nowMillis: Long = System.currentTimeMillis()) {
            if (mode !in ALL_MODES) return
            if (!modeStates.value.containsKey(mode)) {
                modeStates.value = modeStates.value + (mode to ModeTimerState(mode))
            }
            activeMode.value = mode
            syncActiveModeFlows(nowMillis)
        }

        fun applyStartMode(
            habitId: String,
            habitName: String,
            mode: String,
            targetSeconds: Long,
            nowMillis: Long = System.currentTimeMillis()
        ): Boolean {
            val current = getModeState(mode)
            val wasPaused = current.isPaused
            val nextTarget = if (targetSeconds > 0L) targetSeconds else current.targetDuration

            val updated = when {
                current.isPaused -> {
                    current.copy(
                        targetDuration = nextTarget,
                        startedAtMillis = nowMillis,
                        isRunning = true,
                        isPaused = false
                    )
                }
                current.isRunning -> {
                    current.copy(targetDuration = nextTarget)
                }
                else -> {
                    current.copy(
                        targetDuration = nextTarget,
                        baseElapsedSeconds = 0L,
                        startedAtMillis = nowMillis,
                        isRunning = true,
                        isPaused = false
                    )
                }
            }

            if (habitId.isNotBlank()) {
                activeHabitId.value = habitId
                activeHabitName.value = habitName
            }
            activeMode.value = mode
            modeStates.value = modeStates.value + (mode to updated)
            syncActiveModeFlows(nowMillis)
            return wasPaused
        }

        fun applyPauseMode(mode: String = activeMode.value, nowMillis: Long = System.currentTimeMillis()) {
            val current = getModeState(mode)
            if (!current.isRunning && !current.isPaused) return
            val elapsed = current.computeElapsedSeconds(nowMillis)
            val updated = current.copy(
                baseElapsedSeconds = elapsed,
                startedAtMillis = 0L,
                isRunning = false,
                isPaused = true
            )
            modeStates.value = modeStates.value + (mode to updated)
            syncActiveModeFlows(nowMillis)
        }

        fun applyResetMode(mode: String = activeMode.value, nowMillis: Long = System.currentTimeMillis()) {
            val current = getModeState(mode)
            val updated = if (mode == "POMODORO") {
                current.copy(
                    targetDuration = 25 * 60L,
                    baseElapsedSeconds = 0L,
                    startedAtMillis = 0L,
                    isRunning = false,
                    isPaused = false,
                    pomodoroRound = 1,
                    isPomodoroBreak = false
                )
            } else {
                current.copy(
                    baseElapsedSeconds = 0L,
                    startedAtMillis = 0L,
                    isRunning = false,
                    isPaused = false
                )
            }
            modeStates.value = modeStates.value + (mode to updated)
            syncActiveModeFlows(nowMillis)
        }

        fun applyStopMode(mode: String = activeMode.value, nowMillis: Long = System.currentTimeMillis()): Long {
            val current = getModeState(mode)
            val elapsed = current.computeElapsedSeconds(nowMillis)
            val resetState = if (mode == "POMODORO") {
                current.copy(
                    targetDuration = 25 * 60L,
                    baseElapsedSeconds = 0L,
                    startedAtMillis = 0L,
                    isRunning = false,
                    isPaused = false,
                    pomodoroRound = 1,
                    isPomodoroBreak = false
                )
            } else {
                current.copy(
                    baseElapsedSeconds = 0L,
                    startedAtMillis = 0L,
                    isRunning = false,
                    isPaused = false
                )
            }
            modeStates.value = modeStates.value + (mode to resetState)

            val otherActive = modeStates.value.values.firstOrNull { it.mode != mode && (it.isRunning || it.isPaused) }
            if (otherActive == null) {
                activeHabitId.value = null
                activeHabitName.value = null
            }
            syncActiveModeFlows(nowMillis)
            return elapsed
        }

        fun setCountdownTarget(context: Context?, targetSeconds: Long) {
            if (targetSeconds <= 0L) return
            val current = getModeState("COUNTDOWN")
            if (!current.isRunning && !current.isPaused) {
                val updated = current.copy(targetDuration = targetSeconds, baseElapsedSeconds = 0L, startedAtMillis = 0L)
                modeStates.value = modeStates.value + ("COUNTDOWN" to updated)
                syncActiveModeFlows()
                if (context != null) {
                    saveTimerState(context)
                }
            }
        }

        fun savePendingSession(context: Context, session: PendingSession) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putLong(KEY_PENDING_ID, session.id)
                putString(KEY_PENDING_HABIT_ID, session.habitId)
                putString(KEY_PENDING_HABIT_NAME, session.habitName)
                putLong(KEY_PENDING_SECONDS, session.seconds)
                putString(KEY_PENDING_MODE, session.mode)
                putLong(KEY_PENDING_COMPLETED_AT, session.completedAt)
                apply()
            }
        }

        fun clearPendingSession(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                remove(KEY_PENDING_ID)
                remove(KEY_PENDING_HABIT_ID)
                remove(KEY_PENDING_HABIT_NAME)
                remove(KEY_PENDING_SECONDS)
                remove(KEY_PENDING_MODE)
                remove(KEY_PENDING_COMPLETED_AT)
                apply()
            }
        }

        fun consumePendingSession(context: Context, id: Long) {
            if (pendingSession.value?.id == id) {
                pendingSession.value = null
                clearPendingSession(context)
            }
        }

        fun saveTimerState(context: Context) {
            val now = System.currentTimeMillis()
            val activeState = getModeState(activeMode.value)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString(KEY_HABIT_ID, activeHabitId.value)
                putString(KEY_HABIT_NAME, activeHabitName.value)
                putString(KEY_MODE, activeMode.value)
                putLong(KEY_TARGET_DURATION, activeState.targetDuration)
                putInt(KEY_POMODORO_ROUND, activeState.pomodoroRound)
                putBoolean(KEY_IS_BREAK, activeState.isPomodoroBreak)
                putLong(KEY_BASE_ELAPSED_SECONDS, activeState.baseElapsedSeconds)
                putLong(KEY_STARTED_AT_MILLIS, activeState.startedAtMillis)
                putBoolean(KEY_IS_RUNNING, activeState.isRunning)
                putBoolean(KEY_IS_PAUSED, activeState.isPaused)
                putLong(KEY_LAST_UPDATED_TIME, now)
                putString(KEY_LAST_COMPLETED_PHASE_KEY, lastCompletedPhaseKey)
                putBoolean("has_per_mode_state", true)

                for (mode in ALL_MODES) {
                    val mState = getModeState(mode)
                    putLong(modePrefKey(mode, KEY_TARGET_DURATION), mState.targetDuration)
                    putLong(modePrefKey(mode, KEY_BASE_ELAPSED_SECONDS), mState.baseElapsedSeconds)
                    putLong(modePrefKey(mode, KEY_STARTED_AT_MILLIS), mState.startedAtMillis)
                    putBoolean(modePrefKey(mode, KEY_IS_RUNNING), mState.isRunning)
                    putBoolean(modePrefKey(mode, KEY_IS_PAUSED), mState.isPaused)
                    putInt(modePrefKey(mode, KEY_POMODORO_ROUND), mState.pomodoroRound)
                    putBoolean(modePrefKey(mode, KEY_IS_BREAK), mState.isPomodoroBreak)
                }
                apply()
            }
        }

        fun restoreTimerState(context: Context) {
            if (restoredThisProcess || anyModeActive()) {
                return
            }
            restoredThisProcess = true

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()

            // Restore pending session if within 12 hours
            val pendingId = prefs.getLong(KEY_PENDING_ID, 0L)
            if (pendingId > 0L) {
                val completedAt = prefs.getLong(KEY_PENDING_COMPLETED_AT, 0L)
                if (completedAt > 0L && (now - completedAt) <= 12 * 3600 * 1000L) {
                    val habitId = prefs.getString(KEY_PENDING_HABIT_ID, "") ?: ""
                    val habitName = prefs.getString(KEY_PENDING_HABIT_NAME, "") ?: ""
                    val seconds = prefs.getLong(KEY_PENDING_SECONDS, 0L)
                    val mode = prefs.getString(KEY_PENDING_MODE, "") ?: ""
                    pendingSession.value = PendingSession(
                        id = pendingId,
                        habitId = habitId,
                        habitName = habitName,
                        seconds = seconds,
                        mode = mode,
                        completedAt = completedAt
                    )
                } else {
                    clearPendingSession(context)
                    pendingSession.value = null
                }
            }

            val savedHabitId = prefs.getString(KEY_HABIT_ID, null)
            val savedHabitName = prefs.getString(KEY_HABIT_NAME, null)
            val savedMode = (prefs.getString(KEY_MODE, "POMODORO") ?: "POMODORO").let {
                if (it in ALL_MODES) it else "POMODORO"
            }
            lastCompletedPhaseKey = prefs.getString(KEY_LAST_COMPLETED_PHASE_KEY, null)

            // Always restore the user's last selected mode so it survives process restore & app restart
            activeMode.value = savedMode

            val hasPerModeState = prefs.getBoolean("has_per_mode_state", false)
            val restoredMap = mutableMapOf<String, ModeTimerState>()
            var phaseCompletedWhileAway = false

            for (mode in ALL_MODES) {
                val defaultTarget = ModeTimerState.defaultTargetForMode(mode)
                val useLegacyFallback = !hasPerModeState && mode == savedMode

                val mTarget = if (hasPerModeState && prefs.contains(modePrefKey(mode, KEY_TARGET_DURATION))) {
                    prefs.getLong(modePrefKey(mode, KEY_TARGET_DURATION), defaultTarget)
                } else if (useLegacyFallback) {
                    prefs.getLong(KEY_TARGET_DURATION, defaultTarget)
                } else {
                    defaultTarget
                }

                val mBase = if (hasPerModeState) {
                    prefs.getLong(modePrefKey(mode, KEY_BASE_ELAPSED_SECONDS), 0L)
                } else if (useLegacyFallback) {
                    prefs.getLong(KEY_BASE_ELAPSED_SECONDS, 0L)
                } else {
                    0L
                }

                val mStartedAt = if (hasPerModeState) {
                    prefs.getLong(modePrefKey(mode, KEY_STARTED_AT_MILLIS), 0L)
                } else if (useLegacyFallback) {
                    prefs.getLong(KEY_STARTED_AT_MILLIS, 0L)
                } else {
                    0L
                }

                val mRunning = if (hasPerModeState) {
                    prefs.getBoolean(modePrefKey(mode, KEY_IS_RUNNING), false)
                } else if (useLegacyFallback) {
                    prefs.getBoolean(KEY_IS_RUNNING, false)
                } else {
                    false
                }

                val mPaused = if (hasPerModeState) {
                    prefs.getBoolean(modePrefKey(mode, KEY_IS_PAUSED), false)
                } else if (useLegacyFallback) {
                    prefs.getBoolean(KEY_IS_PAUSED, false)
                } else {
                    false
                }

                val mRound = if (hasPerModeState) {
                    prefs.getInt(modePrefKey(mode, KEY_POMODORO_ROUND), 1)
                } else if (useLegacyFallback) {
                    prefs.getInt(KEY_POMODORO_ROUND, 1)
                } else {
                    1
                }

                val mBreak = if (hasPerModeState) {
                    prefs.getBoolean(modePrefKey(mode, KEY_IS_BREAK), false)
                } else if (useLegacyFallback) {
                    prefs.getBoolean(KEY_IS_BREAK, false)
                } else {
                    false
                }

                if (savedHabitId == null || (!mRunning && !mPaused)) {
                    restoredMap[mode] = ModeTimerState(
                        mode = mode,
                        targetDuration = mTarget,
                        pomodoroRound = mRound,
                        isPomodoroBreak = mBreak
                    )
                } else if (mPaused) {
                    restoredMap[mode] = ModeTimerState(
                        mode = mode,
                        targetDuration = mTarget,
                        baseElapsedSeconds = mBase,
                        startedAtMillis = 0L,
                        isRunning = false,
                        isPaused = true,
                        pomodoroRound = mRound,
                        isPomodoroBreak = mBreak
                    )
                } else {
                    val elapsed = if (mStartedAt > 0L) {
                        mBase + ((now - mStartedAt) / 1000L).coerceAtLeast(0L)
                    } else {
                        mBase
                    }
                    if (mode != "STOPWATCH" && mTarget > 0L && elapsed >= mTarget) {
                        phaseCompletedWhileAway = true
                        val completedAt = if (mStartedAt > 0L) mStartedAt + (mTarget - mBase) * 1000L else now
                        val isWithin12Hours = (now - completedAt) <= 12 * 3600 * 1000L
                        val isFocusPhase = (mode == "COUNTDOWN") || (mode == "POMODORO" && !mBreak)
                        val phaseKey = "$mode|${if (mBreak) "break" else "focus"}|$mRound|$mStartedAt"

                        if (isFocusPhase && isWithin12Hours && lastCompletedPhaseKey != phaseKey) {
                            lastCompletedPhaseKey = phaseKey
                            val session = PendingSession(
                                id = completedAt,
                                habitId = savedHabitId,
                                habitName = savedHabitName ?: "",
                                seconds = mTarget,
                                mode = mode,
                                completedAt = completedAt
                            )
                            pendingSession.value = session
                            savePendingSession(context, session)
                            prefs.edit().putString(KEY_LAST_COMPLETED_PHASE_KEY, phaseKey).apply()
                        }

                        if (mode == "POMODORO") {
                            val nextBreak = !mBreak
                            val nextRound = if (!mBreak) mRound else if (mRound >= 4) 1 else mRound + 1
                            val nextTarget = if (nextBreak) {
                                if (mRound < 4) 5 * 60L else 15 * 60L
                            } else {
                                25 * 60L
                            }
                            restoredMap[mode] = ModeTimerState(
                                mode = mode,
                                targetDuration = nextTarget,
                                baseElapsedSeconds = 0L,
                                startedAtMillis = 0L,
                                isRunning = false,
                                isPaused = false,
                                pomodoroRound = nextRound,
                                isPomodoroBreak = nextBreak
                            )
                        } else {
                            restoredMap[mode] = ModeTimerState(
                                mode = mode,
                                targetDuration = mTarget,
                                baseElapsedSeconds = mTarget,
                                startedAtMillis = 0L,
                                isRunning = false,
                                isPaused = false
                            )
                        }
                    } else {
                        restoredMap[mode] = ModeTimerState(
                            mode = mode,
                            targetDuration = mTarget,
                            baseElapsedSeconds = mBase,
                            startedAtMillis = mStartedAt,
                            isRunning = true,
                            isPaused = false,
                            pomodoroRound = mRound,
                            isPomodoroBreak = mBreak
                        )
                    }
                }
            }

            modeStates.value = restoredMap

            if (savedHabitId != null && (anyModeActive() || phaseCompletedWhileAway)) {
                activeHabitId.value = savedHabitId
                activeHabitName.value = savedHabitName
            }

            syncActiveModeFlows(now)

            if (phaseCompletedWhileAway) {
                saveTimerState(context)
                TimerWidgetProvider.updateWidget(context)
            }

            if (savedHabitId != null && anyModeRunning()) {
                val runningModeState = getDisplayModeState(now)
                val intent = Intent(context, TimerService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_HABIT_ID, savedHabitId)
                    putExtra(EXTRA_HABIT_NAME, savedHabitName)
                    putExtra(EXTRA_TIMER_MODE, runningModeState.mode)
                    putExtra(EXTRA_TARGET_SECONDS, runningModeState.targetDuration)
                    putExtra(EXTRA_IS_RESTORE, true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun clearTimerState(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                remove(KEY_HABIT_ID)
                remove(KEY_HABIT_NAME)
                remove(KEY_TARGET_DURATION)
                remove(KEY_POMODORO_ROUND)
                remove(KEY_IS_BREAK)
                remove(KEY_BASE_ELAPSED_SECONDS)
                remove(KEY_STARTED_AT_MILLIS)
                remove(KEY_IS_RUNNING)
                remove(KEY_IS_PAUSED)
                remove(KEY_LAST_UPDATED_TIME)
                // Keep KEY_MODE so the user's selected mode persists across restarts
                putString(KEY_MODE, activeMode.value)
                for (mode in ALL_MODES) {
                    remove(modePrefKey(mode, KEY_BASE_ELAPSED_SECONDS))
                    remove(modePrefKey(mode, KEY_STARTED_AT_MILLIS))
                    remove(modePrefKey(mode, KEY_IS_RUNNING))
                    remove(modePrefKey(mode, KEY_IS_PAUSED))
                    remove(modePrefKey(mode, KEY_POMODORO_ROUND))
                    remove(modePrefKey(mode, KEY_IS_BREAK))
                }
                apply()
            }
        }

        fun selectMode(context: Context, mode: String) {
            if (mode !in ALL_MODES) return
            applySelectMode(mode)
            saveTimerState(context)
            TimerWidgetProvider.updateWidget(context)
            if (anyModeActive()) {
                val intent = Intent(context, TimerService::class.java).apply {
                    action = ACTION_SELECT_MODE
                    putExtra(EXTRA_TIMER_MODE, mode)
                }
                context.startService(intent)
            }
        }

        fun startTimer(
            context: Context,
            habitId: String,
            habitName: String,
            mode: String = activeMode.value,
            targetSeconds: Long = getModeState(mode).targetDuration
        ) {
            val wasPaused = applyStartMode(
                habitId = habitId,
                habitName = habitName,
                mode = mode,
                targetSeconds = targetSeconds
            )
            saveTimerState(context)
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HABIT_ID, habitId)
                putExtra(EXTRA_HABIT_NAME, habitName)
                putExtra(EXTRA_TIMER_MODE, mode)
                putExtra(EXTRA_TARGET_SECONDS, targetSeconds)
                putExtra("EXTRA_WAS_PAUSED", wasPaused)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun changeActiveHabit(context: Context, habitId: String, habitName: String) {
            activeHabitId.value = habitId
            activeHabitName.value = habitName
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_CHANGE_HABIT
                putExtra(EXTRA_HABIT_ID, habitId)
                putExtra(EXTRA_HABIT_NAME, habitName)
            }
            context.startService(intent)
        }

        fun pauseTimer(context: Context, mode: String = activeMode.value) {
            applyPauseMode(mode)
            saveTimerState(context)
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_TIMER_MODE, mode)
            }
            context.startService(intent)
        }

        fun resetTimer(context: Context, mode: String = activeMode.value) {
            applyResetMode(mode)
            saveTimerState(context)
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_RESET
                putExtra(EXTRA_TIMER_MODE, mode)
            }
            context.startService(intent)
        }

        fun stopTimer(context: Context, mode: String = activeMode.value) {
            applyStopMode(mode)
            saveTimerState(context)
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_TIMER_MODE, mode)
            }
            context.startService(intent)
        }

        fun nextTrack(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_NEXT_TRACK
            }
            context.startService(intent)
        }

        fun previousTrack(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_PREVIOUS_TRACK
            }
            context.startService(intent)
        }

        fun toggleMusic(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_TOGGLE_MUSIC
            }
            context.startService(intent)
        }

        fun lowerVolume(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_VOLUME_DOWN
            }
            context.startService(intent)
        }

        fun raiseVolume(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_VOLUME_UP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        PlaylistPreferences.init(applicationContext)

        // Observe preference changes dynamically and sync playback list
        serviceScope.launch {
            kotlinx.coroutines.flow.combine(
                PlaylistPreferences.playlist,
                PlaylistPreferences.shuffle,
                PlaylistPreferences.favoritesOnly
            ) { plist, shuffle, favoritesOnly ->
                Triple(plist, shuffle, favoritesOnly)
            }.collect { (plist, shuffle, favoritesOnly) ->
                syncPlaybackState(plist, shuffle, favoritesOnly)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("TimerService", "onStartCommand action: $action")
        when (action) {
            ACTION_SELECT_MODE -> {
                val mode = intent.getStringExtra(EXTRA_TIMER_MODE) ?: activeMode.value
                applySelectMode(mode)
                saveTimerState(this)
                if (anyModeActive()) {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, buildNotification())
                }
                TimerWidgetProvider.updateWidget(this)
            }
            ACTION_START -> {
                val isRestore = intent?.getBooleanExtra(EXTRA_IS_RESTORE, false) ?: false
                val habitId = intent?.getStringExtra(EXTRA_HABIT_ID) ?: (activeHabitId.value ?: "")
                val habitName = intent?.getStringExtra(EXTRA_HABIT_NAME) ?: (activeHabitName.value ?: "")
                val mode = intent?.getStringExtra(EXTRA_TIMER_MODE) ?: activeMode.value
                val defaultTarget = getModeState(mode).targetDuration
                val target = intent?.getLongExtra(EXTRA_TARGET_SECONDS, defaultTarget) ?: defaultTarget
                val wasPausedExtra = intent?.getBooleanExtra("EXTRA_WAS_PAUSED", false) ?: false

                val wasPausedBeforeStart = if (!isRestore) {
                    val pausedInState = applyStartMode(
                        habitId = habitId,
                        habitName = habitName,
                        mode = mode,
                        targetSeconds = target
                    )
                    wasPausedExtra || pausedInState
                } else {
                    syncActiveModeFlows()
                    false
                }

                startForegroundCompat(buildNotification())
                startTimerCoroutine()
                saveTimerState(this)
                TimerWidgetProvider.updateWidget(this)

                if (!isRestore && PlaylistPreferences.autoPlayWithTimer.value) {
                    when (PlaylistPreferences.musicSourceMode.value) {
                        MusicSourceMode.EXTERNAL -> {
                            if (!MediaControlHelper.isMusicActive(this)) {
                                MediaControlHelper.play(this)
                            }
                            isMusicPlaying.value = true
                            currentTrackName.value = "Active Media (YouTube Music)"
                        }
                        MusicSourceMode.LOCAL -> {
                            if (wasPausedBeforeStart) {
                                if (mediaPlayer != null) {
                                    try {
                                        mediaPlayer?.start()
                                        isMusicPlaying.value = true
                                    } catch (e: Exception) {
                                        Log.e("TimerService", "Error starting paused mediaPlayer", e)
                                        initializePlaylistAndPlay(currentIndex)
                                    }
                                } else {
                                    initializePlaylistAndPlay(currentIndex)
                                }
                            } else if (mediaPlayer?.isPlaying != true) {
                                initializePlaylistAndPlay(0)
                            }
                        }
                        MusicSourceMode.OFF -> {}
                    }
                }
            }
            ACTION_CHANGE_HABIT -> {
                val habitId = intent.getStringExtra(EXTRA_HABIT_ID)
                val habitName = intent.getStringExtra(EXTRA_HABIT_NAME)
                if (!habitId.isNullOrBlank()) {
                    activeHabitId.value = habitId
                    activeHabitName.value = habitName
                    saveTimerState(this)
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, buildNotification())
                    TimerWidgetProvider.updateWidget(this)
                }
            }
            ACTION_PAUSE -> {
                val mode = intent.getStringExtra(EXTRA_TIMER_MODE) ?: getDisplayModeState().mode
                applyPauseMode(mode)
                if (!anyModeRunning()) {
                    stopTimerCoroutine()
                }
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
                startForegroundCompat(buildNotification())
                saveTimerState(this)
                TimerWidgetProvider.updateWidget(this)

                if (!anyModeRunning() && PlaylistPreferences.autoPlayWithTimer.value) {
                    when (PlaylistPreferences.musicSourceMode.value) {
                        MusicSourceMode.EXTERNAL -> {
                            if (MediaControlHelper.isMusicActive(this)) {
                                MediaControlHelper.pause(this)
                            }
                            isMusicPlaying.value = false
                        }
                        MusicSourceMode.LOCAL -> {
                            try {
                                if (mediaPlayer?.isPlaying == true) {
                                    mediaPlayer?.pause()
                                    isMusicPlaying.value = false
                                }
                            } catch (e: Exception) {
                                Log.e("TimerService", "Error pausing mediaPlayer", e)
                            }
                        }
                        MusicSourceMode.OFF -> {}
                    }
                }
            }
            ACTION_RESET -> {
                val mode = intent.getStringExtra(EXTRA_TIMER_MODE) ?: activeMode.value
                applyResetMode(mode)
                if (!anyModeRunning()) {
                    stopTimerCoroutine()
                }
                saveTimerState(this)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
                TimerWidgetProvider.updateWidget(this)
            }
            ACTION_STOP -> {
                val mode = intent.getStringExtra(EXTRA_TIMER_MODE) ?: getDisplayModeState().mode
                val saveSession = intent.getBooleanExtra(EXTRA_SAVE_SESSION, false)
                val habitIdBeforeStop = activeHabitId.value
                val stateBeforeStop = getModeState(mode)
                val stoppedElapsed = applyStopMode(mode)

                if (saveSession && !habitIdBeforeStop.isNullOrBlank() && stoppedElapsed > 0L && !(mode == "POMODORO" && stateBeforeStop.isPomodoroBreak)) {
                    val appCtx = applicationContext
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = com.example.data.AppDatabase.getDatabase(appCtx)
                            val today = com.example.util.DateUtils.todayStr()
                            val existing = db.timeSessionDao().getSession(habitIdBeforeStop, today)
                            val nextDuration = (existing?.durationSeconds ?: 0L) + stoppedElapsed
                            db.timeSessionDao().insertSession(
                                com.example.data.TimeSessionEntity(
                                    habitId = habitIdBeforeStop,
                                    date = today,
                                    durationSeconds = nextDuration
                                )
                            )
                            com.example.receiver.WidgetUpdateHelper.updateWidgets(appCtx)
                        } catch (e: Exception) {
                            Log.e("TimerService", "Error saving session on stop", e)
                        }
                    }
                }

                if (!anyModeRunning()) {
                    stopTimerCoroutine()
                }

                if (anyModeActive()) {
                    saveTimerState(this)
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, buildNotification())
                    TimerWidgetProvider.updateWidget(this)
                } else {
                    clearTimerState(this)
                    try {
                        mediaPlayer?.stop()
                    } catch (e: Exception) {
                        Log.e("TimerService", "Error stopping mediaPlayer", e)
                    } finally {
                        mediaPlayer?.release()
                        mediaPlayer = null
                        currentTrackUri.value = null
                        currentTrackName.value = null
                        isMusicPlaying.value = false
                    }

                    TimerWidgetProvider.updateWidget(this)
                    stopSelf()
                }
            }
            ACTION_NEXT_TRACK -> {
                when (PlaylistPreferences.musicSourceMode.value) {
                    MusicSourceMode.EXTERNAL -> {
                        MediaControlHelper.nextTrack(this)
                        isMusicPlaying.value = true
                        currentTrackName.value = "Active Media (YouTube Music)"
                    }
                    MusicSourceMode.LOCAL -> {
                        playNextTrack()
                    }
                    MusicSourceMode.OFF -> {}
                }
                TimerWidgetProvider.updateWidget(this)
            }
            ACTION_PREVIOUS_TRACK -> {
                when (PlaylistPreferences.musicSourceMode.value) {
                    MusicSourceMode.EXTERNAL -> {
                        MediaControlHelper.previousTrack(this)
                        isMusicPlaying.value = true
                        currentTrackName.value = "Active Media (YouTube Music)"
                    }
                    MusicSourceMode.LOCAL -> {
                        playPreviousTrack()
                    }
                    MusicSourceMode.OFF -> {}
                }
                TimerWidgetProvider.updateWidget(this)
            }
            ACTION_TOGGLE_MUSIC -> {
                when (PlaylistPreferences.musicSourceMode.value) {
                    MusicSourceMode.EXTERNAL -> {
                        MediaControlHelper.togglePlayPause(this)
                        val active = MediaControlHelper.isMusicActive(this)
                        isMusicPlaying.value = !active
                        currentTrackName.value = "Active Media (YouTube Music)"
                    }
                    MusicSourceMode.LOCAL -> {
                        if (mediaPlayer?.isPlaying == true) {
                            try {
                                mediaPlayer?.pause()
                            } catch (e: Exception) {
                                Log.e("TimerService", "Error pausing mediaPlayer", e)
                            }
                            isMusicPlaying.value = false
                        } else if (mediaPlayer != null) {
                            try {
                                mediaPlayer?.start()
                                isMusicPlaying.value = true
                            } catch (e: Exception) {
                                Log.e("TimerService", "Error starting mediaPlayer", e)
                                initializePlaylistAndPlay(currentIndex)
                            }
                        } else {
                            initializePlaylistAndPlay(0)
                        }
                    }
                    MusicSourceMode.OFF -> {}
                }
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
                TimerWidgetProvider.updateWidget(this)
            }
            ACTION_VOLUME_DOWN -> {
                MediaControlHelper.lowerVolume(this)
            }
            ACTION_VOLUME_UP -> {
                MediaControlHelper.raiseVolume(this)
            }
        }
        return START_NOT_STICKY
    }

    private fun getTracksForPlayback(): List<PlaylistTrack> {
        val plist = PlaylistPreferences.playlist.value
        return if (PlaylistPreferences.favoritesOnly.value) {
            plist.filter { it.isFavorite }
        } else {
            plist
        }
    }

    private fun syncPlaybackState(
        plist: List<PlaylistTrack>,
        isShuffle: Boolean,
        isFavoritesOnly: Boolean
    ) {
        val filteredList = if (isFavoritesOnly) plist.filter { it.isFavorite } else plist

        if (filteredList.isEmpty()) {
            playbackList = emptyList()
            currentIndex = 0
            currentTrackUri.value = null
            currentTrackName.value = null
            try {
                mediaPlayer?.release()
            } catch (e: Exception) {}
            mediaPlayer = null
            TimerWidgetProvider.updateWidget(this)
            return
        }

        val currentUri = currentTrackUri.value
        val stillExists = currentUri != null && filteredList.any { it.uri == currentUri }

        if (stillExists) {
            if (isShuffle) {
                val existingUris = playbackList.map { it.uri }.toSet()
                val newUris = filteredList.map { it.uri }.toSet()
                if (existingUris != newUris) {
                    val currentTrack = filteredList.first { it.uri == currentUri }
                    val remaining = filteredList.filter { it.uri != currentUri }.shuffled()
                    playbackList = listOf(currentTrack) + remaining
                    currentIndex = 0
                } else {
                    currentIndex = playbackList.indexOfFirst { it.uri == currentUri }.coerceAtLeast(0)
                }
            } else {
                playbackList = filteredList
                currentIndex = playbackList.indexOfFirst { it.uri == currentUri }.coerceAtLeast(0)
            }
        } else {
            playbackList = if (isShuffle) filteredList.shuffled() else filteredList
            currentIndex = 0
        }

        val trackToReflect = playbackList.getOrNull(currentIndex)
        if (trackToReflect != null) {
            currentTrackUri.value = trackToReflect.uri
            currentTrackName.value = trackToReflect.name
        }
        TimerWidgetProvider.updateWidget(this)
    }

    private fun initializePlaylistAndPlay(index: Int = 0) {
        val plist = getTracksForPlayback()
        if (plist.isEmpty()) {
            Log.d("TimerService", "Cannot start music: Playlist is empty")
            return
        }
        val shuffle = PlaylistPreferences.shuffle.value
        playbackList = if (shuffle) plist.shuffled() else plist
        currentIndex = index.coerceIn(0, playbackList.size - 1)
        consecutiveFailuresCount = 0
        playTrack(currentIndex)
    }

    private fun playTrack(index: Int) {
        if (playbackList.isEmpty()) return
        if (consecutiveFailuresCount >= playbackList.size) {
            Log.e("TimerService", "All tracks in playlist failed to load/play. Aborting loop to prevent crash.")
            isMusicPlaying.value = false
            currentTrackUri.value = null
            currentTrackName.value = null
            consecutiveFailuresCount = 0
            TimerWidgetProvider.updateWidget(this)
            return
        }

        val track = playbackList[index]
        currentIndex = index
        currentTrackUri.value = track.uri
        currentTrackName.value = track.name
        TimerWidgetProvider.updateWidget(this)

        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@TimerService, Uri.parse(track.uri))
                setOnCompletionListener {
                    consecutiveFailuresCount = 0
                    playNextTrack()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("TimerService", "MediaPlayer error: what=$what, extra=$extra")
                    consecutiveFailuresCount++
                    playNextTrack()
                    true
                }
                prepare()
                start()
            }
            consecutiveFailuresCount = 0
            isMusicPlaying.value = true
        } catch (e: Exception) {
            Log.e("TimerService", "Error playing track: ${track.name}", e)
            isMusicPlaying.value = false
            consecutiveFailuresCount++
            playNextTrack()
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
        TimerWidgetProvider.updateWidget(this)
    }

    private fun playNextTrack() {
        if (playbackList.isEmpty()) {
            currentTrackUri.value = null
            currentTrackName.value = null
            TimerWidgetProvider.updateWidget(this)
            return
        }
        val nextIndex = (currentIndex + 1) % playbackList.size
        playTrack(nextIndex)
    }

    private fun playPreviousTrack() {
        if (playbackList.isEmpty()) {
            currentTrackUri.value = null
            currentTrackName.value = null
            TimerWidgetProvider.updateWidget(this)
            return
        }
        val prevIndex = if (currentIndex - 1 < 0) playbackList.size - 1 else currentIndex - 1
        playTrack(prevIndex)
    }

    private fun startTimerCoroutine() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (anyModeRunning()) {
                delay(1000)
                if (!anyModeRunning()) break

                val now = System.currentTimeMillis()
                syncActiveModeFlows(now)

                // Check completion for COUNTDOWN and POMODORO even if another mode tab is currently viewed
                val countdownState = getModeState("COUNTDOWN")
                if (countdownState.isRunning && countdownState.targetDuration > 0L && countdownState.computeElapsedSeconds(now) >= countdownState.targetDuration) {
                    handleCountdownComplete(now)
                }

                val pomodoroState = getModeState("POMODORO")
                if (pomodoroState.isRunning && pomodoroState.targetDuration > 0L && pomodoroState.computeElapsedSeconds(now) >= pomodoroState.targetDuration) {
                    handlePomodoroComplete(now)
                }

                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
                saveTimerState(this@TimerService)
                TimerWidgetProvider.updateWidget(this@TimerService)
            }
        }
    }

    private fun handleCountdownComplete(now: Long) {
        val state = getModeState("COUNTDOWN")
        val target = state.targetDuration
        val phaseKey = "COUNTDOWN|focus|1|${state.startedAtMillis}"
        if (lastCompletedPhaseKey != phaseKey) {
            lastCompletedPhaseKey = phaseKey
            val completedAt = if (state.startedAtMillis > 0L) {
                state.startedAtMillis + (target - state.baseElapsedSeconds) * 1000L
            } else {
                now
            }
            val session = PendingSession(
                id = completedAt,
                habitId = activeHabitId.value ?: "",
                habitName = activeHabitName.value ?: "",
                seconds = target,
                mode = "COUNTDOWN",
                completedAt = completedAt
            )
            pendingSession.value = session
            savePendingSession(this, session)
        }

        val updated = state.copy(
            baseElapsedSeconds = target,
            startedAtMillis = 0L,
            isRunning = false,
            isPaused = false
        )
        modeStates.value = modeStates.value + ("COUNTDOWN" to updated)
        syncActiveModeFlows(now)

        saveTimerState(this)
        TimerWidgetProvider.updateWidget(this)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun handlePomodoroComplete(now: Long) {
        val state = getModeState("POMODORO")
        val target = state.targetDuration
        val isBreak = state.isPomodoroBreak
        val round = state.pomodoroRound

        val updated = if (!isBreak) {
            // Focus complete: create pending session ONLY for focus completion!
            val phaseKey = "POMODORO|focus|$round|${state.startedAtMillis}"
            if (lastCompletedPhaseKey != phaseKey) {
                lastCompletedPhaseKey = phaseKey
                val completedAt = if (state.startedAtMillis > 0L) {
                    state.startedAtMillis + (target - state.baseElapsedSeconds) * 1000L
                } else {
                    now
                }
                val session = PendingSession(
                    id = completedAt,
                    habitId = activeHabitId.value ?: "",
                    habitName = activeHabitName.value ?: "",
                    seconds = target,
                    mode = "POMODORO",
                    completedAt = completedAt
                )
                pendingSession.value = session
                savePendingSession(this, session)
            }

            // Transition to break
            state.copy(
                targetDuration = if (round < 4) 5 * 60L else 15 * 60L,
                baseElapsedSeconds = 0L,
                startedAtMillis = 0L,
                isRunning = false,
                isPaused = false,
                isPomodoroBreak = true
            )
        } else {
            // Break complete: NO pending session created!
            // Transition to next focus round
            state.copy(
                targetDuration = 25 * 60L,
                baseElapsedSeconds = 0L,
                startedAtMillis = 0L,
                isRunning = false,
                isPaused = false,
                pomodoroRound = if (round >= 4) 1 else round + 1,
                isPomodoroBreak = false
            )
        }

        modeStates.value = modeStates.value + ("POMODORO" to updated)
        syncActiveModeFlows(now)

        saveTimerState(this)
        TimerWidgetProvider.updateWidget(this)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun stopTimerCoroutine() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("TimerService", "Error starting foreground service: ${e.message}", e)
        }
    }

    private fun buildNotification(): Notification {
        val now = System.currentTimeMillis()
        val displayState = getDisplayModeState(now)
        val habitName = activeHabitName.value ?: "Focus Session"
        val modeBadge = formatModeBadge(displayState)
        val timeStr = "$modeBadge ${formatModeTime(displayState, now)}"

        val statusText = if (displayState.isPaused) "Paused" else if (displayState.isRunning) "Running" else "Ready"
        val musicMode = PlaylistPreferences.musicSourceMode.value
        val trackText = when (musicMode) {
            MusicSourceMode.EXTERNAL -> "🎵 YouTube Music"
            MusicSourceMode.LOCAL -> currentTrackName.value?.let { "🎵 $it" } ?: "🎵 Music"
            MusicSourceMode.OFF -> null
        }
        val contentText = if (trackText != null) "$statusText: $habitName • $timeStr • $trackText" else "$statusText: $habitName • $timeStr"

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = if (displayState.isPaused) {
            val resumeIntent = Intent(this, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HABIT_ID, activeHabitId.value)
                putExtra(EXTRA_HABIT_NAME, activeHabitName.value)
                putExtra(EXTRA_TIMER_MODE, displayState.mode)
                putExtra(EXTRA_TARGET_SECONDS, displayState.targetDuration)
            }
            val resumePending = PendingIntent.getService(
                this, 101, resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Resume",
                resumePending
            ).build()
        } else {
            val pauseIntent = Intent(this, TimerService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_TIMER_MODE, displayState.mode)
            }
            val pausePending = PendingIntent.getService(
                this, 102, pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                pausePending
            ).build()
        }

        val stopIntent = Intent(this, TimerService::class.java).apply {
            action = ACTION_STOP
            putExtra(EXTRA_TIMER_MODE, displayState.mode)
            putExtra(EXTRA_SAVE_SESSION, true)
        }
        val stopPending = PendingIntent.getService(
            this, 103, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            stopPending
        ).build()

        val prevIntent = Intent(this, TimerService::class.java).apply { action = ACTION_PREVIOUS_TRACK }
        val prevPending = PendingIntent.getService(this, 104, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val toggleMusicIntent = Intent(this, TimerService::class.java).apply { action = ACTION_TOGGLE_MUSIC }
        val toggleMusicPending = PendingIntent.getService(this, 105, toggleMusicIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(this, TimerService::class.java).apply { action = ACTION_NEXT_TRACK }
        val nextPending = PendingIntent.getService(this, 106, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val musicPlayIcon = if (isMusicPlaying.value) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val musicActionTitle = if (isMusicPlaying.value) "Pause Music" else "Play Music"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Focus: $habitName ($modeBadge)")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(anyModeRunning())
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .addAction(android.R.drawable.ic_media_previous, "Prev", prevPending)
            .addAction(musicPlayIcon, musicActionTitle, toggleMusicPending)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Timer & Focus Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing focus timer and audio playback controls"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimerCoroutine()
        serviceScope.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("TimerService", "Error releasing mediaPlayer in onDestroy", e)
        }
        mediaPlayer = null
        isMusicPlaying.value = false
        isRunning.value = false
        isPaused.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
