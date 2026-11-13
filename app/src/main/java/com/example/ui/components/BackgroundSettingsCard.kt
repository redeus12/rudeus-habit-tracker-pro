package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.HabitViewModel
import com.example.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun BackgroundSettingsCard(
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config by viewModel.backgroundConfig.collectAsStateWithLifecycle()
    val hasImage = config.imagePath != null && File(config.imagePath).exists()
    val accentColor = remember(config.accentColorHex) { Color(config.accentColorHex) }

    var wallpaperToDelete by remember { mutableStateOf<WallpaperItem?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val remainingCapacity = maxOf(1, BackgroundPreferences.MAX_WALLPAPERS_COUNT - config.wallpapers.size)

    val multiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(
            maxItems = if (remainingCapacity > 1) remainingCapacity else 2
        )
    ) { uris ->
        if (uris.isNotEmpty()) {
            val countToTake = minOf(uris.size, 12 - config.wallpapers.size)
            if (countToTake <= 0) {
                Toast.makeText(context, "Wallpaper limit reached (12 max).", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            val subUris = uris.take(countToTake)
            viewModel.addCustomWallpapers(subUris) { addedCount ->
                if (addedCount > 0) {
                    Toast.makeText(context, "Added $addedCount wallpaper(s) & applied accent!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to load selected wallpapers", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Confirmation dialog for deleting a single wallpaper
    if (wallpaperToDelete != null) {
        AlertDialog(
            onDismissRequest = { wallpaperToDelete = null },
            title = { Text("Remove Wallpaper", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Remove this wallpaper from your rotation library?", color = Color.White.copy(alpha = 0.85f)) },
            containerColor = Color(0xFF1E2421),
            confirmButton = {
                TextButton(
                    onClick = {
                        val item = wallpaperToDelete
                        wallpaperToDelete = null
                        if (item != null) {
                            viewModel.removeCustomWallpaper(item.id)
                            Toast.makeText(context, "Wallpaper removed", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Remove", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { wallpaperToDelete = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    // Confirmation dialog for removing all wallpapers
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Reset Wallpapers", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Remove all custom wallpapers and return to the default theme?", color = Color.White.copy(alpha = 0.85f)) },
            containerColor = Color(0xFF1E2421),
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog = false
                        viewModel.removeAllCustomWallpapers()
                        Toast.makeText(context, "All wallpapers removed. Default theme restored.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Remove All", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row with Title and Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wallpaper,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Custom Wallpaper & Glass",
                            style = Typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.text,
                                fontSize = 16.sp
                            )
                        )
                        Text(
                            text = if (config.wallpapers.size > 1) "${config.wallpapers.size} wallpapers in rotation" else "Frosted translucent glass with backdrop blur",
                            style = Typography.bodySmall.copy(
                                color = AppTheme.colors.textMuted,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Switch(
                    checked = config.isEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && config.wallpapers.isEmpty()) {
                            multiPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        } else {
                            viewModel.setBackgroundEnabled(enabled)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accentColor,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = if (AppTheme.colors.isGlassEnabled || AppTheme.colors.isDark) Color.White.copy(alpha = 0.30f) else AppTheme.colors.border,
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }

            // 1. Live Preview Card (reflects blur, opacity, dim in real-time)
            BackgroundLivePreview(config = config)

            // 2. Wallpaper Gallery / Thumbnails Row (Small 72dp cached thumbnails)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Wallpapers (${config.wallpapers.size}/${BackgroundPreferences.MAX_WALLPAPERS_COUNT})",
                        style = Typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.text,
                            fontSize = 13.sp
                        )
                    )
                    if (config.wallpapers.isNotEmpty()) {
                        Text(
                            text = "Tap to activate",
                            style = Typography.bodySmall.copy(
                                color = AppTheme.colors.textMuted,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // "Add" Tile
                    item {
                        val isDarkOrGlass = AppTheme.colors.isGlassEnabled || AppTheme.colors.isDark
                        Surface(
                            onClick = {
                                if (config.wallpapers.size >= BackgroundPreferences.MAX_WALLPAPERS_COUNT) {
                                    Toast.makeText(context, "Maximum limit of 12 wallpapers reached. Remove some first.", Toast.LENGTH_SHORT).show()
                                } else {
                                    multiPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            },
                            modifier = Modifier.size(72.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDarkOrGlass) Color.White.copy(alpha = 0.10f) else AppTheme.colors.surface2,
                            border = BorderStroke(1.dp, if (isDarkOrGlass) Color.White.copy(alpha = 0.30f) else AppTheme.colors.border),
                            shadowElevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Add Wallpaper",
                                    tint = accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Add",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.text
                                )
                            }
                        }
                    }

                    // Existing Wallpaper Thumbnails
                    itemsIndexed(config.wallpapers, key = { _, item -> item.id }) { index, item ->
                        val isActive = item.filePath == config.imagePath || (config.wallpapers.size == 1 && config.isEnabled)
                        val isDayItem = item.id == config.dayWallpaperId && config.rotationMode == RotationMode.DAY_NIGHT
                        val isNightItem = item.id == config.nightWallpaperId && config.rotationMode == RotationMode.DAY_NIGHT

                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    BorderStroke(
                                        if (isActive) 2.5.dp else 1.dp,
                                        if (isActive) accentColor else Color.White.copy(alpha = 0.25f)
                                    ),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    viewModel.setActiveWallpaper(index)
                                    Toast.makeText(context, "Wallpaper active", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(File(item.filePath))
                                    .size(160, 160)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Wallpaper thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Active Checkmark Badge or Day/Night icon
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(accentColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            } else if (isDayItem) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFB300)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WbSunny,
                                        contentDescription = "Day",
                                        tint = Color.Black,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            } else if (isNightItem) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF5C6BC0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NightsStay,
                                        contentDescription = "Night",
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }

                            // Remove button (x)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .clickable {
                                        wallpaperToDelete = item
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove wallpaper",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Rotation Mode Section (Only visible when at least 1 wallpaper exists)
            if (config.wallpapers.isNotEmpty() && config.isEnabled) {
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.15f),
                    thickness = 1.dp
                )

                Text(
                    text = "Automatic Wallpaper Rotation",
                    style = Typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                )

                // Mode Selector Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RotationMode.values().forEach { mode ->
                        val isSelected = config.rotationMode == mode
                        GlassPill(
                            isSelected = isSelected,
                            accentColor = accentColor,
                            onClick = { viewModel.setWallpaperRotationMode(mode) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Sub-options depending on selected mode
                when (config.rotationMode) {
                    RotationMode.INTERVAL -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Rotation Interval",
                                style = Typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )

                            // Interval chips: 1h, 3h, 6h, 12h, 24h
                            val intervals = listOf(
                                60 to "1h",
                                180 to "3h",
                                360 to "6h",
                                720 to "12h",
                                1440 to "24h"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                intervals.forEach { (mins, label) ->
                                    val isSelected = config.rotationIntervalMinutes == mins
                                    GlassPill(
                                        isSelected = isSelected,
                                        accentColor = accentColor,
                                        onClick = { viewModel.setWallpaperRotationInterval(mins) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 7.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            // Shuffle Switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Shuffle Wallpapers",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Random cycle without immediate repeats",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.70f)
                                    )
                                }
                                Switch(
                                    checked = config.shuffle,
                                    onCheckedChange = { viewModel.setWallpaperShuffle(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = Color.White.copy(alpha = 0.30f),
                                        uncheckedBorderColor = Color.Transparent
                                    )
                                )
                            }

                            // Next Change Time Label
                            val nextChange = remember(config.rotationMode, config.rotationIntervalMinutes, config.rotationAnchorTime, config.wallpapers.size) {
                                BackgroundPreferences.computeNextChangeTime(config, System.currentTimeMillis())
                            }
                            if (nextChange != Long.MAX_VALUE && nextChange > 0L) {
                                val timeStr = remember(nextChange) {
                                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                                    val formatted = sdf.format(Date(nextChange))
                                    val diffMs = nextChange - System.currentTimeMillis()
                                    val diffMins = (diffMs / 60_000L).coerceAtLeast(0)
                                    val h = diffMins / 60
                                    val m = diffMins % 60
                                    val inText = if (h > 0) "${h}h ${m}m" else "${m}m"
                                    "Next change at $formatted (in $inText)"
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = timeStr,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.90f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    RotationMode.APP_OPEN -> {
                        Text(
                            text = "Advances to the next wallpaper each time the app is launched.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }

                    RotationMode.DAY_NIGHT -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Daytime (06:00 - 18:00) & Nighttime (18:00 - 06:00)",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.75f)
                            )

                            // Day Wallpaper Selection Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(imageVector = Icons.Default.WbSunny, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                                    Text("Day Wallpaper:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    itemsIndexed(config.wallpapers) { idx, item ->
                                        val isSelected = item.id == config.dayWallpaperId
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                    BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) Color(0xFFFFB300) else Color.White.copy(alpha = 0.3f)),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    viewModel.setDayNightWallpapers(item.id, config.nightWallpaperId)
                                                }
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context).data(File(item.filePath)).size(80, 80).build(),
                                                contentDescription = "Day option $idx",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }

                            // Night Wallpaper Selection Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(imageVector = Icons.Default.NightsStay, contentDescription = null, tint = Color(0xFF5C6BC0), modifier = Modifier.size(16.dp))
                                    Text("Night Wallpaper:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    itemsIndexed(config.wallpapers) { idx, item ->
                                        val isSelected = item.id == config.nightWallpaperId
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                    BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) Color(0xFF5C6BC0) else Color.White.copy(alpha = 0.3f)),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    viewModel.setDayNightWallpapers(config.dayWallpaperId, item.id)
                                                }
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context).data(File(item.filePath)).size(80, 80).build(),
                                                contentDescription = "Night option $idx",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    RotationMode.OFF -> {
                        // Static mode note
                    }
                }
            }

            // 4. Effects & Adjustments (Sliders: Blur, Glass Opacity, Dim)
            if (hasImage && config.isEnabled) {
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.15f),
                    thickness = 1.dp
                )

                Text(
                    text = "Effects & Adjustments",
                    style = Typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                )

                // Slider 1: Blur (0 - 40 dp)
                GlassSliderRow(
                    label = "Backdrop Blur",
                    valueText = "${config.blurRadius.roundToInt()} dp",
                    value = config.blurRadius,
                    valueRange = 0f..40f,
                    accentColor = accentColor,
                    onValueChange = { viewModel.setBackgroundBlur(it) }
                )

                // Slider 2: Glass Opacity (10% - 60%)
                GlassSliderRow(
                    label = "Glass Tint Opacity",
                    valueText = "${(config.glassOpacity * 100).roundToInt()}%",
                    value = config.glassOpacity,
                    valueRange = 0.10f..0.60f,
                    accentColor = accentColor,
                    onValueChange = { viewModel.setBackgroundOpacity(it) }
                )

                // Slider 3: Dim / Scrim (0% - 60%)
                GlassSliderRow(
                    label = "Wallpaper Dim",
                    valueText = "${(config.dim * 100).roundToInt()}%" + if (config.activeWallpaper?.isBright == true && config.dim < 0.30f) " (auto-bright: 30%)" else "",
                    value = config.dim,
                    valueRange = 0f..0.60f,
                    accentColor = accentColor,
                    onValueChange = { viewModel.setBackgroundDim(it) }
                )

                // 5. Actions Row: Reset Controls & Remove All
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            viewModel.resetBackgroundControls()
                            Toast.makeText(context, "Effects reset to defaults", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reset Controls",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    DangerGlassButton(
                        onClick = { showDeleteAllDialog = true },
                        height = 36.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = Color(0xFFFFCDD2),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Remove All",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFFFFCDD2)
                        )
                    }
                }
            }
        }
    }
}

