package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
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

object BackupManager {
    private const val TAG = "BackupManager"

    suspend fun exportData(context: Context, database: AppDatabase, destinationUri: Uri): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val habitEntities = database.habitDao().getAllHabitsList()
                val completionEntities = database.habitCompletionDao().getAllCompletionsList()
                val timeSessionEntities = database.timeSessionDao().getAllSessionsFlow().first()
                val subtaskEntities = database.habitSubtaskDao().getAllSubtasksFlow().first()
                val taskEntities = database.taskDao().getAllTasks().first()

                val rootJson = JSONObject().apply {
                    put("app", "HabitLedger")
                    put("version", 1)
                    put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))

                    // Habits
                    val habitsArr = JSONArray()
                    habitEntities.forEach { h ->
                        val hObj = JSONObject().apply {
                            put("id", h.id)
                            put("name", h.name)
                            put("category", h.category)
                            put("colorHex", h.colorHex)
                            put("daysOfWeek", h.daysOfWeek)
                            if (h.durationDays != null) put("durationDays", h.durationDays)
                            put("createdAt", h.createdAt)
                            if (h.reminderTime != null) put("reminderTime", h.reminderTime)
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

                val rootJson = JSONObject(jsonString)
                if (!rootJson.has("habits")) {
                    return@withContext Pair(false, "Invalid backup format: Missing habits data")
                }

                var restoredHabits = 0
                var restoredCompletions = 0
                var restoredSessions = 0

                // Restore Habits
                val habitsArr = rootJson.optJSONArray("habits")
                if (habitsArr != null) {
                    for (i in 0 until habitsArr.length()) {
                        val h = habitsArr.getJSONObject(i)
                        val entity = HabitEntity(
                            id = h.getString("id"),
                            name = h.getString("name"),
                            category = h.optString("category", "General"),
                            colorHex = h.optString("colorHex", "#E5A93C"),
                            daysOfWeek = h.optString("daysOfWeek", "0,1,2,3,4,5,6"),
                            durationDays = if (h.has("durationDays")) h.optInt("durationDays") else null,
                            createdAt = h.optString("createdAt", DateUtils.todayStr()),
                            completions = "",
                            reminderTime = if (h.has("reminderTime")) h.optString("reminderTime") else null,
                            isArchived = h.optBoolean("isArchived", false),
                            trackTimeSpent = h.optBoolean("trackTimeSpent", false)
                        )
                        database.habitDao().insertHabit(entity)
                        restoredHabits++
                    }
                }

                // Restore Completions
                val compsArr = rootJson.optJSONArray("completions")
                if (compsArr != null) {
                    for (i in 0 until compsArr.length()) {
                        val c = compsArr.getJSONObject(i)
                        val entity = HabitCompletionEntity(
                            habitId = c.getString("habitId"),
                            dateStr = c.getString("dateStr")
                        )
                        database.habitCompletionDao().insertCompletion(entity)
                        restoredCompletions++
                    }
                }

                // Restore Time Sessions
                val sessionsArr = rootJson.optJSONArray("timeSessions")
                if (sessionsArr != null) {
                    for (i in 0 until sessionsArr.length()) {
                        val s = sessionsArr.getJSONObject(i)
                        val entity = TimeSessionEntity(
                            habitId = s.getString("habitId"),
                            date = s.getString("date"),
                            durationSeconds = s.getLong("durationSeconds")
                        )
                        database.timeSessionDao().insertSession(entity)
                        restoredSessions++
                    }
                }

                // Restore Subtasks
                val subsArr = rootJson.optJSONArray("subtasks")
                if (subsArr != null) {
                    for (i in 0 until subsArr.length()) {
                        val st = subsArr.getJSONObject(i)
                        val entity = HabitSubtaskEntity(
                            id = st.getString("id"),
                            habitId = st.getString("habitId"),
                            dateStr = st.getString("dateStr"),
                            text = st.optString("text", st.optString("title", "")),
                            isCompleted = st.getBoolean("isCompleted")
                        )
                        database.habitSubtaskDao().insertSubtask(entity)
                    }
                }

                // Restore Tasks
                val tasksArr = rootJson.optJSONArray("tasks")
                if (tasksArr != null) {
                    for (i in 0 until tasksArr.length()) {
                        val t = tasksArr.getJSONObject(i)
                        val entity = TaskEntity(
                            id = t.getString("id"),
                            text = t.optString("text", t.optString("title", "")),
                            isCompleted = t.getBoolean("isCompleted"),
                            createdAt = t.optString("createdAt", DateUtils.todayStr())
                        )
                        database.taskDao().insertTask(entity)
                    }
                }

                com.example.receiver.WidgetUpdateHelper.updateWidgets(context)
                Log.d(TAG, "Successfully restored backup: $restoredHabits habits, $restoredCompletions completions.")
                Pair(true, "Successfully restored $restoredHabits habits and $restoredCompletions completions!")
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                Pair(false, "Import failed: ${e.localizedMessage ?: "Invalid file"}")
            }
        }
    }
}
