package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Calendar
import java.util.Random
import java.util.UUID

enum class RotationMode(val displayName: String) {
    OFF("Off"),
    INTERVAL("Every N hours"),
    APP_OPEN("On app open"),
    DAY_NIGHT("Day & night")
}

@Immutable
data class WallpaperItem(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val accentColorHex: Long = 0xFFF59E0BL,
    val isBright: Boolean = false
)

@Immutable
data class BackgroundConfig(
    val isEnabled: Boolean = false,
    val imagePath: String? = null,
    val blurRadius: Float = 24f,        // 0f to 40f, default 24f
    val glassOpacity: Float = 0.25f,    // 0.10f to 0.60f, default 0.25f
    val dim: Float = 0.25f,             // 0f to 0.60f, default 0.25f
    val accentColorHex: Long = 0xFFF59E0BL, // Default AmberGold
    val wallpapers: List<WallpaperItem> = emptyList(),
    val rotationMode: RotationMode = RotationMode.OFF,
    val rotationIntervalMinutes: Int = 60, // 60 (1h), 180 (3h), 360 (6h), 720 (12h), 1440 (24h)
    val rotationAnchorTime: Long = 0L,
    val shuffle: Boolean = false,
    val activeIndex: Int = 0,
    val dayWallpaperId: String? = null,
    val nightWallpaperId: String? = null
) {
    val activeWallpaper: WallpaperItem? get() {
        if (wallpapers.isEmpty()) return null
        val idx = activeIndex.coerceIn(0, wallpapers.size - 1)
        return wallpapers.getOrNull(idx)
    }

    val effectiveDim: Float get() {
        val isCurrentBright = activeWallpaper?.isBright ?: false
        return if (isCurrentBright) maxOf(dim, 0.30f) else dim
    }

    val dimAmount: Float get() = effectiveDim
}

object BackgroundPreferences {
    private const val PREFS_NAME = "habit_ledger_bg_prefs"
    private const val KEY_IS_ENABLED = "bg_is_enabled"
    private const val KEY_IMAGE_PATH = "bg_image_path"
    private const val KEY_BLUR_RADIUS = "bg_blur_radius"
    private const val KEY_GLASS_OPACITY = "bg_glass_opacity"
    private const val KEY_DIM = "bg_dim"
    private const val KEY_ACCENT_COLOR = "bg_accent_color"
    private const val KEY_WALLPAPERS_JSON = "bg_wallpapers_json"
    private const val KEY_ROTATION_MODE = "bg_rotation_mode"
    private const val KEY_ROTATION_INTERVAL = "bg_rotation_interval"
    private const val KEY_ROTATION_ANCHOR = "bg_rotation_anchor"
    private const val KEY_SHUFFLE = "bg_shuffle"
    private const val KEY_ACTIVE_INDEX = "bg_active_index"
    private const val KEY_DAY_WALLPAPER_ID = "bg_day_wallpaper_id"
    private const val KEY_NIGHT_WALLPAPER_ID = "bg_night_wallpaper_id"

    private const val BG_LEGACY_FILENAME = "custom_app_background.jpg"
    private const val WALLPAPERS_DIR_NAME = "wallpapers"
    const val MAX_WALLPAPERS_COUNT = 12

    private var prefs: SharedPreferences? = null
    private val _config = MutableStateFlow(BackgroundConfig())
    val config: StateFlow<BackgroundConfig> = _config.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = p

            val wallpapersDir = File(context.filesDir, WALLPAPERS_DIR_NAME).apply {
                if (!exists()) mkdirs()
            }

            var wallpapersList = deserializeWallpapers(p.getString(KEY_WALLPAPERS_JSON, null))

