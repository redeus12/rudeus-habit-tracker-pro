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

        // State observable from UI
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

        fun computeElapsedSeconds(nowMillis: Long = System.currentTimeMillis()): Long {
            return if (startedAtMillis > 0L) {
                baseElapsedSeconds + ((nowMillis - startedAtMillis) / 1000L).coerceAtLeast(0L)
            } else {
                baseElapsedSeconds
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
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString(KEY_HABIT_ID, activeHabitId.value)
                putString(KEY_HABIT_NAME, activeHabitName.value)
                putString(KEY_MODE, activeMode.value)
                putLong(KEY_TARGET_DURATION, targetDuration.value)
                putInt(KEY_POMODORO_ROUND, pomodoroRound.value)
                putBoolean(KEY_IS_BREAK, isPomodoroBreak.value)
                putLong(KEY_BASE_ELAPSED_SECONDS, baseElapsedSeconds)
                putLong(KEY_STARTED_AT_MILLIS, startedAtMillis)
                putBoolean(KEY_IS_RUNNING, isRunning.value)
                putBoolean(KEY_IS_PAUSED, isPaused.value)
                putLong(KEY_LAST_UPDATED_TIME, System.currentTimeMillis())
                putString(KEY_LAST_COMPLETED_PHASE_KEY, lastCompletedPhaseKey)
                apply()
            }
        }

        fun restoreTimerState(context: Context) {
            if (restoredThisProcess || isRunning.value || isPaused.value) {
                return
            }
            restoredThisProcess = true

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Restore pending session if within 12 hours
            val pendingId = prefs.getLong(KEY_PENDING_ID, 0L)
            if (pendingId > 0L) {
                val completedAt = prefs.getLong(KEY_PENDING_COMPLETED_AT, 0L)
                val now = System.currentTimeMillis()
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
            val savedMode = prefs.getString(KEY_MODE, "POMODORO") ?: "POMODORO"
            val savedTarget = prefs.getLong(KEY_TARGET_DURATION, 25 * 60L)
            val savedRound = prefs.getInt(KEY_POMODORO_ROUND, 1)
            val savedIsBreak = prefs.getBoolean(KEY_IS_BREAK, false)
            val savedIsRunning = prefs.getBoolean(KEY_IS_RUNNING, false)
            val savedIsPaused = prefs.getBoolean(KEY_IS_PAUSED, false)
            val savedBase = prefs.getLong(KEY_BASE_ELAPSED_SECONDS, 0L)
            val savedStartedAt = prefs.getLong(KEY_STARTED_AT_MILLIS, 0L)
            lastCompletedPhaseKey = prefs.getString(KEY_LAST_COMPLETED_PHASE_KEY, null)

            if (savedHabitId == null || (!savedIsRunning && !savedIsPaused)) {
                return
            }

            activeHabitId.value = savedHabitId
            activeHabitName.value = savedHabitName
            activeMode.value = savedMode
            targetDuration.value = savedTarget
            pomodoroRound.value = savedRound
            isPomodoroBreak.value = savedIsBreak

            if (savedIsPaused) {
                baseElapsedSeconds = savedBase
                startedAtMillis = 0L
                elapsedSeconds.value = savedBase
                isRunning.value = false
                isPaused.value = true
                return
            }

            // Running state
            val now = System.currentTimeMillis()
            val elapsed = if (savedStartedAt > 0L) savedBase + ((now - savedStartedAt) / 1000L).coerceAtLeast(0L) else savedBase

            if (savedMode != "STOPWATCH" && savedTarget > 0L && elapsed >= savedTarget) {
                // Phase ended while app was away
                val completedAt = if (savedStartedAt > 0L) savedStartedAt + (savedTarget - savedBase) * 1000L else now
                val isWithin12Hours = (now - completedAt) <= 12 * 3600 * 1000L
                val isFocusPhase = (savedMode == "COUNTDOWN") || (savedMode == "POMODORO" && !savedIsBreak)
                val phaseKey = "$savedMode|${if (savedIsBreak) "break" else "focus"}|$savedRound|$savedStartedAt"

                if (isFocusPhase && isWithin12Hours) {
                    if (lastCompletedPhaseKey != phaseKey) {
                        lastCompletedPhaseKey = phaseKey
                        val session = PendingSession(
                            id = completedAt,
                            habitId = savedHabitId,
                            habitName = savedHabitName ?: "",
                            seconds = savedTarget,
                            mode = savedMode,
                            completedAt = completedAt
                        )
                        pendingSession.value = session
                        savePendingSession(context, session)
                        prefs.edit().putString(KEY_LAST_COMPLETED_PHASE_KEY, phaseKey).apply()
                    }
                }

                if (savedMode == "POMODORO") {
                    if (!savedIsBreak) {
                        isPomodoroBreak.value = true
                        targetDuration.value = if (savedRound < 4) 5 * 60L else 15 * 60L
                    } else {
                        isPomodoroBreak.value = false
                        pomodoroRound.value = if (savedRound >= 4) 1 else savedRound + 1
                        targetDuration.value = 25 * 60L
                    }
                    elapsedSeconds.value = 0L
                    baseElapsedSeconds = 0L
                    startedAtMillis = 0L
                    isRunning.value = false
                    isPaused.value = false
                } else if (savedMode == "COUNTDOWN") {
                    elapsedSeconds.value = savedTarget
                    baseElapsedSeconds = savedTarget
                    startedAtMillis = 0L
                    isRunning.value = false
                    isPaused.value = false
                }

                saveTimerState(context)
                TimerWidgetProvider.updateWidget(context)
            } else {
                baseElapsedSeconds = savedBase
                startedAtMillis = savedStartedAt
                elapsedSeconds.value = elapsed
                isRunning.value = true
                isPaused.value = false

                val intent = Intent(context, TimerService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_HABIT_ID, savedHabitId)
                    putExtra(EXTRA_HABIT_NAME, savedHabitName)
                    putExtra(EXTRA_TIMER_MODE, savedMode)
                    putExtra(EXTRA_TARGET_SECONDS, savedTarget)
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
                remove(KEY_MODE)
                remove(KEY_TARGET_DURATION)
                remove(KEY_POMODORO_ROUND)
                remove(KEY_IS_BREAK)
                remove(KEY_BASE_ELAPSED_SECONDS)
                remove(KEY_STARTED_AT_MILLIS)
                remove(KEY_IS_RUNNING)
                remove(KEY_IS_PAUSED)
                remove(KEY_LAST_UPDATED_TIME)
                apply()
            }
        }

        fun startTimer(
            context: Context,
            habitId: String,
            habitName: String,
            mode: String = "POMODORO",
            targetSeconds: Long = 25 * 60L
        ) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HABIT_ID, habitId)
                putExtra(EXTRA_HABIT_NAME, habitName)
                putExtra(EXTRA_TIMER_MODE, mode)
                putExtra(EXTRA_TARGET_SECONDS, targetSeconds)
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

        fun pauseTimer(context: Context) {
            val now = System.currentTimeMillis()
            baseElapsedSeconds = computeElapsedSeconds(now)
            startedAtMillis = 0L
            elapsedSeconds.value = baseElapsedSeconds
            isRunning.value = false
            isPaused.value = true
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resetTimer(context: Context) {
            isRunning.value = false
            isPaused.value = false
            baseElapsedSeconds = 0L
            startedAtMillis = 0L
            elapsedSeconds.value = 0L
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_RESET
            }
            context.startService(intent)
        }

        fun stopTimer(context: Context) {
            isRunning.value = false
            isPaused.value = false
            baseElapsedSeconds = 0L
            startedAtMillis = 0L
            elapsedSeconds.value = 0L
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_STOP
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
            ACTION_START -> {
                val isRestore = intent?.getBooleanExtra(EXTRA_IS_RESTORE, false) ?: false
                val habitId = intent?.getStringExtra(EXTRA_HABIT_ID) ?: (activeHabitId.value ?: "")
                val habitName = intent?.getStringExtra(EXTRA_HABIT_NAME) ?: (activeHabitName.value ?: "")
                val mode = intent?.getStringExtra(EXTRA_TIMER_MODE) ?: activeMode.value
                val target = intent?.getLongExtra(EXTRA_TARGET_SECONDS, targetDuration.value) ?: targetDuration.value

                if (!isRestore) {
                    val isNewHabit = activeHabitId.value != habitId
                    val isNewMode = activeMode.value != mode
                    val isResuming = isPaused.value && !isNewHabit && !isNewMode

                    if (isNewHabit || isNewMode) {
                        baseElapsedSeconds = 0L
                        startedAtMillis = System.currentTimeMillis()
                        elapsedSeconds.value = 0L
                        if (isNewMode) {
                            isPomodoroBreak.value = false
                            pomodoroRound.value = 1
                        }
                    } else if (isResuming) {
                        startedAtMillis = System.currentTimeMillis()
                        elapsedSeconds.value = baseElapsedSeconds
                    } else {
                        baseElapsedSeconds = 0L
                        startedAtMillis = System.currentTimeMillis()
                        elapsedSeconds.value = 0L
                    }
                }
                
                activeHabitId.value = habitId
                activeHabitName.value = habitName
                activeMode.value = mode
                if (target > 0L) {
                    targetDuration.value = target
                }
                val wasPausedBeforeStart = isPaused.value
                isRunning.value = true
                isPaused.value = false

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
                            if (wasPausedBeforeStart && isResuming) {
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
                            } else {
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
                val now = System.currentTimeMillis()
                if (startedAtMillis > 0L) {
                    baseElapsedSeconds = computeElapsedSeconds(now)
                    startedAtMillis = 0L
                }
                elapsedSeconds.value = baseElapsedSeconds
                isRunning.value = false
                isPaused.value = true
                stopTimerCoroutine()
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
                startForegroundCompat(buildNotification())
                saveTimerState(this)
                TimerWidgetProvider.updateWidget(this)

                if (PlaylistPreferences.autoPlayWithTimer.value) {
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
                stopTimerCoroutine()
                isRunning.value = false
                isPaused.value = false
                baseElapsedSeconds = 0L
                startedAtMillis = 0L
                elapsedSeconds.value = 0L
                saveTimerState(this)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
                TimerWidgetProvider.updateWidget(this)
            }
            ACTION_STOP -> {
                stopTimerCoroutine()
                isRunning.value = false
                isPaused.value = false
                baseElapsedSeconds = 0L
                startedAtMillis = 0L
                activeHabitId.value = null
                activeHabitName.value = null
                elapsedSeconds.value = 0L
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
            while (isRunning.value) {
                delay(1000)
                if (!isRunning.value) break

                val now = System.currentTimeMillis()
                val elapsed = computeElapsedSeconds(now)
                elapsedSeconds.value = elapsed

                val currentMode = activeMode.value
                val target = targetDuration.value

                // Mode completion checks
                if (currentMode == "COUNTDOWN" && target > 0L && elapsed >= target) {
                    handleCountdownComplete(now)
                    break
                } else if (currentMode == "POMODORO" && target > 0L && elapsed >= target) {
                    handlePomodoroComplete(now)
                    break
                }

                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
                saveTimerState(this@TimerService)
                TimerWidgetProvider.updateWidget(this@TimerService)
            }
        }
    }

    private fun handleCountdownComplete(now: Long) {
        val target = targetDuration.value
        val phaseKey = "COUNTDOWN|focus|1|$startedAtMillis"
        if (lastCompletedPhaseKey != phaseKey) {
            lastCompletedPhaseKey = phaseKey
            val completedAt = if (startedAtMillis > 0L) {
                startedAtMillis + (target - baseElapsedSeconds) * 1000L
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

        isRunning.value = false
        isPaused.value = false
        elapsedSeconds.value = target
        baseElapsedSeconds = target
        startedAtMillis = 0L

        saveTimerState(this)
        TimerWidgetProvider.updateWidget(this)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun handlePomodoroComplete(now: Long) {
        val target = targetDuration.value
        val isBreak = isPomodoroBreak.value
        val round = pomodoroRound.value

        if (!isBreak) {
            // Focus complete: create pending session ONLY for focus completion!
            val phaseKey = "POMODORO|focus|$round|$startedAtMillis"
            if (lastCompletedPhaseKey != phaseKey) {
                lastCompletedPhaseKey = phaseKey
                val completedAt = if (startedAtMillis > 0L) {
                    startedAtMillis + (target - baseElapsedSeconds) * 1000L
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
            isPomodoroBreak.value = true
            targetDuration.value = if (round < 4) 5 * 60L else 15 * 60L
            elapsedSeconds.value = 0L
            baseElapsedSeconds = 0L
            startedAtMillis = 0L
            isRunning.value = false
            isPaused.value = false
        } else {
            // Break complete: NO pending session created!
            // Transition to next focus round
            isPomodoroBreak.value = false
            pomodoroRound.value = if (round >= 4) 1 else round + 1
            targetDuration.value = 25 * 60L
            elapsedSeconds.value = 0L
            baseElapsedSeconds = 0L
            startedAtMillis = 0L
            isRunning.value = false
            isPaused.value = false
        }

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
        val habitName = activeHabitName.value ?: "Focus Session"
        val seconds = elapsedSeconds.value
        val mode = activeMode.value
        val target = targetDuration.value

        val timeStr = when (mode) {
            "COUNTDOWN" -> {
                val rem = (target - seconds).coerceAtLeast(0L)
                val m = rem / 60
                val s = rem % 60
                String.format("%02d:%02d", m, s)
            }
            "POMODORO" -> {
                val rem = (target - seconds).coerceAtLeast(0L)
                val m = rem / 60
                val s = rem % 60
                val phase = if (isPomodoroBreak.value) "Break" else "Focus"
                "$phase " + String.format("%02d:%02d", m, s)
            }
            else -> {
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                val secs = seconds % 60
                if (hours > 0) {
                    String.format("%02d:%02d:%02d", hours, minutes, secs)
                } else {
                    String.format("%02d:%02d", minutes, secs)
                }
            }
        }

        val statusText = if (isPaused.value) "Paused" else if (isRunning.value) "Running" else "Ready"
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

        val pauseResumeAction = if (isPaused.value) {
            val resumeIntent = Intent(this, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HABIT_ID, activeHabitId.value)
                putExtra(EXTRA_HABIT_NAME, activeHabitName.value)
                putExtra(EXTRA_TIMER_MODE, activeMode.value)
                putExtra(EXTRA_TARGET_SECONDS, targetDuration.value)
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
            .setContentTitle("Focus: $habitName")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(isRunning.value)
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
