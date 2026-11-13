package com.example.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.toDomain
import com.example.data.toEntity
import com.example.model.Habit
import com.example.util.DateUtils
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("NotificationReceiver", "--- onReceive STARTED ---")
        Log.d("NotificationReceiver", "Action received: $action")

        // 1. Handle Boot Completed
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == "android.intent.action.QUICKBOOT_POWERON" || 
            action == "android.intent.action.REBOOT"
        ) {
            Log.d("NotificationReceiver", "Boot broadcast caught! Triggering rescheduling...")
            rescheduleAllHabits(context)
            WidgetUpdateReceiver.scheduleMidnightWidgetUpdate(context)
            return
        }

        // 2. Handle Mark Done action from Notification buttons
        if (action == "ACTION_MARK_DONE") {
            val habitId = intent.getStringExtra("habit_id")
            Log.d("NotificationReceiver", "Mark Done broadcast caught for habit ID: $habitId")
            if (habitId != null) {
                markHabitDone(context, habitId)
            }
            return
        }

        // 3. Otherwise, it is a scheduled habit reminder trigger
        val habitId = intent.getStringExtra("habit_id")
        val habitName = intent.getStringExtra("habit_name")
        Log.d("NotificationReceiver", "Reminder trigger received: habitId=$habitId, habitName=$habitName")

        if (habitId == null || habitName == null) {
            Log.e("NotificationReceiver", "Required extras habit_id or habit_name are missing!")
            return
        }

        showReminderNotification(context, habitId, habitName)
    }

    private fun showReminderNotification(context: Context, habitId: String, habitName: String) {
        try {
            Log.d("NotificationReceiver", "Creating notification channel before showing reminder...")
            NotificationHelper.createNotificationChannel(context)

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                habitId.hashCode() + 5000,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Create notification builder
            Log.d("NotificationReceiver", "Building notification for: $habitName")
            val builder = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Habit Reminder")
                .setContentText("Time for your habit: $habitName")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(true)

            // Add Mark Done action
            val markDoneIntent = Intent(context, NotificationReceiver::class.java).apply {
                action = "ACTION_MARK_DONE"
                putExtra("habit_id", habitId)
            }
            val markDonePendingIntent = PendingIntent.getBroadcast(
                context,
                habitId.hashCode() + 6000,
                markDoneIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.checkbox_on_background, "Mark done", markDonePendingIntent)

            // Double check permissions before posting
            val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            Log.d("NotificationReceiver", "Permission status: POST_NOTIFICATIONS isGranted=$hasNotificationPermission")

            // Notify
            val notificationId = habitId.hashCode()
            Log.d("NotificationReceiver", "Attempting to post notification (ID: $notificationId) to NotificationManagerCompat...")
            
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, builder.build())
            }
            Log.d("NotificationReceiver", "Notification posted successfully.")

        } catch (e: SecurityException) {
            Log.e("NotificationReceiver", "SecurityException occurred while posting notification! (Usually missing permission)", e)
        } catch (e: Exception) {
            Log.e("NotificationReceiver", "General Exception occurred while building or posting notification!", e)
        }
    }

    private fun markHabitDone(context: Context, habitId: String) {
        val goAsync = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("NotificationReceiver", "Fetching database in markHabitDone...")
                val db = AppDatabase.getDatabase(context)
                val dao = db.habitDao()
                val entity = dao.getHabitById(habitId)
                if (entity != null) {
                    val habit = entity.toDomain()
                    val today = DateUtils.todayStr()
                    
                    if (!habit.completions.contains(today)) {
                        val updatedCompletions = habit.completions + today
                        val updatedHabit = habit.copy(completions = updatedCompletions)
                        dao.insertHabit(updatedHabit.toEntity())
                        Log.d("NotificationReceiver", "Habit '${habit.name}' marked completed in database.")
                        WidgetUpdateHelper.updateWidgets(context)
                    } else {
                        Log.d("NotificationReceiver", "Habit '${habit.name}' was already completed for today.")
                    }

                    // Dismiss the notification
                    val notificationId = habitId.hashCode()
                    Log.d("NotificationReceiver", "Dismissing notification ID: $notificationId")
                    val manager = NotificationManagerCompat.from(context)
                    manager.cancel(notificationId)
                } else {
                    Log.e("NotificationReceiver", "Habit with ID $habitId not found in database.")
                }
            } catch (e: Exception) {
                Log.e("NotificationReceiver", "Exception in markHabitDone coroutine", e)
            } finally {
                Log.d("NotificationReceiver", "markHabitDone background work finished.")
                goAsync.finish()
            }
        }
    }

    private fun rescheduleAllHabits(context: Context) {
        val goAsync = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("NotificationReceiver", "Fetching habits to reschedule after boot...")
                val db = AppDatabase.getDatabase(context)
                val habits = db.habitDao().getAllHabitsList().map { it.toDomain() }
                Log.d("NotificationReceiver", "Found ${habits.size} habits. Processing scheduling...")
                
                var scheduledCount = 0
                habits.forEach { habit ->
                    if (habit.reminderTime != null && !habit.isArchived) {
                        Log.d("NotificationReceiver", "Rescheduling habit: ${habit.name} at ${habit.reminderTime}")
                        NotificationHelper.scheduleHabitReminder(context, habit)
                        scheduledCount++
                    }
                }
                Log.d("NotificationReceiver", "Successfully rescheduled $scheduledCount habit reminders.")
            } catch (e: Exception) {
                Log.e("NotificationReceiver", "Exception in rescheduleAllHabits coroutine", e)
            } finally {
                Log.d("NotificationReceiver", "rescheduleAllHabits background work finished.")
                goAsync.finish()
            }
        }
    }
}
