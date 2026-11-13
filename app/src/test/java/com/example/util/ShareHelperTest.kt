package com.example.util

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.example.model.Habit
import com.example.model.HabitWeekTrend
import com.example.ui.HabitWithAnalytics
import com.example.ui.components.computeDayReportData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareHelperTest {

    @Test
    fun testCurrentStreakAsOf() {
        val habit = Habit(
            id = "test_1",
            name = "Morning Meditation",
            category = "Mindfulness",
            colorHex = "#046A38",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = "2026-09-01",
            completions = setOf("2026-09-10", "2026-09-11", "2026-09-12")
        )

        val streakOn12 = DateUtils.currentStreakAsOf(habit, "2026-09-12")
        assertEquals(3, streakOn12)

        val streakOn13 = DateUtils.currentStreakAsOf(habit, "2026-09-13")
        // On 13, today is incomplete but streak is not broken yet (currentStreak logic counts yesterday)
        assertEquals(3, streakOn13)
    }

    @Test
    fun testGenerateHabitsCurvesBitmap() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        val habit1 = Habit(
            id = "h1",
            name = "Morning Reading 📚",
            category = "study",
            colorHex = "#2563EB",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = "2026-08-01",
            completions = (1..20).map { String.format("2026-09-%02d", it) }.toSet()
        )
        val habit2 = Habit(
            id = "h2",
            name = "رياضة الصباح",
            category = "health",
            colorHex = "#10B981",
            days = listOf(1, 2, 3, 4, 5),
            durationDays = null,
            createdAt = "2026-08-15",
            completions = setOf("2026-09-01", "2026-09-02", "2026-09-03")
        )

        val analyticsList = listOf(
            HabitWithAnalytics(
                habit = habit1,
                currentStreak = 20,
                longestStreak = 25,
                durationInfo = null,
                daysText = "Every day",
                completionRate30d = 0.85f,
                weeklyTimeString = null,
                isScheduledToday = true,
                isDoneToday = true,
                isFrozenToday = false,
                progressCurve = listOf(
                    HabitWeekTrend(1, "W1", "2026-09-01", "2026-09-07", 0.71f, 7, 5),
                    HabitWeekTrend(2, "W2", "2026-09-08", "2026-09-14", 0.85f, 7, 6),
                    HabitWeekTrend(3, "W3", "2026-09-15", "2026-09-21", 1.0f, 7, 7)
                )
            ),
            HabitWithAnalytics(
                habit = habit2,
                currentStreak = 3,
                longestStreak = 10,
                durationInfo = null,
                daysText = "Mon-Fri",
                completionRate30d = 0.60f,
                weeklyTimeString = null,
                isScheduledToday = true,
                isDoneToday = false,
                isFrozenToday = false,
                progressCurve = listOf(
                    HabitWeekTrend(1, "W1", "2026-09-01", "2026-09-07", 0.60f, 5, 3),
                    HabitWeekTrend(2, "W2", "2026-09-08", "2026-09-14", 0.60f, 5, 3)
                )
            )
        )

        val bitmap = ShareHelper.generateHabitsCurvesBitmap(activity, analyticsList, showFooter = true)
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertTrue("Height should be >= 720, but was ${bitmap.height}", bitmap.height >= 720)
        assertTrue("Height should be <= 4096, but was ${bitmap.height}", bitmap.height <= 4096)
    }

    @Test
    fun testGenerateHabitsCurvesBitmap_FiveHabitsOddCountWithLongNames() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        val longNames = listOf(
            "Extra Long Habit Name That Exceeds Twenty-Five Characters 🚀",
            "Continuous Mindfulness And Meditation For Focus And Clarity",
            "Intense Daily Cardio And Core Strength Training",
            "Deep Work Session Focused On Software Engineering",
            "Read 30 Pages Of Technical Literature Daily"
        )

        val analyticsList = (1..5).map { i ->
            val habit = Habit(
                id = "h$i",
                name = longNames[i - 1],
                category = "productivity",
                colorHex = if (i % 2 == 0) "#E11D48" else "#2563EB",
                days = listOf(0, 1, 2, 3, 4, 5, 6),
                durationDays = null,
                createdAt = "2026-08-01",
                completions = (1..15).map { day -> String.format("2026-09-%02d", day) }.toSet()
            )
            HabitWithAnalytics(
                habit = habit,
                currentStreak = i * 4,
                longestStreak = 30,
                durationInfo = null,
                daysText = "Every day",
                completionRate30d = 0.75f,
                weeklyTimeString = null,
                isScheduledToday = true,
                isDoneToday = true,
                isFrozenToday = false,
                progressCurve = listOf(
                    HabitWeekTrend(1, "W1", "2026-08-24", "2026-08-30", 0.70f, 7, 5),
                    HabitWeekTrend(2, "W2", "2026-08-31", "2026-09-06", 0.85f, 7, 6),
                    HabitWeekTrend(3, "W3", "2026-09-07", "2026-09-13", 0.90f, 7, 6),
                    HabitWeekTrend(4, "W4", "2026-09-14", "2026-09-20", 1.00f, 7, 7)
                )
            )
        }

        val bitmap = ShareHelper.generateHabitsCurvesBitmap(activity, analyticsList, showFooter = true)
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertTrue("Height should be valid: ${bitmap.height}", bitmap.height in 720..4096)
    }

    @Test
    fun testGenerateHabitsCurvesBitmap_SixHabitsEvenCount() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        val analyticsList = (1..6).map { i ->
            val habit = Habit(
                id = "h$i",
                name = "Habit Number $i Consistency & Discipline",
                category = "health",
                colorHex = when (i % 3) {
                    0 -> "#10B981"
                    1 -> "#F59E0B"
                    else -> "#8B5CF6"
                },
                days = listOf(0, 1, 2, 3, 4, 5, 6),
                durationDays = null,
                createdAt = "2026-08-01",
                completions = (1..20).map { day -> String.format("2026-09-%02d", day) }.toSet()
            )
            HabitWithAnalytics(
                habit = habit,
                currentStreak = i * 3,
                longestStreak = 25,
                durationInfo = null,
                daysText = "Every day",
                completionRate30d = 0.80f,
                weeklyTimeString = null,
                isScheduledToday = true,
                isDoneToday = false,
                isFrozenToday = false,
                progressCurve = listOf(
                    HabitWeekTrend(1, "W1", "2026-08-24", "2026-08-30", 0.60f, 7, 4),
                    HabitWeekTrend(2, "W2", "2026-08-31", "2026-09-06", 0.75f, 7, 5),
                    HabitWeekTrend(3, "W3", "2026-09-07", "2026-09-13", 0.85f, 7, 6),
                    HabitWeekTrend(4, "W4", "2026-09-14", "2026-09-20", 0.95f, 7, 7)
                )
            )
        }

        val bitmap = ShareHelper.generateHabitsCurvesBitmap(activity, analyticsList, showFooter = true)
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertTrue("Height should be valid: ${bitmap.height}", bitmap.height in 720..4096)
    }

    @Test
    fun testComputeDayReportData_PercentageAndCounts() {
        val testDate = "2026-09-21" // Monday (dayOfWeek = 1 in our 0=Sun..6=Sat mapping)
        val h1 = Habit(
            id = "h1",
            name = "Morning Workout",
            category = "Health",
            colorHex = "#10B981",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = "2026-09-01",
            completions = setOf("2026-09-21")
        )
        val h2 = Habit(
            id = "h2",
            name = "Read 30 mins",
            category = "Learning",
            colorHex = "#3B82F6",
            days = listOf(1, 2, 3, 4, 5),
            durationDays = null,
            createdAt = "2026-09-01",
            completions = setOf("2026-09-21")
        )
        val h3 = Habit(
            id = "h3",
            name = "Meditate",
            category = "Mindfulness",
            colorHex = "#8B5CF6",
            days = listOf(1, 3, 5),
            durationDays = null,
            createdAt = "2026-09-01",
            completions = emptySet()
        )

        val reportData = computeDayReportData(testDate, listOf(h1, h2, h3), java.util.Locale.US)
        assertEquals(3, reportData.scheduledCount)
        assertEquals(2, reportData.completedCount)
        assertEquals(67, reportData.percentage) // 2/3 = 66.66% -> 67%
        assertEquals(3, reportData.allScheduledHabits.size)
        assertTrue(DateUtils.isDone(reportData.allScheduledHabits[0], testDate))
        assertTrue(DateUtils.isDone(reportData.allScheduledHabits[1], testDate))
        assertEquals(false, DateUtils.isDone(reportData.allScheduledHabits[2], testDate))
    }

    @Test
    fun testComputeDayReportData_FrozenHabitsExcluded() {
        val testDate = "2026-09-21"
        val h1 = Habit(
            id = "h1",
            name = "Active Habit 1",
            category = "Health",
            colorHex = "#10B981",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = "2026-09-01",
            completions = setOf("2026-09-21")
        )
        val h2 = Habit(
            id = "h2",
            name = "Active Habit 2",
            category = "Work",
            colorHex = "#3B82F6",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = "2026-09-01",
            completions = emptySet()
        )
        val h3Frozen = Habit(
            id = "h3",
            name = "Frozen Habit",
            category = "Health",
            colorHex = "#F59E0B",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = "2026-09-01",
            completions = emptySet(),
            frozenDates = setOf("2026-09-21")
        )

        val reportData = computeDayReportData(testDate, listOf(h1, h2, h3Frozen), java.util.Locale.US)
        // Frozen habits must be excluded from counts and percentage
        assertEquals(2, reportData.scheduledCount)
        assertEquals(1, reportData.completedCount)
        assertEquals(50, reportData.percentage)
        assertEquals(3, reportData.allScheduledHabits.size) // still listed in row list
        assertEquals(2, reportData.activeScheduledHabits.size) // excluded from active count
    }

    @Test
    fun testComputeDayReportData_EmptyState() {
        val testDate = "2026-09-21"
        val reportData = computeDayReportData(testDate, emptyList(), java.util.Locale.US)
        assertEquals(0, reportData.scheduledCount)
        assertEquals(0, reportData.completedCount)
        assertEquals(0, reportData.percentage)
        assertTrue(reportData.allScheduledHabits.isEmpty())
        assertTrue(reportData.activeScheduledHabits.isEmpty())
    }

    @Test
    fun testComputeDayReportData_RowLimits() {
        val testDate = "2026-09-21"
        val habits = (1..8).map { i ->
            Habit(
                id = "h$i",
                name = "Habit $i",
                category = "Category $i",
                colorHex = "#10B981",
                days = listOf(0, 1, 2, 3, 4, 5, 6),
                durationDays = null,
                createdAt = "2026-09-01",
                completions = if (i <= 3) setOf("2026-09-21") else emptySet()
            )
        }

        val reportData = computeDayReportData(testDate, habits, java.util.Locale.US)
        assertEquals(8, reportData.scheduledCount)
        assertEquals(3, reportData.completedCount)
        assertEquals(8, reportData.allScheduledHabits.size)
        val displayHabits = reportData.allScheduledHabits.take(7)
        val overflowCount = reportData.allScheduledHabits.size - displayHabits.size
        assertEquals(7, displayHabits.size)
        assertEquals(1, overflowCount)
    }

    @Test
    fun testComputeDayReportData_DateFormatting() {
        val testDate = "2026-09-21"
        val reportData = computeDayReportData(testDate, emptyList(), java.util.Locale.US)
        assertEquals("Mon, Sep 21, 2026", reportData.pillDate)
        assertEquals("Monday, September 21, 2026", reportData.fullDate)
    }

    @Test
    fun testGenerateDailyReportBitmap_DimensionsAndIntegrity() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val testDate = "2026-09-21"
        val habit = Habit(
            id = "h1",
            name = "Daily Meditation",
            category = "Mindfulness",
            colorHex = "#046A38",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = "2026-09-01",
            completions = setOf("2026-09-21")
        )

        val bitmap = ShareHelper.generateDailyReportBitmap(activity, testDate, listOf(habit), showFooter = true)
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
    }

    @Test
    fun testSingleHabitStreak_ScheduledSkipping() {
        // Mon = 1, Thu = 4. Mon 2026-09-14, Thu 2026-09-17. Fri 2026-09-18 (unscheduled)
        val habitDoneBoth = Habit(
            id = "h_streak",
            name = "Weekly Sync",
            category = "work",
            colorHex = "#2563EB",
            days = listOf(1, 4),
            durationDays = null,
            createdAt = "2026-09-01",
            completions = setOf("2026-09-14", "2026-09-17")
        )

        // Checked on Friday (unscheduled): keeps streak of 2
        val streakOnFri = DateUtils.calculateStreak(habitDoneBoth, today = "2026-09-18")
        assertEquals(2, streakOnFri)

        // If Thursday was missed, checked on Friday: streak resets to 0
        val habitMissedThu = habitDoneBoth.copy(completions = setOf("2026-09-14"))
        val streakMissedThu = DateUtils.calculateStreak(habitMissedThu, today = "2026-09-18")
        assertEquals(0, streakMissedThu)
    }

    @Test
    fun testSingleHabit_30DayAccuracyRounding() {
        // Scheduled every day for 3 days: 18 (done), 19 (missed), 20 (done today) -> 2 out of 3 -> 67%
        val habit = Habit(
            id = "h_acc",
            name = "Guitar",
            category = "art",
            colorHex = "#10B981",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = "2026-09-18",
            completions = setOf("2026-09-18", "2026-09-20")
        )
        val today = "2026-09-20" // 3 days: 18, 19, 20. 2 done out of 3 -> 66.67% -> 67%
        val rate = DateUtils.completionRate(habit, 30, today = today)
        val roundedPct = kotlin.math.round(rate * 100f).toInt()
        assertEquals(67, roundedPct)
    }

    @Test
    fun testSingleHabit_MomentumTierThresholds() {
        val tier0 = com.example.ui.components.getMomentumTier(0)
        assertEquals("GETTING STARTED", tier0.tierName)

        val tier39 = com.example.ui.components.getMomentumTier(39)
        assertEquals("GETTING STARTED", tier39.tierName)

        val tier40 = com.example.ui.components.getMomentumTier(40)
        assertEquals("BUILDING HABIT", tier40.tierName)

        val tier59 = com.example.ui.components.getMomentumTier(59)
        assertEquals("BUILDING HABIT", tier59.tierName)

        val tier60 = com.example.ui.components.getMomentumTier(60)
        assertEquals("STRONG MOMENTUM", tier60.tierName)

        val tier79 = com.example.ui.components.getMomentumTier(79)
        assertEquals("STRONG MOMENTUM", tier79.tierName)

        val tier80 = com.example.ui.components.getMomentumTier(80)
        assertEquals("ELITE CONSISTENCY", tier80.tierName)

        val tier100 = com.example.ui.components.getMomentumTier(100)
        assertEquals("ELITE CONSISTENCY", tier100.tierName)
    }

    @Test
    fun testBuildChartBuckets_GranularityAndPartial() {
        val today = "2026-09-20"

        // History < 28 days -> DAY granularity
        val habitDay = Habit(
            id = "h_day",
            name = "Drink Water",
            category = "health",
            colorHex = "#03A9F4",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = "2026-09-05", // 15 days ago (< 28 days)
            completions = setOf("2026-09-18", "2026-09-19")
        )
        val (granDay, bucketsDay) = DateUtils.buildChartBuckets(habitDay, today = today)
        assertEquals(com.example.model.ChartGranularity.DAY, granDay)
        assertTrue(bucketsDay.isNotEmpty())

        // 6 weeks of history (between 28 days and 12 weeks) -> WEEK granularity
        val habit6w = Habit(
            id = "h_6w",
            name = "Drink Water",
            category = "health",
            colorHex = "#03A9F4",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = "2026-08-05", // ~46 days ago
            completions = setOf("2026-09-18", "2026-09-19")
        )
        val (gran6w, buckets6w) = DateUtils.buildChartBuckets(habit6w, today = today)
        assertEquals(com.example.model.ChartGranularity.WEEK, gran6w)
        assertTrue(buckets6w.isNotEmpty())

        // 20 weeks of history: > 12 weeks -> MONTH granularity
        val habit20w = Habit(
            id = "h_20w",
            name = "Gym",
            category = "fitness",
            colorHex = "#EF4444",
            days = listOf(1, 3, 5),
            durationDays = null,
            createdAt = "2026-05-01", // ~20 weeks ago
            completions = setOf("2026-09-18")
        )
        val (gran20w, buckets20w) = DateUtils.buildChartBuckets(habit20w, today = today)
        assertEquals(com.example.model.ChartGranularity.MONTH, gran20w)
        assertTrue(buckets20w.isNotEmpty())
        // Current month (September 2026, today is 20th < 30) is partial
        val lastMonthBucket = buckets20w.last()
        assertTrue(lastMonthBucket.isPartial)
    }

    @Test
    fun testComputeSingleHabitShareData_PerformanceMatchesLastBucket() {
        val today = "2026-09-20"
        val habit = Habit(
            id = "h_match",
            name = "Reading",
            category = "study",
            colorHex = "#2563EB",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = "2026-09-01",
            completions = setOf("2026-09-15", "2026-09-16", "2026-09-17")
        )
        val data = com.example.ui.components.computeSingleHabitShareData(habit, todayStr = today)
        assertNotNull(data.latestBucket)
        val expectedPct = Math.round(data.latestBucket!!.rate * 100f)
        assertEquals(expectedPct, data.performancePercentage)
        assertEquals(data.momentumInfo.tierName, com.example.ui.components.getMomentumTier(data.performancePercentage).tierName)
    }

    @Test
    fun testGenerateSingleHabitBitmap_DimensionsAndIntegrity() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val habit = Habit(
            id = "h_single_render",
            name = "Morning Yoga",
            category = "Mindfulness",
            colorHex = "#046A38",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = "2026-09-01",
            completions = setOf("2026-09-10", "2026-09-11", "2026-09-12")
        )

        val bitmap = ShareHelper.generateSingleHabitBitmap(activity, habit, showFooter = true)
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
    }

    @Test
    fun testGenerateSingleHabitBitmap_SingleBucket() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        // Habit created today, only 1 week/bucket exists
        val today = DateUtils.todayStr()
        val habit = Habit(
            id = "h_single_bucket",
            name = "Quick Habit",
            category = "General",
            colorHex = "#3B82F6",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = today,
            completions = setOf(today)
        )

        val data = com.example.ui.components.computeSingleHabitShareData(habit, todayStr = today)
        assertEquals(1, data.buckets.size)

        val bitmap = ShareHelper.generateSingleHabitBitmap(activity, habit, showFooter = true)
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
    }

    @Test
    fun testGenerateSingleHabitBitmap_LongName30PlusChars() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val habit = Habit(
            id = "h_long_name",
            name = "Drink 3 Liters of Water and Stretch Every Single Day",
            category = "Health & Wellness",
            colorHex = "#10B981",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            reminderTime = "08:30 AM",
            createdAt = "2026-08-01",
            completions = setOf("2026-09-18", "2026-09-19", "2026-09-20")
        )

        assertTrue(habit.name.length >= 30)

        val bitmap = ShareHelper.generateSingleHabitBitmap(activity, habit, showFooter = true)
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
    }

    @Test
    fun testGenerateSingleHabitBitmap_ArabicName() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val habit = Habit(
            id = "h_arabic_name",
            name = "قراءة القرآن الكريم والأذكار اليومية",
            category = "روحانيات",
            colorHex = "#046A38",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = "2026-08-15",
            completions = setOf("2026-09-18", "2026-09-19", "2026-09-20")
        )

        val bitmap = ShareHelper.generateSingleHabitBitmap(activity, habit, showFooter = true)
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
    }

    @Test
    fun testBuildChartBuckets_DayGranularity() {
        // Habit created 5 days ago (< 28 days) -> should produce ChartGranularity.DAY
        val habit = Habit(
            id = "h_5days",
            name = "Morning Jog",
            category = "Fitness",
            colorHex = "#3B82F6",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = "2026-09-15",
            completions = setOf("2026-09-15", "2026-09-16", "2026-09-18", "2026-09-20")
        )

        val (granularity, buckets) = DateUtils.buildChartBuckets(habit, today = "2026-09-20")
        assertEquals(com.example.model.ChartGranularity.DAY, granularity)
        assertEquals(6, buckets.size) // Sep 15, 16, 17, 18, 19, 20

        // Running completion rate check:
        // Day 1 (15th): 1/1 = 1.0f
        assertEquals(1.0f, buckets[0].rate, 0.001f)
        // Day 2 (16th): 2/2 = 1.0f
        assertEquals(1.0f, buckets[1].rate, 0.001f)
        // Day 3 (17th): 2/3 = 0.667f
        assertEquals(2f / 3f, buckets[2].rate, 0.001f)
        // Day 4 (18th): 3/4 = 0.75f
        assertEquals(3f / 4f, buckets[3].rate, 0.001f)
        // Day 5 (19th): 3/5 = 0.60f
        assertEquals(3f / 5f, buckets[4].rate, 0.001f)
        // Day 6 (20th): 4/6 = 0.667f
        assertEquals(4f / 6f, buckets[5].rate, 0.001f)
    }

    @Test
    fun testTrendsChartBucketMatchShareCardBuckets() {
        val habit5Days = Habit(
            id = "h_5days",
            name = "Morning Jog",
            category = "Fitness",
            colorHex = "#3B82F6",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = "2026-09-15",
            completions = setOf("2026-09-15", "2026-09-16", "2026-09-18", "2026-09-20")
        )

        val today = "2026-09-20"
        val (granularity, buckets) = DateUtils.buildChartBuckets(habit5Days, today = today)
        assertEquals(com.example.model.ChartGranularity.DAY, granularity)
        assertEquals(6, buckets.size)

        val lastBucket = buckets.last()
        assertEquals(4, lastBucket.doneDays)
        assertEquals(6, lastBucket.scheduledDays)
        assertEquals(4f / 6f, lastBucket.rate, 0.001f)
        assertTrue(lastBucket.label.isNotEmpty())
        // Label should not be an ISO string (no 2026-09-20)
        assertTrue(!lastBucket.label.contains("2026-"))
        assertTrue(!lastBucket.fullRangeLabel.contains("2026-09-20"))

        val olderHabit = Habit(
            id = "h_older",
            name = "Read Books",
            category = "Education",
            colorHex = "#8B5CF6",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            durationDays = null,
            createdAt = "2026-07-01",
            completions = (1..50).map { String.format("2026-07-%02d", ((it % 28) + 1)) }.toSet()
        )

        val (oldGranularity, oldBuckets) = DateUtils.buildChartBuckets(olderHabit, today = today)
        assertTrue(oldGranularity == com.example.model.ChartGranularity.WEEK || oldGranularity == com.example.model.ChartGranularity.MONTH)
        assertTrue(oldBuckets.isNotEmpty())
        oldBuckets.forEach { bucket ->
            assertTrue(!bucket.label.startsWith("W")) // Not W1, W2, etc.
            assertTrue(!bucket.label.contains("2026-09-")) // Not ISO string
        }
    }
}
