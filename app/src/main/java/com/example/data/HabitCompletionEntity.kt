package com.example.data

import androidx.room.Entity

@Entity(tableName = "habit_completions", primaryKeys = ["habitId", "dateStr"])
data class HabitCompletionEntity(
    val habitId: String,
    val dateStr: String
)
