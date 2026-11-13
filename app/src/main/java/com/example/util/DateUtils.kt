package com.example.util

import com.example.model.CategoryPerformance
import com.example.model.ChartBucket
import com.example.model.ChartGranularity
import com.example.model.DurationInfo
import com.example.model.Habit
import com.example.model.HabitWeekTrend
import com.example.model.TrendPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.DayOfWeek
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object DateUtils {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val dateCache = java.util.concurrent.ConcurrentHashMap<String, LocalDate>()

    private fun parseDate(dateStr: String): LocalDate {
        return dateCache.computeIfAbsent(dateStr) {
            try {
                LocalDate.parse(it, formatter)
            } catch (e: Exception) {
                LocalDate.now()
            }
        }
    }

    private fun formatDate(date: LocalDate): String {
        return date.format(formatter)
    }

    fun todayStr(): String = formatDate(LocalDate.now())

    fun addDays(dateStr: String, n: Long): String {
        return try {
            val date = parseDate(dateStr)
            formatDate(date.plusDays(n))
        } catch (e: Exception) {
            todayStr()
        }
    }

    fun daysBetween(a: String, b: String): Long {
        return try {
            val dateA = parseDate(a)
            val dateB = parseDate(b)
            dateB.toEpochDay() - dateA.toEpochDay()
        } catch (e: Exception) {
            0L
        }
    }

    fun dayOfWeek(dateStr: String): Int {
        return try {
            val date = parseDate(dateStr)
            // DayOfWeek value: Monday is 1, Sunday is 7.
            // We want 0=Sun..6=Sat.
            val value = date.dayOfWeek.value
            if (value == 7) 0 else value
        } catch (e: Exception) {
            0
        }
    }

    fun lastNDates(n: Int): List<String> {
        val out = ArrayList<String>(n)
        val today = LocalDate.now()
        for (i in n - 1 downTo 0) {
            out.add(formatDate(today.minusDays(i.toLong())))
        }
        return out
    }

    fun isScheduled(habit: Habit, dateStr: String): Boolean {
        if (dateStr < habit.createdAt) return false
        val dow = dayOfWeek(dateStr) // 0=Sun..6=Sat
        if (!habit.days.contains(dow)) return false
        if (habit.durationDays != null) {
            val diff = daysBetween(habit.createdAt, dateStr)
            if (diff >= habit.durationDays) return false
        }
        return true
    }

    fun isDone(habit: Habit, dateStr: String): Boolean {
        return habit.completions.contains(dateStr)
    }

    fun isFrozen(habit: Habit, dateStr: String): Boolean {
        return habit.frozenDates.contains(dateStr)
    }

    fun isMissed(habit: Habit, dateStr: String): Boolean {
        return isScheduled(habit, dateStr) && !isDone(habit, dateStr) && !isFrozen(habit, dateStr) && dateStr < todayStr()
    }

    /**
     * Calculates the streak for a habit respecting its schedule.
     *
     * Rules:
     * 1. A day counts ONLY if the habit is SCHEDULED on that day (habit.days, durationDays, createdAt).
     * 2. Unscheduled days are SKIPPED completely: they never break the streak and never add to it.
     * 3. Current streak = number of consecutive SCHEDULED days completed, counting backwards from today.
     *    Walk backwards through the calendar, skip unscheduled days, stop at first scheduled day that was missed.
     * 4. If today is scheduled but not yet completed, do not break the streak yet: start counting from
     *    the previous scheduled day. It breaks only after today ends without completion.
     * 5. Days where the habit was frozen (Freeze) or marked as rest are also skipped, not counted as missed.
     * 6. Habits created after a date: days before the creation date are ignored.
     */
    fun calculateStreak(
        habit: Habit,
        logs: Set<String> = habit.completions,
        today: String = todayStr(),
        frozenDates: Set<String> = habit.frozenDates
    ): Int {
        var d = today
        var count = 0
        while (d >= habit.createdAt) {
            if (isScheduled(habit, d)) {
                if (logs.contains(d)) {
                    count++
                } else if (frozenDates.contains(d)) {
                    // Frozen / excused rest day: skipped, does not break streak, does not add to it
                } else if (d == today) {
                    // Today is scheduled but not yet completed: do not break yet,
                    // continue checking previous scheduled days
                } else {
                    // First scheduled day that was missed: streak resets
                    break
                }
            }
            d = addDays(d, -1)
        }
        return count
    }

    /**
     * Calculates the longest (best) consecutive streak of scheduled days completed.
     * Follows the same rule as calculateStreak.
     */
    fun calculateLongestStreak(
        habit: Habit,
        logs: Set<String> = habit.completions,
        today: String = todayStr(),
        frozenDates: Set<String> = habit.frozenDates
    ): Int {
        var end = if (habit.durationDays != null) {
            addDays(habit.createdAt, (habit.durationDays - 1).toLong())
        } else {
            today
        }
        if (end > today) end = today
        if (habit.createdAt > end) return 0

        var d = habit.createdAt
        var maxStreak = 0
        var currentRun = 0

        while (d <= end) {
            if (isScheduled(habit, d)) {
                if (logs.contains(d)) {
                    currentRun++
                    if (currentRun > maxStreak) {
                        maxStreak = currentRun
                    }
                } else if (frozenDates.contains(d)) {
                    // Excused rest / frozen day preserves current streak without resetting or incrementing
                } else if (d == today) {
                    // Today is pending, does not break current streak yet
                } else {
                    // Missed scheduled day
                    currentRun = 0
                }
            }
            d = addDays(d, 1)
        }

        return max(maxStreak, calculateStreak(habit, logs, today, frozenDates))
    }

    fun currentStreakAsOf(
        habit: Habit,
        asOfDate: String,
        logs: Set<String> = habit.completions,
        frozenDates: Set<String> = habit.frozenDates
    ): Int = calculateStreak(habit, logs, asOfDate, frozenDates)

    fun currentStreak(habit: Habit): Int = currentStreakAsOf(habit, todayStr())

    fun longestStreak(habit: Habit): Int = calculateLongestStreak(habit, habit.completions, todayStr(), habit.frozenDates)

    fun completionRate(habit: Habit, periodDays: Int, today: String = todayStr()): Float {
        val since = addDays(today, -(periodDays - 1).toLong())
        val start = if (since > habit.createdAt) since else habit.createdAt
        var end = today
        if (habit.durationDays != null) {
            val durEnd = addDays(habit.createdAt, (habit.durationDays - 1).toLong())
            if (durEnd < end) end = durEnd
        }
        if (start > end) return 0f
        var total = 0
        var done = 0
        var d = start
        while (d <= end) {
            if (isScheduled(habit, d)) {
                val isTodayPending = (d == today && !isDone(habit, d) && !isFrozen(habit, d))
                if (!isTodayPending) {
                    if (!isFrozen(habit, d)) {
                        total++
                        if (isDone(habit, d)) done++
                    } else if (isDone(habit, d)) {
                        total++
                        done++
                    }
                }
            }
            d = addDays(d, 1)
        }
        return if (total > 0) done.toFloat() / total else 0f
    }

    fun overallCompletionToday(habits: List<Habit>): Pair<Int, Int> {
        val t = todayStr()
        val scheduled = habits.filter { isScheduled(it, t) }
        if (scheduled.isEmpty()) return Pair(0, 0)
        val done = scheduled.count { isDone(it, t) }
        return Pair(done, scheduled.size)
    }

    fun overallRate(habits: List<Habit>, periodDays: Int): Float {
        if (habits.isEmpty()) return 0f
        var sum = 0f
        habits.forEach { sum += completionRate(it, periodDays) }
        return sum / habits.size
    }

    fun bestStreakHabit(habits: List<Habit>): Pair<String, Int> {
        var bestName = "—"
        var maxStreak = 0
        habits.forEach { h ->
            val s = currentStreak(h)
            if (s > maxStreak) {
                maxStreak = s
                bestName = h.name
            }
        }
        return Pair(bestName, maxStreak)
    }

    fun categoryPerformance(habits: List<Habit>): List<CategoryPerformance> {
        val map = mutableMapOf<String, MutableList<Habit>>()
        habits.forEach { h ->
            map.getOrPut(h.category) { mutableListOf() }.add(h)
        }
        return map.map { (cat, list) ->
            val rate = list.map { completionRate(it, 30) }.average().toFloat()
            CategoryPerformance(category = cat, rate = rate, color = list.first().color)
        }.sortedByDescending { it.rate }
    }

    fun weeklyTrend(habits: List<Habit>, weeks: Int): List<TrendPoint> {
        val out = mutableListOf<TrendPoint>()
        for (w in weeks - 1 downTo 0) {
            val weekStart = addDays(todayStr(), -(w * 7 + 6).toLong())
            var total = 0
            var done = 0
            for (i in 0..6) {
                val d = addDays(weekStart, i.toLong())
                if (d > todayStr()) continue
                habits.forEach { h ->
                    if (isScheduled(h, d)) {
                        if (!isFrozen(h, d)) {
                            total++
                            if (isDone(h, d)) done++
                        } else if (isDone(h, d)) {
                            total++
                            done++
                        }
                    }
                }
            }
            out.add(TrendPoint("W${weeks - w}", if (total > 0) done.toFloat() / total else 0f))
        }
        return out
    }

    fun monthlyTrend(habits: List<Habit>, months: Int): List<TrendPoint> {
        val out = mutableListOf<TrendPoint>()
        val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val today = LocalDate.now()
        for (m in months - 1 downTo 0) {
            val targetMonth = today.minusMonths(m.toLong())
            val label = monthNames[targetMonth.monthValue - 1]
            val firstDay = targetMonth.withDayOfMonth(1)
            val daysInMonth = targetMonth.lengthOfMonth()

            var total = 0
            var done = 0
            for (day in 1..daysInMonth) {
                val currentDate = firstDay.withDayOfMonth(day)
                val d = currentDate.format(formatter)
                if (d > todayStr()) continue
                habits.forEach { h ->
                    if (isScheduled(h, d)) {
                        if (!isFrozen(h, d)) {
                            total++
                            if (isDone(h, d)) done++
                        } else if (isDone(h, d)) {
                            total++
                            done++
                        }
                    }
                }
            }
            out.add(TrendPoint(label, if (total > 0) done.toFloat() / total else 0f))
        }
        return out
    }

    fun fieldIntensity(habits: List<Habit>, dateStr: String): Float {
        val active = habits.filter { isScheduled(it, dateStr) }
        if (active.isEmpty()) return -1f
        val done = active.count { isDone(it, dateStr) }
        return done.toFloat() / active.size
    }

    fun durationInfo(habit: Habit): DurationInfo? {
        if (habit.durationDays == null) return null
        val elapsed = max(0, (daysBetween(habit.createdAt, todayStr()) + 1).toInt())
        val capped = min(elapsed, habit.durationDays)
        val done = elapsed >= habit.durationDays
        return DurationInfo(elapsed = capped, total = habit.durationDays, done = done)
    }

    val DAY_NAMES = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    fun daysText(habit: Habit): String {
        if (habit.days.size == 7) return "Every day"
        return habit.days.sorted().joinToString(" · ") { DAY_NAMES[it] }
    }

    /**
     * Builds chart buckets (weeks or months) for habit consistency curves.
     * - Up to 12 weeks of history: one point per week (WEEK).
     * - More than 12 weeks: one point per calendar month (MONTH, last 12 months at most).
     * - rate = done / scheduled for bucket. Only scheduled days count; frozen days excluded unless completed.
     * - current unfinished week or month is marked isPartial.
     */
    fun buildChartBuckets(
        habit: Habit,
        today: String = todayStr()
    ): Pair<ChartGranularity, List<ChartBucket>> {
        val start = habit.createdAt
        var end = today
        if (habit.durationDays != null) {
            val durEnd = addDays(habit.createdAt, (habit.durationDays - 1).toLong())
            if (durEnd < end) end = durEnd
        }
        if (start > end) return Pair(ChartGranularity.DAY, emptyList())

        val totalDays = daysBetween(start, end) + 1

        if (totalDays < 28) {
            // Under 28 days: ONE point per SCHEDULED day with running completion rate
            val buckets = mutableListOf<ChartBucket>()
            var runningScheduled = 0
            var runningDone = 0
            var d = start
            while (d <= end) {
                if (isScheduled(habit, d)) {
                    val frozen = isFrozen(habit, d)
                    val done = isDone(habit, d)
                    if (frozen) {
                        if (done) {
                            runningScheduled++
                            runningDone++
                            val rate = runningDone.toFloat() / runningScheduled
                            val ld = parseDate(d)
                            val label = ld.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
                            val isPartial = (d == today)
                            buckets.add(
                                ChartBucket(
                                    label = label,
                                    fullRangeLabel = label,
                                    startDateStr = d,
                                    endDateStr = d,
                                    rate = rate,
                                    scheduledDays = runningScheduled,
                                    doneDays = runningDone,
                                    isPartial = isPartial
                                )
                            )
                        }
                    } else {
                        if (d == today) {
                            if (done) {
                                runningScheduled++
                                runningDone++
                                val rate = runningDone.toFloat() / runningScheduled
                                val ld = parseDate(d)
                                val label = ld.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
                                buckets.add(
                                    ChartBucket(
                                        label = label,
                                        fullRangeLabel = label,
                                        startDateStr = d,
                                        endDateStr = d,
                                        rate = rate,
                                        scheduledDays = runningScheduled,
                                        doneDays = runningDone,
                                        isPartial = true
                                    )
                                )
                            }
                        } else {
                            runningScheduled++
                            if (done) runningDone++
                            val rate = runningDone.toFloat() / runningScheduled
                            val ld = parseDate(d)
                            val label = ld.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
                            buckets.add(
                                ChartBucket(
                                    label = label,
                                    fullRangeLabel = label,
                                    startDateStr = d,
                                    endDateStr = d,
                                    rate = rate,
                                    scheduledDays = runningScheduled,
                                    doneDays = runningDone,
                                    isPartial = false
                                )
                            )
                        }
                    }
                }
                if (d == end) break
                d = addDays(d, 1)
            }
            return Pair(ChartGranularity.DAY, buckets)
        } else if (totalDays <= 84) {
            // 28 days up to 12 weeks: ONE point per week
            val buckets = mutableListOf<ChartBucket>()
            var curStart = start
            while (curStart <= end) {
                val normalEnd = addDays(curStart, 6)
                var curEnd = normalEnd
                if (curEnd > end) curEnd = end

                var scheduled = 0
                var done = 0
                var d = curStart
                while (d <= curEnd) {
                    if (isScheduled(habit, d)) {
                        if (!isFrozen(habit, d)) {
                            scheduled++
                            if (isDone(habit, d)) done++
                        } else if (isDone(habit, d)) {
                            scheduled++
                            done++
                        }
                    }
                    if (d == curEnd) break
                    d = addDays(d, 1)
                }

                if (scheduled > 0) {
                    val rate = done.toFloat() / scheduled
                    val isPartial = (today >= curStart && today < normalEnd && today <= end)
                    val startLd = parseDate(curStart)
                    val endLd = parseDate(curEnd)
                    val startLabel = startLd.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
                    val endLabel = endLd.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
                    buckets.add(
                        ChartBucket(
                            label = startLabel,
                            fullRangeLabel = "$startLabel - $endLabel",
                            startDateStr = curStart,
                            endDateStr = curEnd,
                            rate = rate,
                            scheduledDays = scheduled,
                            doneDays = done,
                            isPartial = isPartial
                        )
                    )
                }
                curStart = addDays(curStart, 7)
            }
            return Pair(ChartGranularity.WEEK, buckets)
        } else {
            // More than 12 weeks: ONE point per calendar month (last 12 months at most)
            val buckets = mutableListOf<ChartBucket>()
            val todayLd = parseDate(today)
            val startLd = parseDate(start)
            val endLd = parseDate(end)

            val firstMonth = startLd.withDayOfMonth(1)
            val endMonth = endLd.withDayOfMonth(1)
            val monthsCount = ChronoUnit.MONTHS.between(firstMonth, endMonth).toInt() + 1
            val maxMonths = min(12, monthsCount)
            val startMonth = endMonth.minusMonths((maxMonths - 1).toLong())

            var m = startMonth
            while (!m.isAfter(endMonth)) {
                val daysInMonth = m.lengthOfMonth()
                val mStartLd = if (m == firstMonth && startLd.dayOfMonth > 1) startLd else m
                val mEndLdRaw = m.withDayOfMonth(daysInMonth)
                val mEndLd = if (m == endMonth && endLd < mEndLdRaw) endLd else mEndLdRaw

                var scheduled = 0
                var done = 0
                var cur = mStartLd
                while (!cur.isAfter(mEndLd)) {
                    val d = cur.format(formatter)
                    if (isScheduled(habit, d)) {
                        if (!isFrozen(habit, d)) {
                            scheduled++
                            if (isDone(habit, d)) done++
                        } else if (isDone(habit, d)) {
                            scheduled++
                            done++
                        }
                    }
                    cur = cur.plusDays(1)
                }

                if (scheduled > 0) {
                    val rate = done.toFloat() / scheduled
                    val isPartial = (m == todayLd.withDayOfMonth(1) && todayLd.dayOfMonth < daysInMonth)
                    val label = m.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))
                    val fullRange = m.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
                    buckets.add(
                        ChartBucket(
                            label = label,
                            fullRangeLabel = fullRange,
                            startDateStr = mStartLd.format(formatter),
                            endDateStr = mEndLd.format(formatter),
                            rate = rate,
                            scheduledDays = scheduled,
                            doneDays = done,
                            isPartial = isPartial
                        )
                    )
                }
                m = m.plusMonths(1)
            }
            return Pair(ChartGranularity.MONTH, buckets)
        }
    }

    fun habitProgressCurve(habit: Habit): List<HabitWeekTrend> {
        val out = mutableListOf<HabitWeekTrend>()
        val start = habit.createdAt
        var end = todayStr()
        if (habit.durationDays != null) {
            val durEnd = addDays(habit.createdAt, (habit.durationDays - 1).toLong())
            if (durEnd < end) end = durEnd
        }
        if (start > end) return emptyList()

        var weekNum = 1
        var curStart = start
        while (curStart <= end) {
            var curEnd = addDays(curStart, 6)
            if (curEnd > end) curEnd = end

            var scheduled = 0
            var done = 0
            var d = curStart
            while (d <= curEnd) {
                if (isScheduled(habit, d)) {
                    if (!isFrozen(habit, d)) {
                        scheduled++
                        if (isDone(habit, d)) done++
                    } else if (isDone(habit, d)) {
                        scheduled++
                        done++
                    }
                }
                if (d == curEnd) break
                d = addDays(d, 1)
            }

            if (scheduled > 0) {
                val rate = done.toFloat() / scheduled
                out.add(
                    HabitWeekTrend(
                        weekNumber = weekNum,
                        weekLabel = "W$weekNum",
                        startDateStr = curStart,
                        endDateStr = curEnd,
                        rate = rate,
                        scheduledDays = scheduled,
                        doneDays = done
                    )
                )
            }
            weekNum++
            if (weekNum > 500) break
            curStart = addDays(curStart, 7)
        }
        return out
    }

    fun getCurrentWeekDates(): List<String> {
        val out = mutableListOf<String>()
        val today = LocalDate.now()
        val dow = today.dayOfWeek.value // 1=Mon..7=Sun
        // We want Sunday of the current week (Sunday is 0=Sun..6=Sat, so Sunday is dow days ago from today unless today is Sunday).
        val offset = if (dow == 7) 0L else dow.toLong()
        val sunday = today.minusDays(offset)
        for (i in 0 until 7) {
            out.add(sunday.plusDays(i.toLong()).format(formatter))
        }
        return out
    }

    fun formatSecondsToHoursMinutes(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) {
            "${h}h ${m}m"
        } else {
            "${m}m"
        }
    }
}
