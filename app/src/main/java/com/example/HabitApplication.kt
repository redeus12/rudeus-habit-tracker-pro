package com.example

import android.app.Application
import android.util.Log
import com.example.service.TimerService

class HabitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            TimerService.restoreTimerState(this)
            Log.d("HabitApplication", "TimerService state restored on Application.onCreate")
        } catch (e: Exception) {
            Log.e("HabitApplication", "Error restoring timer state in Application", e)
        }
    }
}
