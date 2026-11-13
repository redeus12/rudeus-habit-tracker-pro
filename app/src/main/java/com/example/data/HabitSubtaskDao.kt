package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitSubtaskDao {
    @Query("SELECT * FROM habit_subtasks")
    fun getAllSubtasksFlow(): Flow<List<HabitSubtaskEntity>>

    @Query("SELECT * FROM habit_subtasks WHERE habitId = :habitId AND dateStr = :dateStr")
    fun getSubtasksForHabitAndDateFlow(habitId: String, dateStr: String): Flow<List<HabitSubtaskEntity>>

    @Query("SELECT * FROM habit_subtasks WHERE habitId = :habitId AND dateStr = :dateStr")
    suspend fun getSubtasksForHabitAndDate(habitId: String, dateStr: String): List<HabitSubtaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtask(subtask: HabitSubtaskEntity)

    @Query("DELETE FROM habit_subtasks WHERE id = :id")
    suspend fun deleteSubtaskById(id: String)

    @Query("DELETE FROM habit_subtasks WHERE habitId = :habitId")
    suspend fun deleteSubtasksByHabitId(habitId: String)

    @Query("DELETE FROM habit_subtasks")
    suspend fun deleteAllSubtasks()
}
