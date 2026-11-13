package com.example.receiver

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

object WidgetUpdateHelper {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingUpdateRunnable: Runnable? = null
    private const val DEBOUNCE_MS = 250L

    fun updateWidgets(context: Context) {
        val appContext = context.applicationContext
        synchronized(this) {
            pendingUpdateRunnable?.let { mainHandler.removeCallbacks(it) }
            val runnable = Runnable {
                executeUpdate(appContext)
            }
            pendingUpdateRunnable = runnable
            mainHandler.postDelayed(runnable, DEBOUNCE_MS)
        }
    }

    fun updateWidgetsImmediate(context: Context) {
        val appContext = context.applicationContext
        synchronized(this) {
            pendingUpdateRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingUpdateRunnable = null
        }
        executeUpdate(appContext)
    }

    private fun executeUpdate(appContext: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(appContext)

            // 1. Update HabitSummaryWidgetProvider
            val summaryIntent = Intent(appContext, HabitSummaryWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val summaryIds = appWidgetManager.getAppWidgetIds(ComponentName(appContext, HabitSummaryWidgetProvider::class.java))
            if (summaryIds.isNotEmpty()) {
                summaryIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, summaryIds)
                appContext.sendBroadcast(summaryIntent)
            }

            // 2. Update HabitListWidgetProvider
            val listIntent = Intent(appContext, HabitListWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val listIds = appWidgetManager.getAppWidgetIds(ComponentName(appContext, HabitListWidgetProvider::class.java))
            if (listIds.isNotEmpty()) {
                listIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, listIds)
                appContext.sendBroadcast(listIntent)
            }

            // 3. Update TimerWidgetProvider
            try {
                TimerWidgetProvider.updateWidget(appContext)
            } catch (e: Exception) {
                Log.e("WidgetUpdateHelper", "Error updating TimerWidgetProvider", e)
            }
        } catch (e: Exception) {
            Log.e("WidgetUpdateHelper", "Error triggering widget updates", e)
        }
    }
}
