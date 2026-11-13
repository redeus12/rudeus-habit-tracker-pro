package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitCompletionDao {
    @Query("SELECT * FROM habit_completions")
    fun getAllCompletionsFlow(): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions")
    suspend fun getAllCompletionsList(): List<HabitCompletionEntity>

    @Query("SELECT dateStr FROM habit_completions WHERE habitId = :habitId")
    fun getCompletionsForHabitFlow(habitId: String): Flow<List<String>>

    @Query("SELECT dateStr FROM habit_completions WHERE habitId = :habitId")
    suspend fun getCompletionsForHabit(habitId: String): List<String>

    @Query("SELECT dateStr FROM habit_completions WHERE habitId = :habitId ORDER BY dateStr DESC")
    suspend fun getCompletionsOrderedDesc(habitId: String): List<String>

    @Query("SELECT COUNT(*) FROM habit_completions WHERE habitId = :habitId AND dateStr BETWEEN :startDate AND :endDate")
    suspend fun getCompletionCountInRange(habitId: String, startDate: String, endDate: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: HabitCompletionEntity)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND dateStr = :dateStr")
    suspend fun deleteCompletion(habitId: String, dateStr: String)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId")
    suspend fun deleteCompletionsByHabitId(habitId: String)

    @Query("DELETE FROM habit_completions")
    suspend fun deleteAllCompletions()
}
