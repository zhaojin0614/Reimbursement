@file:OptIn(ExperimentalMaterial3Api::class)

package com.zhaojin.reimbursement.ui.report

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil

import com.zhaojin.reimbursement.ui.components.PillToggle
import com.zhaojin.reimbursement.ui.components.SoftCard
import com.zhaojin.reimbursement.ui.components.glassBorder
import com.zhaojin.reimbursement.ui.theme.ComponentGap
import com.zhaojin.reimbursement.ui.theme.ExpenseRed
import com.zhaojin.reimbursement.ui.theme.IncomeGreen
import com.zhaojin.reimbursement.ui.theme.ReportBlue
import com.zhaojin.reimbursement.ui.theme.ReportBlueLight
import kotlinx.coroutines.launch

// ============================================================
// Theme-aware report palette: light/dark via CompositionLocal
// ============================================================

private data class ReportColors(
    val cardBg: Color,
    val bgGray: Color,
    val textDark: Color,
    val textGray: Color,
    val divider: Color,
    val neutralGray: Color
)

private val LocalReportColors = compositionLocalOf {
    ReportColors(
        cardBg = Color(0xFFFFFFFF),
        bgGray = Color(0xFFF6F8F7),
        textDark = Color(0xFF2A3833),
        textGray = Color(0xFF8A9B96),
        divider = Color(0xFFE6EDEA),
        neutralGray = Color(0xFFE7EEEA)
    )
}

@Composable
private fun rememberReportColors(): ReportColors {
    val scheme = MaterialTheme.colorScheme
    return ReportColors(
        cardBg = scheme.surface,
        bgGray = scheme.background,
        textDark = scheme.onSurface,
        textGray = scheme.onSurfaceVariant,
        divider = scheme.outline.copy(alpha = 0.35f),
        neutralGray = scheme.surfaceVariant
    )
}

/**
 * 报表页。作为主导航 Tab 使用时 [onBack] 传 null（隐藏返回按钮、不拦截返回），
 * 由其他页面覆盖式打开时可传回调。
 */
