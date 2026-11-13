package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.HabitSubtask

@Entity(tableName = "habit_subtasks")
data class HabitSubtaskEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val dateStr: String,
    val text: String,
    val isCompleted: Boolean
)

fun HabitSubtaskEntity.toDomain(): HabitSubtask {
    return HabitSubtask(
        id = id,
        habitId = habitId,
        dateStr = dateStr,
        text = text,
        isCompleted = isCompleted
    )
}

fun HabitSubtask.toEntity(): HabitSubtaskEntity {
    return HabitSubtaskEntity(
        id = id,
        habitId = habitId,
        dateStr = dateStr,
        text = text,
        isCompleted = isCompleted
    )
}
