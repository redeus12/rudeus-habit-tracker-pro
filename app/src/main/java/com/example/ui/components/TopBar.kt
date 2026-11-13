package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.util.DateUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TopBar(
    title: String = "Today",
    activeHabitsCount: Int = 0,
    completedTodayCount: Int = 0,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onProfileClick: () -> Unit = {}
) {
    val dateDisplay = remember {
        try {
            val date = LocalDate.now()
            date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)).uppercase()
        } catch (e: Exception) {
            DateUtils.todayStr().uppercase()
        }
    }

    val isGlass = AppTheme.colors.isGlassEnabled

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Uppercase date + Bold Title with Glass Shadow
            Column {
                Text(
                    text = dateDisplay,
                    style = Typography.labelMedium.copy(
                        color = if (isGlass) Color.White.copy(alpha = 0.75f) else AppTheme.colors.textMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = title,
                    style = Typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = if (isGlass) Color.White else AppTheme.colors.text,
                        shadow = if (isGlass) GlassTextShadow else null
                    )
                )
            }

            // Right: Status Pill Badge (Streak / Completions) + Profile Avatar Button
            val pillBgColor = if (isGlass) Color.White.copy(alpha = 0.18f) else AppTheme.colors.surface
            val pillBorderColor = if (isGlass) Color.White.copy(alpha = 0.35f) else AppTheme.colors.border
            val pillContentColor = if (isGlass) Color.White else AppTheme.colors.text

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // "0/7 Done" Flame Pill with root-sized backdrop blur
                GlassSurface(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = pillBgColor,
                    borderColor = pillBorderColor,
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🔥",
                            fontSize = 14.sp
                        )
                        Text(
                            text = "$completedTodayCount/$activeHabitsCount Done",
                            style = Typography.labelMedium.copy(
                                color = pillContentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }

                // Profile Avatar Circular Button with root-sized backdrop blur
                GlassIconButton(
                    onClick = onProfileClick,
                    size = 40.dp,
                    backgroundColor = pillBgColor,
                    borderColor = pillBorderColor
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = pillContentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
