package com.example.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.service.TimerService

class TimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        updateWidget(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateWidget(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, TimerWidgetProvider::class.java)
            )
            if (appWidgetIds.isNotEmpty()) {
                updateWidget(context, appWidgetManager, appWidgetIds)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val isRunning = TimerService.isRunning.value
            val isPaused = TimerService.isPaused.value
            val habitId = TimerService.activeHabitId.value
            val habitName = TimerService.activeHabitName.value
            val elapsedSeconds = TimerService.elapsedSeconds.value
            val trackName = TimerService.currentTrackName.value

            Log.d("TimerWidgetProvider", "Updating timer widget. isRunning=$isRunning, isPaused=$isPaused, habit=$habitName, elapsed=$elapsedSeconds, track=$trackName")

            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_timer)

                if (habitId != null && (isRunning || isPaused)) {
                    // Show active state
                    views.setViewVisibility(R.id.widget_timer_active_layout, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_timer_idle_layout, View.GONE)

                    // Set Habit Name
                    views.setTextViewText(R.id.widget_timer_habit_name, habitName ?: "Active Habit")

                    // Set Elapsed Time (MM:SS)
                    val hours = elapsedSeconds / 3600
                    val minutes = (elapsedSeconds % 3600) / 60
                    val secs = elapsedSeconds % 60
                    val timeStr = if (hours > 0) {
                        String.format("%02d:%02d:%02d", hours, minutes, secs)
                    } else {
                        String.format("%02d:%02d", minutes, secs)
                    }
                    views.setTextViewText(R.id.widget_timer_elapsed_time, timeStr)

                    // Set Track Name
                    val mode = com.example.ui.theme.PlaylistPreferences.musicSourceMode.value
                    val displayTrack = when (mode) {
                        com.example.ui.theme.MusicSourceMode.EXTERNAL -> "Media: YouTube Music / Active Player"
                        com.example.ui.theme.MusicSourceMode.LOCAL -> {
                            if (!trackName.isNullOrBlank()) "Music: $trackName" else "No local track playing"
                        }
                        com.example.ui.theme.MusicSourceMode.OFF -> "Music: Off"
                    }
                    views.setTextViewText(R.id.widget_timer_track_name, displayTrack)

                    // Configure Play/Pause PendingIntent
                    val isTogglingToPlay = !isRunning || isPaused
                    val playPauseIntent = Intent(context, TimerService::class.java).apply {
                        if (isTogglingToPlay) {
                            action = TimerService.ACTION_START
                            putExtra(TimerService.EXTRA_HABIT_ID, habitId)
                            putExtra(TimerService.EXTRA_HABIT_NAME, habitName)
                        } else {
                            action = TimerService.ACTION_PAUSE
                        }
                    }
                    
                    val playPausePendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isTogglingToPlay) {
                        PendingIntent.getForegroundService(
                            context,
                            widgetId + 100,
                            playPauseIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    } else {
                        PendingIntent.getService(
                            context,
                            widgetId + 100,
                            playPauseIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    }

                    views.setOnClickPendingIntent(R.id.widget_timer_btn_play_pause, playPausePendingIntent)

                    // Set correct icon for play/pause
                    val playPauseIcon = if (isRunning && !isPaused) {
                        android.R.drawable.ic_media_pause
                    } else {
                        android.R.drawable.ic_media_play
                    }
                    views.setImageViewResource(R.id.widget_timer_btn_play_pause, playPauseIcon)

                    // Configure Stop PendingIntent
                    val stopIntent = Intent(context, TimerService::class.java).apply {
                        action = TimerService.ACTION_STOP
                    }
                    val stopPendingIntent = PendingIntent.getService(
                        context,
                        widgetId + 200,
                        stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_timer_btn_stop, stopPendingIntent)

                    // Configure Prev Track PendingIntent
                    val prevIntent = Intent(context, TimerService::class.java).apply {
                        action = TimerService.ACTION_PREVIOUS_TRACK
                    }
                    val prevPendingIntent = PendingIntent.getService(
                        context,
                        widgetId + 300,
                        prevIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_timer_btn_prev, prevPendingIntent)

                    // Configure Next Track PendingIntent
                    val nextIntent = Intent(context, TimerService::class.java).apply {
                        action = TimerService.ACTION_NEXT_TRACK
                    }
                    val nextPendingIntent = PendingIntent.getService(
                        context,
                        widgetId + 400,
                        nextIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_timer_btn_next, nextPendingIntent)

                } else {
                    // Show idle state
                    views.setViewVisibility(R.id.widget_timer_active_layout, View.GONE)
                    views.setViewVisibility(R.id.widget_timer_idle_layout, View.VISIBLE)
                }

                // Global click action to open the app
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    widgetId,
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_timer_root, pendingIntent)

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
