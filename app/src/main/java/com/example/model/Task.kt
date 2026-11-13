package com.example.model

import androidx.compose.runtime.Immutable

@Immutable
data class Task(
    val id: String,
    val text: String,
    val isCompleted: Boolean,
    val createdAt: String
)

