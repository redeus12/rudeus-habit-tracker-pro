package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.Habit

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val colorHex: String,
    val daysOfWeek: String, // comma separated integers e.g. "0,1,2,3,4,5,6"
    val durationDays: Int?, // null if ongoing
    val createdAt: String, // "YYYY-MM-DD"
    val completions: String, // comma separated date strings e.g. "2026-07-01,2026-07-02"
    val reminderTime: String?, // "HH:mm"
    val isArchived: Boolean = false,
    val trackTimeSpent: Boolean = false
)

fun HabitEntity.toDomain(
    completionsSet: Set<String> = emptySet(),
    frozenSet: Set<String> = emptySet()
): Habit {
    val daysList = if (daysOfWeek.isBlank()) listOf(0, 1, 2, 3, 4, 5, 6) else daysOfWeek.split(",").mapNotNull { it.toIntOrNull() }
    return Habit(
        id = id,
        name = name,
        category = category,
        colorHex = colorHex,
        days = daysList,
        durationDays = durationDays,
        createdAt = createdAt,
        completions = completionsSet,
        reminderTime = reminderTime,
        isArchived = isArchived,
        trackTimeSpent = trackTimeSpent,
        frozenDates = frozenSet
    )
}

fun Habit.toEntity(): HabitEntity {
    return HabitEntity(
        id = id,
        name = name,
        category = category,
        colorHex = colorHex,
        daysOfWeek = days.joinToString(","),
        durationDays = durationDays,
        createdAt = createdAt,
        completions = "",
        reminderTime = reminderTime,
        isArchived = isArchived,
        trackTimeSpent = trackTimeSpent
    )
}
