package com.example.util

import com.example.model.Habit
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakScheduleTest {

    private fun createHabit(
        id: String = "test_habit",
        name: String = "Workout",
        days: List<Int> = listOf(1, 4), // 0=Sun, 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat
        createdAt: String = "2026-09-14", // Monday
        completions: Set<String> = emptySet(),
        frozenDates: Set<String> = emptySet(),
        durationDays: Int? = null
    ): Habit {
        return Habit(
            id = id,
            name = name,
            category = "Fitness",
            colorHex = "#4CAF50",
            days = days,
            durationDays = durationDays,
            createdAt = createdAt,
            completions = completions,
            frozenDates = frozenDates
        )
    }

    /**
     * (a) Mon/Thu habit done on both, checked on Friday -> streak continues
     * 2026-09-14 (Mon) = DayOfWeek 1
     * 2026-09-17 (Thu) = DayOfWeek 4
     * 2026-09-18 (Fri) = DayOfWeek 5 (unscheduled)
     */
    @Test
    fun testMonThuHabit_DoneOnBoth_CheckedOnFriday_StreakContinues() {
        val habit = createHabit(
            days = listOf(1, 4),
            createdAt = "2026-09-14",
            completions = setOf("2026-09-14", "2026-09-17")
        )

        val friday = "2026-09-18" // Unscheduled day
        val streak = DateUtils.calculateStreak(habit, habit.completions, friday)
        assertEquals("Mon and Thu completed, checked on unscheduled Friday: streak must continue", 2, streak)

        val bestStreak = DateUtils.calculateLongestStreak(habit, habit.completions, friday)
        assertEquals("Best streak must be 2", 2, bestStreak)
    }

    /**
     * (b) Missed Thursday -> streak resets
     * Mon was done, Thursday was scheduled but missed. Checked on Friday.
     */
    @Test
    fun testMonThuHabit_MissedThursday_CheckedOnFriday_StreakResets() {
        val habit = createHabit(
            days = listOf(1, 4),
            createdAt = "2026-09-14",
            completions = setOf("2026-09-14") // Thu 2026-09-17 is NOT completed
        )

        val friday = "2026-09-18"
        val streak = DateUtils.calculateStreak(habit, habit.completions, friday)
        assertEquals("Missed Thursday scheduled session: current streak must reset to 0", 0, streak)

        val bestStreak = DateUtils.calculateLongestStreak(habit, habit.completions, friday)
        assertEquals("Best historic streak remains 1 from completed Monday", 1, bestStreak)
    }

    /**
     * (c) Today scheduled and not done yet -> streak preserved
     * If today is scheduled and pending, the streak is not broken yet,
     * counting from the previous scheduled day.
     */
    @Test
    fun testTodayScheduledAndNotDoneYet_StreakPreserved() {
        // Case 1: Mon/Thu habit, Mon completed, today is Thu (scheduled) not done yet
        val monThuHabit = createHabit(
            days = listOf(1, 4),
            createdAt = "2026-09-14",
            completions = setOf("2026-09-14")
        )

        val thursday = "2026-09-17" // Scheduled today, not in completions
        val streak1 = DateUtils.calculateStreak(monThuHabit, monThuHabit.completions, thursday)
        assertEquals("Today is scheduled Thursday but not done yet: previous streak must be preserved", 1, streak1)

        // Case 2: Daily habit, Tue and Wed done. Today is Thursday (scheduled), not done yet.
        val dailyHabit = createHabit(
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            createdAt = "2026-09-15",
            completions = setOf("2026-09-15", "2026-09-16")
        )
        val streak2 = DateUtils.calculateStreak(dailyHabit, dailyHabit.completions, thursday)
        assertEquals("Daily habit: Tue & Wed completed, Thu not completed yet: streak must preserve 2", 2, streak2)
    }

    /**
     * (d) Frozen day -> skipped
     * Thursday was frozen (excused rest day). Streak skips it and maintains previous completions.
     */
    @Test
    fun testFrozenDay_Skipped() {
        // Subcase 1: Mon done, Thu frozen, checked on Friday
        val habit = createHabit(
            days = listOf(1, 4),
            createdAt = "2026-09-14",
            completions = setOf("2026-09-14"),
            frozenDates = setOf("2026-09-17") // Thursday frozen
        )

        val friday = "2026-09-18"
        val streak = DateUtils.calculateStreak(habit, habit.completions, friday)
        assertEquals("Thursday was frozen: streak skips Thursday without breaking, maintaining 1", 1, streak)

        // Subcase 2: Mon 14 done, Thu 17 frozen, Mon 21 done. Checked on Tue 22.
        val habitMultiWeek = createHabit(
            days = listOf(1, 4),
            createdAt = "2026-09-14",
            completions = setOf("2026-09-14", "2026-09-21"),
            frozenDates = setOf("2026-09-17")
        )
        val tuesday = "2026-09-22"
        val streakMultiWeek = DateUtils.calculateStreak(habitMultiWeek, habitMultiWeek.completions, tuesday)
        assertEquals("Frozen Thursday connects Mon 14 and Mon 21: total streak must be 2", 2, streakMultiWeek)
    }

    @Test
    fun testCompletionRate_DenominatorOnlyIncludesScheduledDays() {
        // Mon/Thu habit over a 7 day period ending on Friday 2026-09-18
        // Days in window (2026-09-12 to 2026-09-18):
        // Sat 12 (unscheduled), Sun 13 (unscheduled)
        // Mon 14 (scheduled, done)
        // Tue 15 (unscheduled), Wed 16 (unscheduled)
        // Thu 17 (scheduled, done)
        // Fri 18 (unscheduled)
        // Total scheduled days in period = 2. Done = 2. Rate = 1.0 (100%)
        val habit = createHabit(
            days = listOf(1, 4),
            createdAt = "2026-09-14",
            completions = setOf("2026-09-14", "2026-09-17")
        )
        val rate = DateUtils.completionRate(habit, 7, today = "2026-09-18")
        assertEquals(1.0f, rate, 0.001f)
    }
}
