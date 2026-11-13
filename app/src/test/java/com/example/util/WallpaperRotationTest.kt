package com.example.util

import com.example.ui.theme.BackgroundConfig
import com.example.ui.theme.BackgroundPreferences
import com.example.ui.theme.RotationMode
import com.example.ui.theme.WallpaperItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WallpaperRotationTest {

    private fun createSampleWallpapers(count: Int): List<WallpaperItem> {
        return (0 until count).map { i ->
            WallpaperItem(
                id = "wp_$i",
                filePath = "/data/user/0/com.example/files/wallpapers/wp_$i.jpg",
                accentColorHex = 0xFF2E7D32,
                isBright = false
            )
        }
    }

    @Test
    fun testEmptyWallpapers() {
        val config = BackgroundConfig(wallpapers = emptyList(), activeIndex = 0)
        assertEquals(0, BackgroundPreferences.computeActiveIndex(config, System.currentTimeMillis()))
    }

    @Test
    fun testSingleWallpaper() {
        val list = createSampleWallpapers(1)
        val config = BackgroundConfig(
            wallpapers = list,
            rotationMode = RotationMode.INTERVAL,
            rotationIntervalMinutes = 60,
            rotationAnchorTime = 0L,
            activeIndex = 0
        )
        // With only 1 wallpaper, active index is always 0
        assertEquals(0, BackgroundPreferences.computeActiveIndex(config, 0L))
        assertEquals(0, BackgroundPreferences.computeActiveIndex(config, 3600_000L * 10))
    }

    @Test
    fun testOffRotationModePreservesActiveIndex() {
        val list = createSampleWallpapers(4)
        val config = BackgroundConfig(
            wallpapers = list,
            rotationMode = RotationMode.OFF,
            activeIndex = 2
        )
        assertEquals(2, BackgroundPreferences.computeActiveIndex(config, System.currentTimeMillis()))
    }

    @Test
    fun testIntervalRotationWithoutShuffle() {
        val list = createSampleWallpapers(4)
        val anchor = 1_000_000L
        val intervalMins = 60
        val intervalMs = 60 * 60 * 1000L

        val config = BackgroundConfig(
            wallpapers = list,
            rotationMode = RotationMode.INTERVAL,
            rotationIntervalMinutes = intervalMins,
            rotationAnchorTime = anchor,
            shuffle = false,
            activeIndex = 0
        )

        // At anchor time -> step 0 -> index 0
        assertEquals(0, BackgroundPreferences.computeActiveIndex(config, anchor))
        // At anchor + 59 mins -> step 0 -> index 0
        assertEquals(0, BackgroundPreferences.computeActiveIndex(config, anchor + intervalMs - 1000L))
        // At anchor + 60 mins -> step 1 -> index 1
        assertEquals(1, BackgroundPreferences.computeActiveIndex(config, anchor + intervalMs))
        // At anchor + 180 mins -> step 3 -> index 3
        assertEquals(3, BackgroundPreferences.computeActiveIndex(config, anchor + 3 * intervalMs))
        // At anchor + 240 mins -> step 4 -> index 0 (wraps around)
        assertEquals(0, BackgroundPreferences.computeActiveIndex(config, anchor + 4 * intervalMs))
    }

    @Test
    fun testIntervalRotationDeterministicShuffle() {
        val list = createSampleWallpapers(5)
        val anchor = 10_000_000L
        val intervalMins = 180
        val intervalMs = 180 * 60 * 1000L

        val config = BackgroundConfig(
            wallpapers = list,
            rotationMode = RotationMode.INTERVAL,
            rotationIntervalMinutes = intervalMins,
            rotationAnchorTime = anchor,
            shuffle = true,
            activeIndex = 0
        )

        val idx0 = BackgroundPreferences.computeActiveIndex(config, anchor)
        val idx1 = BackgroundPreferences.computeActiveIndex(config, anchor + intervalMs)
        val idx2 = BackgroundPreferences.computeActiveIndex(config, anchor + 2 * intervalMs)

        assertTrue(idx0 in 0..4)
        assertTrue(idx1 in 0..4)
        assertTrue(idx2 in 0..4)

        // Same timestamp computed twice must yield identical index (deterministic)
        assertEquals(idx1, BackgroundPreferences.computeActiveIndex(config, anchor + intervalMs))
    }

    @Test
    fun testDayNightRotation() {
        val list = createSampleWallpapers(3)
        val dayId = list[0].id
        val nightId = list[2].id

        val config = BackgroundConfig(
            wallpapers = list,
            rotationMode = RotationMode.DAY_NIGHT,
            dayWallpaperId = dayId,
            nightWallpaperId = nightId
        )

        // Midday (12:00 PM) -> Day wallpaper (index 0)
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        assertEquals(0, BackgroundPreferences.computeActiveIndex(config, cal.timeInMillis))

        // Nighttime (22:00 / 10 PM) -> Night wallpaper (index 2)
        cal.set(Calendar.HOUR_OF_DAY, 22)
        cal.set(Calendar.MINUTE, 0)
        assertEquals(2, BackgroundPreferences.computeActiveIndex(config, cal.timeInMillis))

        // Early morning (03:00 AM) -> Night wallpaper (index 2)
        cal.set(Calendar.HOUR_OF_DAY, 3)
        cal.set(Calendar.MINUTE, 0)
        assertEquals(2, BackgroundPreferences.computeActiveIndex(config, cal.timeInMillis))

        // Morning (08:00 AM) -> Day wallpaper (index 0)
        cal.set(Calendar.HOUR_OF_DAY, 8)
        cal.set(Calendar.MINUTE, 0)
        assertEquals(0, BackgroundPreferences.computeActiveIndex(config, cal.timeInMillis))
    }

    @Test
    fun testComputeNextChangeTime() {
        val list = createSampleWallpapers(3)
        val anchor = 1000L
        val intervalMins = 60
        val intervalMs = 60 * 60 * 1000L

        val config = BackgroundConfig(
            wallpapers = list,
            rotationMode = RotationMode.INTERVAL,
            rotationIntervalMinutes = intervalMins,
            rotationAnchorTime = anchor
        )

        val now = anchor + 15 * 60 * 1000L // 15 mins in
        val nextChange = BackgroundPreferences.computeNextChangeTime(config, now)
        assertEquals(anchor + intervalMs, nextChange)
    }
}
