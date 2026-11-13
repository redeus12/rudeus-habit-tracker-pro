package com.example.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.R
import com.example.data.AppDatabase
import com.example.data.toDomain
import com.example.data.HabitRepository
import com.example.util.AppPreferences
import com.example.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HabitListWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_HABIT = "com.example.receiver.ACTION_TOGGLE_HABIT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE_HABIT) {
            val token = intent.getStringExtra(AppPreferences.EXTRA_INTERNAL_TOKEN)
            if (!AppPreferences.verifyInternalToken(context, token)) {
                Log.w("HabitListWidget", "Rejected toggle broadcast with invalid internal token.")
                return
            }
            val habitId = intent.getStringExtra("habit_id")
            Log.d("HabitListWidget", "Toggle broadcast received for habit ID: $habitId")
            if (habitId != null) {
                val goAsync = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val repository = HabitRepository(
                            db.habitDao(),
                            db.taskDao(),
                            db.habitSubtaskDao(),
                            db.timeSessionDao(),
                            db.habitCompletionDao(),
                            db.habitFreezeDao(),
                            db
                        )
                        val subtaskDao = db.habitSubtaskDao()
                        val habit = repository.getHabitById(habitId)
                        if (habit != null) {
                            val today = DateUtils.todayStr()
                            val isCompleted = habit.completions.contains(today)
                            val targetState = !isCompleted
                            val newCompletions = if (isCompleted) {
                                habit.completions - today
                            } else {
                                habit.completions + today
                            }
                            val updatedHabit = habit.copy(completions = newCompletions)
                            repository.insertHabit(updatedHabit)
                            
                            // Update any subtasks for this habit and today
                            val subtaskEntities = subtaskDao.getSubtasksForHabitAndDate(habit.id, today)
                            if (subtaskEntities.isNotEmpty()) {
                                subtaskEntities.forEach { sub ->
                                    subtaskDao.insertSubtask(sub.copy(isCompleted = targetState))
                                }
                            }
                            
                            Log.d("HabitListWidget", "Habit '${habit.name}' completion toggled successfully in DB.")
                            
                            // Instantly refresh widgets
                            WidgetUpdateHelper.updateWidgets(context)
                        } else {
                            Log.e("HabitListWidget", "Habit with ID $habitId not found in DB.")
                        }
                    } catch (e: Exception) {
                        Log.e("HabitListWidget", "Error toggling habit completion via widget", e)
                    } finally {
                        goAsync.finish()
                    }
                }
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d("HabitListWidget", "onUpdate called for ${appWidgetIds.size} widgets")

        val goAsync = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val activeEntities = db.habitDao().getAllHabitsList()
                val completionsList = db.habitCompletionDao().getAllCompletionsList()
                val freezesList = db.habitFreezeDao().getAllFreezesList()
                val completionsMap = completionsList.groupBy { it.habitId }
                    .mapValues { entry -> entry.value.map { it.dateStr }.toSet() }
                val freezesMap = freezesList.groupBy { it.habitId }
                    .mapValues { entry -> entry.value.map { it.dateStr }.toSet() }
                val habits = activeEntities.map { entity ->
                    val comps = completionsMap[entity.id] ?: emptySet()
                    val frzs = freezesMap[entity.id] ?: emptySet()
                    entity.toDomain(comps, frzs)
                }

                val today = DateUtils.todayStr()
                val scheduledToday = habits.filter { DateUtils.isScheduled(it, today) }
                val doneCount = scheduledToday.count { DateUtils.isDone(it, today) }
                val totalCount = scheduledToday.size
                val percentage = if (totalCount > 0) (doneCount * 100) / totalCount else 0

                appWidgetIds.forEach { widgetId ->
                    val views = RemoteViews(context.packageName, R.layout.widget_list)

                    // Update Top Header Progress info
                    views.setTextViewText(R.id.widget_list_status, "$doneCount/$totalCount done")
                    views.setProgressBar(R.id.widget_list_progress_bar, 100, percentage, false)

                    // Empty State vs List State
                    if (scheduledToday.isEmpty()) {
                        views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_scroll_view, View.GONE)
                    } else {
                        views.setViewVisibility(R.id.widget_empty_text, View.GONE)
                        views.setViewVisibility(R.id.widget_scroll_view, View.VISIBLE)
                    }

                    // Populate 8 pre-defined rows
                    val numRows = 8
                    for (i in 0 until numRows) {
                        val rowLayoutId = when (i) {
                            0 -> R.id.row_1
                            1 -> R.id.row_2
                            2 -> R.id.row_3
                            3 -> R.id.row_4
                            4 -> R.id.row_5
                            5 -> R.id.row_6
                            6 -> R.id.row_7
                            else -> R.id.row_8
                        }

                        val dotId = when (i) {
                            0 -> R.id.row_1_dot
                            1 -> R.id.row_2_dot
                            2 -> R.id.row_3_dot
                            3 -> R.id.row_4_dot
                            4 -> R.id.row_5_dot
                            5 -> R.id.row_6_dot
                            6 -> R.id.row_7_dot
                            else -> R.id.row_8_dot
                        }

                        val nameId = when (i) {
                            0 -> R.id.row_1_name
                            1 -> R.id.row_2_name
                            2 -> R.id.row_3_name
                            3 -> R.id.row_4_name
                            4 -> R.id.row_5_name
                            5 -> R.id.row_6_name
                            6 -> R.id.row_7_name
                            else -> R.id.row_8_name
                        }

                        val rateId = when (i) {
                            0 -> R.id.row_1_rate
                            1 -> R.id.row_2_rate
                            2 -> R.id.row_3_rate
                            3 -> R.id.row_4_rate
                            4 -> R.id.row_5_rate
                            5 -> R.id.row_6_rate
                            6 -> R.id.row_7_rate
                            else -> R.id.row_8_rate
                        }

                        val checkboxId = when (i) {
                            0 -> R.id.row_1_checkbox
                            1 -> R.id.row_2_checkbox
                            2 -> R.id.row_3_checkbox
                            3 -> R.id.row_4_checkbox
                            4 -> R.id.row_5_checkbox
                            5 -> R.id.row_6_checkbox
                            6 -> R.id.row_7_checkbox
                            else -> R.id.row_8_checkbox
                        }

                        if (i < scheduledToday.size) {
                            val habit = scheduledToday[i]
                            views.setViewVisibility(rowLayoutId, View.VISIBLE)
                            views.setTextViewText(nameId, habit.name)

                            // 30-day Rolling Completion rate percentage
                            val rate = DateUtils.completionRate(habit, 30)
                            val ratePercent = (rate * 100).toInt()
                            views.setTextViewText(rateId, "30d Rate: $ratePercent%")

                            // Dot color
                            try {
                                val colorInt = android.graphics.Color.parseColor(habit.colorHex)
                                views.setInt(dotId, "setColorFilter", colorInt)
                            } catch (e: Exception) {
                                views.setInt(dotId, "setColorFilter", 0xFFE2A857.toInt())
                            }

                            // Checkbox status
                            val isCompleted = DateUtils.isDone(habit, today)
                            if (isCompleted) {
                                views.setImageViewResource(checkboxId, R.drawable.ic_check_circle)
                            } else {
                                views.setImageViewResource(checkboxId, R.drawable.ic_unchecked_circle)
                            }

                            // Click Toggle Intent
                            val toggleIntent = Intent(context, HabitListWidgetProvider::class.java).apply {
                                action = ACTION_TOGGLE_HABIT
                                putExtra("habit_id", habit.id)
                                putExtra(AppPreferences.EXTRA_INTERNAL_TOKEN, AppPreferences.getOrCreateInternalToken(context))
                            }
                            val requestCode = widgetId * 31 + habit.id.hashCode()
                            val togglePendingIntent = PendingIntent.getBroadcast(
                                context,
                                requestCode,
                                toggleIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(rowLayoutId, togglePendingIntent)

                        } else {
                            // Hide unused row slot
                            views.setViewVisibility(rowLayoutId, View.GONE)
                        }
                    }

                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } catch (e: Exception) {
                Log.e("HabitListWidget", "Error updating widget layout", e)
            } finally {
                goAsync.finish()
            }
        }
    }
}