@Composable
fun ReportScreen(
    onBack: (() -> Unit)? = null,
    viewModel: ReportViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val periodType by viewModel.periodType.collectAsState()
    val currentOffset by viewModel.currentOffset.collectAsState()

    var showCustomRangePicker by remember { mutableStateOf(false) }

    BackHandler(enabled = onBack != null) {
        onBack?.invoke()
    }

    if (showCustomRangePicker) {
        CustomRangePickerDialog(
            initialStart = viewModel.customRange.value?.start,
            initialEnd = viewModel.customRange.value?.end,
            onDismiss = { showCustomRangePicker = false },
            onConfirm = { start, end ->
                viewModel.setCustomRange(start, end)
                showCustomRangePicker = false
            }
        )
    }

    CompositionLocalProvider(LocalReportColors provides rememberReportColors()) {
        // 背景由 MainApp 根布局的 AmbientBackground 提供
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("报销报表", fontWeight = FontWeight.Bold, color = LocalReportColors.current.textDark) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = LocalReportColors.current.textDark)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .verticalScroll(rememberScrollState())
            ) {
                // Period tabs：与记账界面同款 PillToggle（同高度/同边距），
                // 紧贴标题栏，不再额外留 12dp 纵向空白
                PillToggle(
                    options = listOf(
                        "周报" to MaterialTheme.colorScheme.primary,
                        "月报" to MaterialTheme.colorScheme.primary,
                        "年报" to MaterialTheme.colorScheme.primary,
                        "自定义" to MaterialTheme.colorScheme.primary
                    ),
                    selectedIndex = when (periodType) {
                        ReportViewModel.PeriodType.WEEK -> 0
                        ReportViewModel.PeriodType.MONTH -> 1
                        ReportViewModel.PeriodType.YEAR -> 2
                        ReportViewModel.PeriodType.CUSTOM -> 3
                    },
                    onSelect = { index ->
                        viewModel.setPeriodType(
                            when (index) {
                                0 -> ReportViewModel.PeriodType.WEEK
                                1 -> ReportViewModel.PeriodType.MONTH
                                2 -> ReportViewModel.PeriodType.YEAR
                                else -> ReportViewModel.PeriodType.CUSTOM
                            }
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                // 自定义时间段的选择入口只有下方 DateNavigation 一处：
                // 「2026.07.15~07.17（点击选择）」既展示当前区间又可点击打开选择器

                // 报表主体：周期切换为瞬时切换（不做过场动画）。
                // 图表全是 Canvas 自绘且年报数据点最多，任何双布局过场
                // （Crossfade）都会掉帧；实测 alpha 淡入在这种重布局下
                // 也贡献可感知的迟滞，用户确认改回秒切
                Column {
                Spacer(modifier = Modifier.height(ComponentGap))

                // Date nav
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DateNavigation(
                        periodType = periodType,
                        label = uiState.periodLabel,
                        currentYear = uiState.currentYear,
                        currentMonth = uiState.currentMonth,
                        canGoNext = currentOffset < 0,
                        onPrev = { viewModel.prevPeriod() },
                        onNext = { viewModel.nextPeriod() },
                        onSelectMonth = { y, m -> viewModel.setMonth(y, m) },
                        onSelectYear = { y -> viewModel.setYear(y) },
                        onSelectCustomRange = { showCustomRangePicker = true }
                    )
                }

                Spacer(modifier = Modifier.height(ComponentGap))

                // Summary cards
                SummaryCards(
                    periodType = periodType,
                    periodTotal = uiState.periodTotal,
                    dailyAvg = uiState.dailyAvg,
                    prevDiff = uiState.prevDiff,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(ComponentGap))

                // Trend line chart
                TrendLineChartSection(
                    periodType = periodType,
                    data = uiState.trendData,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Bar chart（自定义时间段无上一周期对比，隐藏）
                if (uiState.barData.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(ComponentGap))
                    TrendBarChartSection(
                        data = uiState.barData,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}
}

@Composable
private fun DateNavigation(
    periodType: ReportViewModel.PeriodType,
    label: String,
    currentYear: Int,
    currentMonth: Int,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectMonth: (Int, Int) -> Unit,
    onSelectYear: (Int) -> Unit,
    onSelectCustomRange: () -> Unit = {}
) {
    when (periodType) {
        ReportViewModel.PeriodType.CUSTOM -> {
            Text(
                text = "$label（点击选择）",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectCustomRange() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
        ReportViewModel.PeriodType.WEEK -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上一周期", tint = LocalReportColors.current.textGray)
                }
                Text(
                    text = label,
                    fontSize = 14.sp,
                    color = LocalReportColors.current.textDark,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(horizontal = 4.dp)
                )
                IconButton(onClick = onNext, modifier = Modifier.size(32.dp), enabled = canGoNext) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下一周期", tint = if (canGoNext) LocalReportColors.current.textGray else LocalReportColors.current.textGray.copy(alpha = 0.3f))
                }
            }
        }
        ReportViewModel.PeriodType.MONTH -> {
            var yearExpanded by remember { mutableStateOf(false) }
            var monthExpanded by remember { mutableStateOf(false) }
            val years = remember { (2020..LocalDate.now().year + 1).toList() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Year dropdown
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { yearExpanded = true }
                    ) {
                        Text("${currentYear}年", fontSize = 14.sp, color = LocalReportColors.current.textDark, fontWeight = FontWeight.Medium)
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = LocalReportColors.current.textGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                        years.forEach { year ->
                            DropdownMenuItem(
                                text = { Text("${year}年") },
                                onClick = {
                                    onSelectMonth(year, currentMonth)
                                    yearExpanded = false
                                }
                            )
                        }
                    }
                }
                // Month dropdown
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { monthExpanded = true }
                    ) {
                        Text(String.format("%02d月", currentMonth), fontSize = 14.sp, color = LocalReportColors.current.textDark, fontWeight = FontWeight.Medium)
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = LocalReportColors.current.textGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                        (1..12).forEach { month ->
                            DropdownMenuItem(
                                text = { Text("${month}月") },
                                onClick = {
                                    onSelectMonth(currentYear, month)
                                    monthExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
        ReportViewModel.PeriodType.YEAR -> {
            var expanded by remember { mutableStateOf(false) }
            val years = remember {
                val now = java.time.Year.now().value
                (2020..now).map { it to "${it}年" }.reversed()
            }
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { expanded = true }
                ) {
                    Text(label, fontSize = 14.sp, color = LocalReportColors.current.textDark, fontWeight = FontWeight.Medium)
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = LocalReportColors.current.textGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    years.forEach { (year, text) ->
                        DropdownMenuItem(
                            text = { Text(text) },
                            onClick = {
                                onSelectYear(year)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCards(
    periodType: ReportViewModel.PeriodType,
    periodTotal: Double,
    dailyAvg: Double,
    prevDiff: Double,
    modifier: Modifier = Modifier
) {
    val typeLabel = "维修报销"
    val (totalLabel, avgLabel, diffLabel) = when (periodType) {
        ReportViewModel.PeriodType.WEEK ->
            Triple("本周${typeLabel}（元）", "日均${typeLabel}（元）", "比上周${typeLabel}（元）")
        ReportViewModel.PeriodType.MONTH ->
            Triple("本月${typeLabel}（元）", "日均${typeLabel}（元）", "比上月${typeLabel}（元）")
        ReportViewModel.PeriodType.YEAR ->
            Triple("本年${typeLabel}（元）", "月均${typeLabel}（元）", "比上年${typeLabel}（元）")
        ReportViewModel.PeriodType.CUSTOM ->
            Triple("时段${typeLabel}（元）", "日均${typeLabel}（元）", "比上一时段（元）")
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ComponentGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(ComponentGap)) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = totalLabel,
                value = "${String.format("%.2f", periodTotal)}",
                valueColor = LocalReportColors.current.textDark,
                leftBorderColor = MaterialTheme.colorScheme.secondary
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = avgLabel,
                value = "${String.format("%.2f", dailyAvg)}",
                valueColor = LocalReportColors.current.textDark,
                leftBorderColor = MaterialTheme.colorScheme.secondary
            )
        }
        // 比上期：报销增加为红（花得多），减少为绿（花得少）
        val diffColor = if (prevDiff > 0) ExpenseRed else IncomeGreen
        val diffSign = if (prevDiff > 0) "+" else ""
        StatCard(
            modifier = Modifier.fillMaxWidth(),
            title = diffLabel,
            value = "$diffSign${String.format("%.2f", prevDiff)}",
            valueColor = diffColor,
            leftBorderColor = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    title: String,
    value: String,
    valueColor: Color,
    leftBorderColor: Color
) {
    SoftCard(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = LocalReportColors.current.cardBg
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .heightIn(min = 80.dp)
                    .background(leftBorderColor)
            )
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = LocalReportColors.current.textGray,
                    fontWeight = FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = valueColor
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = LocalReportColors.current.textDark,
        modifier = modifier.padding(bottom = 10.dp, top = 4.dp)
    )
}

@Composable
private fun TrendLineChartSection(
    periodType: ReportViewModel.PeriodType,
    data: List<ReportViewModel.TrendPoint>,
    modifier: Modifier = Modifier
) {
    val title = when (periodType) {
        ReportViewModel.PeriodType.WEEK -> "本周趋势"
        ReportViewModel.PeriodType.MONTH -> "本月趋势"
        ReportViewModel.PeriodType.YEAR -> "本年趋势"
        ReportViewModel.PeriodType.CUSTOM -> "时间段趋势"
    }
    val typeLabel = "维修报销"

    SoftCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = LocalReportColors.current.cardBg,
        contentPadding = 16.dp
    ) {
        SectionTitle(title)
        if (data.isNotEmpty()) {
            TrendLineChart(
                data = data,
                typeLabel = typeLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        } else {
            EmptyChartState("暂无数据")
        }
    }
}

@Composable
private fun TrendLineChart(
    data: List<ReportViewModel.TrendPoint>,
    typeLabel: String,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableIntStateOf(-1) }
    val textMeasurer = rememberTextMeasurer()
    val colors = LocalReportColors.current

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(data) {
                    detectTapGestures { offset ->
                        val paddingLeft = 44.dp.toPx()
                        val paddingRight = 16.dp.toPx()
                        val plotWidth = size.width.toFloat() - paddingLeft - paddingRight
                        val step = plotWidth / (data.size - 1).coerceAtLeast(1)
                        val idx = ((offset.x - paddingLeft + step / 2) / step)
                            .toInt()
                            .coerceIn(0, data.size - 1)
                        selectedIndex = if (selectedIndex == idx) -1 else idx
                    }
                }
        ) {
            val paddingLeft = 44.dp.toPx()
            val paddingBottom = 32.dp.toPx()
            val paddingTop = 28.dp.toPx()
            val paddingRight = 16.dp.toPx()

            val plotWidth = size.width - paddingLeft - paddingRight
            val plotHeight = size.height - paddingTop - paddingBottom

            val maxValue = (data.maxOfOrNull { it.amount } ?: 0.0).let {
                if (it == 0.0) 100.0 else ceil(it / 5.0) * 5.0
            }

            // Horizontal grid lines + Y labels
            for (i in 0..4) {
                val y = paddingTop + plotHeight * (1 - i / 4f)
                drawLine(
                    color = colors.divider,
                    start = Offset(paddingLeft, y),
                    end = Offset(size.width - paddingRight, y),
                    strokeWidth = 1f
                )
                val label = String.format("%.2f", maxValue * i / 4)
                val labelResult = textMeasurer.measure(
                    text = label,
                    style = TextStyle(fontSize = 10.sp, color = colors.textGray)
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(
                        paddingLeft - labelResult.size.width - 6f,
                        y - labelResult.size.height / 2
                    ),
                    style = TextStyle(fontSize = 10.sp, color = colors.textGray)
                )
            }

            // Compute points
            val points = data.mapIndexed { index, point ->
                val x = if (data.size <= 1) {
                    paddingLeft + plotWidth / 2
                } else {
                    paddingLeft + plotWidth * index / (data.size - 1)
                }
                val y = paddingTop + plotHeight * (1 - (point.amount / maxValue).toFloat())
                Offset(x, y)
            }

            // X labels - sparse display to avoid crowding
            val labelStep = when (data.size) {
                in 0..10 -> 1
                in 11..20 -> 2
                in 21..31 -> 5
                else -> kotlin.math.max(1, data.size / 6)
            }
            data.forEachIndexed { index, point ->
                if (index % labelStep == 0 || index == data.lastIndex) {
                    val x = if (data.size <= 1) {
                        paddingLeft + plotWidth / 2
                    } else {
                        paddingLeft + plotWidth * index / (data.size - 1)
                    }
                    drawText(
                        textMeasurer = textMeasurer,
                        text = point.label,
                        topLeft = Offset(
                            x - 12.dp.toPx(),
                            size.height - paddingBottom + 6f
                        ),
                        style = TextStyle(fontSize = 10.sp, color = colors.textGray, textAlign = TextAlign.Center)
                    )
                }
            }

            // Fill area
            if (points.size > 1) {
                val fillPath = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                    lineTo(points.last().x, paddingTop + plotHeight)
                    lineTo(points[0].x, paddingTop + plotHeight)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(ReportBlue.copy(alpha = 0.2f), Color.Transparent),
                        startY = paddingTop,
                        endY = paddingTop + plotHeight
                    )
                )
            }

            // Line
            if (points.size > 1) {
                val linePath = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
                drawPath(
                    path = linePath,
                    color = ReportBlue,
                    style = Stroke(width = 2.5f)
                )
            }

            // Points
            points.forEachIndexed { index, point ->
                val radius = if (index == selectedIndex) 6f else 3.5f
                drawCircle(color = Color.White, radius = radius + 2f, center = point)
                drawCircle(color = ReportBlue, radius = radius, center = point)
            }

            // Tooltip
            if (selectedIndex in points.indices) {
                val point = data[selectedIndex]
                val pt = points[selectedIndex]
                val tooltipText = "${point.label}${typeLabel} ¥${String.format("%.2f", point.amount)}"
                val tooltipStyle = TextStyle(
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                val tooltipResult = textMeasurer.measure(text = tooltipText, style = tooltipStyle)
                val bubbleWidth = tooltipResult.size.width + 20f
                val bubbleHeight = tooltipResult.size.height + 10f
                val triangleHeight = 6f

                var bubbleLeft = pt.x - bubbleWidth / 2
                if (bubbleLeft < 4f) bubbleLeft = 4f
                if (bubbleLeft + bubbleWidth > size.width - 4f) {
                    bubbleLeft = size.width - 4f - bubbleWidth
                }

                val bubbleTop = pt.y - bubbleHeight - triangleHeight - 8f
                val bubbleBottom = bubbleTop + bubbleHeight

                drawRoundRect(
                    color = ReportBlue,
                    topLeft = Offset(bubbleLeft, bubbleTop),
                    size = Size(bubbleWidth, bubbleHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                val trianglePath = Path().apply {
                    moveTo(pt.x, pt.y - 8f)
                    lineTo(pt.x - triangleHeight, bubbleBottom)
                    lineTo(pt.x + triangleHeight, bubbleBottom)
                    close()
                }
                drawPath(trianglePath, color = ReportBlue)

                drawText(
                    textMeasurer = textMeasurer,
                    text = tooltipText,
                    topLeft = Offset(bubbleLeft + 10f, bubbleTop + 5f),
                    style = tooltipStyle
                )
            }
        }
    }
}

@Composable
private fun TrendBarChartSection(
    data: List<ReportViewModel.BarPoint>,
    modifier: Modifier = Modifier
) {
    val title = "维修报销趋势"
    SoftCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = LocalReportColors.current.cardBg,
        contentPadding = 16.dp
    ) {
        SectionTitle(title)
        if (data.isNotEmpty()) {
            TrendBarChart(
                data = data,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        } else {
            EmptyChartState("暂无数据")
        }
    }
}

@Composable
private fun TrendBarChart(
    data: List<ReportViewModel.BarPoint>,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableIntStateOf(-1) }
    val textMeasurer = rememberTextMeasurer()
    val colors = LocalReportColors.current

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(data) {
                    detectTapGestures { offset ->
                        val paddingLeft = 44.dp.toPx()
                        val paddingRight = 16.dp.toPx()
                        val plotWidth = size.width.toFloat() - paddingLeft - paddingRight
                        val barSlotWidth = plotWidth / data.size
                        val idx = ((offset.x - paddingLeft) / barSlotWidth)
                            .toInt()
                            .coerceIn(0, data.size - 1)
                        selectedIndex = if (selectedIndex == idx) -1 else idx
                    }
                }
        ) {
            val paddingLeft = 44.dp.toPx()
            val paddingBottom = 32.dp.toPx()
            val paddingTop = 28.dp.toPx()
            val paddingRight = 16.dp.toPx()

            val plotWidth = size.width - paddingLeft - paddingRight
            val plotHeight = size.height - paddingTop - paddingBottom

            val maxValue = (data.maxOfOrNull { it.amount } ?: 0.0).let {
                if (it == 0.0) 100.0 else ceil(it / 5.0) * 5.0
            }

            // Grid lines + Y labels
            for (i in 0..4) {
                val y = paddingTop + plotHeight * (1 - i / 4f)
                drawLine(
                    color = colors.divider,
                    start = Offset(paddingLeft, y),
                    end = Offset(size.width - paddingRight, y),
                    strokeWidth = 1f
                )
                val label = String.format("%.2f", maxValue * i / 4)
                val labelResult = textMeasurer.measure(
                    text = label,
                    style = TextStyle(fontSize = 10.sp, color = colors.textGray)
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(
                        paddingLeft - labelResult.size.width - 6f,
                        y - labelResult.size.height / 2
                    ),
                    style = TextStyle(fontSize = 10.sp, color = colors.textGray)
                )
            }

            // Bars
            val barSlotWidth = plotWidth / data.size
            val barWidth = barSlotWidth * 0.5f
            data.forEachIndexed { index, point ->
                val centerX = paddingLeft + barSlotWidth * index + barSlotWidth / 2
                val barHeight = (point.amount / maxValue).toFloat() * plotHeight
                val top = paddingTop + plotHeight - barHeight

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(ReportBlueLight, ReportBlue),
                        startY = top,
                        endY = paddingTop + plotHeight
                    ),
                    topLeft = Offset(centerX - barWidth / 2, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }

            // X labels
            data.forEachIndexed { index, point ->
                val centerX = paddingLeft + barSlotWidth * index + barSlotWidth / 2
                drawText(
                    textMeasurer = textMeasurer,
                    text = point.label,
                    topLeft = Offset(
                        centerX - 12.dp.toPx(),
                        size.height - paddingBottom + 6f
                    ),
                    style = TextStyle(fontSize = 10.sp, color = colors.textGray, textAlign = TextAlign.Center)
                )
            }

            // Tooltip
            if (selectedIndex in data.indices) {
                val point = data[selectedIndex]
                val centerX = paddingLeft + barSlotWidth * selectedIndex + barSlotWidth / 2
                val barHeight = (point.amount / maxValue).toFloat() * plotHeight
                val top = paddingTop + plotHeight - barHeight

                val line1 = "¥${String.format("%.2f", point.amount)}"
                val line2 = point.tooltipLabel

                val style1 = TextStyle(
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                val style2 = TextStyle(
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )

                val result1 = textMeasurer.measure(text = line1, style = style1)
                val result2 = textMeasurer.measure(text = line2, style = style2)

                val bubbleWidth = maxOf(result1.size.width, result2.size.width) + 20f
                val bubbleHeight = result1.size.height + result2.size.height + 14f
                val triangleHeight = 6f

                var bubbleLeft = centerX - bubbleWidth / 2
                if (bubbleLeft < 4f) bubbleLeft = 4f
                if (bubbleLeft + bubbleWidth > size.width - 4f) {
                    bubbleLeft = size.width - 4f - bubbleWidth
                }

                val bubbleTop = top - bubbleHeight - triangleHeight - 6f
                val bubbleBottom = bubbleTop + bubbleHeight

                drawRoundRect(
                    color = ReportBlue,
                    topLeft = Offset(bubbleLeft, bubbleTop),
                    size = Size(bubbleWidth, bubbleHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                val trianglePath = Path().apply {
                    moveTo(centerX, top - 6f)
                    lineTo(centerX - triangleHeight, bubbleBottom)
                    lineTo(centerX + triangleHeight, bubbleBottom)
                    close()
                }
                drawPath(trianglePath, color = ReportBlue)

                drawText(
                    textMeasurer = textMeasurer,
                    text = line1,
                    topLeft = Offset(
                        bubbleLeft + bubbleWidth / 2 - result1.size.width / 2,
                        bubbleTop + 6f
                    ),
                    style = style1
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = line2,
                    topLeft = Offset(
                        bubbleLeft + bubbleWidth / 2 - result2.size.width / 2,
                        bubbleTop + 8f + result1.size.height
                    ),
                    style = style2
                )
            }
        }
    }
}

@Composable
private fun EmptyChartState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.BarChart,
                contentDescription = null,
                tint = LocalReportColors.current.textGray.copy(alpha = 0.4f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = LocalReportColors.current.textGray
            )
        }
    }
}

/**
 * 自定义时间段选择弹窗：自绘月历（最长 366 天）。
 * 不用 M3 DateRangePicker：窄宽度下 7 列会挤成 6 列、不支持年月快速跳转、
 * 区间底色按单元格绘制导致断开。自绘实现保证——
 * 7 列完整等宽；点「2026年7月」展开年/月快跳面板；选中区间的底色带
 * 从起点圆右缘一直连到终点圆左缘（起点右半格 + 中间整格 + 终点左半格拼接）。
 */
@Composable
private fun CustomRangePickerDialog(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    val today = remember { LocalDate.now() }
    var displayMonth by remember { mutableStateOf(YearMonth.from(initialStart ?: today)) }
    var rangeStart by remember { mutableStateOf(initialStart) }
    var rangeEnd by remember { mutableStateOf(initialEnd) }
    var showJumpPanel by remember { mutableStateOf(false) }
    var jumpYear by remember { mutableStateOf(displayMonth.year) }

    val colors = LocalReportColors.current
    val bandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val accent = MaterialTheme.colorScheme.primary

    val rangeTooLong = rangeStart != null && rangeEnd != null &&
        (rangeEnd!!.toEpochDay() - rangeStart!!.toEpochDay()) > 365

    fun onDayClick(date: LocalDate) {
        val s = rangeStart
        when {
            // 未开始选，或上一段已选完 → 开始新一段
            s == null || rangeEnd != null -> { rangeStart = date; rangeEnd = null }
            date.isBefore(s) -> rangeStart = date
            else -> rangeEnd = date
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.cardBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                // ── 年月导航：◀ 2026年7月 ▾ ▶ ──────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { displayMonth = displayMonth.minusMonths(1) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowLeft, contentDescription = "上一月",
                            tint = colors.textDark, modifier = Modifier.size(22.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                jumpYear = displayMonth.year
                                showJumpPanel = !showJumpPanel
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${displayMonth.year}年${displayMonth.monthValue}月",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (showJumpPanel) accent else colors.textDark
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown, contentDescription = "选择年月",
                            tint = colors.textGray, modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { displayMonth = displayMonth.plusMonths(1) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowRight, contentDescription = "下一月",
                            tint = colors.textDark, modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // ── 年/月快跳面板 ──────────────────────────────────────
                if (showJumpPanel) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val years = remember(today) { (2020..today.year + 1).toList() }
                    Text(
                        text = "年份",
                        fontSize = 11.sp,
                        color = colors.textGray,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        years.forEach { year ->
                            val selected = year == jumpYear
                            Text(
                                text = "${year}年",
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) accent else colors.textDark,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) accent.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { jumpYear = year }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "月份",
                        fontSize = 11.sp,
                        color = colors.textGray,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    listOf(1..6, 7..12).forEach { months ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            months.forEach { month ->
                                val selected = jumpYear == displayMonth.year && month == displayMonth.monthValue
                                Text(
                                    text = "${month}月",
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) accent else colors.textDark,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 3.dp, vertical = 3.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) accent.copy(alpha = 0.15f) else colors.divider)
                                        .clickable {
                                            displayMonth = YearMonth.of(jumpYear, month)
                                            showJumpPanel = false
                                        }
                                        .padding(vertical = 7.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ── 星期表头：7 列完整等宽 ─────────────────────────────
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                        Text(
                            text = day,
                            fontSize = 12.sp,
                            color = colors.textGray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))

                // ── 月历网格：周一开头，区间底色连续拼接 ───────────────
                val leadingBlanks = displayMonth.atDay(1).dayOfWeek.value - 1
                val cells: List<LocalDate?> =
                    List(leadingBlanks) { null } + (1..displayMonth.lengthOfMonth()).map { displayMonth.atDay(it) }
                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            RangePickerDayCell(
                                date = date,
                                rangeStart = rangeStart,
                                rangeEnd = rangeEnd,
                                today = today,
                                bandColor = bandColor,
                                accent = accent,
                                onClick = { date?.let { onDayClick(it) } },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── 已选摘要 + 操作 ────────────────────────────────────
                val fmt: (LocalDate) -> String = { "%d.%02d.%02d".format(it.year, it.monthValue, it.dayOfMonth) }
                val summary = when {
                    rangeStart != null && rangeEnd != null -> "已选 ${fmt(rangeStart!!)} ~ ${fmt(rangeEnd!!)}"
                    rangeStart != null -> "已选 ${fmt(rangeStart!!)}，请选择结束日期"
                    else -> "点选起止日期"
                }
                Text(
                    text = if (rangeTooLong) "最长可选 366 天，请重新选择" else summary,
                    fontSize = 12.sp,
                    color = if (rangeTooLong) ExpenseRed else colors.textGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(
                        enabled = rangeStart != null && rangeEnd != null && !rangeTooLong,
                        onClick = { onConfirm(rangeStart!!, rangeEnd!!) }
                    ) { Text("确定") }
                }
            }
        }
    }
}

/** 月历单格：端点实心圆 + 区间底色带（起点右半格/中间整格/终点左半格拼成连续色带） */
@Composable
private fun RangePickerDayCell(
    date: LocalDate?,
    rangeStart: LocalDate?,
    rangeEnd: LocalDate?,
    today: LocalDate,
    bandColor: Color,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .then(if (date != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        date ?: return@Box
        val isStart = date == rangeStart
        val isEnd = date == rangeEnd
        val inRange = rangeStart != null && rangeEnd != null &&
            date.isAfter(rangeStart) && date.isBefore(rangeEnd)

        // 底色带高度略小于圆，视觉上像环绕日期的横带
        if (rangeStart != null && rangeEnd != null) {
            when {
                isStart && isEnd -> {}
                isStart -> Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(0.66f)
                        .fillMaxWidth(0.5f)
                        .background(bandColor)
                )
                isEnd -> Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight(0.66f)
                        .fillMaxWidth(0.5f)
                        .background(bandColor)
                )
                inRange -> Box(
                    modifier = Modifier
                        .fillMaxHeight(0.66f)
                        .fillMaxWidth()
                        .background(bandColor)
                )
            }
        }

        if (isStart || isEnd) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    fontSize = 13.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 13.sp,
                color = if (date == today) accent else LocalReportColors.current.textDark,
                fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
