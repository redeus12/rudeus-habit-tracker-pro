package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParsedBackupData(
    val habits: List<HabitEntity>,
    val completions: List<HabitCompletionEntity>,
    val freezes: List<HabitFreezeEntity>,
    val timeSessions: List<TimeSessionEntity>,
    val subtasks: List<HabitSubtaskEntity>,
    val tasks: List<TaskEntity>
)

object BackupManager {
    private const val TAG = "BackupManager"
    private const val MAX_BACKUP_BYTES = 15 * 1024 * 1024 // 15 MB safety guard

    fun buildBackupJson(
        habitEntities: List<HabitEntity>,
        completionEntities: List<HabitCompletionEntity>,
        freezeEntities: List<HabitFreezeEntity>,
        timeSessionEntities: List<TimeSessionEntity>,
        subtaskEntities: List<HabitSubtaskEntity>,
        taskEntities: List<TaskEntity>
    ): JSONObject {
        return JSONObject().apply {
            put("app", "HabitLedger")
            put("version", 2)
            put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))

            // Habits (including archived)
            val habitsArr = JSONArray()
            habitEntities.forEach { h ->
                val hObj = JSONObject().apply {
                    put("id", h.id)
                    put("name", h.name)
                    put("category", h.category)
                    put("colorHex", h.colorHex)
                    put("daysOfWeek", h.daysOfWeek)
                    if (h.durationDays != null) put("durationDays", h.durationDays) else put("durationDays", JSONObject.NULL)
                    put("createdAt", h.createdAt)
                    if (!h.reminderTime.isNullOrBlank()) put("reminderTime", h.reminderTime) else put("reminderTime", JSONObject.NULL)
                    put("isArchived", h.isArchived)
                    put("trackTimeSpent", h.trackTimeSpent)
                }
                habitsArr.put(hObj)
            }
            put("habits", habitsArr)

            // Completions
            val compsArr = JSONArray()
            completionEntities.forEach { c ->
                val cObj = JSONObject().apply {
                    put("habitId", c.habitId)
                    put("dateStr", c.dateStr)
                }
                compsArr.put(cObj)
            }
            put("completions", compsArr)

            // Freezes
            val freezesArr = JSONArray()
            freezeEntities.forEach { f ->
                val fObj = JSONObject().apply {
                    put("habitId", f.habitId)
                    put("dateStr", f.dateStr)
                    put("reason", f.reason)
                }
                freezesArr.put(fObj)
            }
            put("freezes", freezesArr)

            // Time Sessions
            val sessionsArr = JSONArray()
            timeSessionEntities.forEach { s ->
                val sObj = JSONObject().apply {
                    put("habitId", s.habitId)
                    put("date", s.date)
                    put("durationSeconds", s.durationSeconds)
                }
                sessionsArr.put(sObj)
            }
            put("timeSessions", sessionsArr)

            // Subtasks
            val subsArr = JSONArray()
            subtaskEntities.forEach { st ->
                val stObj = JSONObject().apply {
                    put("id", st.id)
                    put("habitId", st.habitId)
                    put("text", st.text)
                    put("isCompleted", st.isCompleted)
                    put("dateStr", st.dateStr)
                }
                subsArr.put(stObj)
            }
            put("subtasks", subsArr)

