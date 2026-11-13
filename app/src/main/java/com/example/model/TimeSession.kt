package com.example.model

import androidx.compose.runtime.Immutable

@Immutable
data class TimeSession(
    val habitId: String,
    val date: String, // "YYYY-MM-DD"
    val durationSeconds: Long
)

