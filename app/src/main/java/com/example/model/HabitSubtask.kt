package com.example.model

import androidx.compose.runtime.Immutable

@Immutable
data class HabitSubtask(
    val id: String,
    val habitId: String,
    val dateStr: String, // "YYYY-MM-DD"
    val text: String,
    val isCompleted: Boolean
)