            // Tasks
            val tasksArr = JSONArray()
            taskEntities.forEach { t ->
                val tObj = JSONObject().apply {
                    put("id", t.id)
                    put("text", t.text)
                    put("isCompleted", t.isCompleted)
                    put("createdAt", t.createdAt)
                }
                tasksArr.put(tObj)
            }
            put("tasks", tasksArr)
        }
    }

    fun parseBackupJson(jsonString: String): ParsedBackupData {
        if (jsonString.isBlank()) {
            throw IllegalArgumentException("Backup file is empty")
        }
        if (jsonString.length > MAX_BACKUP_BYTES) {
            throw IllegalArgumentException("Backup file exceeds maximum supported size")
        }

        val rootJson = JSONObject(jsonString)
        val habitsArr = rootJson.optJSONArray("habits")
            ?: throw IllegalArgumentException("Invalid backup format: Missing habits data")

        val habits = mutableListOf<HabitEntity>()
        val completions = mutableListOf<HabitCompletionEntity>()
        val freezes = mutableListOf<HabitFreezeEntity>()
        val timeSessions = mutableListOf<TimeSessionEntity>()
        val subtasks = mutableListOf<HabitSubtaskEntity>()
        val tasks = mutableListOf<TaskEntity>()

        for (i in 0 until habitsArr.length()) {
            val h = habitsArr.getJSONObject(i)
            val id = h.optString("id", "").trim()
            val name = h.optString("name", "").trim()
            if (id.isEmpty() || name.isEmpty()) continue

            val durationDays = if (h.has("durationDays") && !h.isNull("durationDays")) {
                h.optInt("durationDays", 0).takeIf { it > 0 }
            } else {
                null
            }
            val reminderTime = if (h.has("reminderTime") && !h.isNull("reminderTime")) {
                h.optString("reminderTime", "").trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            } else {
                null
            }

            habits.add(
                HabitEntity(
                    id = id,
                    name = name,
                    category = h.optString("category", "General").ifBlank { "General" },
                    colorHex = h.optString("colorHex", "#E5A93C").ifBlank { "#E5A93C" },
                    daysOfWeek = h.optString("daysOfWeek", "1,2,3,4,5,6,7").ifBlank { "1,2,3,4,5,6,7" },
                    durationDays = durationDays,
                    createdAt = h.optString("createdAt", DateUtils.todayStr()).ifBlank { DateUtils.todayStr() },
                    completions = "",
                    reminderTime = reminderTime,
                    isArchived = h.optBoolean("isArchived", false),
                    trackTimeSpent = h.optBoolean("trackTimeSpent", false)
                )
            )

            // Support legacy embedded completions inside habit JSON if present
            val legacyCompsArr = h.optJSONArray("completions")
            if (legacyCompsArr != null) {
                for (j in 0 until legacyCompsArr.length()) {
                    val dateStr = legacyCompsArr.optString(j, "").trim()
                    if (dateStr.isNotEmpty()) {
                        completions.add(HabitCompletionEntity(habitId = id, dateStr = dateStr))
                    }
                }
            }
            val legacyFrozenArr = h.optJSONArray("frozenDates")
            if (legacyFrozenArr != null) {
                for (j in 0 until legacyFrozenArr.length()) {
                    val dateStr = legacyFrozenArr.optString(j, "").trim()
                    if (dateStr.isNotEmpty()) {
                        freezes.add(HabitFreezeEntity(habitId = id, dateStr = dateStr, reason = "Rest Day"))
                    }
                }
            }
        }

        val compsArr = rootJson.optJSONArray("completions")
        if (compsArr != null) {
            for (i in 0 until compsArr.length()) {
                val c = compsArr.getJSONObject(i)
                val habitId = c.optString("habitId", "").trim()
                val dateStr = c.optString("dateStr", "").trim()
                if (habitId.isNotEmpty() && dateStr.isNotEmpty()) {
                    completions.add(HabitCompletionEntity(habitId = habitId, dateStr = dateStr))
                }
            }
        }

        val freezesArr = rootJson.optJSONArray("freezes")
        if (freezesArr != null) {
            for (i in 0 until freezesArr.length()) {
                val f = freezesArr.getJSONObject(i)
                val habitId = f.optString("habitId", "").trim()
                val dateStr = f.optString("dateStr", "").trim()
                val reason = f.optString("reason", "Rest Day").ifBlank { "Rest Day" }
                if (habitId.isNotEmpty() && dateStr.isNotEmpty()) {
                    freezes.add(HabitFreezeEntity(habitId = habitId, dateStr = dateStr, reason = reason))
                }
            }
        }

        val sessionsArr = rootJson.optJSONArray("timeSessions")
        if (sessionsArr != null) {
            for (i in 0 until sessionsArr.length()) {
                val s = sessionsArr.getJSONObject(i)
                val habitId = s.optString("habitId", "").trim()
                val date = s.optString("date", s.optString("dateStr", "")).trim()
                val durationSeconds = s.optLong("durationSeconds", 0L)
                if (habitId.isNotEmpty() && date.isNotEmpty() && durationSeconds >= 0L) {
                    timeSessions.add(TimeSessionEntity(habitId = habitId, date = date, durationSeconds = durationSeconds))
                }
            }
        }

        val subsArr = rootJson.optJSONArray("subtasks")
        if (subsArr != null) {
            for (i in 0 until subsArr.length()) {
                val st = subsArr.getJSONObject(i)
                val id = st.optString("id", "").trim()
                val habitId = st.optString("habitId", "").trim()
                val dateStr = st.optString("dateStr", "").trim()
                val text = st.optString("text", st.optString("title", "")).trim()
                if (id.isNotEmpty() && habitId.isNotEmpty() && dateStr.isNotEmpty() && text.isNotEmpty()) {
                    subtasks.add(
                        HabitSubtaskEntity(
                            id = id,
                            habitId = habitId,
                            dateStr = dateStr,
                            text = text,
                            isCompleted = st.optBoolean("isCompleted", false)
                        )
                    )
                }
            }
        }

        val tasksArr = rootJson.optJSONArray("tasks")
        if (tasksArr != null) {
            for (i in 0 until tasksArr.length()) {
                val t = tasksArr.getJSONObject(i)
                val id = t.optString("id", "").trim()
                val text = t.optString("text", t.optString("title", "")).trim()
                if (id.isNotEmpty() && text.isNotEmpty()) {
                    tasks.add(
                        TaskEntity(
                            id = id,
                            text = text,
                            isCompleted = t.optBoolean("isCompleted", false),
                            createdAt = t.optString("createdAt", DateUtils.todayStr()).ifBlank { DateUtils.todayStr() }
                        )
                    )
                }
            }
        }

        return ParsedBackupData(
            habits = habits,
            completions = completions.distinctBy { it.habitId to it.dateStr },
            freezes = freezes.distinctBy { it.habitId to it.dateStr },
            timeSessions = timeSessions.distinctBy { it.habitId to it.date },
            subtasks = subtasks.distinctBy { it.id },
            tasks = tasks.distinctBy { it.id }
        )
    }

    suspend fun exportData(context: Context, database: AppDatabase, destinationUri: Uri): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val habitEntities = database.habitDao().getAllHabitsIncludingArchivedList()
                val completionEntities = database.habitCompletionDao().getAllCompletionsList()
                val freezeEntities = database.habitFreezeDao().getAllFreezesList()
                val timeSessionEntities = database.timeSessionDao().getAllSessionsFlow().first()
                val subtaskEntities = database.habitSubtaskDao().getAllSubtasksFlow().first()
                val taskEntities = database.taskDao().getAllTasks().first()

                val rootJson = buildBackupJson(
                    habitEntities = habitEntities,
                    completionEntities = completionEntities,
                    freezeEntities = freezeEntities,
                    timeSessionEntities = timeSessionEntities,
                    subtaskEntities = subtaskEntities,
                    taskEntities = taskEntities
                )

                context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                    OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(rootJson.toString(2))
                        writer.flush()
                    }
                } ?: return@withContext Pair(false, "Could not open file output stream")

                Log.d(TAG, "Successfully exported backup with ${habitEntities.size} habits.")
                Pair(true, "Successfully exported ${habitEntities.size} habits and ${completionEntities.size} records.")
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                Pair(false, "Export failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    suspend fun importData(context: Context, database: AppDatabase, sourceUri: Uri): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                        reader.readText()
                    }
                } ?: return@withContext Pair(false, "Could not open file input stream")

                val parsed = parseBackupJson(jsonString)

                database.withTransaction {
                    parsed.habits.forEach { database.habitDao().insertHabit(it) }
                    parsed.completions.forEach { database.habitCompletionDao().insertCompletion(it) }
                    parsed.freezes.forEach { database.habitFreezeDao().insertFreeze(it) }
                    parsed.timeSessions.forEach { database.timeSessionDao().insertSession(it) }
                    parsed.subtasks.forEach { database.habitSubtaskDao().insertSubtask(it) }
                    parsed.tasks.forEach { database.taskDao().insertTask(it) }
                }

                // Sync alarms for restored habits
                parsed.habits.forEach { entity ->
                    NotificationHelper.scheduleHabitReminder(context, entity.toDomain())
                }

                com.example.receiver.WidgetUpdateHelper.updateWidgets(context)
                Log.d(TAG, "Successfully restored backup: ${parsed.habits.size} habits, ${parsed.completions.size} completions, ${parsed.freezes.size} freezes.")
                Pair(true, "Successfully restored ${parsed.habits.size} habits and ${parsed.completions.size} completions!")
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                Pair(false, "Import failed: ${e.localizedMessage ?: "Invalid file"}")
            }
        }
    }
}
