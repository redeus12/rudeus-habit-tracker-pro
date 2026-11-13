package com.example.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.toDomain
import com.example.model.Habit
import com.example.util.DateUtils
import com.example.util.NotificationHelper
import android.util.Log
import android.os.Build

class StreakAtRiskWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Log.d("StreakAtRiskWorker", "--- doWork STARTED ---")
        try {
            NotificationHelper.createNotificationChannel(applicationContext)
            val database = AppDatabase.getDatabase(applicationContext)
            val activeEntities = database.habitDao().getAllHabitsList()
            val completionsList = database.habitCompletionDao().getAllCompletionsList()
            val freezesList = database.habitFreezeDao().getAllFreezesList()
            val completionsMap = completionsList.groupBy { it.habitId }
                .mapValues { entry -> entry.value.map { it.dateStr }.toSet() }
            val freezesMap = freezesList.groupBy { it.habitId }
                .mapValues { entry -> entry.value.map { it.dateStr }.toSet() }
            val habits = activeEntities.map { entity ->
                val comps = completionsMap[entity.id] ?: emptySet()
                val frz = freezesMap[entity.id] ?: emptySet()
                entity.toDomain(comps, frz)
            }
            val today = DateUtils.todayStr()
            
            Log.d("StreakAtRiskWorker", "Fetched ${habits.size} active habits from database for today's check ($today).")
            
            habits.forEach { habit ->
                val isScheduled = DateUtils.isScheduled(habit, today)
                val isDone = DateUtils.isDone(habit, today)
                val isFrozen = DateUtils.isFrozen(habit, today)
                val streak = DateUtils.currentStreak(habit)
                
                Log.d("StreakAtRiskWorker", "Analyzing habit: '${habit.name}' -> scheduledToday=$isScheduled, doneToday=$isDone, frozenToday=$isFrozen, streak=$streak")
                
                if (isScheduled && !isDone && !isFrozen && streak >= 3) {
                    Log.d("StreakAtRiskWorker", "Habit '${habit.name}' meets Streak At Risk requirements! Showing notification...")
                    showStreakAtRiskNotification(habit, streak)
                }
            }
        } catch (e: Exception) {
            Log.e("StreakAtRiskWorker", "Error occurred during background work execution!", e)
            return Result.failure()
        }
        
        Log.d("StreakAtRiskWorker", "--- doWork COMPLETED SUCCESSFULLY ---")
        return Result.success()
    }
    
    private fun showStreakAtRiskNotification(habit: Habit, streak: Int) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            habit.id.hashCode() + 2000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d("StreakAtRiskWorker", "Building Streak At Risk Notification for '${habit.name}' with channel ID: ${NotificationHelper.CHANNEL_ID}")
        val builder = NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Streak At Risk!")
            .setContentText("Don't lose your $streak-day streak on ${habit.name} — mark it done today!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            
        try {
            val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    applicationContext,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            Log.d("StreakAtRiskWorker", "Permission status: POST_NOTIFICATIONS isGranted=$hasNotificationPermission")

            val notificationId = habit.id.hashCode() + 1000
            Log.d("StreakAtRiskWorker", "Posting Streak At Risk notification ID: $notificationId")
            with(NotificationManagerCompat.from(applicationContext)) {
                notify(notificationId, builder.build())
            }
            Log.d("StreakAtRiskWorker", "Streak At Risk notification posted successfully.")
        } catch (e: SecurityException) {
            Log.e("StreakAtRiskWorker", "SecurityException posting notification (permission issue)!", e)
        } catch (e: Exception) {
            Log.e("StreakAtRiskWorker", "General error posting notification!", e)
        }
    }
}
