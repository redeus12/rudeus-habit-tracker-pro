package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.HabitViewModel
import com.example.ui.HeatmapPoint
import com.example.ui.theme.*
import com.example.util.DateUtils
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun DisciplineFieldCard(viewModel: HabitViewModel) {
    val accentColor = MaterialTheme.colorScheme.primary
    val analyticsState by viewModel.analyticsState.collectAsStateWithLifecycle()
    val todayDone = analyticsState.todayDone
    val todayTotal = analyticsState.todayTotal
    val todayPct = analyticsState.todayPct
    val rate30 = analyticsState.rate30d
    val bestStreakName = analyticsState.bestStreakName
    val bestStreakValue = analyticsState.bestStreakValue
    val activeHabitsCount = analyticsState.activeHabitsCount

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Discipline field",
                    style = Typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = AppTheme.colors.text
                    )
                )
                Text(
                    text = "18-week consistency",
                    style = Typography.bodyMedium.copy(
                        color = AppTheme.colors.textFaint,
                        fontSize = 11.5.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Unified Ring + Stats inside flat container (no nested glass)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Smaller Circular Progress Ring on the left
                    val trackColor = AppTheme.colors.surface3
                    val textColor = AppTheme.colors.text
                    val labelColor = AppTheme.colors.textMuted
                    val accentColor = MaterialTheme.colorScheme.primary

                    Box(
                        modifier = Modifier.size(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(64.dp)) {
                            val strokeWidth = 6.5.dp.toPx()
                            // Track
                            drawArc(
                                color = trackColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth)
                            )
                            // Progress
                            if (todayPct > 0f) {
                                drawArc(
                                    color = accentColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * todayPct,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "$todayDone/$todayTotal",
                                style = Typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = textColor
                                )
                            )
                            Text(
                                text = "today",
                                style = Typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = labelColor,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }

                    // Stats laid out in a single horizontal row
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactStatItem(
                            num = "${(rate30 * 100).roundToInt()}%",
                            lbl = "30D RATE"
                        )
                        CompactStatItem(
                            num = "$bestStreakValue d",
                            lbl = if (bestStreakName.isNotBlank() && bestStreakName != "None") bestStreakName.take(8).uppercase() else "BEST STREAK"
                        )
                        CompactStatItem(
                            num = activeHabitsCount.toString(),
                            lbl = "ACTIVE"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Shrunk Heatmap Grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Column {
                    CompactHeatmapGrid(heatmapPoints = analyticsState.heatmapPoints)
                    Spacer(modifier = Modifier.height(6.dp))
                    // Legend - single thin line directly under the grid
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Less",
                            style = Typography.labelSmall.copy(color = AppTheme.colors.textFaint, fontSize = 9.5.sp)
                        )
                        LegendSquare(color = AppTheme.colors.surface3)
                        LegendSquare(color = accentColor.copy(alpha = 0.3f))
                        LegendSquare(color = accentColor.copy(alpha = 0.55f))
                        LegendSquare(color = accentColor.copy(alpha = 0.8f))
                        LegendSquare(color = accentColor)
                        Text(
                            text = "More",
                            style = Typography.labelSmall.copy(color = AppTheme.colors.textFaint, fontSize = 9.5.sp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(MutedCrimson.copy(alpha = 0.4f), RoundedCornerShape(1.5.dp))
                                .border(0.8.dp, MutedCrimson, RoundedCornerShape(1.5.dp))
                        )
                        Text(
                            text = "Missed",
                            style = Typography.labelSmall.copy(color = AppTheme.colors.textMuted, fontSize = 9.5.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactHeatmapGrid(heatmapPoints: List<HeatmapPoint>) {
    val accentColor = MaterialTheme.colorScheme.primary
    val padded = mutableListOf<HeatmapPoint?>()
    val dates = heatmapPoints.map { it.dateStr }
    val firstDow = if (dates.isNotEmpty()) DateUtils.dayOfWeek(dates.first()) else 0
    for (i in 0 until firstDow) {
        padded.add(null)
    }
    padded.addAll(heatmapPoints)
    val weeks = ceil(padded.size / 7.0).toInt()

    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (w in 0 until weeks) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (d in 0 until 7) {
                    val idx = w * 7 + d
                    val point = if (idx < padded.size) padded[idx] else null
                    if (point == null) {
                        Box(modifier = Modifier.size(7.5.dp))
                    } else {
                        val v = point.fieldIntensity
                        val isMissedDay = point.isMissedDay

                        val color = when {
                            isMissedDay -> MutedCrimson.copy(alpha = 0.4f)
                            v < 0 || v == 0f -> AppTheme.colors.surface3
                            v <= 0.25f -> accentColor.copy(alpha = 0.3f)
                            v <= 0.5f -> accentColor.copy(alpha = 0.55f)
                            v <= 0.75f -> accentColor.copy(alpha = 0.8f)
                            else -> accentColor
                        }
                        val border = if (isMissedDay) BorderStroke(0.8.dp, MutedCrimson) else null

                        Box(
                            modifier = Modifier
                                .size(7.5.dp)
                                .background(color, RoundedCornerShape(1.5.dp))
                                .then(if (border != null) Modifier.border(border, RoundedCornerShape(1.5.dp)) else Modifier)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LegendSquare(color: Color) {
    Box(
        modifier = Modifier
            .size(7.dp)
            .background(color, RoundedCornerShape(1.5.dp))
    )
}

@Composable
fun CompactStatItem(num: String, lbl: String) {
    val accentColor = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = num,
            style = Typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = accentColor
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = lbl,
            style = Typography.labelSmall.copy(
                color = AppTheme.colors.textFaint,
                fontSize = 9.5.sp,
                letterSpacing = 0.3.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
