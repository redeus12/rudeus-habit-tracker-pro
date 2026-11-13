package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import com.example.util.NotificationHelper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.HabitViewModel
import com.example.ui.theme.*
import com.example.util.DateUtils
import kotlin.math.roundToInt

@Composable
fun SettingsTabContent(
    viewModel: HabitViewModel,
    onResetClicked: () -> Unit,
    onLoadSampleClicked: () -> Unit
) {
    val context = LocalContext.current
    val currentTheme by viewModel.themeMode.collectAsStateWithLifecycle()
    val useDynamicColor by viewModel.useDynamicColor.collectAsStateWithLifecycle()
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val isSoundEffectsEnabled by viewModel.isSoundEffectsEnabled.collectAsStateWithLifecycle()
    val isHapticsEnabled by viewModel.isHapticsEnabled.collectAsStateWithLifecycle()
    val customSoundName by viewModel.customSoundName.collectAsStateWithLifecycle()
    var showOnboardingModal by remember { mutableStateOf(false) }

    val accentColor = MaterialTheme.colorScheme.primary
    val onAccentColor = MaterialTheme.colorScheme.onPrimary
    val accentContainer = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.setCustomSound(uri)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri) { _, _ -> }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackup(uri) { _, _ -> }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section Header Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text(
                                text = "Settings",
                                style = Typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.text,
                                    fontSize = 22.sp
                                )
                            )
                            Text(
                                text = "Theme, alarms, feedback, and data management.",
                                style = Typography.bodyMedium.copy(color = AppTheme.colors.textFaint, fontSize = 13.sp)
                            )
                        }
                    }
                }
            }

        // Theme Mode Selection Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Theme Mode",
                        style = Typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.text,
                            fontSize = 18.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Switch between Dark and Light mode. The transition animate smoothly between color schemes.",
                    style = Typography.bodyMedium.copy(color = AppTheme.colors.textMuted, fontSize = 13.sp)
                )
                Spacer(modifier = Modifier.height(18.dp))

                // 3 Options Grid / Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ThemeOptionCard(
                        modifier = Modifier.weight(1f),
                        title = "Light mode",
                        icon = Icons.Default.LightMode,
                        isSelected = currentTheme == ThemeMode.LIGHT,
                        onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) }
                    )
                    ThemeOptionCard(
                        modifier = Modifier.weight(1f),
                        title = "Dark mode",
                        icon = Icons.Default.DarkMode,
                        isSelected = currentTheme == ThemeMode.DARK,
                        onClick = { viewModel.setThemeMode(ThemeMode.DARK) }
                    )
                    ThemeOptionCard(
                        modifier = Modifier.weight(1f),
                        title = "Follow system",
                        icon = Icons.Default.SettingsBrightness,
                        isSelected = currentTheme == ThemeMode.SYSTEM,
                        onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) }
                    )
                }

                val isAndroid12OrAbove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = AppTheme.colors.borderSoft, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isAndroid12OrAbove) {
                            viewModel.setUseDynamicColor(!useDynamicColor)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "Use device wallpaper colors (Material You)",
                            style = Typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isAndroid12OrAbove) AppTheme.colors.text else AppTheme.colors.textMuted
                            )
                        )
                        Text(
                            text = if (isAndroid12OrAbove) {
                                "Apply colors from your wallpaper to personalize the app theme dynamically."
                            } else {
                                "Dynamic color is only available on Android 12+ (API 31+)."
                            },
                            style = Typography.bodySmall.copy(
                                color = AppTheme.colors.textFaint,
                                fontSize = 12.sp
                            )
                        )
                    }
                    Switch(
                        checked = useDynamicColor && isAndroid12OrAbove,
                        onCheckedChange = { viewModel.setUseDynamicColor(it) },
                        enabled = isAndroid12OrAbove,
                        modifier = Modifier.testTag("dynamic_color_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = onAccentColor,
                            checkedTrackColor = accentColor,
                            uncheckedThumbColor = AppTheme.colors.textMuted,
                            uncheckedTrackColor = AppTheme.colors.surface2
                        )
                    )
                }
            }
        }

        // Custom Background & Glassmorphism Card
        BackgroundSettingsCard(viewModel = viewModel)

        // System Permissions & Notifications Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            val context = LocalContext.current
            var hasNotificationPermission by remember {
                mutableStateOf(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }
                )
            }
            var hasExactAlarmPermission by remember {
                mutableStateOf(NotificationHelper.isExactAlarmPermissionGranted(context))
            }
            var isIgnoringBatteryOptimizations by remember {
                mutableStateOf(NotificationHelper.isIgnoringBatteryOptimizations(context))
            }

            // Request permission launcher for POST_NOTIFICATIONS
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                hasNotificationPermission = isGranted
            }

            // Auto refresh statuses on resume
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        } else {
                            true
                        }
                        hasExactAlarmPermission = NotificationHelper.isExactAlarmPermissionGranted(context)
                        isIgnoringBatteryOptimizations = NotificationHelper.isIgnoringBatteryOptimizations(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "System Permissions & Reminders",
                        style = Typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.text,
                            fontSize = 18.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ensure these settings are active so notifications fire exactly on time and survive device reboots.",
                    style = Typography.bodyMedium.copy(color = AppTheme.colors.textMuted, fontSize = 13.sp)
                )
                Spacer(modifier = Modifier.height(18.dp))

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Row 1: POST_NOTIFICATIONS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Notification Delivery",
                                style = Typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = AppTheme.colors.text)
                            )
                            Text(
                                text = "Allows showing habit alerts and daily checklists.",
                                style = Typography.bodySmall.copy(color = AppTheme.colors.textFaint, fontSize = 12.sp)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (hasNotificationPermission) MossGreen.copy(alpha = 0.15f) else MutedCrimson.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (hasNotificationPermission) "Granted" else "Required",
                                    style = Typography.labelSmall.copy(
                                        color = if (hasNotificationPermission) MossGreen else MutedCrimson,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                ModernOutlinedButton(
                                    onClick = { permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                                    border = BorderStroke(1.dp, accentColor),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = AppTheme.colors.borderSoft, thickness = 1.dp)

                    // Row 2: SCHEDULE_EXACT_ALARM
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Exact Alarm Timing",
                                style = Typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = AppTheme.colors.text)
                            )
                            Text(
                                text = "Required to trigger notifications exactly on time.",
                                style = Typography.bodySmall.copy(color = AppTheme.colors.textFaint, fontSize = 12.sp)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (hasExactAlarmPermission) MossGreen.copy(alpha = 0.15f) else accentContainer
                            ) {
                                Text(
                                    text = if (hasExactAlarmPermission) "Active" else "Disabled",
                                    style = Typography.labelSmall.copy(
                                        color = if (hasExactAlarmPermission) MossGreen else accentColor,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (!hasExactAlarmPermission) {
                                ModernOutlinedButton(
                                    onClick = { NotificationHelper.requestExactAlarmPermission(context) },
                                    border = BorderStroke(1.dp, accentColor),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = AppTheme.colors.borderSoft, thickness = 1.dp)

                    // Row 3: Battery Optimization
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Battery Optimization",
                                style = Typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = AppTheme.colors.text)
                            )
                            Text(
                                text = " Xiaomi/Oppo/Samsung restrict background alarms. Enable to bypass restrictions.",
                                style = Typography.bodySmall.copy(color = AppTheme.colors.textFaint, fontSize = 12.sp)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isIgnoringBatteryOptimizations) MossGreen.copy(alpha = 0.15f) else AppTheme.colors.border.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (isIgnoringBatteryOptimizations) "Unrestricted" else "Optimized",
                                    style = Typography.labelSmall.copy(
                                        color = if (isIgnoringBatteryOptimizations) MossGreen else AppTheme.colors.textMuted,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (!isIgnoringBatteryOptimizations) {
                                ModernOutlinedButton(
                                    onClick = { NotificationHelper.requestIgnoreBatteryOptimizations(context) },
                                    border = BorderStroke(1.dp, accentColor),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Configure", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Feedback & Interactivity Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Sound & Haptics",
                            style = Typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.text,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "Audio chimes, tactile haptics, and celebration effects.",
                            style = Typography.bodyMedium.copy(color = AppTheme.colors.textMuted, fontSize = 13.sp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Sound toggle row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Completion Chimes",
                            style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = AppTheme.colors.text)
                        )
                        Text(
                            text = "Motivational audio cue when marking habits completed or saving timers.",
                            style = Typography.bodyMedium.copy(color = AppTheme.colors.textFaint, fontSize = 12.sp)
                        )
                    }
                    Switch(
                        checked = isSoundEffectsEnabled,
                        onCheckedChange = { viewModel.setSoundEffectsEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = onAccentColor,
                            checkedTrackColor = accentColor
                        )
                    )
                }

                if (isSoundEffectsEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Custom Audio Tone Controller Box (flat box, no nested glass)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(
                                                if (customSoundName != null) accentColor.copy(alpha = 0.2f) else AppTheme.colors.borderSoft.copy(alpha = 0.5f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Audiotrack,
                                            contentDescription = null,
                                            tint = if (customSoundName != null) accentColor else AppTheme.colors.textMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = if (customSoundName != null) "Custom Audio Active" else "Default Completion Sound",
                                            style = Typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (customSoundName != null) accentColor else AppTheme.colors.text
                                            )
                                        )
                                        Text(
                                            text = if (customSoundName != null) customSoundName!! else "Built-in HabitLedger chime",
                                            style = Typography.bodySmall.copy(
                                                color = AppTheme.colors.textMuted,
                                                fontSize = 12.sp
                                            ),
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Preview button
                                IconButton(
                                    onClick = { viewModel.previewCompletionSound() },
                                    modifier = Modifier.size(38.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = accentColor.copy(alpha = 0.18f),
                                        contentColor = accentColor
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Preview Sound",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Pick from local device storage button
                                Button(
                                    onClick = { audioPickerLauncher.launch("audio/*") },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentColor,
                                        contentColor = onAccentColor
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (customSoundName != null) "Change Audio" else "Pick Audio from Device",
                                        style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    )
                                }

                                // Reset to default button
                                if (customSoundName != null) {
                                    OutlinedButton(
                                        onClick = { viewModel.resetCustomSound() },
                                        border = BorderStroke(1.dp, AppTheme.colors.borderSoft),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = AppTheme.colors.textMuted
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RestartAlt,
                                            contentDescription = "Reset",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Default",
                                            style = Typography.labelMedium.copy(fontSize = 12.sp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Select any audio track (MP3, WAV, M4A, OGG) stored locally on your device to play whenever you complete habits.",
                                style = Typography.bodySmall.copy(color = AppTheme.colors.textFaint, fontSize = 11.sp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = AppTheme.colors.borderSoft, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Haptic toggle row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Haptic Feedback",
                            style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = AppTheme.colors.text)
                        )
                        Text(
                            text = "Tactile vibration pulses on habit check-ins and timer milestones.",
                            style = Typography.bodyMedium.copy(color = AppTheme.colors.textFaint, fontSize = 12.sp)
                        )
                    }
                    Switch(
                        checked = isHapticsEnabled,
                        onCheckedChange = { viewModel.setHapticsEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = onAccentColor,
                            checkedTrackColor = accentColor
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = AppTheme.colors.borderSoft, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Test celebration button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Test Celebration FX",
                            style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = AppTheme.colors.text)
                        )
                        Text(
                            text = "Preview full celebratory confetti burst and vibration pattern.",
                            style = Typography.bodyMedium.copy(color = AppTheme.colors.textFaint, fontSize = 12.sp)
                        )
                    }
                    ModernOutlinedButton(
                        onClick = { viewModel.triggerCelebration() },
                        border = BorderStroke(1.dp, accentColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
                    ) {
                        Text("Test FX", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Data Management Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "Data & Backup",
                    style = Typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.text,
                        fontSize = 18.sp
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Export, restore, or reset your ledger records safely.",
                    style = Typography.bodyMedium.copy(color = AppTheme.colors.textMuted, fontSize = 13.sp)
                )
                Spacer(modifier = Modifier.height(20.dp))

                // Export Backup Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Export Backup",
                            style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = AppTheme.colors.text)
                        )
                        Text(
                            text = "Save all habits, streaks, and sessions to a JSON file.",
                            style = Typography.bodyMedium.copy(color = AppTheme.colors.textFaint, fontSize = 12.sp)
                        )
                    }
                    ModernOutlinedButton(
                        onClick = {
                            exportLauncher.launch("habitledger_backup_${com.example.util.DateUtils.todayStr()}.json")
                        },
                        border = BorderStroke(1.dp, accentColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
                    ) {
                        Text("Export", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = AppTheme.colors.borderSoft, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Import Backup Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Import Backup",
                            style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = AppTheme.colors.text)
                        )
                        Text(
                            text = "Restore habits and historical records from a JSON backup.",
                            style = Typography.bodyMedium.copy(color = AppTheme.colors.textFaint, fontSize = 12.sp)
                        )
                    }
                    ModernOutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        },
                        border = BorderStroke(1.dp, accentColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
                    ) {
                        Text("Import", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = AppTheme.colors.borderSoft, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Load Demo Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Load Example Habits",
                            style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = AppTheme.colors.text)
                        )
                        Text(
                            text = "Populate your tracker with demo habits and historical streak curves.",
                            style = Typography.bodyMedium.copy(color = AppTheme.colors.textFaint, fontSize = 12.sp)
                        )
                    }
                    ModernOutlinedButton(
                        onClick = onLoadSampleClicked,
                        border = BorderStroke(1.dp, accentColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
                    ) {
                        Text("Load Demo", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = AppTheme.colors.borderSoft, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Reset Data Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Reset All Data",
                            style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MutedCrimson)
                        )
                        Text(
                            text = "Permanently erase all tracked habits and consistency logs.",
                            style = Typography.bodyMedium.copy(color = AppTheme.colors.textFaint, fontSize = 12.sp)
                        )
                    }
                    ModernOutlinedButton(
                        onClick = onResetClicked,
                        border = BorderStroke(1.dp, MutedCrimson),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MutedCrimson)
                    ) {
                        Text("Reset All", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // App Tour Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "App Guide & Tour",
                        style = Typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.text,
                            fontSize = 17.sp
                        )
                    )
                    Text(
                        text = "Review the 3-step guide to consistency, YouTube Music focus, and home widgets.",
                        style = Typography.bodyMedium.copy(color = AppTheme.colors.textMuted, fontSize = 13.sp)
                    )
                }
                ModernButton(
                    onClick = { showOnboardingModal = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = onAccentColor
                    )
                ) {
                    Text("View Tour", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // About Footer Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Habit Ledger",
                        style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = AppTheme.colors.text)
                    )
                    Text(
                        text = "Consistency, Measured · Built with Jetpack Compose & Material 3",
                        style = Typography.labelSmall.copy(color = AppTheme.colors.textFaint, fontSize = 11.sp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = AppTheme.colors.surface3,
                    border = BorderStroke(1.dp, AppTheme.colors.border)
                ) {
                    Text(
                        text = "v1.2",
                        style = Typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = AppTheme.colors.textMuted, fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    if (showOnboardingModal) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showOnboardingModal = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            OnboardingScreen(onFinish = { showOnboardingModal = false })
        }
    }
    }
}

@Composable
fun ThemeOptionCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val onAccentColor = MaterialTheme.colorScheme.onPrimary
    val borderColor = if (isSelected) accentColor else AppTheme.colors.borderSoft
    val bgColor = if (isSelected) accentColor.copy(alpha = 0.16f) else AppTheme.colors.surface2
    val contentColor = if (isSelected) accentColor else AppTheme.colors.textMuted

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = contentColor,
                    modifier = Modifier.size(26.dp)
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-6).dp)
                            .size(14.dp)
                            .background(accentColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = onAccentColor,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = Typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) AppTheme.colors.text else AppTheme.colors.textMuted,
                    fontSize = 12.sp
                )
            )
        }
    }
}
