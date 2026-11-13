package com.example.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.parseHexColor

@Immutable
data class Habit(
    val id: String,
    val name: String,
    val category: String,
    val colorHex: String,
    val days: List<Int>, // 0=Sun..6=Sat
    val durationDays: Int?, // null = ongoing
    val createdAt: String, // "YYYY-MM-DD"
    val completions: Set<String>, // set of date strings "YYYY-MM-DD"
    val reminderTime: String? = null, // "HH:mm"
    val isArchived: Boolean = false,
    val trackTimeSpent: Boolean = false,
    val frozenDates: Set<String> = emptySet() // dates where streak is frozen/protected
) {
    val color: Color
        get() = parseHexColor(colorHex)
}

@Immutable
data class DurationInfo(
    val elapsed: Int,
    val total: Int,
    val done: Boolean
)

@Immutable
data class CategoryPerformance(
    val category: String,
    val rate: Float,
    val color: Color
)

@Immutable
data class TrendPoint(
    val label: String,
    val rate: Float
)

@Immutable
data class HabitWeekTrend(
    val weekNumber: Int,
    val weekLabel: String,
    val startDateStr: String,
    val endDateStr: String,
    val rate: Float,
    val scheduledDays: Int,
    val doneDays: Int
)

enum class ChartGranularity {
    DAY,
    WEEK,
    MONTH
}

@Immutable
data class ChartBucket(
    val label: String,
    val fullRangeLabel: String,
    val startDateStr: String,
    val endDateStr: String,
    val rate: Float,
    val scheduledDays: Int,
    val doneDays: Int,
    val isPartial: Boolean
)

