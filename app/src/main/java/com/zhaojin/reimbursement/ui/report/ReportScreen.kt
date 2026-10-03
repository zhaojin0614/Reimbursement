@file:OptIn(ExperimentalMaterial3Api::class)

package com.zhaojin.reimbursement.ui.report

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.sqrt

import com.zhaojin.reimbursement.ui.components.BillDatePickerDialog
import com.zhaojin.reimbursement.ui.theme.categoryColor
import com.zhaojin.reimbursement.ui.components.GlassCompactDialog
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

    // 自定义时间段：pickingCustom = true 选开始 / false 选结束 / null 关闭
    var pickingCustom by remember { mutableStateOf<Boolean?>(null) }
    // 点击分类构成后查看的分类账单明细
    var billsCategory by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = onBack != null) {
        onBack?.invoke()
    }

    billsCategory?.let { category ->
        val catBills = uiState.categoryBills[category].orEmpty().sortedByDescending { it.timestamp }
        val catTotal = catBills.sumOf { it.amount }
        GlassCompactDialog(
            onDismissRequest = { billsCategory = null },
            title = "$category · ${uiState.periodLabel}",
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(catBills, key = { it.id }) { bill ->
                        val date = java.time.Instant.ofEpochMilli(bill.timestamp)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = date.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd")),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(44.dp)
                            )
                            Text(
                                text = bill.title.ifBlank { "未填写内容" },
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Text(
                                text = "¥%,.2f".format(bill.amount),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text(
                                text = "合计（${catBills.size}笔）",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "¥%,.2f".format(catTotal),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { billsCategory = null }) { Text("关闭") }
            }
        )
    }

    pickingCustom?.let { pickingStart ->
        val current = viewModel.customRange.value
        val initial = (if (pickingStart) current?.start else current?.end) ?: LocalDate.now()
        BillDatePickerDialog(
            title = if (pickingStart) "选择开始日期" else "选择结束日期",
            initialDate = initial,
            maxDate = LocalDate.now(),
            onDismiss = { pickingCustom = null },
            onConfirm = { picked ->
                // setCustomRange 内部会归一化起止顺序
                if (pickingStart) {
                    viewModel.setCustomRange(picked, current?.end ?: picked)
                } else {
                    viewModel.setCustomRange(current?.start ?: picked, picked)
                }
                pickingCustom = null
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
                        customStart = viewModel.customRange.value?.start,
                        customEnd = viewModel.customRange.value?.end,
                        onPickStart = { pickingCustom = true },
                        onPickEnd = { pickingCustom = false }
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

                // 分类构成：饼图 + 图例（仅有账单时显示），点击分类查看当期账单明细
                if (uiState.categoryShares.isNotEmpty()) {
                    CategoryBreakdownCard(
                        shares = uiState.categoryShares,
                        totalAmount = uiState.periodTotal,
                        periodLabel = uiState.periodLabel,
                        onCategoryClick = { billsCategory = it },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(ComponentGap))
                }

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

/** 自定义时间段的起/止入口：label + 当前日期，点击打开对应日历 */
@Composable
private fun CustomRangeChip(
    label: String,
    dateText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalReportColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.divider.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = colors.textGray
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = dateText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (dateText == "未选择") colors.textGray else colors.textDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
    customStart: LocalDate?,
    customEnd: LocalDate?,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit
) {
    when (periodType) {
        ReportViewModel.PeriodType.CUSTOM -> {
            // 左边选开始、右边选结束（各自打开独立日历）
            val fmt: (LocalDate) -> String =
                { "%d.%02d.%02d".format(it.year, it.monthValue, it.dayOfMonth) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CustomRangeChip(
                    label = "开始",
                    dateText = customStart?.let(fmt) ?: "未选择",
                    onClick = onPickStart,
                    modifier = Modifier.weight(1f)
                )
                CustomRangeChip(
                    label = "结束",
                    dateText = customEnd?.let(fmt) ?: "未选择",
                    onClick = onPickEnd,
                    modifier = Modifier.weight(1f)
                )
            }
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
        // 比上期：报销增加为红（花得多），减少为绿（花得少）。
        // 自定义时段没有固定上一期可比，不展示这一项。
        if (periodType != ReportViewModel.PeriodType.CUSTOM) {
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
 * 分类构成：环形饼图 + 图例（占比/金额），点击扇区或图例行查看该分类
 * 在当期的账单明细。
 */
@Composable
private fun CategoryBreakdownCard(
    shares: List<ReportViewModel.CategoryShare>,
    totalAmount: Double,
    periodLabel: String,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = shares.sumOf { it.amount }.coerceAtLeast(0.0001)
    SoftCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "分类构成",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(
                    modifier = Modifier
                        .size(190.dp)
                        .pointerInput(shares) {
                            detectTapGestures { pos ->
                                val dx = pos.x - size.width / 2f
                                val dy = pos.y - size.height / 2f
                                if (sqrt(dx * dx + dy * dy) > minOf(size.width, size.height) / 2f) {
                                    return@detectTapGestures
                                }
                                // 画布扇区从 -90°（12 点方向）顺时针展开，触点角度换算回同一坐标系
                                var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                                angle = (angle + 90f + 360f) % 360f
                                var acc = 0f
                                shares.forEachIndexed { i, share ->
                                    val sweep = (share.amount / total * 360.0).toFloat().coerceAtLeast(0.01f)
                                    if (angle in acc..(acc + sweep)) {
                                        onCategoryClick(share.name)
                                        return@detectTapGestures
                                    }
                                    acc += sweep
                                }
                            }
                        }
                ) {
                    val ringWidth = 38.dp.toPx()
                    val radius = (size.minDimension - ringWidth) / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    var start = -90f
                    shares.forEachIndexed { i, share ->
                        val sweep = (share.amount / total * 360.0).toFloat().coerceAtLeast(0.01f)
                        drawArc(
                            color = categoryColor(share.name),
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2f, radius * 2f),
                            style = Stroke(width = ringWidth)
                        )
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "总金额",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "¥%,.2f".format(totalAmount),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = periodLabel,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            shares.forEachIndexed { i, share ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCategoryClick(share.name) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(categoryColor(share.name))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = share.name,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "%.0f%%".format(share.fraction * 100),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "¥%,.2f".format(share.amount),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "查看该分类账单",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}