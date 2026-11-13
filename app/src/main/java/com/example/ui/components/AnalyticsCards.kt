package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Habit
import com.example.model.TrendPoint
import com.example.ui.theme.*
import com.example.util.DateUtils
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun CategoryPerformanceCard(habits: List<Habit>) {
    val cats = remember(habits) { DateUtils.categoryPerformance(habits) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Category performance",
                    style = Typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = AppTheme.colors.text
                    )
                )
                Text(
                    text = "30-day rate",
                    style = Typography.bodyMedium.copy(
                        color = AppTheme.colors.textFaint,
                        fontSize = 11.5.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (cats.isEmpty()) {
                Text(
                    text = "Add habits to see category performance.",
                    style = Typography.bodyMedium.copy(color = AppTheme.colors.textFaint, fontSize = 12.sp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    cats.forEach { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 36.dp)
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Color dot
                            Box(
                                modifier = Modifier
                                    .size(7.5.dp)
                                    .background(c.color, CircleShape)
                            )

                            Spacer(modifier = Modifier.width(7.dp))

                            // Label
                            Text(
                                text = c.category,
                                style = Typography.bodyMedium.copy(
                                    color = AppTheme.colors.textMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = Modifier.width(78.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Track & Progress bar
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.5.dp)
                                    .background(AppTheme.colors.surface3, RoundedCornerShape(3.5.dp))
                            ) {
                                val pct = c.rate
                                if (pct > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(pct)
                                            .background(c.color, RoundedCornerShape(3.5.dp))
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Percentage
                            Text(
                                text = "${(c.rate * 100).roundToInt()}%",
                                style = Typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = AppTheme.colors.textFaint,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Merged Weekly + Monthly Progress Card with Toggle
 */
@Composable
fun ProgressTrendCard(habits: List<Habit>) {
    val accentColor = MaterialTheme.colorScheme.primary
    val accentContainer = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    var selectedTab by remember { mutableStateOf(0) } // 0: Weekly, 1: Monthly
    val isWeekly = selectedTab == 0
    val weeklyData = remember(habits) { DateUtils.weeklyTrend(habits, 8) }
    val monthlyData = remember(habits) { DateUtils.monthlyTrend(habits, 6) }
    val data = if (isWeekly) weeklyData else monthlyData
    val barColor = if (isWeekly) accentColor else accentColor.copy(alpha = 0.75f)

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with Segmented Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isWeekly) "Weekly progress" else "Monthly progress",
                        style = Typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = AppTheme.colors.text
                        )
                    )
                    Text(
                        text = if (isWeekly) "Last 8 weeks" else "Last 6 months",
                        style = Typography.bodyMedium.copy(
                            color = AppTheme.colors.textFaint,
                            fontSize = 11.sp
                        )
                    )
                }

                // Compact Toggle Pills
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppTheme.colors.surface3
                ) {
                    Row(
                        modifier = Modifier.padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isWeekly) accentContainer else Color.Transparent,
                            modifier = Modifier.clickable { selectedTab = 0 }
                        ) {
                            Text(
                                text = "Weekly",
                                style = Typography.labelSmall.copy(
                                    fontWeight = if (isWeekly) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isWeekly) accentColor else AppTheme.colors.textMuted,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (!isWeekly) accentContainer else Color.Transparent,
                            modifier = Modifier.clickable { selectedTab = 1 }
                        ) {
                            Text(
                                text = "Monthly",
                                style = Typography.labelSmall.copy(
                                    fontWeight = if (!isWeekly) FontWeight.Bold else FontWeight.Normal,
                                    color = if (!isWeekly) accentColor else AppTheme.colors.textMuted,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                data.forEach { pt ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .widthIn(max = 22.dp)
                                .background(
                                    AppTheme.colors.surface3,
                                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 1.5.dp, bottomEnd = 1.5.dp)
                                ),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            val pct = max(0.05f, pt.rate)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(pct)
                                    .background(
                                        barColor,
                                        RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 1.5.dp, bottomEnd = 1.5.dp)
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pt.label,
                            style = Typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = AppTheme.colors.textFaint,
                                fontSize = 8.5.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// Keep backward compatibility if called elsewhere
@Composable
fun WeeklyProgressCard(habits: List<Habit>) {
    ProgressTrendCard(habits = habits)
}

@Composable
fun MonthlyProgressCard(habits: List<Habit>) {
    ProgressTrendCard(habits = habits)
}

