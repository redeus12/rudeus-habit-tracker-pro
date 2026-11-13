package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class HabitWeeklyTimeSum(
    val habitId: String,
    val totalSeconds: Long
)

@Dao
interface TimeSessionDao {
    @Query("SELECT * FROM time_sessions")
    fun getAllSessionsFlow(): Flow<List<TimeSessionEntity>>

    @Query("SELECT * FROM time_sessions WHERE habitId = :habitId AND date = :date")
    suspend fun getSession(habitId: String, date: String): TimeSessionEntity?

    @Query("SELECT * FROM time_sessions WHERE habitId = :habitId")
    fun getSessionsForHabit(habitId: String): Flow<List<TimeSessionEntity>>

    @Query("SELECT habitId, SUM(durationSeconds) as totalSeconds FROM time_sessions WHERE date BETWEEN :start AND :end GROUP BY habitId")
    fun getWeeklyTotalsFlow(start: String, end: String): Flow<List<HabitWeeklyTimeSum>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TimeSessionEntity)

    @Query("DELETE FROM time_sessions WHERE habitId = :habitId")
    suspend fun deleteSessionsByHabitId(habitId: String)

    @Query("DELETE FROM time_sessions")
    suspend fun deleteAllSessions()
}