            // Migration from legacy single file if needed
            val legacyFile = File(context.filesDir, BG_LEGACY_FILENAME)
            if (legacyFile.exists() && legacyFile.length() > 0) {
                if (wallpapersList.isEmpty()) {
                    try {
                        val newId = UUID.randomUUID().toString()
                        val migratedTarget = File(wallpapersDir, "$newId.jpg")
                        legacyFile.copyTo(migratedTarget, overwrite = true)
                        legacyFile.delete()

                        val accent = p.getLong(KEY_ACCENT_COLOR, 0xFFF59E0BL)
                        val item = WallpaperItem(
                            id = newId,
                            filePath = migratedTarget.absolutePath,
                            accentColorHex = accent,
                            isBright = false
                        )
                        wallpapersList = listOf(item)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    try { legacyFile.delete() } catch (e: Exception) { e.printStackTrace() }
                }
            }

            val savedModeStr = p.getString(KEY_ROTATION_MODE, RotationMode.OFF.name)
            val savedMode = try {
                RotationMode.valueOf(savedModeStr ?: RotationMode.OFF.name)
            } catch (e: Exception) {
                RotationMode.OFF
            }

            val savedInterval = p.getInt(KEY_ROTATION_INTERVAL, 60)
            val savedAnchor = p.getLong(KEY_ROTATION_ANCHOR, System.currentTimeMillis())
            val savedShuffle = p.getBoolean(KEY_SHUFFLE, false)
            var savedActiveIndex = p.getInt(KEY_ACTIVE_INDEX, 0)
            val savedDayId = p.getString(KEY_DAY_WALLPAPER_ID, null)
            val savedNightId = p.getString(KEY_NIGHT_WALLPAPER_ID, null)

            var savedPath = p.getString(KEY_IMAGE_PATH, null)
            if (savedPath != null && !File(savedPath).exists()) {
                savedPath = null
            }

            if (savedActiveIndex >= wallpapersList.size) {
                savedActiveIndex = 0
            }

            val activeItem = wallpapersList.getOrNull(savedActiveIndex)
            val effectivePath = activeItem?.filePath ?: savedPath
            val effectiveAccent = activeItem?.accentColorHex ?: p.getLong(KEY_ACCENT_COLOR, 0xFFF59E0BL)

            val isEnabled = p.getBoolean(KEY_IS_ENABLED, false) && effectivePath != null

            val initialConfig = BackgroundConfig(
                isEnabled = isEnabled,
                imagePath = effectivePath,
                blurRadius = p.getFloat(KEY_BLUR_RADIUS, 24f),
                glassOpacity = p.getFloat(KEY_GLASS_OPACITY, 0.25f),
                dim = p.getFloat(KEY_DIM, 0.25f),
                accentColorHex = effectiveAccent,
                wallpapers = wallpapersList,
                rotationMode = savedMode,
                rotationIntervalMinutes = savedInterval,
                rotationAnchorTime = savedAnchor,
                shuffle = savedShuffle,
                activeIndex = savedActiveIndex,
                dayWallpaperId = savedDayId ?: wallpapersList.firstOrNull()?.id,
                nightWallpaperId = savedNightId ?: (if (wallpapersList.size > 1) wallpapersList[1].id else wallpapersList.firstOrNull()?.id)
            )

            _config.value = initialConfig

            // Evaluate active wallpaper on start
            onAppOpen()
        }
    }

    private fun updateConfig(transform: (BackgroundConfig) -> BackgroundConfig) {
        val newConfig = transform(_config.value)
        _config.value = newConfig
        prefs?.edit()?.apply {
            putBoolean(KEY_IS_ENABLED, newConfig.isEnabled)
            putString(KEY_IMAGE_PATH, newConfig.imagePath)
            putFloat(KEY_BLUR_RADIUS, newConfig.blurRadius)
            putFloat(KEY_GLASS_OPACITY, newConfig.glassOpacity)
            putFloat(KEY_DIM, newConfig.dim)
            putLong(KEY_ACCENT_COLOR, newConfig.accentColorHex)
            putString(KEY_WALLPAPERS_JSON, serializeWallpapers(newConfig.wallpapers))
            putString(KEY_ROTATION_MODE, newConfig.rotationMode.name)
            putInt(KEY_ROTATION_INTERVAL, newConfig.rotationIntervalMinutes)
            putLong(KEY_ROTATION_ANCHOR, newConfig.rotationAnchorTime)
            putBoolean(KEY_SHUFFLE, newConfig.shuffle)
            putInt(KEY_ACTIVE_INDEX, newConfig.activeIndex)
            putString(KEY_DAY_WALLPAPER_ID, newConfig.dayWallpaperId)
            putString(KEY_NIGHT_WALLPAPER_ID, newConfig.nightWallpaperId)
            apply()
        }
    }

    fun setEnabled(enabled: Boolean) {
        updateConfig { it.copy(isEnabled = enabled) }
        if (enabled) {
            evaluateRotation()
        }
    }

    fun setBlurRadius(blur: Float) {
        val clamped = blur.coerceIn(0f, 40f)
        updateConfig { it.copy(blurRadius = clamped) }
    }

    fun setGlassOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0.10f, 0.60f)
        updateConfig { it.copy(glassOpacity = clamped) }
    }

    fun setDim(dim: Float) {
        val clamped = dim.coerceIn(0f, 0.60f)
        updateConfig { it.copy(dim = clamped) }
    }

    fun resetControlsToDefault() {
        updateConfig {
            it.copy(
                blurRadius = 24f,
                glassOpacity = 0.25f,
                dim = 0.25f
            )
        }
    }

    fun setRotationMode(mode: RotationMode) {
        updateConfig {
            it.copy(
                rotationMode = mode,
                rotationAnchorTime = System.currentTimeMillis()
            )
        }
        evaluateRotation()
    }

    fun setRotationInterval(minutes: Int) {
        updateConfig {
            it.copy(
                rotationIntervalMinutes = minutes,
                rotationAnchorTime = System.currentTimeMillis()
            )
        }
        evaluateRotation()
    }

    fun setShuffle(shuffle: Boolean) {
        updateConfig {
            it.copy(
                shuffle = shuffle,
                rotationAnchorTime = System.currentTimeMillis()
            )
        }
        evaluateRotation()
    }

    fun setDayNightWallpapers(dayId: String?, nightId: String?) {
        updateConfig {
            it.copy(
                dayWallpaperId = dayId,
                nightWallpaperId = nightId
            )
        }
        evaluateRotation()
    }

    fun setActiveWallpaperIndex(index: Int) {
        val current = _config.value
        if (index in current.wallpapers.indices) {
            val item = current.wallpapers[index]
            updateConfig {
                it.copy(
                    activeIndex = index,
                    imagePath = item.filePath,
                    accentColorHex = item.accentColorHex,
                    rotationAnchorTime = System.currentTimeMillis()
                )
            }
        }
    }

    fun onAppOpen() {
        val current = _config.value
        if (current.isEnabled && current.rotationMode == RotationMode.APP_OPEN && current.wallpapers.size > 1) {
            val validIndices = current.wallpapers.indices.filter {
                File(current.wallpapers[it].filePath).exists()
            }
            val effectiveIndices = if (validIndices.isNotEmpty()) validIndices else current.wallpapers.indices.toList()
            val currentPos = effectiveIndices.indexOf(current.activeIndex)
            val nextPos = if (currentPos >= 0) (currentPos + 1) % effectiveIndices.size else 0
            val nextIndex = effectiveIndices[nextPos]
            val item = current.wallpapers[nextIndex]
            updateConfig {
                it.copy(
                    activeIndex = nextIndex,
                    imagePath = item.filePath,
                    accentColorHex = item.accentColorHex
                )
            }
        } else {
            evaluateRotation()
        }
    }

    fun evaluateRotation(nowMillis: Long = System.currentTimeMillis()) {
        val current = _config.value
        if (!current.isEnabled || current.wallpapers.isEmpty()) return

        val newIndex = computeActiveIndex(current, nowMillis)
        val targetItem = current.wallpapers.getOrNull(newIndex) ?: return

        if (current.activeIndex != newIndex || current.imagePath != targetItem.filePath || current.accentColorHex != targetItem.accentColorHex) {
            updateConfig {
                it.copy(
                    activeIndex = newIndex,
                    imagePath = targetItem.filePath,
                    accentColorHex = targetItem.accentColorHex
                )
            }
        }
    }

    /**
     * Pure function to compute the active wallpaper index based on configuration and current timestamp.
     */
    fun computeActiveIndex(config: BackgroundConfig, nowMillis: Long): Int {
        if (config.wallpapers.isEmpty()) return 0

        // Find valid wallpaper items that exist on disk
        val existingIndices = config.wallpapers.indices.filter { idx ->
            File(config.wallpapers[idx].filePath).exists()
        }
        // Fall back to full list if no files on disk (e.g. during unit tests with mock paths)
        val effectiveIndices = if (existingIndices.isNotEmpty()) existingIndices else config.wallpapers.indices.toList()
        if (effectiveIndices.isEmpty()) return 0
        if (effectiveIndices.size == 1) return effectiveIndices[0]

        return when (config.rotationMode) {
            RotationMode.OFF -> {
                val target = config.activeIndex.coerceIn(0, config.wallpapers.size - 1)
                if (target in effectiveIndices) target else effectiveIndices[0]
            }
            RotationMode.APP_OPEN -> {
                val target = config.activeIndex.coerceIn(0, config.wallpapers.size - 1)
                if (target in effectiveIndices) target else effectiveIndices[0]
            }
            RotationMode.DAY_NIGHT -> {
                val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val isDay = hour in 6..17 // 06:00 to 17:59:59

                if (isDay) {
                    val dayIdx = config.wallpapers.indexOfFirst { it.id == config.dayWallpaperId }
                    if (dayIdx in effectiveIndices) dayIdx else effectiveIndices[0]
                } else {
                    val nightIdx = config.wallpapers.indexOfFirst { it.id == config.nightWallpaperId }
                    if (nightIdx in effectiveIndices) {
                        nightIdx
                    } else if (effectiveIndices.size > 1) {
                        effectiveIndices[1]
                    } else {
                        effectiveIndices[0]
                    }
                }
            }
            RotationMode.INTERVAL -> {
                val intervalMs = maxOf(1, config.rotationIntervalMinutes) * 60_000L
                val anchor = if (config.rotationAnchorTime > 0L) config.rotationAnchorTime else 0L
                val elapsed = maxOf(0L, nowMillis - anchor)
                val period = elapsed / intervalMs
                val count = effectiveIndices.size

                val slot = if (!config.shuffle) {
                    (period % count).toInt()
                } else {
                    val cycle = period / count
                    val posInCycle = (period % count).toInt()
                    val permutation = getShuffledOrder(count, cycle, anchor)
                    permutation[posInCycle]
                }
                effectiveIndices[slot]
            }
        }
    }

    /**
     * Generates a deterministic permutation for shuffle mode without repeating across cycle boundary.
     */
    fun getShuffledOrder(count: Int, cycle: Long, seed: Long): List<Int> {
        if (count <= 1) return listOf(0)

        val cycleSeed = seed xor (cycle * 6364136223846793005L + 1442695040888963407L)
        val random = Random(cycleSeed)
        val list = (0 until count).toMutableList()
        for (i in count - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = list[i]
            list[i] = list[j]
            list[j] = temp
        }

        if (cycle > 0) {
            val prevSeed = seed xor ((cycle - 1) * 6364136223846793005L + 1442695040888963407L)
            val prevRandom = Random(prevSeed)
            val prevList = (0 until count).toMutableList()
            for (i in count - 1 downTo 1) {
                val j = prevRandom.nextInt(i + 1)
                val temp = prevList[i]
                prevList[i] = prevList[j]
                prevList[j] = temp
            }
            val lastOfPrev = prevList.last()
            if (list.first() == lastOfPrev && count > 1) {
                val swapIdx = if (count > 2) 1 + random.nextInt(count - 1) else 1
                val temp = list[0]
                list[0] = list[swapIdx]
                list[swapIdx] = temp
            }
        }
        return list
    }

    /**
     * Pure function to compute timestamp (epoch ms) of the next scheduled wallpaper change.
     */
    fun computeNextChangeTime(config: BackgroundConfig, nowMillis: Long): Long {
        if (config.wallpapers.size <= 1) return Long.MAX_VALUE
        return when (config.rotationMode) {
            RotationMode.OFF, RotationMode.APP_OPEN -> Long.MAX_VALUE
            RotationMode.INTERVAL -> {
                val intervalMs = maxOf(1, config.rotationIntervalMinutes) * 60_000L
                val anchor = if (config.rotationAnchorTime > 0L) config.rotationAnchorTime else 0L
                val elapsed = maxOf(0L, nowMillis - anchor)
                val currentPeriod = elapsed / intervalMs
                anchor + (currentPeriod + 1) * intervalMs
            }
            RotationMode.DAY_NIGHT -> {
                val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val targetCal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (hour < 6) {
                    targetCal.set(Calendar.HOUR_OF_DAY, 6)
                } else if (hour < 18) {
                    targetCal.set(Calendar.HOUR_OF_DAY, 18)
                } else {
                    targetCal.add(Calendar.DAY_OF_YEAR, 1)
                    targetCal.set(Calendar.HOUR_OF_DAY, 6)
                }
                targetCal.timeInMillis
            }
        }
    }

    /**
     * Saves multiple image URIs up to the maximum limit of 12.
     */
    fun saveImagesFromUris(context: Context, uris: List<Uri>): Int {
        val currentWallpapers = _config.value.wallpapers.toMutableList()
        val remainingCapacity = MAX_WALLPAPERS_COUNT - currentWallpapers.size
        if (remainingCapacity <= 0) return 0

        val urisToProcess = uris.take(remainingCapacity)
        var addedCount = 0
        val wallpapersDir = File(context.filesDir, WALLPAPERS_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

        for (uri in urisToProcess) {
            try {
                val id = UUID.randomUUID().toString()
                val targetFile = File(wallpapersDir, "$id.jpg")

                var inputStream: InputStream? = context.contentResolver.openInputStream(uri) ?: continue
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(inputStream, null, boundsOptions)
                inputStream?.close()

                val maxDimension = 1920
                var sampleSize = 1
                while ((boundsOptions.outWidth / sampleSize) > maxDimension || (boundsOptions.outHeight / sampleSize) > maxDimension) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decodeStream = context.contentResolver.openInputStream(uri) ?: continue
                val bitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
                decodeStream.close()

                if (bitmap != null) {
                    val accentLong = extractAccentColorFromBitmap(bitmap)
                    val isBright = calculateIsBright(bitmap)

                    FileOutputStream(targetFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    bitmap.recycle()

                    val item = WallpaperItem(
                        id = id,
                        filePath = targetFile.absolutePath,
                        accentColorHex = accentLong,
                        isBright = isBright
                    )
                    currentWallpapers.add(item)
                    addedCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (addedCount > 0) {
            updateConfig { cfg ->
                val wasEmpty = cfg.wallpapers.isEmpty() || cfg.imagePath == null
                val newActiveIndex = if (wasEmpty) 0 else cfg.activeIndex.coerceIn(0, currentWallpapers.size - 1)
                val activeItem = currentWallpapers[newActiveIndex]
                cfg.copy(
                    isEnabled = true,
                    wallpapers = currentWallpapers,
                    activeIndex = newActiveIndex,
                    imagePath = activeItem.filePath,
                    accentColorHex = activeItem.accentColorHex,
                    rotationAnchorTime = if (cfg.rotationAnchorTime == 0L) System.currentTimeMillis() else cfg.rotationAnchorTime,
                    dayWallpaperId = cfg.dayWallpaperId ?: currentWallpapers.firstOrNull()?.id,
                    nightWallpaperId = cfg.nightWallpaperId ?: (if (currentWallpapers.size > 1) currentWallpapers[1].id else currentWallpapers.firstOrNull()?.id)
                )
            }
        }

        return addedCount
    }

    /**
     * Backward-compatible helper for single URI.
     */
    fun saveImageFromUri(context: Context, uri: Uri): Boolean {
        return saveImagesFromUris(context, listOf(uri)) > 0
    }

    fun removeWallpaper(context: Context, id: String) {
        val current = _config.value
        val updated = current.wallpapers.filter { it.id != id }
        val itemToRemove = current.wallpapers.firstOrNull { it.id == id }

        if (itemToRemove != null) {
            try {
                val f = File(itemToRemove.filePath)
                if (f.exists()) f.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (updated.isEmpty()) {
            updateConfig {
                it.copy(
                    isEnabled = false,
                    imagePath = null,
                    accentColorHex = 0xFFF59E0BL,
                    wallpapers = emptyList(),
                    activeIndex = 0,
                    dayWallpaperId = null,
                    nightWallpaperId = null
                )
            }
        } else {
            val newIndex = current.activeIndex.coerceIn(0, updated.size - 1)
            val activeItem = updated[newIndex]
            updateConfig {
                it.copy(
                    wallpapers = updated,
                    activeIndex = newIndex,
                    imagePath = activeItem.filePath,
                    accentColorHex = activeItem.accentColorHex,
                    dayWallpaperId = if (it.dayWallpaperId == id) updated.first().id else it.dayWallpaperId,
                    nightWallpaperId = if (it.nightWallpaperId == id) (if (updated.size > 1) updated[1].id else updated.first().id) else it.nightWallpaperId
                )
            }
        }
    }

    fun removeAllWallpapers(context: Context) {
        try {
            val dir = File(context.filesDir, WALLPAPERS_DIR_NAME)
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
            val legacyFile = File(context.filesDir, BG_LEGACY_FILENAME)
            if (legacyFile.exists()) legacyFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        updateConfig {
            it.copy(
                isEnabled = false,
                imagePath = null,
                accentColorHex = 0xFFF59E0BL,
                wallpapers = emptyList(),
                activeIndex = 0,
                rotationMode = RotationMode.OFF,
                dayWallpaperId = null,
                nightWallpaperId = null
            )
        }
    }

    /**
     * Extracts a vibrant/dominant accent color from a bitmap using Palette API
     * and darkens it if lightness > 0.6 so white text stays readable.
     */
    fun extractAccentColorFromBitmap(bitmap: Bitmap): Long {
        return try {
            val palette = Palette.from(bitmap).generate()
            val vibrant = palette.vibrantSwatch?.rgb
            val darkVibrant = palette.darkVibrantSwatch?.rgb
            val lightVibrant = palette.lightVibrantSwatch?.rgb
            val dominant = palette.dominantSwatch?.rgb
            val muted = palette.mutedSwatch?.rgb

            val selectedColorInt = vibrant ?: dominant ?: darkVibrant ?: lightVibrant ?: muted ?: 0xFFF59E0B.toInt()

            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(selectedColorInt, hsl)

            if (hsl[2] > 0.60f) {
                hsl[2] = 0.50f
            } else if (hsl[2] < 0.25f) {
                hsl[2] = 0.42f
            }
            if (hsl[1] < 0.35f) {
                hsl[1] = 0.55f
            }

            val contrastColorInt = ColorUtils.HSLToColor(hsl)
            val a = (contrastColorInt ushr 24) and 0xFF
            val r = (contrastColorInt ushr 16) and 0xFF
            val g = (contrastColorInt ushr 8) and 0xFF
            val b = contrastColorInt and 0xFF
            (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
        } catch (e: Exception) {
            4294319627L // 0xFFF59E0BL
        }
    }

    fun calculateIsBright(bitmap: Bitmap): Boolean {
        return try {
            val sampleStep = maxOf(1, minOf(bitmap.width, bitmap.height) / 20)
            var totalLuminance = 0.0
            var count = 0
            for (y in 0 until bitmap.height step sampleStep) {
                for (x in 0 until bitmap.width step sampleStep) {
                    val pixel = bitmap.getPixel(x, y)
                    totalLuminance += ColorUtils.calculateLuminance(pixel)
                    count++
                }
            }
            val avg = if (count > 0) totalLuminance / count else 0.2
            avg > 0.45
        } catch (e: Exception) {
            false
        }
    }

    private fun serializeWallpapers(wallpapers: List<WallpaperItem>): String {
        val array = JSONArray()
        for (item in wallpapers) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("filePath", item.filePath)
                put("accentColorHex", item.accentColorHex)
                put("isBright", item.isBright)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeWallpapers(json: String?): List<WallpaperItem> {
        if (json.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<WallpaperItem>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val filePath = obj.optString("filePath", "")
                val accentColorHex = obj.optLong("accentColorHex", 0xFFF59E0BL)
                val isBright = obj.optBoolean("isBright", false)
                if (filePath.isNotEmpty() && File(filePath).exists()) {
                    list.add(WallpaperItem(id, filePath, accentColorHex, isBright))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}

