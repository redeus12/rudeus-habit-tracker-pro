package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.HabitLedgerScreen
import com.example.ui.HabitViewModel
import com.example.ui.theme.MyApplicationTheme
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.worker.StreakAtRiskWorker
import com.example.util.NotificationHelper
import com.example.receiver.WidgetUpdateReceiver
import com.example.receiver.WidgetUpdateHelper
import android.util.Log
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    // Initialize sound effects engine
    try {
        com.example.util.AppFeedbackHelper.init(this)
    } catch (e: Exception) {
        Log.e("MainActivity", "Error initializing AppFeedbackHelper", e)
    }

    Log.d("MainActivity", "onCreate started. Setting up notifications and background tasks...")
    
    // Ensure the notification channel is created at the earliest moment
    try {
        NotificationHelper.createNotificationChannel(this)
        Log.d("MainActivity", "Successfully verified/created notification channel on startup.")
    } catch (e: Exception) {
        Log.e("MainActivity", "Error creating notification channel on startup", e)
    }

    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 20)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }
    var delay = target.timeInMillis - now.timeInMillis
    if (delay < 0) delay += TimeUnit.DAYS.toMillis(1)

    Log.d("MainActivity", "Scheduling StreakAtRiskWorker: target 20:00, initial delay = ${delay / 1000} seconds.")

    val workRequest = PeriodicWorkRequestBuilder<StreakAtRiskWorker>(24, TimeUnit.HOURS)
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .build()
    
    try {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "streak_at_risk_work",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        Log.d("MainActivity", "StreakAtRiskWorker enqueued successfully via WorkManager.")
    } catch (e: Exception) {
        Log.e("MainActivity", "Failed to enqueue StreakAtRiskWorker via WorkManager", e)
    }

    // Schedule midnight widget updates and refresh all widget instances
    try {
        WidgetUpdateReceiver.scheduleMidnightWidgetUpdate(this)
        WidgetUpdateHelper.updateWidgets(this)
        Log.d("MainActivity", "Scheduled midnight widget updates and triggered initial widget refresh.")
    } catch (e: Exception) {
        Log.e("MainActivity", "Error scheduling/updating widgets on launch", e)
    }

    setContent {
      val viewModel: HabitViewModel = viewModel()
      val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
      val useDynamicColor by viewModel.useDynamicColor.collectAsStateWithLifecycle()
      val backgroundConfig by viewModel.backgroundConfig.collectAsStateWithLifecycle()
      val isGlassEnabled = backgroundConfig.isEnabled && backgroundConfig.imagePath != null
      val accentColor = androidx.compose.ui.graphics.Color(backgroundConfig.accentColorHex)
      val configuration = androidx.compose.ui.platform.LocalConfiguration.current
      val density = androidx.compose.ui.platform.LocalDensity.current
      val rootSize = androidx.compose.runtime.remember(configuration.screenWidthDp, configuration.screenHeightDp) {
          com.example.ui.theme.GlassRootSize(
              width = androidx.compose.ui.unit.Dp(configuration.screenWidthDp.toFloat()),
              height = androidx.compose.ui.unit.Dp(configuration.screenHeightDp.toFloat())
          )
      }
      val rootSizePx = androidx.compose.runtime.remember(configuration.screenWidthDp, configuration.screenHeightDp, density) {
          with(density) {
              androidx.compose.ui.unit.IntSize(
                  androidx.compose.ui.unit.Dp(configuration.screenWidthDp.toFloat()).roundToPx(),
                  androidx.compose.ui.unit.Dp(configuration.screenHeightDp.toFloat()).roundToPx()
              )
          }
      }
      val blurredWallpaperState = com.example.util.rememberBlurredWallpaperState(backgroundConfig, rootSizePx)

      // Automatic Wallpaper Rotation Coroutine: delays until next boundary time (no 1-second polling)
      androidx.compose.runtime.LaunchedEffect(
          backgroundConfig.isEnabled,
          backgroundConfig.rotationMode,
          backgroundConfig.rotationIntervalMinutes,
          backgroundConfig.rotationAnchorTime,
          backgroundConfig.wallpapers.size
      ) {
          if (backgroundConfig.isEnabled && (backgroundConfig.rotationMode == com.example.ui.theme.RotationMode.INTERVAL || backgroundConfig.rotationMode == com.example.ui.theme.RotationMode.DAY_NIGHT)) {
              while (true) {
                  val now = System.currentTimeMillis()
                  val nextChange = com.example.ui.theme.BackgroundPreferences.computeNextChangeTime(backgroundConfig, now)
                  if (nextChange == Long.MAX_VALUE) break
                  val delayMs = (nextChange - now).coerceAtLeast(1000L)
                  kotlinx.coroutines.delay(delayMs)
                  com.example.ui.theme.BackgroundPreferences.evaluateRotation(System.currentTimeMillis())
              }
          }
      }

      androidx.compose.runtime.CompositionLocalProvider(
          com.example.ui.theme.LocalBackgroundConfig provides backgroundConfig,
          com.example.ui.theme.LocalGlassRootSize provides rootSize,
          com.example.ui.theme.LocalBlurredWallpaper provides blurredWallpaperState
      ) {
          MyApplicationTheme(
              themeMode = themeMode,
              useDynamicColor = useDynamicColor,
              isGlassEnabled = isGlassEnabled,
              accentColor = accentColor
          ) {
            HabitLedgerScreen(modifier = Modifier.fillMaxSize(), viewModel = viewModel)
          }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    try {
        com.example.ui.theme.BackgroundPreferences.evaluateRotation()
    } catch (e: Exception) {
        Log.e("MainActivity", "Error evaluating background rotation on resume", e)
    }
  }
}

