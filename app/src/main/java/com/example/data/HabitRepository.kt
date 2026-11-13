package com.example.data

import androidx.room.withTransaction
import com.example.model.Habit
import com.example.model.Task
import com.example.model.HabitSubtask
import com.example.model.TimeSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HabitRepository(
    private val habitDao: HabitDao,
    private val taskDao: TaskDao,
    private val habitSubtaskDao: HabitSubtaskDao,
    private val timeSessionDao: TimeSessionDao,
    private val habitCompletionDao: HabitCompletionDao,
    private val habitFreezeDao: HabitFreezeDao,
    private val db: AppDatabase? = null
) {
    private suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return if (db != null) {
            db.withTransaction { block() }
        } else {
            block()
        }
    }
    val allHabits: Flow<List<Habit>> = kotlinx.coroutines.flow.combine(
        habitDao.getAllHabits(),
        habitCompletionDao.getAllCompletionsFlow(),
        habitFreezeDao.getAllFreezesFlow()
    ) { habitsList, completionsList, freezesList ->
        val completionsMap = completionsList.groupBy { it.habitId }
            .mapValues { entry -> entry.value.map { it.dateStr }.toSet() }
        val freezesMap = freezesList.groupBy { it.habitId }
            .mapValues { entry -> entry.value.map { it.dateStr }.toSet() }
        habitsList.map { entity ->
            val comps = completionsMap[entity.id] ?: emptySet()
            val frzs = freezesMap[entity.id] ?: emptySet()
            entity.toDomain(comps, frzs)
        }
    }

    val allFreezes: Flow<List<HabitFreezeEntity>> = habitFreezeDao.getAllFreezesFlow()

    val allTimeSessions: Flow<List<TimeSession>> = timeSessionDao.getAllSessionsFlow().map { list ->
        list.map { it.toDomain() }
    }

    val archivedHabits: Flow<List<Habit>> = kotlinx.coroutines.flow.combine(
        habitDao.getArchivedHabits(),
        habitCompletionDao.getAllCompletionsFlow(),
        habitFreezeDao.getAllFreezesFlow()
    ) { habitsList, completionsList, freezesList ->
        val completionsMap = completionsList.groupBy { it.habitId }
            .mapValues { entry -> entry.value.map { it.dateStr }.toSet() }
        val freezesMap = freezesList.groupBy { it.habitId }
            .mapValues { entry -> entry.value.map { it.dateStr }.toSet() }
        habitsList.map { entity ->
            val comps = completionsMap[entity.id] ?: emptySet()
            val frzs = freezesMap[entity.id] ?: emptySet()
            entity.toDomain(comps, frzs)
        }
    }

    val allTasks: Flow<List<Task>> = taskDao.getAllTasks().map { list ->
        list.map { it.toDomain() }
    }

    val allSubtasks: Flow<List<HabitSubtask>> = habitSubtaskDao.getAllSubtasksFlow().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getSubtasksForHabitAndDate(habitId: String, dateStr: String): List<HabitSubtask> {
        return habitSubtaskDao.getSubtasksForHabitAndDate(habitId, dateStr).map { it.toDomain() }
    }

    suspend fun getHabitById(id: String): Habit? {
        val entity = habitDao.getHabitById(id) ?: return null
        val completions = habitCompletionDao.getCompletionsForHabit(id).toSet()
        val freezes = habitFreezeDao.getFreezesForHabit(id).toSet()
        return entity.toDomain(completions, freezes)
    }

    suspend fun insertHabit(habit: Habit) = runInTransaction {
        habitDao.insertHabit(habit.toEntity())
        
        // Sync completions in the completions table
        val currentInDb = habitCompletionDao.getCompletionsForHabit(habit.id).toSet()
        val toAdd = habit.completions - currentInDb
        val toRemove = currentInDb - habit.completions
        
        for (date in toAdd) {
            habitCompletionDao.insertCompletion(HabitCompletionEntity(habit.id, date))
        }
        for (date in toRemove) {
            habitCompletionDao.deleteCompletion(habit.id, date)
        }

        // Sync freezes in the freezes table
        val currentFreezesInDb = habitFreezeDao.getFreezesForHabit(habit.id).toSet()
        val freezesToAdd = habit.frozenDates - currentFreezesInDb
        val freezesToRemove = currentFreezesInDb - habit.frozenDates
        for (date in freezesToAdd) {
            habitFreezeDao.insertFreeze(HabitFreezeEntity(habit.id, date, "Rest Day"))
        }
        for (date in freezesToRemove) {
            habitFreezeDao.deleteFreeze(habit.id, date)
        }
    }

    suspend fun toggleFreeze(habitId: String, dateStr: String, reason: String = "Rest Day") {
        val currentFreezes = habitFreezeDao.getFreezesForHabit(habitId).toSet()
        if (currentFreezes.contains(dateStr)) {
            habitFreezeDao.deleteFreeze(habitId, dateStr)
        } else {
            habitFreezeDao.insertFreeze(HabitFreezeEntity(habitId, dateStr, reason))
        }
    }

    suspend fun setFreezeState(habitId: String, dateStr: String, isFrozen: Boolean, reason: String = "Rest Day") {
        if (isFrozen) {
            habitFreezeDao.insertFreeze(HabitFreezeEntity(habitId, dateStr, reason))
        } else {
            habitFreezeDao.deleteFreeze(habitId, dateStr)
        }
    }

    suspend fun freezeAllHabitsForDate(dateStr: String, reason: String = "Rest Day") = runInTransaction {
        val habitsList = habitDao.getAllHabitsList()
        for (h in habitsList) {
            habitFreezeDao.insertFreeze(HabitFreezeEntity(h.id, dateStr, reason))
        }
    }

    suspend fun unfreezeAllHabitsForDate(dateStr: String) {
        habitFreezeDao.deleteFreezesForDate(dateStr)
    }

    suspend fun deleteHabit(id: String) = runInTransaction {
        habitDao.deleteHabitById(id)
        habitSubtaskDao.deleteSubtasksByHabitId(id)
        timeSessionDao.deleteSessionsByHabitId(id)
        habitCompletionDao.deleteCompletionsByHabitId(id)
        habitFreezeDao.deleteFreezesByHabitId(id)
    }

    suspend fun deleteAllHabits() = runInTransaction {
        habitDao.deleteAllHabits()
        taskDao.deleteAllTasks()
        habitSubtaskDao.deleteAllSubtasks()
        timeSessionDao.deleteAllSessions()
        habitCompletionDao.deleteAllCompletions()
        habitFreezeDao.deleteAllFreezes()
    }

    fun getWeeklyTotals(start: String, end: String): Flow<Map<String, Long>> {
        return timeSessionDao.getWeeklyTotalsFlow(start, end).map { list ->
            list.associate { it.habitId to it.totalSeconds }
        }
    }

    suspend fun insertOrAccumulateSession(habitId: String, dateStr: String, durationSeconds: Long) {
        val existing = timeSessionDao.getSession(habitId, dateStr)
        if (existing != null) {
            val updated = existing.copy(durationSeconds = existing.durationSeconds + durationSeconds)
            timeSessionDao.insertSession(updated)
        } else {
            timeSessionDao.insertSession(TimeSessionEntity(habitId, dateStr, durationSeconds))
        }
    }

    suspend fun insertSubtask(subtask: HabitSubtask) {
        habitSubtaskDao.insertSubtask(subtask.toEntity())
    }

    suspend fun deleteSubtask(id: String) {
        habitSubtaskDao.deleteSubtaskById(id)
    }

    suspend fun insertTask(task: Task) {
        taskDao.insertTask(task.toEntity())
    }

    suspend fun deleteTask(id: String) {
        taskDao.deleteTaskById(id)
    }

    suspend fun deleteCompletedTasks() {
        taskDao.deleteCompletedTasks()
    }
}
