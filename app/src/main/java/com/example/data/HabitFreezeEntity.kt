package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habit_freezes", primaryKeys = ["habitId", "dateStr"])
data class HabitFreezeEntity(
    val habitId: String,
    val dateStr: String,
    val reason: String = "Rest Day" // "Rest Day", "Sick", "Travel", "Emergency"
)
