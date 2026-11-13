package com.example.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.toDomain
import com.example.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HabitSummaryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d("HabitSummaryWidget", "onUpdate called for ${appWidgetIds.size} widgets")

        val goAsync = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val activeEntities = db.habitDao().getAllHabitsList()
                val completionsList = db.habitCompletionDao().getAllCompletionsList()
                val completionsMap = completionsList.groupBy { it.habitId }
                    .mapValues { entry -> entry.value.map { it.dateStr }.toSet() }
                val habits = activeEntities.map { entity ->
                    val comps = completionsMap[entity.id] ?: emptySet()
                    entity.toDomain(comps)
                }
                
                val today = DateUtils.todayStr()
                val scheduledToday = habits.filter { DateUtils.isScheduled(it, today) }
                val doneCount = scheduledToday.count { DateUtils.isDone(it, today) }
                val totalCount = scheduledToday.size
                val percentage = if (totalCount > 0) (doneCount * 100) / totalCount else 0

                Log.d("HabitSummaryWidget", "Updating widgets: done=$doneCount, total=$totalCount, percentage=$percentage")

                appWidgetIds.forEach { widgetId ->
                    val views = RemoteViews(context.packageName, R.layout.widget_summary)
                    
                    // Set progress and texts
                    views.setTextViewText(R.id.widget_percentage, "$percentage%")
                    views.setTextViewText(R.id.widget_status, "$doneCount/$totalCount done today")
                    views.setProgressBar(R.id.widget_progress_bar, 100, percentage, false)

                    // On-click pending intent to launch MainActivity
                    val mainIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        widgetId,
                        mainIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_summary_root, pendingIntent)

                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } catch (e: Exception) {
                Log.e("HabitSummaryWidget", "Error updating widget", e)
            } finally {
                goAsync.finish()
            }
        }
    }
}
