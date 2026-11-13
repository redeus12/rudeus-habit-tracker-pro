package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ChartBucket
import com.example.model.ChartGranularity
import com.example.model.Habit
import com.example.ui.HabitViewModel
import com.example.ui.HabitWithAnalytics
import com.example.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TrendsTabContent(
    viewModel: HabitViewModel,
    onNewHabit: () -> Unit,
    onLoadSample: () -> Unit
) {
    val context = LocalContext.current
    val habitsWithAnalytics by viewModel.habitsWithAnalytics.collectAsStateWithLifecycle()
    var showShareModal by remember { mutableStateOf(false) }

    val accentColor = MaterialTheme.colorScheme.primary
    val onAccentColor = MaterialTheme.colorScheme.onPrimary
    val accentContainer = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Section Header Card with Share Action
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(accentContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Progress Curves",
                                style = Typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.text,
                                    fontSize = 20.sp
                                )
                            )
                            Text(
                                text = "Consistency trajectories & performance curves",
                                style = Typography.bodyMedium.copy(color = AppTheme.colors.textFaint, fontSize = 12.5.sp)
                            )
                        }
                    }

                    if (habitsWithAnalytics.isNotEmpty()) {
                        Button(
                            onClick = { showShareModal = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = onAccentColor
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Share Curves",
                                style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            )
                        }
                    }
                }
            }
        }

        // Infographic Poster Showcase Banner
        if (habitsWithAnalytics.isNotEmpty()) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
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
                            Text("✨", fontSize = 22.sp)
                            Column {
                                Text(
                                    text = "Share All Habits Curve Infographic",
                                    style = Typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor,
                                        fontSize = 15.sp
                                    )
                                )
                                Text(
                                    text = "Export an attractive HD poster card with your habit trajectories & Google Play badge to inspire your friends.",
                                    style = Typography.bodySmall.copy(color = AppTheme.colors.textMuted, fontSize = 12.sp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showShareModal = true },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = onAccentColor
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Preview & Share Poster",
                                style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.saveHabitsCurvesToGallery(context)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = accentColor
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Save to Gallery",
                                style = Typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
                            )
                        }
                    }
                }
            }
        }

        if (habitsWithAnalytics.isEmpty()) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No habits tracked yet",
                        style = Typography.titleMedium.copy(
                            color = AppTheme.colors.text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add your first habit or load example habits to see your consistency curves evolve over time.",
                        style = Typography.bodyMedium.copy(color = AppTheme.colors.textFaint, textAlign = TextAlign.Center),
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ModernButton(
                            onClick = onNewHabit,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = onAccentColor
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("+ New habit", fontWeight = FontWeight.Bold)
                        }
                        ModernOutlinedButton(
                            onClick = onLoadSample,
                            border = BorderStroke(1.dp, accentColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Load Example Habits", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            habitsWithAnalytics.forEach { item ->
                HabitProgressCurveCard(habitWithAnalytics = item)
            }
        }
    }

    ShareCurvesModal(
        isOpen = showShareModal,
        habitsWithAnalytics = habitsWithAnalytics,
        onClose = { showShareModal = false },
        onSaveSuccess = {
            viewModel.triggerCelebration()
        }
    )
}

