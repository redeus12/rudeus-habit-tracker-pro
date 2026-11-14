package com.example.service

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.HabitRepository
import com.example.model.Habit
import com.example.util.DateUtils
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimerModeSwitchTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetServiceGlobals()
        context.getSharedPreferences("TimerServicePrefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        resetServiceGlobals()
        context.getSharedPreferences("TimerServicePrefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun resetServiceGlobals() {
        TimerService.modeStates.value = TimerService.defaultModeStates()
        TimerService.activeHabitId.value = null
        TimerService.activeHabitName.value = null
        TimerService.activeMode.value = "POMODORO"
        TimerService.targetDuration.value = 25 * 60L
        TimerService.elapsedSeconds.value = 0L
        TimerService.isRunning.value = false
        TimerService.isPaused.value = false
        TimerService.pomodoroRound.value = 1
        TimerService.isPomodoroBreak.value = false
        TimerService.baseElapsedSeconds = 0L
        TimerService.startedAtMillis = 0L
        TimerService.restoredThisProcess = false
    }

    @Test
    fun switchingModesWhileStopwatchRunning_neverStopsOrLosesTime() {
        val t0 = 1_000_000L

        // 1. Select and start Stopwatch
        TimerService.applySelectMode("STOPWATCH", t0)
        TimerService.applyStartMode(
            habitId = "h_focus",
            habitName = "Deep Work",
            mode = "STOPWATCH",
            targetSeconds = 0L,
            nowMillis = t0
        )

        assertEquals("STOPWATCH", TimerService.activeMode.value)
        assertTrue(TimerService.isRunning.value)
        assertEquals(0L, TimerService.elapsedSeconds.value)

        // 2. Advance 42 seconds and switch to Pomodoro tab
        val t1 = t0 + 42_000L
        TimerService.applySelectMode("POMODORO", t1)
        assertEquals("POMODORO", TimerService.activeMode.value)
        // Pomodoro itself is idle
        assertFalse(TimerService.isRunning.value)
        assertEquals(0L, TimerService.elapsedSeconds.value)
        // Stopwatch is still running in the background with 42s elapsed
        val stopwatchAtT1 = TimerService.getModeState("STOPWATCH")
        assertTrue(stopwatchAtT1.isRunning)
        assertEquals(42L, stopwatchAtT1.computeElapsedSeconds(t1))

        // 3. Advance to 95 seconds and switch to Countdown tab
        val t2 = t0 + 95_000L
        TimerService.applySelectMode("COUNTDOWN", t2)
        assertEquals("COUNTDOWN", TimerService.activeMode.value)
        assertTrue(TimerService.getModeState("STOPWATCH").isRunning)
        assertEquals(95L, TimerService.getModeState("STOPWATCH").computeElapsedSeconds(t2))

        // 4. Advance to 135 seconds and switch back to Stopwatch tab
        val t3 = t0 + 135_000L
        TimerService.applySelectMode("STOPWATCH", t3)
        assertEquals("STOPWATCH", TimerService.activeMode.value)
        assertTrue(TimerService.isRunning.value)
        assertFalse(TimerService.isPaused.value)
        assertEquals(135L, TimerService.elapsedSeconds.value)
        assertEquals(135L, TimerService.computeElapsedSeconds(t3))
    }

    @Test
    fun independentPerModeState_startingOrPausingAnotherModeDoesNotResetStopwatch() {
        val t0 = 2_000_000L

        // Start Stopwatch at t0
        TimerService.applyStartMode(
            habitId = "h_study",
            habitName = "Study Kotlin",
            mode = "STOPWATCH",
            targetSeconds = 0L,
            nowMillis = t0
        )

        // At t0 + 60s, switch to Pomodoro and start Pomodoro
        val t1 = t0 + 60_000L
        TimerService.applySelectMode("POMODORO", t1)
        TimerService.applyStartMode(
            habitId = "h_study",
            habitName = "Study Kotlin",
            mode = "POMODORO",
            targetSeconds = 25 * 60L,
            nowMillis = t1
        )

        // At t0 + 90s (Pomodoro elapsed = 30s, Stopwatch elapsed = 90s), pause Pomodoro
        val t2 = t0 + 90_000L
        TimerService.applyPauseMode("POMODORO", t2)
        assertEquals(30L, TimerService.getModeState("POMODORO").computeElapsedSeconds(t2))
        assertTrue(TimerService.getModeState("POMODORO").isPaused)

        // Stopwatch is still running untouched at 90s
        val swState = TimerService.getModeState("STOPWATCH")
        assertTrue(swState.isRunning)
        assertFalse(swState.isPaused)
        assertEquals(90L, swState.computeElapsedSeconds(t2))

        // Switch back to Stopwatch at t0 + 110s -> shows 110s
        val t3 = t0 + 110_000L
        TimerService.applySelectMode("STOPWATCH", t3)
        assertTrue(TimerService.isRunning.value)
        assertEquals(110L, TimerService.elapsedSeconds.value)
    }

    @Test
    fun resetDoesNotSaveSession_stopSavesAccurateSessionDurationToLedger() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = HabitRepository(
                habitDao = db.habitDao(),
                taskDao = db.taskDao(),
                habitSubtaskDao = db.habitSubtaskDao(),
                timeSessionDao = db.timeSessionDao(),
                habitCompletionDao = db.habitCompletionDao(),
                habitFreezeDao = db.habitFreezeDao(),
                db = db
            )
            val today = DateUtils.todayStr()
            val habitId = "h_coding"
            repository.insertHabit(
                Habit(
                    id = habitId,
                    name = "Coding Session",
                    category = "Work",
                    colorHex = "#34A853",
                    days = listOf(0, 1, 2, 3, 4, 5, 6),
                    
                durationDays = null,

                createdAt = today,
                    
                completions = emptySet(),

                trackTimeSpent = true
                )
            )

            val t0 = 5_000_000L
            TimerService.applyStartMode(habitId, "Coding Session", "STOPWATCH", 0L, t0)

            // Run for 75s then Reset -> must reset to 0 and NOT save a session
            val tReset = t0 + 75_000L
            TimerService.applyResetMode("STOPWATCH", tReset)
            assertEquals(0L, TimerService.elapsedSeconds.value)
            assertFalse(TimerService.isRunning.value)
            assertNull(db.timeSessionDao().getSession(habitId, today))

            // Start again at t1, switch modes while running, then Stop after 125s and save session
            val t1 = t0 + 80_000L
            TimerService.applyStartMode(habitId, "Coding Session", "STOPWATCH", 0L, t1)
            TimerService.applySelectMode("POMODORO", t1 + 50_000L)
            TimerService.applySelectMode("STOPWATCH", t1 + 125_000L)

            val stoppedDuration = TimerService.applyStopMode("STOPWATCH", t1 + 125_000L)
            assertEquals(125L, stoppedDuration)
            assertFalse(TimerService.isRunning.value)
            assertEquals(0L, TimerService.elapsedSeconds.value)

            repository.insertOrAccumulateSession(habitId, today, stoppedDuration)
            val savedEntity = db.timeSessionDao().getSession(habitId, today)
            assertNotNull(savedEntity)
            assertEquals(125L, savedEntity?.durationSeconds)
        } finally {
            db.close()
        }
    }

    @Test
    fun saveAndRestoreTimerState_preservesSelectedModeAndRunningStopwatch() {
        val now = System.currentTimeMillis()
        val started55sAgo = now - 55_000L

        TimerService.applySelectMode("STOPWATCH", started55sAgo)
        TimerService.applyStartMode(
            habitId = "h_read",
            habitName = "Reading",
            mode = "STOPWATCH",
            targetSeconds = 0L,
            nowMillis = started55sAgo
        )
        TimerService.saveTimerState(context)

        // Simulate process recreation
        resetServiceGlobals()
        assertEquals("POMODORO", TimerService.activeMode.value)
        assertFalse(TimerService.isRunning.value)

        TimerService.restoreTimerState(context)

        assertEquals("STOPWATCH", TimerService.activeMode.value)
        assertTrue(TimerService.isRunning.value)
        assertTrue("Expected at least 54s elapsed after restore, got ${TimerService.elapsedSeconds.value}", TimerService.elapsedSeconds.value >= 54L)
    }
}
