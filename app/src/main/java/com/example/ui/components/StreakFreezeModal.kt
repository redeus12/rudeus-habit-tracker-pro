package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.Habit
import com.example.ui.theme.*
import com.example.util.DateUtils

data class FreezeReason(
    val emoji: String,
    val title: String,
    val subtitle: String
)

val FREEZE_REASONS = listOf(
    FreezeReason("🛋️", "Scheduled Rest Day", "Planned active recovery or rest"),
    FreezeReason("🤒", "Sick & Recovery", "Resting to recover health"),
    FreezeReason("✈️", "Travel & Vacation", "Away from normal routine"),
    FreezeReason("🚨", "Emergency / Priority", "Unexpected life priorities")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StreakFreezeModal(
    isOpen: Boolean,
    habit: Habit?, // null = apply to all active habits
    selectedDate: String,
    onClose: () -> Unit,
    onConfirmFreeze: (habitId: String?, dateStr: String, reason: String) -> Unit,
    onUnfreeze: (habitId: String?, dateStr: String) -> Unit
) {
    if (!isOpen) return

    var chosenReason by remember { mutableStateOf(FREEZE_REASONS[0].title) }
    var applyToAll by remember(habit) { mutableStateOf(habit == null) }
    val isCurrentlyFrozen = if (habit != null) DateUtils.isFrozen(habit, selectedDate) else false

    val icyColor = Color(0xFF26C6DA)
    val icyBackground = Color(0xFF006064)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        GlassCard(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with icy shield theme
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = icyColor.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, icyColor.copy(alpha = 0.5f)),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AcUnit,
                                    contentDescription = null,
                                    tint = icyColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Streak Freeze & Rest Day",
                                style = Typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = AppTheme.colors.text
                                )
                            )
                            Text(
                                text = "Protect your consistency streak",
                                style = Typography.bodySmall.copy(
                                    color = AppTheme.colors.textFaint,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = AppTheme.colors.textFaint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Informational banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = icyColor.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, icyColor.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🛡️", fontSize = 24.sp)
                        Column {
                            Text(
                                text = "No Streak Lost · No Guilt",
                                style = Typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = icyColor,
                                    fontSize = 13.sp
                                )
                            )
                            Text(
                                text = "Freezing $selectedDate pauses requirement without breaking your active streak or lowering monthly stats.",
                                style = Typography.bodySmall.copy(
                                    color = AppTheme.colors.textMuted,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Target Habit Switcher
                if (habit != null) {
                    Text(
                        text = "APPLY PROTECTION TO",
                        style = Typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textFaint,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !applyToAll,
                            onClick = { applyToAll = false },
                            label = { Text(habit.name, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = icyColor.copy(alpha = 0.2f),
                                selectedLabelColor = icyColor
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = !applyToAll,
                                selectedBorderColor = icyColor,
                                borderColor = AppTheme.colors.borderSoft
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = applyToAll,
                            onClick = { applyToAll = true },
                            label = { Text("All Habits 🌐", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = icyColor.copy(alpha = 0.2f),
                                selectedLabelColor = icyColor
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = applyToAll,
                                selectedBorderColor = icyColor,
                                borderColor = AppTheme.colors.borderSoft
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Reasons list
                Text(
                    text = "SELECT REASON FOR REST",
                    style = Typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textFaint,
                        fontSize = 11.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FREEZE_REASONS.forEach { r ->
                        val isSelected = chosenReason == r.title
                        Surface(
                            onClick = { chosenReason = r.title },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) icyColor.copy(alpha = 0.15f) else AppTheme.colors.surface,
                            border = BorderStroke(
                                1.2.dp,
                                if (isSelected) icyColor else AppTheme.colors.borderSoft
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(r.emoji, fontSize = 20.sp)
                                    Column {
                                        Text(
                                            text = r.title,
                                            style = Typography.titleSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = if (isSelected) icyColor else AppTheme.colors.text
                                            )
                                        )
                                        Text(
                                            text = r.subtitle,
                                            style = Typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                color = AppTheme.colors.textFaint
                                            )
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = icyColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isCurrentlyFrozen && !applyToAll) {
                        OutlinedButton(
                            onClick = {
                                onUnfreeze(habit?.id, selectedDate)
                                onClose()
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MutedCrimson.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MutedCrimson),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Unfreeze 🔥", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onClose,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AppTheme.colors.border),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.textMuted),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = {
                            val targetId = if (applyToAll) null else habit?.id
                            onConfirmFreeze(targetId, selectedDate, chosenReason)
                            onClose()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = icyColor,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AcUnit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (applyToAll) "Freeze All ❄️" else "Freeze Streak ❄️",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
