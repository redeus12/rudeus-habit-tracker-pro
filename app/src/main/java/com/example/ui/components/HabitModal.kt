package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.Habit
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitModal(
    isOpen: Boolean,
    editingHabit: Habit?,
    onClose: () -> Unit,
    onSave: (name: String, category: String, colorHex: String, days: List<Int>, durationDays: Int?, reminderTime: String?, trackTimeSpent: Boolean) -> Unit,
    onDelete: (Habit) -> Unit
) {
    if (!isOpen) return

    var name by remember(editingHabit) { mutableStateOf(editingHabit?.name ?: "") }
    var category by remember(editingHabit) { mutableStateOf(editingHabit?.category ?: "") }
    var colorHex by remember(editingHabit) { mutableStateOf(editingHabit?.colorHex ?: HabitPalette[0]) }
    var days by remember(editingHabit) { mutableStateOf(editingHabit?.days ?: listOf(0, 1, 2, 3, 4, 5, 6)) }
    var durationDays by remember(editingHabit) { mutableStateOf(editingHabit?.durationDays) }
    var customDurationText by remember(editingHabit) {
        mutableStateOf(editingHabit?.durationDays?.toString() ?: "")
    }
    var reminderTime by remember(editingHabit) { mutableStateOf(editingHabit?.reminderTime ?: "") }
    var trackTimeSpent by remember(editingHabit) { mutableStateOf(editingHabit?.trackTimeSpent ?: false) }

    val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
    val durationPresets = listOf(7, 21, 30, 60, 90, null) // null = Ongoing

    val accentColor = MaterialTheme.colorScheme.primary
    val onAccentColor = MaterialTheme.colorScheme.onPrimary
    val accentContainer = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        GlassCard(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(20.dp)
                .imePadding(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(26.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (editingHabit != null) "Edit habit" else "New habit",
                    style = Typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = AppTheme.colors.text
                    ),
                    modifier = Modifier.padding(bottom = 18.dp)
                )

                // Field: Name
                ModalLabel("NAME")
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    placeholder = { Text("e.g. Morning run", color = AppTheme.colors.textFaint) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppTheme.colors.surface,
                        unfocusedContainerColor = AppTheme.colors.surface,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = AppTheme.colors.border,
                        focusedTextColor = AppTheme.colors.text,
                        unfocusedTextColor = AppTheme.colors.text
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                // Field: Category
                ModalLabel("CATEGORY")
                OutlinedTextField(
                    value = category,
                    onValueChange = { if (it.length <= 24) category = it },
                    placeholder = { Text("e.g. Exercise", color = AppTheme.colors.textFaint) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppTheme.colors.surface,
                        unfocusedContainerColor = AppTheme.colors.surface,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = AppTheme.colors.border,
                        focusedTextColor = AppTheme.colors.text,
                        unfocusedTextColor = AppTheme.colors.text
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                // Field: Color Swatches
                ModalLabel("COLOR")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    HabitPalette.forEach { hex ->
                        val color = parseHexColor(hex)
                        val isSelected = colorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) AppTheme.colors.text else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }

                // Field: Days
                ModalLabel("WHICH DAYS DO YOU DO IT?")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    dayLabels.forEachIndexed { idx, lbl ->
                        val isSelected = days.contains(idx)
                        val bgColor = if (isSelected) accentColor else AppTheme.colors.surface
                        val txtColor = if (isSelected) onAccentColor else AppTheme.colors.textMuted
                        val borderColor = if (isSelected) accentColor else AppTheme.colors.border

                        Surface(
                            onClick = {
                                if (isSelected) {
                                    if (days.size > 1) {
                                        days = days - idx
                                    }
                                } else {
                                    days = (days + idx).sorted()
                                }
                            },
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = bgColor,
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = lbl,
                                    style = Typography.labelMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = txtColor
                                    )
                                )
                            }
                        }
                    }
                }

                // Field: Duration
                ModalLabel("DURATION")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp)
                ) {
                    durationPresets.forEach { preset ->
                        val isSelected = durationDays == preset
                        val bgColor = if (isSelected) accentColor else AppTheme.colors.surface
                        val txtColor = if (isSelected) onAccentColor else AppTheme.colors.textMuted
                        val borderColor = if (isSelected) accentColor else AppTheme.colors.border

                        Surface(
                            onClick = {
                                durationDays = preset
                                customDurationText = preset?.toString() ?: ""
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = bgColor,
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Text(
                                text = if (preset == null) "Ongoing" else "$preset days",
                                style = Typography.bodyMedium.copy(
                                    color = txtColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = customDurationText,
                    onValueChange = { txt ->
                        customDurationText = txt
                        val parsed = txt.toIntOrNull()
                        if (parsed != null && parsed > 0) {
                            durationDays = parsed
                        } else if (txt.isBlank()) {
                            durationDays = null
                        }
                    },
                    placeholder = { Text("Or type a custom number of days", color = AppTheme.colors.textFaint, fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppTheme.colors.surface,
                        unfocusedContainerColor = AppTheme.colors.surface,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = AppTheme.colors.border,
                        focusedTextColor = AppTheme.colors.text,
                        unfocusedTextColor = AppTheme.colors.text
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                ModalLabel("REMINDER TIME (HH:MM)")
                OutlinedTextField(
                    value = reminderTime,
                    onValueChange = { if (it.length <= 5) reminderTime = it },
                    placeholder = { Text("e.g. 08:30", color = AppTheme.colors.textFaint) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppTheme.colors.surface,
                        unfocusedContainerColor = AppTheme.colors.surface,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = AppTheme.colors.border,
                        focusedTextColor = AppTheme.colors.text,
                        unfocusedTextColor = AppTheme.colors.text
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Field: Track time spent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { trackTimeSpent = !trackTimeSpent }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Track time spent",
                            style = Typography.bodyMedium.copy(
                                color = AppTheme.colors.text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                        Text(
                            text = "Log and stopwatch the minutes spent on this habit daily",
                            style = Typography.labelSmall.copy(
                                color = AppTheme.colors.textFaint,
                                fontSize = 11.sp
                            )
                        )
                    }
                    Switch(
                        checked = trackTimeSpent,
                        onCheckedChange = { trackTimeSpent = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = accentColor,
                            checkedTrackColor = accentContainer
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                // Modal Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Delete button if editing
                    if (editingHabit != null) {
                        ModernOutlinedButton(
                            onClick = { onDelete(editingHabit) },
                            border = BorderStroke(1.dp, ClayOrange),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ClayOrange),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Delete", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // Right: Cancel and Save
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModernOutlinedButton(
                            onClick = onClose,
                            border = BorderStroke(1.dp, AppTheme.colors.border),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.textMuted),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.SemiBold)
                        }

                        ModernButton(
                            onClick = {
                                onSave(name, category, colorHex, days, durationDays, reminderTime, trackTimeSpent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = onAccentColor
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModalLabel(text: String) {
    Text(
        text = text,
        style = Typography.labelSmall.copy(
            color = AppTheme.colors.textFaint,
            fontSize = 11.sp,
            letterSpacing = 0.6.sp
        ),
        modifier = Modifier.padding(bottom = 6.dp)
    )
}
