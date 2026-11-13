package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.Task

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val text: String,
    val isCompleted: Boolean,
    val createdAt: String
)

fun TaskEntity.toDomain(): Task {
    return Task(
        id = id,
        text = text,
        isCompleted = isCompleted,
        createdAt = createdAt
    )
}

fun Task.toEntity(): TaskEntity {
    return TaskEntity(
        id = id,
        text = text,
        isCompleted = isCompleted,
        createdAt = createdAt
    )
}
