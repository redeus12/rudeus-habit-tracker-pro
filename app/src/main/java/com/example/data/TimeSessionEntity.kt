package com.example.data

import androidx.room.Entity
import com.example.model.TimeSession

@Entity(tableName = "time_sessions", primaryKeys = ["habitId", "date"])
data class TimeSessionEntity(
    val habitId: String,
    val date: String, // "YYYY-MM-DD"
    val durationSeconds: Long
)

fun TimeSessionEntity.toDomain(): TimeSession {
    return TimeSession(
        habitId = habitId,
        date = date,
        durationSeconds = durationSeconds
    )
}

fun TimeSession.toEntity(): TimeSessionEntity {
    return TimeSessionEntity(
        habitId = habitId,
        date = date,
        durationSeconds = durationSeconds
    )
}
