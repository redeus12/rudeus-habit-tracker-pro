package com.example.util

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Density
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.model.Habit
import com.example.model.Task
import com.example.ui.components.AllHabitsShareCard
import com.example.ui.components.DayReportShareCard
import com.example.ui.components.ShareDayReportModal
import com.example.ui.components.ShareSingleHabitModal
import com.example.ui.components.SingleHabitShareCard
import com.example.ui.components.computeDayReportData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ShareHelper {

    private fun getDayNames(days: List<Int>): String {
        if (days.size == 7) return "Every day"
        val names = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        return days.sorted().mapNotNull { if (it in 0..6) names[it] else null }.joinToString(", ")
    }

    private fun formatDisplayDate(dateStr: String): String {
        return try {
            val date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US))
            date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US))
        } catch (e: Exception) {
            dateStr
        }
    }

    fun saveAndShareBitmap(
        context: Context,
        bitmap: Bitmap,
        chooserTitle: String,
        captionText: String
    ) {
        try {
            val imagesDir = File(context.cacheDir, "images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            val imageFile = File(imagesDir, "share_${System.currentTimeMillis()}.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, captionText)
                putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not generate share image", Toast.LENGTH_SHORT).show()
        }
    }

    // =========================================================================
    // 1. SHARE DAILY PROGRESS AS A COMPOSE-RENDERED OFF-SCREEN GLASS POSTER
    // =========================================================================
    fun shareDailyProgress(
        context: Context,
        dateStr: String,
        habitsToday: List<Habit>,
        tasks: List<Task>
    ) {
        var act: ComponentActivity? = null
        var c = context
        while (c is ContextWrapper) {
            if (c is ComponentActivity) {
                act = c
                break
            }
            c = c.baseContext
        }

        if (act != null && !act.isFinishing && !act.isDestroyed) {
            val dialog = android.app.Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val composeView = ComposeView(act).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    ShareDayReportModal(
                        isOpen = true,
                        dateStr = dateStr,
                        habits = habitsToday,
                        onClose = { dialog.dismiss() }
                    )
                }
            }
            composeView.setViewTreeLifecycleOwner(act)
            composeView.setViewTreeSavedStateRegistryOwner(act)
            composeView.setViewTreeViewModelStoreOwner(act)
            dialog.setContentView(composeView)
            dialog.show()
        } else {
            // Fallback for headless / test execution
            shareDailyReportDirect(context, dateStr, habitsToday, showFooter = true)
        }
    }

    fun shareDailyReportDirect(
        context: Context,
        dateStr: String,
        habitsToday: List<Habit>,
        showFooter: Boolean = true
    ) {
        val bitmap = generateDailyReportBitmap(context, dateStr, habitsToday, showFooter)
        val reportData = computeDayReportData(dateStr, habitsToday)
        val caption = if (reportData.scheduledCount > 0) {
            "🔥 My Daily Progress: ${reportData.percentage}% completed (${reportData.completedCount} of ${reportData.scheduledCount} habits done) on ${reportData.pillDate}. Building discipline with Rudeus Habit! 💪"
        } else {
            "📅 My Day Report for ${reportData.pillDate} on Rudeus Habit. Building daily discipline! 💪"
        }
        saveAndShareBitmap(context, bitmap, "Share Daily Progress Report", caption)
    }

    fun saveDailyReportToGallery(
        context: Context,
        dateStr: String,
        habitsToday: List<Habit>,
        showFooter: Boolean = true
    ): Boolean {
        val bitmap = generateDailyReportBitmap(context, dateStr, habitsToday, showFooter)
        return saveBitmapToGallery(context, bitmap, "RudeusHabit_DayReport_$dateStr")
    }

    fun generateDailyReportBitmap(
        context: Context,
        dateStr: String,
        habitsToday: List<Habit>,
        showFooter: Boolean = true
    ): Bitmap {
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            renderDayReportComposeViewToBitmap(context, dateStr, habitsToday, showFooter)
        } else {
            runBlocking(Dispatchers.Main) {
                renderDayReportComposeViewToBitmap(context, dateStr, habitsToday, showFooter)
            }
        }
    }

    private fun renderDayReportComposeViewToBitmap(
        context: Context,
        dateStr: String,
        habitsToday: List<Habit>,
        showFooter: Boolean
    ): Bitmap {
        var act: ComponentActivity? = null
        var c = context
        while (c is ContextWrapper) {
            if (c is ComponentActivity) {
                act = c
                break
            }
            c = c.baseContext
        }

        // Wait until the wallpaper is loaded before capturing
        val bgBundle = ShareBackgroundHelper.prepareBackground(context, 1080, 1920)

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                    DayReportShareCard(
                        dateStr = dateStr,
                        habits = habitsToday,
                        showFooter = showFooter,
                        backgroundImage = bgBundle.baseBackground,
                        blurredBackground = bgBundle.blurredBackground,
                        accentColor = bgBundle.accentColor,
                        glassOpacity = bgBundle.glassOpacity
                    )
                }
            }
        }

        val decorView = act?.window?.decorView as? ViewGroup
        if (act != null) {
            decorView?.setViewTreeLifecycleOwner(act)
            decorView?.setViewTreeSavedStateRegistryOwner(act)
            decorView?.setViewTreeViewModelStoreOwner(act)
            composeView.setViewTreeLifecycleOwner(act)
            composeView.setViewTreeSavedStateRegistryOwner(act)
            composeView.setViewTreeViewModelStoreOwner(act)
        } else {
            val standaloneLifecycle = StandaloneLifecycleOwner()
            composeView.setViewTreeLifecycleOwner(standaloneLifecycle)
            composeView.setViewTreeSavedStateRegistryOwner(StandaloneSavedStateRegistryOwner(standaloneLifecycle))
            composeView.setViewTreeViewModelStoreOwner(StandaloneViewModelStoreOwner())
        }

        val addedToDecor = if (decorView != null) {
            decorView.addView(composeView, ViewGroup.LayoutParams(1080, 1920))
            true
        } else {
            false
        }

        try {
            // Fixed height 1920px (9:16), width 1080px (Density 2f, 1f)
            val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
            composeView.measure(widthSpec, heightSpec)
            composeView.layout(0, 0, 1080, 1920)

            val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            composeView.draw(canvas)
            return bitmap
        } finally {
            if (addedToDecor && decorView != null) {
                decorView.removeView(composeView)
            }
        }
    }

    // =========================================================================
    // 2. GENERATE & SHARE RICH SINGLE HABIT MASTERY POSTER
    // =========================================================================
    fun generateSingleHabitBitmap(
        context: Context,
        habit: Habit,
        analytics: com.example.ui.HabitWithAnalytics? = null,
        showFooter: Boolean = true
    ): Bitmap {
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            renderSingleHabitComposeViewToBitmap(context, habit, showFooter)
        } else {
            runBlocking(Dispatchers.Main) {
                renderSingleHabitComposeViewToBitmap(context, habit, showFooter)
            }
        }
    }

    private fun renderSingleHabitComposeViewToBitmap(
        context: Context,
        habit: Habit,
        showFooter: Boolean
    ): Bitmap {
        var act: ComponentActivity? = null
        var c = context
        while (c is ContextWrapper) {
            if (c is ComponentActivity) {
                act = c
                break
            }
            c = c.baseContext
        }

        // Wait until wallpaper and blurred background are ready
        val bgBundle = ShareBackgroundHelper.prepareBackground(context, 1080, 1920)

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                    SingleHabitShareCard(
                        habit = habit,
                        todayStr = DateUtils.todayStr(),
                        showFooter = showFooter,
                        wallpaperBitmap = bgBundle.baseBackground,
                        blurredBackgroundBitmap = bgBundle.blurredBackground,
                        accentColor = bgBundle.accentColor,
                        glassOpacity = bgBundle.glassOpacity
                    )
                }
            }
        }

        val decorView = act?.window?.decorView as? ViewGroup
        if (act != null) {
            decorView?.setViewTreeLifecycleOwner(act)
            decorView?.setViewTreeSavedStateRegistryOwner(act)
            decorView?.setViewTreeViewModelStoreOwner(act)
            composeView.setViewTreeLifecycleOwner(act)
            composeView.setViewTreeSavedStateRegistryOwner(act)
            composeView.setViewTreeViewModelStoreOwner(act)
        } else {
            val standaloneLifecycle = StandaloneLifecycleOwner()
            composeView.setViewTreeLifecycleOwner(standaloneLifecycle)
            composeView.setViewTreeSavedStateRegistryOwner(StandaloneSavedStateRegistryOwner(standaloneLifecycle))
            composeView.setViewTreeViewModelStoreOwner(StandaloneViewModelStoreOwner())
        }

        val addedToDecor = if (decorView != null) {
            decorView.addView(composeView, ViewGroup.LayoutParams(1080, 1920))
            true
        } else {
            false
        }

        try {
            // Fixed height 1920px (9:16), width 1080px (Density 2f, 1f)
            val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
            composeView.measure(widthSpec, heightSpec)
            composeView.layout(0, 0, 1080, 1920)

            val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            composeView.draw(canvas)
            return bitmap
        } finally {
            if (addedToDecor && decorView != null) {
                decorView.removeView(composeView)
            }
        }
    }

    fun shareSingleHabit(
        context: Context,
        habit: Habit,
        analytics: com.example.ui.HabitWithAnalytics? = null
    ) {
        var act: ComponentActivity? = null
        var c = context
        while (c is ContextWrapper) {
            if (c is ComponentActivity) {
                act = c
                break
            }
            c = c.baseContext
        }

        if (act != null && !act.isFinishing && !act.isDestroyed) {
            val dialog = android.app.Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val composeView = ComposeView(act).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    ShareSingleHabitModal(
                        isOpen = true,
                        habit = habit,
                        todayStr = DateUtils.todayStr(),
                        onClose = { dialog.dismiss() }
                    )
                }
            }
            composeView.setViewTreeLifecycleOwner(act)
            composeView.setViewTreeSavedStateRegistryOwner(act)
            composeView.setViewTreeViewModelStoreOwner(act)
            dialog.setContentView(composeView)
            dialog.show()
        } else {
            // Fallback for headless / test execution
            shareSingleHabitDirect(context, habit, showFooter = true)
        }
    }

    fun shareSingleHabitDirect(
        context: Context,
        habit: Habit,
        showFooter: Boolean = true
    ) {
        val bitmap = generateSingleHabitBitmap(context, habit, showFooter = showFooter)
        val activeStreak = DateUtils.calculateStreak(habit, today = DateUtils.todayStr())
        val bestStreak = DateUtils.calculateLongestStreak(habit, today = DateUtils.todayStr())
        val accuracy30d = (DateUtils.completionRate(habit, 30) * 100f).roundToInt()

        val caption = "⚡ \"${habit.name}\" on Rudeus Habit!\n" +
                "🔥 Active Streak: $activeStreak days | 🏆 Best: $bestStreak days | 📊 30-Day Accuracy: $accuracy30d%"
        saveAndShareBitmap(context, bitmap, "Share Habit Spotlight", caption)
    }

    fun shareHabitStreak(context: Context, habit: Habit) {
        shareSingleHabit(context, habit, null)
    }

    // ==========================================
    // 3. SHARE CHALLENGE CONQUERED TROPHY IMAGE
    // ==========================================
    fun shareChallengeConquered(context: Context, habit: Habit) {
        val durDays = habit.durationDays ?: 30
        val longestStreak = DateUtils.longestStreak(habit)
        val totalCompletions = habit.completions.size

        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Warm Cream Canvas Gradient Background
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.parseColor("#FAF7F2"),
                Color.parseColor("#F3EBE0"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Warm Ambient Glowing Accents
        val trophyGlow = Paint().apply {
            shader = RadialGradient(
                width / 2f, 290f, 380f,
                Color.parseColor("#2AF59E0B"), Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width / 2f, 290f, 380f, trophyGlow)

        val emeraldGlow = Paint().apply {
            shader = RadialGradient(
                width * 0.15f, 950f, 350f,
                Color.parseColor("#18046A38"), Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.15f, 950f, 350f, emeraldGlow)

        val peachGlow = Paint().apply {
            shader = RadialGradient(
                width * 0.85f, 950f, 350f,
                Color.parseColor("#1ED97A5F"), Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.85f, 950f, 350f, peachGlow)

        // Outer Warm Card Frame
        val framePaint = Paint().apply {
            color = Color.parseColor("#E0D8CA")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(32f, 32f, width - 32f, height - 32f), 36f, 36f, framePaint)

        // Trophy Icon
        val trophyIconPaint = Paint().apply {
            textSize = 115f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("🏆", width / 2f, 205f, trophyIconPaint)

        val titlePaint = Paint().apply {
            color = Color.parseColor("#B45309")
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
            isAntiAlias = true
        }
        canvas.drawText("CHALLENGE CONQUERED!", width / 2f, 285f, titlePaint)

        val subTitlePaint = Paint().apply {
            color = Color.parseColor("#57534E")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("$durDays-Day Goal Successfully Completed", width / 2f, 340f, subTitlePaint)

        // Habit Name Ribbon
        val ribbonRect = RectF(80f, 400f, width - 80f, 530f)
        val ribbonBg = Paint().apply {
            color = Color.parseColor("#FFFFFF")
            isAntiAlias = true
        }
        canvas.drawRoundRect(ribbonRect, 20f, 20f, ribbonBg)
        val ribbonBorder = Paint().apply {
            color = Color.parseColor("#10B981")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            isAntiAlias = true
        }
        canvas.drawRoundRect(ribbonRect, 20f, 20f, ribbonBorder)

        val hNamePaint = Paint().apply {
            color = Color.parseColor("#1C1917")
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("✨ ${habit.name} ✨", width / 2f, 480f, hNamePaint)

        // Grid of accomplishments
        val gridY = 575f
        val cardWidth = (width - 160f - 30f) / 2f
        val cardHeight = 155f

        val accomplishments = listOf(
            Triple("🔥 Longest Streak", "$longestStreak Days", "#D97706"),
            Triple("✅ Total Completed", "$totalCompletions Days", "#059669"),
            Triple("🎯 Target Achieved", "$durDays / $durDays Days", "#0284C7"),
            Triple("👑 Discipline Score", "100% Mastered", "#7C3AED")
        )

        accomplishments.forEachIndexed { i, item ->
            val row = i / 2
            val col = i % 2
            val left = 80f + col * (cardWidth + 30f)
            val top = gridY + row * (cardHeight + 20f)
            val rect = RectF(left, top, left + cardWidth, top + cardHeight)

            val cBg = Paint().apply {
                color = Color.parseColor("#FFFFFF")
                isAntiAlias = true
            }
            canvas.drawRoundRect(rect, 18f, 18f, cBg)
            val cStroke = Paint().apply {
                color = Color.parseColor("#E8E0D2")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                isAntiAlias = true
            }
            canvas.drawRoundRect(rect, 18f, 18f, cStroke)

            val vPaint = Paint().apply {
                color = Color.parseColor(item.third)
                textSize = 36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText(item.second, rect.centerX(), rect.top + 68f, vPaint)

            val lPaint = Paint().apply {
                color = Color.parseColor("#78716C")
                textSize = 21f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText(item.first, rect.centerX(), rect.top + 115f, lPaint)
        }

        // Bottom CTA
        val footerRect = RectF(80f, height - 240f, width - 80f, height - 70f)
        val footerBg = Paint().apply {
            shader = LinearGradient(
                footerRect.left, footerRect.top, footerRect.right, footerRect.bottom,
                Color.parseColor("#FFFBF5"),
                Color.parseColor("#FDF6EC"),
                Shader.TileMode.CLAMP
            )
            isAntiAlias = true
        }
        canvas.drawRoundRect(footerRect, 22f, 22f, footerBg)

        val footerBorder = Paint().apply {
            color = Color.parseColor("#F5CBA7")
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            isAntiAlias = true
        }
        canvas.drawRoundRect(footerRect, 22f, 22f, footerBorder)

        val ctaPaint = Paint().apply {
            color = Color.parseColor("#92400E")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("🎉 Challenge Conquered on Rudeus Habit!", width / 2f, height - 160f, ctaPaint)

        val subCtaPaint = Paint().apply {
            color = Color.parseColor("#78716C")
            textSize = 21f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("Download on Google Play & start building your streak", width / 2f, height - 110f, subCtaPaint)

        val caption = "🏆 I just conquered the $durDays-Day Challenge for \"${habit.name}\" on Rudeus Habit! 🎉 Best streak: $longestStreak days! 💪"
        saveAndShareBitmap(context, bitmap, "Share Challenge Conquered", caption)
    }

    // =========================================================================
    // 4. SAVE BITMAP TO DEVICE GALLERY (Zero permission MediaStore / Pictures)
    // =========================================================================
    fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        title: String = "RudeusHabit_Curves"
    ): Boolean {
        return try {
            val filename = "${title}_${System.currentTimeMillis()}.png"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/RudeusHabit")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    Toast.makeText(context, "Saved to Pictures/RudeusHabit in your Gallery! 📸", Toast.LENGTH_LONG).show()
                    true
                } else {
                    false
                }
            } else {
                val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                val appDir = File(imagesDir, "RudeusHabit")
                if (!appDir.exists()) appDir.mkdirs()
                val imageFile = File(appDir, filename)
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = Uri.fromFile(imageFile)
                context.sendBroadcast(mediaScanIntent)
                Toast.makeText(context, "Saved to Pictures/RudeusHabit! 📸", Toast.LENGTH_LONG).show()
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to save image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    // =========================================================================
    // 5. GENERATE ALL HABITS CURVES POSTER (COMPOSE OFF-SCREEN RENDERING)
    // =========================================================================
    fun generateHabitsCurvesBitmap(
        context: Context,
        habitsWithAnalytics: List<com.example.ui.HabitWithAnalytics>,
        showFooter: Boolean = true
    ): Bitmap {
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            renderComposeViewToBitmap(context, habitsWithAnalytics, showFooter)
        } else {
            runBlocking(Dispatchers.Main) {
                renderComposeViewToBitmap(context, habitsWithAnalytics, showFooter)
            }
        }
    }

    private fun renderComposeViewToBitmap(
        context: Context,
        habitsWithAnalytics: List<com.example.ui.HabitWithAnalytics>,
        showFooter: Boolean
    ): Bitmap {
        var act: ComponentActivity? = null
        var c = context
        while (c is ContextWrapper) {
            if (c is ComponentActivity) {
                act = c
                break
            }
            c = c.baseContext
        }

        // Initial background bundle for measuring
        val initialBgBundle = ShareBackgroundHelper.prepareBackground(context, 1080, 1920)

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                    AllHabitsShareCard(
                        habitsWithAnalytics = habitsWithAnalytics,
                        showFooter = showFooter,
                        backgroundImage = initialBgBundle.baseBackground,
                        blurredBackground = initialBgBundle.blurredBackground,
                        accentColor = initialBgBundle.accentColor,
                        glassOpacity = initialBgBundle.glassOpacity
                    )
                }
            }
        }

        val decorView = act?.window?.decorView as? ViewGroup
        if (act != null) {
            decorView?.setViewTreeLifecycleOwner(act)
            decorView?.setViewTreeSavedStateRegistryOwner(act)
            decorView?.setViewTreeViewModelStoreOwner(act)
            composeView.setViewTreeLifecycleOwner(act)
            composeView.setViewTreeSavedStateRegistryOwner(act)
            composeView.setViewTreeViewModelStoreOwner(act)
        } else {
            val standaloneLifecycle = StandaloneLifecycleOwner()
            composeView.setViewTreeLifecycleOwner(standaloneLifecycle)
            composeView.setViewTreeSavedStateRegistryOwner(StandaloneSavedStateRegistryOwner(standaloneLifecycle))
            composeView.setViewTreeViewModelStoreOwner(StandaloneViewModelStoreOwner())
        }

        val addedToDecor = if (decorView != null) {
            decorView.addView(composeView, ViewGroup.LayoutParams(1080, ViewGroup.LayoutParams.WRAP_CONTENT))
            true
        } else {
            false
        }

        try {
            // Measure content height (540dp logical width * 2f density = 1080px, height unconstrained)
            val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            val heightSpecUnspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            composeView.measure(widthSpec, heightSpecUnspecified)

            val measuredH = composeView.measuredHeight.coerceIn(720, 4096)

            // If measured height differs significantly, prepare matching background bundle
            val finalBgBundle = if (kotlin.math.abs(measuredH - initialBgBundle.baseBackground.height) > 64) {
                ShareBackgroundHelper.prepareBackground(context, 1080, measuredH)
            } else {
                initialBgBundle
            }

            composeView.setContent {
                CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                    AllHabitsShareCard(
                        habitsWithAnalytics = habitsWithAnalytics,
                        showFooter = showFooter,
                        backgroundImage = finalBgBundle.baseBackground,
                        blurredBackground = finalBgBundle.blurredBackground,
                        accentColor = finalBgBundle.accentColor,
                        glassOpacity = finalBgBundle.glassOpacity
                    )
                }
            }

            val heightSpecExact = View.MeasureSpec.makeMeasureSpec(measuredH, View.MeasureSpec.EXACTLY)
            composeView.measure(widthSpec, heightSpecExact)
            composeView.layout(0, 0, 1080, measuredH)

            val bitmap = Bitmap.createBitmap(1080, measuredH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            composeView.draw(canvas)
            return bitmap
        } finally {
            if (addedToDecor && decorView != null) {
                decorView.removeView(composeView)
            }
        }
    }

    // Standalone Lifecycle Owners for headless Compose execution
    private class StandaloneLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
        override val lifecycle: Lifecycle get() = registry
    }

    private class StandaloneSavedStateRegistryOwner(
        private val lifecycleOwner: LifecycleOwner
    ) : SavedStateRegistryOwner {
        private val controller = SavedStateRegistryController.create(this).apply {
            performRestore(null)
        }
        override val lifecycle: Lifecycle get() = lifecycleOwner.lifecycle
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
    }

    private class StandaloneViewModelStoreOwner : ViewModelStoreOwner {
        private val store = ViewModelStore()
        override val viewModelStore: ViewModelStore get() = store
    }

    // =========================================================================
    // 6. SHARE HABITS CURVES INFOGRAPHIC (Direct Share Sheet)
    // =========================================================================
    fun shareHabitsCurvesInfographic(
        context: Context,
        habitsWithAnalytics: List<com.example.ui.HabitWithAnalytics>,
        showFooter: Boolean = true
    ) {
        val bitmap = generateHabitsCurvesBitmap(context, habitsWithAnalytics, showFooter)
        val activeCount = habitsWithAnalytics.count { !it.habit.isArchived }
        val avgConsistency = if (activeCount > 0) {
            (habitsWithAnalytics.filter { !it.habit.isArchived }.map { it.completionRate30d.toDouble() }.average() * 100.0).roundToInt()
        } else 0

        val caption = "📈 My Habit Consistency Curves on \"Rudeus Habit\"! Building unbreakable discipline day by day ($activeCount habits tracked, $avgConsistency% consistency rate) 🔥💪"
        saveAndShareBitmap(context, bitmap, "Share Rudeus Habit Curves", caption)
    }

    // =========================================================================
    // 7. SAVE HABITS CURVES POSTER DIRECTLY TO GALLERY
    // =========================================================================
    fun saveHabitsCurvesToGallery(
        context: Context,
        habitsWithAnalytics: List<com.example.ui.HabitWithAnalytics>,
        showFooter: Boolean = true
    ): Boolean {
        val bitmap = generateHabitsCurvesBitmap(context, habitsWithAnalytics, showFooter)
        return saveBitmapToGallery(context, bitmap, "RudeusHabit_ConsistencyCurves")
    }
}
