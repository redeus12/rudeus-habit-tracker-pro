package com.example.ui.components

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.Habit
import com.example.ui.theme.AppTheme
import com.example.ui.theme.GlassCard
import com.example.ui.theme.Typography
import com.example.util.DateUtils
import com.example.util.ShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun ShareSingleHabitModal(
    isOpen: Boolean,
    habit: Habit,
    todayStr: String = DateUtils.todayStr(),
    onClose: () -> Unit,
    onSaveSuccess: () -> Unit = {}
) {
    if (!isOpen) return

    val context = LocalContext.current
    var showFooter by remember { mutableStateOf(true) }
    var previewBitmap by remember(habit, todayStr, showFooter) { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(true) }

    LaunchedEffect(habit, todayStr, showFooter) {
        isGenerating = true
        withContext(Dispatchers.Default) {
            val bmp = ShareHelper.generateSingleHabitBitmap(context, habit, showFooter = showFooter)
            previewBitmap = bmp
            isGenerating = false
        }
    }

    val accentColor = MaterialTheme.colorScheme.primary
    val onAccentColor = MaterialTheme.colorScheme.onPrimary
    val accentContainer = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .widthIn(max = 520.dp)
                    .fillMaxHeight(0.92f),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(accentContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Share Habit Spotlight",
                                    style = Typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.text
                                    )
                                )
                                Text(
                                    text = habit.name,
                                    style = Typography.bodySmall.copy(
                                        color = AppTheme.colors.textMuted
                                    ),
                                    maxLines = 1
                                )
                            }
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = AppTheme.colors.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // "Show footer" toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Show footer",
                                style = Typography.bodyMedium.copy(
                                    color = AppTheme.colors.text,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = "Include app mark & tagline",
                                style = Typography.bodySmall.copy(
                                    color = AppTheme.colors.textMuted,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Switch(
                            checked = showFooter,
                            onCheckedChange = { showFooter = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accentColor
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrollable Bitmap Preview Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.40f))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isGenerating || previewBitmap == null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = accentColor,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "Rendering high-res glass card...",
                                    style = Typography.bodySmall.copy(
                                        color = AppTheme.colors.textMuted
                                    )
                                )
                            }
                        } else {
                            val bitmap = previewBitmap!!
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Habit Spotlight Preview",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.FillWidth
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons (Save & Share)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Save Image Button
                        OutlinedButton(
                            onClick = {
                                val bmp = previewBitmap
                                if (bmp != null) {
                                    ShareHelper.saveBitmapToGallery(context, bmp, "Rudeus_Habit_${habit.name.replace(" ", "_")}_Spotlight")
                                    Toast.makeText(context, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
                                    onSaveSuccess()
                                }
                            },
                            enabled = !isGenerating && previewBitmap != null,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Save Image",
                                style = Typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }

                        // Share Habit Button
                        Button(
                            onClick = {
                                val bmp = previewBitmap
                                if (bmp != null) {
                                    val activeStreak = DateUtils.calculateStreak(habit, today = todayStr)
                                    val bestStreak = DateUtils.calculateLongestStreak(habit, today = todayStr)
                                    val accuracy30d = (DateUtils.completionRate(habit, 30, today = todayStr) * 100f).roundToInt()
                                    val caption = "⚡ \"${habit.name}\" on Rudeus Habit!\n" +
                                            "🔥 Active Streak: $activeStreak days | 🏆 Best: $bestStreak days | 📊 30-Day Accuracy: $accuracy30d%"
                                    ShareHelper.saveAndShareBitmap(context, bmp, "Share Habit Spotlight", caption)
                                }
                            },
                            enabled = !isGenerating && previewBitmap != null,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = onAccentColor
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Share Habit",
                                style = Typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}
