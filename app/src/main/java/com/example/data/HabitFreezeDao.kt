package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitFreezeDao {
    @Query("SELECT * FROM habit_freezes")
    fun getAllFreezesFlow(): Flow<List<HabitFreezeEntity>>

    @Query("SELECT * FROM habit_freezes")
    suspend fun getAllFreezesList(): List<HabitFreezeEntity>

    @Query("SELECT dateStr FROM habit_freezes WHERE habitId = :habitId")
    suspend fun getFreezesForHabit(habitId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFreeze(freeze: HabitFreezeEntity)

    @Query("DELETE FROM habit_freezes WHERE habitId = :habitId AND dateStr = :dateStr")
    suspend fun deleteFreeze(habitId: String, dateStr: String)

    @Query("DELETE FROM habit_freezes WHERE habitId = :habitId")
    suspend fun deleteFreezesByHabitId(habitId: String)

    @Query("DELETE FROM habit_freezes WHERE dateStr = :dateStr")
    suspend fun deleteFreezesForDate(dateStr: String)

    @Query("DELETE FROM habit_freezes")
    suspend fun deleteAllFreezes()
}