@Composable
fun HabitProgressCurveCard(habitWithAnalytics: HabitWithAnalytics) {
    val habit = habitWithAnalytics.habit
    val (granularity, buckets) = habitWithAnalytics.chart
    val latestBucket = buckets.lastOrNull()
    val latestRate = latestBucket?.rate ?: 0f
    val latestPct = if (latestBucket != null && latestBucket.scheduledDays > 0) {
        (latestRate * 100f).roundToInt()
    } else null

    val topSubtext = when (granularity) {
        ChartGranularity.DAY -> "SO FAR"
        ChartGranularity.WEEK -> if (latestBucket?.isPartial == true) "THIS WEEK" else "LATEST WEEK"
        ChartGranularity.MONTH -> if (latestBucket?.isPartial == true) "THIS MONTH" else "LATEST MONTH"
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            // Header: Dot + Name + Category Tag + Latest %
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(habit.color, CircleShape)
                    )
                    Text(
                        text = habit.name,
                        style = Typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = AppTheme.colors.text
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = habit.color,
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Text(
                            text = habit.category,
                            style = Typography.labelSmall.copy(
                                color = AppTheme.colors.iconOnAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.4.sp
                            ),
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (latestPct == null) "—" else "$latestPct%",
                        style = Typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (latestPct == null) AppTheme.colors.textFaint else habit.color
                        )
                    )
                    Text(
                        text = topSubtext,
                        style = Typography.labelSmall.copy(
                            color = AppTheme.colors.textFaint,
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Body: Chart or empty placeholder
            if (buckets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .background(AppTheme.colors.surface3, RoundedCornerShape(10.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No data yet",
                        style = Typography.bodyMedium.copy(color = AppTheme.colors.textMuted, textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                ProgressCurveChart(
                    granularity = granularity,
                    buckets = buckets,
                    habitColor = habit.color
                )
            }
        }
    }
}

@Composable
fun ProgressCurveChart(
    granularity: ChartGranularity,
    buckets: List<ChartBucket>,
    habitColor: Color
) {
    var selectedIndex by remember(buckets) { mutableStateOf<Int?>(null) }
    val activeIndex = (selectedIndex ?: buckets.lastIndex).coerceIn(0, buckets.lastIndex)
    val activeBucket = buckets[activeIndex]
    var canvasWidth by remember { mutableStateOf(0f) }

    val isSelectedLast = (activeIndex == buckets.lastIndex)
    val captionText = when (granularity) {
        ChartGranularity.DAY -> "So far"
        ChartGranularity.WEEK -> {
            if (isSelectedLast) {
                if (activeBucket.isPartial) "This week" else "Latest week"
            } else {
                "Selected week"
            }
        }
        ChartGranularity.MONTH -> {
            if (isSelectedLast) {
                if (activeBucket.isPartial) "This month" else "Latest month"
            } else {
                "Selected month"
            }
        }
    }

    val activePct = (activeBucket.rate * 100f).roundToInt()

    Column(modifier = Modifier.fillMaxWidth()) {
        // Tooltip / Active Point Info Banner (2 lines max, no 3-line wrap)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.06f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeBucket.fullRangeLabel,
                        style = Typography.labelMedium.copy(
                            color = AppTheme.colors.text,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = captionText,
                        style = Typography.labelSmall.copy(
                            color = AppTheme.colors.textFaint,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = habitColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, habitColor.copy(alpha = 0.8f))
                ) {
                    Text(
                        text = "$activePct% (${activeBucket.doneDays}/${activeBucket.scheduledDays}d)",
                        style = Typography.labelMedium.copy(
                            color = habitColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (buckets.size > 1) {
            Text(
                text = "Tap any dot to inspect",
                style = Typography.labelSmall.copy(
                    color = AppTheme.colors.textFaint.copy(alpha = 0.7f),
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
            )
        }

        // Chart Area (Y-axis labels + Canvas)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Y-axis percentage labels
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 10.dp, top = 6.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text("100%", style = Typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = AppTheme.colors.textFaint, fontSize = 10.sp))
                Text("50%", style = Typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = AppTheme.colors.textFaint, fontSize = 10.sp))
                Text("0%", style = Typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = AppTheme.colors.textFaint, fontSize = 10.sp))
            }

            val gridColor = AppTheme.colors.borderSoft
            val gridColorFaint = AppTheme.colors.borderSoft.copy(alpha = 0.5f)
            val surfaceBg = AppTheme.colors.surface

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged { canvasWidth = it.width.toFloat() }
                    .pointerInput(buckets, canvasWidth) {
                        detectTapGestures(
                            onTap = { offset ->
                                if (buckets.isNotEmpty() && canvasWidth > 0f) {
                                    if (buckets.size == 1) {
                                        selectedIndex = 0
                                    } else {
                                        val frac = (offset.x / canvasWidth).coerceIn(0f, 1f)
                                        val idx = (frac * (buckets.size - 1)).roundToInt().coerceIn(0, buckets.lastIndex)
                                        selectedIndex = idx
                                    }
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val topPad = 6.dp.toPx()
                    val botPad = 6.dp.toPx()
                    val chartH = h - topPad - botPad
                    val N = buckets.size

                    val y100 = topPad
                    val y50 = topPad + chartH * 0.5f
                    val y0 = topPad + chartH

                    // Draw horizontal gridlines
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y100),
                        end = Offset(w, y100),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = gridColorFaint,
                        start = Offset(0f, y50),
                        end = Offset(w, y50),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y0),
                        end = Offset(w, y0),
                        strokeWidth = 1.dp.toPx()
                    )

                    val pts = buckets.mapIndexed { i, b ->
                        val x = if (N <= 1) w * 0.5f else i.toFloat() / (N - 1) * w
                        val y = (topPad + (1f - b.rate.coerceIn(0f, 1f)) * chartH).coerceIn(y100, y0)
                        Offset(x, y)
                    }

                    if (N >= 2) {
                        val linePath = Path().apply {
                            moveTo(pts[0].x, pts[0].y)
                            for (i in 0 until pts.size - 1) {
                                val p0 = pts[i]
                                val p1 = pts[i + 1]
                                val dx = p1.x - p0.x
                                val c1 = Offset(p0.x + dx * 0.4f, p0.y.coerceIn(y100, y0))
                                val c2 = Offset(p1.x - dx * 0.4f, p1.y.coerceIn(y100, y0))
                                cubicTo(c1.x, c1.y, c2.x, c2.y, p1.x, p1.y)
                            }
                        }

                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(pts.last().x, y0)
                            lineTo(pts.first().x, y0)
                            close()
                        }

                        // Gradient fill under curve with alpha 0.18
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    habitColor.copy(alpha = 0.18f),
                                    habitColor.copy(alpha = 0.02f)
                                ),
                                startY = y100,
                                endY = y0
                            )
                        )

                        // Smooth line with 2dp width
                        drawPath(
                            path = linePath,
                            color = habitColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // Vertical guideline for selected point
                    if (N >= 2 && activeIndex in pts.indices) {
                        val selX = pts[activeIndex].x
                        drawLine(
                            color = habitColor.copy(alpha = 0.45f),
                            start = Offset(selX, y100),
                            end = Offset(selX, y0),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Draw data points (dots)
                    pts.forEachIndexed { i, pt ->
                        val isSelected = (i == activeIndex)
                        val isLast = (i == pts.lastIndex)
                        val isPartial = (isLast && buckets[i].isPartial)
                        val dotRadius = if (isSelected) 5.5f.dp.toPx() else 3.5f.dp.toPx()

                        if (isSelected) {
                            drawCircle(
                                color = habitColor.copy(alpha = 0.25f),
                                radius = dotRadius + 3.dp.toPx(),
                                center = pt
                            )
                        }

                        if (isPartial) {
                            drawCircle(
                                color = surfaceBg,
                                radius = dotRadius,
                                center = pt
                            )
                            drawCircle(
                                color = habitColor,
                                radius = dotRadius,
                                center = pt,
                                style = Stroke(width = 1.8f.dp.toPx())
                            )
                            if (isSelected) {
                                drawCircle(
                                    color = habitColor,
                                    radius = 2.dp.toPx(),
                                    center = pt
                                )
                            }
                        } else {
                            drawCircle(
                                color = habitColor,
                                radius = dotRadius,
                                center = pt
                            )
                            drawCircle(
                                color = Color.White,
                                radius = if (isSelected) 2.2f.dp.toPx() else 1.2f.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }

                if (buckets.size == 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 32.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = "Building your curve",
                            style = Typography.labelSmall.copy(
                                color = AppTheme.colors.textFaint,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // X-axis date labels (at most 5, first and last always, centered under dot, no overlap)
        TrendsXLabelsRow(
            buckets = buckets,
            granularity = granularity,
            activeIndex = activeIndex,
            habitColor = habitColor,
            modifier = Modifier.padding(start = 36.dp)
        )
    }
}

@Composable
private fun TrendsXLabelsRow(
    buckets: List<ChartBucket>,
    granularity: ChartGranularity,
    activeIndex: Int,
    habitColor: Color,
    modifier: Modifier = Modifier
) {
    if (buckets.isEmpty()) return

    val labels = remember(buckets, granularity) {
        if (granularity == ChartGranularity.MONTH) {
            val years = buckets.mapNotNull {
                try { LocalDate.parse(it.startDateStr).year } catch (e: Exception) { null }
            }
            val hasMultipleYears = years.distinct().size > 1
            buckets.mapIndexed { idx, bucket ->
                try {
                    val ld = LocalDate.parse(bucket.startDateStr)
                    if (hasMultipleYears) {
                        val prevYear = if (idx > 0) LocalDate.parse(buckets[idx - 1].startDateStr).year else null
                        if (prevYear == null || ld.year != prevYear) {
                            ld.format(DateTimeFormatter.ofPattern("MMM ''yy", Locale.getDefault()))
                        } else {
                            ld.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))
                        }
                    } else {
                        bucket.label
                    }
                } catch (e: Exception) {
                    bucket.label
                }
            }
        } else {
            buckets.map { it.label }
        }
    }

    if (buckets.size == 1) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = labels[0],
                style = Typography.labelSmall.copy(
                    color = habitColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp
                )
            )
        }
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        if (totalWidthPx <= 0f) return@BoxWithConstraints

        val density = LocalDensity.current
        val charWidthPx = with(density) { 6.5.dp.toPx() }
        val minGapPx = with(density) { 8.dp.toPx() }

        val n = buckets.size
        val maxLabels = 5
        val candidateIndices = if (n <= maxLabels) {
            (0 until n).toList()
        } else {
            val step = (n - 1).toDouble() / (maxLabels - 1)
            val list = mutableListOf<Int>()
            list.add(0)
            for (i in 1 until maxLabels - 1) {
                val idx = (i * step).roundToInt().coerceIn(0, n - 1)
                if (!list.contains(idx)) list.add(idx)
            }
            if (!list.contains(n - 1)) list.add(n - 1)
            list.sorted()
        }

        data class Placement(val index: Int, val text: String, val leftPx: Float, val rightPx: Float)

        val firstText = labels[0]
        val firstWidthPx = firstText.length * charWidthPx
        val firstPlacement = Placement(0, firstText, 0f, firstWidthPx)

        val lastText = labels[n - 1]
        val lastWidthPx = lastText.length * charWidthPx
        val lastLeftPx = (totalWidthPx - lastWidthPx).coerceAtLeast(0f)
        val lastPlacement = Placement(n - 1, lastText, lastLeftPx, totalWidthPx)

        val chosenPlacements = mutableListOf<Placement>()
        chosenPlacements.add(firstPlacement)

        var prevRightEdge = firstWidthPx

        for (idx in candidateIndices) {
            if (idx == 0 || idx == n - 1) continue
            val labelText = labels[idx]
            val labelWidthPx = labelText.length * charWidthPx
            val dotCenterPx = idx.toFloat() / (n - 1) * totalWidthPx
            val leftPx = (dotCenterPx - labelWidthPx / 2f).coerceIn(0f, (totalWidthPx - labelWidthPx).coerceAtLeast(0f))
            val rightPx = leftPx + labelWidthPx

            if (leftPx >= prevRightEdge + minGapPx && rightPx <= lastLeftPx - minGapPx) {
                chosenPlacements.add(Placement(idx, labelText, leftPx, rightPx))
                prevRightEdge = rightPx
            }
        }

        chosenPlacements.add(lastPlacement)

        for (placement in chosenPlacements) {
            val isSelected = (placement.index == activeIndex)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(placement.leftPx.roundToInt(), 0) }
            ) {
                Text(
                    text = placement.text,
                    style = Typography.labelSmall.copy(
                        color = if (isSelected) habitColor else AppTheme.colors.textFaint,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 10.5.sp
                    )
                )
            }
        }
    }
}

