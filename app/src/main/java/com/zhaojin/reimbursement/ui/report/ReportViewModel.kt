package com.zhaojin.reimbursement.ui.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhaojin.reimbursement.data.AppDatabase
import com.zhaojin.reimbursement.data.BillEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

class ReportViewModel(application: Application) : AndroidViewModel(application) {

    private val billDao = AppDatabase.getDatabase(application).billDao()

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState

    private val _periodType = MutableStateFlow(PeriodType.WEEK)
    val periodType: StateFlow<PeriodType> = _periodType

    private val _currentOffset = MutableStateFlow(0)
    val currentOffset: StateFlow<Int> = _currentOffset

    private val _customRange = MutableStateFlow<DateRange?>(null)
    val customRange: StateFlow<DateRange?> = _customRange

    init {
        // Reactive pipeline: any change of period/offset re-queries the
        // time-ranged Flow, and new bills inserted while the report is open
        // automatically refresh it (previously a one-shot load that went stale).
        viewModelScope.launch {
            combine(
                _periodType, _currentOffset, _customRange
            ) { type: PeriodType, offset: Int, custom: DateRange? ->
                ReportQuery(type, offset, custom)
            }.flatMapLatest { q ->
                val earliestMillis = earliestMillisFor(q.type, q.offset, q.custom)
                billDao.getBillsSince(earliestMillis).mapLatest { bills ->
                    buildUiState(bills, q.type, q.offset, q.custom)
                }
            }.collect { _uiState.value = it }
        }
    }

    /** 一次报表查询的全部参数 */
    private data class ReportQuery(
        val type: PeriodType,
        val offset: Int,
        val custom: DateRange?
    )

    fun setPeriodType(type: PeriodType) {
        _periodType.value = type
        _currentOffset.value = 0
    }

    fun prevPeriod() {
        _currentOffset.value -= 1
    }

    fun nextPeriod() {
        _currentOffset.value += 1
    }

    fun setMonth(year: Int, month: Int) {
        val now = LocalDate.now()
        val selected = LocalDate.of(year, month, 1)
        _currentOffset.value = (selected.year - now.year) * 12 + (selected.monthValue - now.monthValue)
    }

    fun setYear(year: Int) {
        val now = LocalDate.now()
        _currentOffset.value = year - now.year
    }

    /** 设置自定义时间段（最长 366 天），对比区间取等长的前一段 */
    fun setCustomRange(start: LocalDate, end: LocalDate) {
        val clampedEnd = minOf(end, start.plusDays(365))
        val s = minOf(start, clampedEnd)
        _customRange.value = DateRange(
            s, clampedEnd,
            s.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            clampedEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            String.format("%d.%02d.%02d~%02d.%02d", s.year, s.monthValue, s.dayOfMonth, clampedEnd.monthValue, clampedEnd.dayOfMonth)
        )
        _periodType.value = PeriodType.CUSTOM
    }

    // ------------------------------------------------------------------
    // UI state derivation (pure, given a bill list)
    // ------------------------------------------------------------------

    private fun earliestMillisFor(type: PeriodType, offset: Int, custom: DateRange?): Long {
        if (type == PeriodType.CUSTOM) {
            val start = custom?.start ?: LocalDate.now()
            return start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val now = LocalDate.now()
        // Bar data goes back 5 periods from the current one
        val earliestDate = when (type) {
            PeriodType.WEEK -> now.plusWeeks((offset - 5).toLong())
                .with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)
            PeriodType.CUSTOM -> LocalDate.now()
            PeriodType.MONTH -> now.plusMonths((offset - 5).toLong()).withDayOfMonth(1)
            PeriodType.YEAR -> now.plusYears((offset - 5).toLong()).withMonth(1).withDayOfMonth(1)
        }
        return earliestDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun buildUiState(
        bills: List<BillEntity>,
        type: PeriodType,
        offset: Int,
        custom: DateRange?
    ): ReportUiState {
        val now = LocalDate.now()

        if (type == PeriodType.CUSTOM && custom == null) {
            return ReportUiState(periodLabel = "请选择时间段", isLoading = false)
        }

        val currentRange = when (type) {
            PeriodType.WEEK -> getWeekRange(now.plusWeeks(offset.toLong()))
            PeriodType.MONTH -> getMonthRange(now.plusMonths(offset.toLong()))
            PeriodType.YEAR -> getYearRange(now.plusYears(offset.toLong()))
            PeriodType.CUSTOM -> custom!!
        }
        val prevRange = when (type) {
            PeriodType.WEEK -> getWeekRange(now.plusWeeks(offset.toLong()).minusWeeks(1))
            PeriodType.MONTH -> getMonthRange(now.plusMonths(offset.toLong()).minusMonths(1))
            PeriodType.YEAR -> getYearRange(now.plusYears(offset.toLong()).minusYears(1))
            PeriodType.CUSTOM -> {
                val c = custom!!
                val lengthDays = ChronoUnit.DAYS.between(c.start, c.end) + 1
                val prevEnd = c.start.minusDays(1)
                val prevStart = prevEnd.minusDays(lengthDays - 1)
                DateRange(
                    prevStart, prevEnd,
                    prevStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    prevEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    "上一时间段"
                )
            }
        }

        val currentBills = bills.filter { it.timestamp in currentRange.startMillis..currentRange.endMillis }
        val prevBills = bills.filter { it.timestamp in prevRange.startMillis..prevRange.endMillis }

        val currentTotal = currentBills.sumOf { it.amount }
        val prevTotal = prevBills.sumOf { it.amount }

        val elapsedDays = calculateElapsedDays(type, offset, currentRange.start, currentRange.end, LocalDate.now())
        val dailyAvg = currentTotal / elapsedDays

        return ReportUiState(
            periodLabel = currentRange.label,
            periodTotal = currentTotal,
            dailyAvg = dailyAvg,
            prevDiff = currentTotal - prevTotal,
            trendData = calculateTrendData(currentBills, type, offset, custom),
            barData = calculateBarData(bills, type, offset),
            currentYear = currentRange.start.year,
            currentMonth = currentRange.start.monthValue,
            periodStartMillis = currentRange.startMillis,
            periodEndMillis = currentRange.endMillis,
            isLoading = false
        )
    }

    private fun calculateTrendData(
        bills: List<BillEntity>,
        type: PeriodType,
        offset: Int,
        custom: DateRange?
    ): List<TrendPoint> {
        val zone = ZoneId.systemDefault()
        // One-pass bucketing: group bills by date once instead of re-filtering
        // the whole list (with a timezone conversion per bill) for every bucket.
        val byDate = bills.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }

        return when (type) {
            PeriodType.WEEK -> {
                val base = LocalDate.now().plusWeeks(offset.toLong())
                    .with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)
                (0..6).map { dayOffset ->
                    val date = base.plusDays(dayOffset.toLong())
                    val amount = byDate[date].orEmpty().sumOf { it.amount }
                    val label = String.format("%02d.%02d", date.monthValue, date.dayOfMonth)
                    TrendPoint(label, amount, date.toString())
                }
            }
            PeriodType.MONTH -> {
                val base = LocalDate.now().plusMonths(offset.toLong()).withDayOfMonth(1)
                val daysInMonth = base.lengthOfMonth()
                (0 until daysInMonth).map { dayOffset ->
                    val date = base.plusDays(dayOffset.toLong())
                    val amount = byDate[date].orEmpty().sumOf { it.amount }
                    TrendPoint("${date.dayOfMonth}日", amount, date.toString())
                }
            }
            PeriodType.YEAR -> {
                val base = LocalDate.now().plusYears(offset.toLong()).withMonth(1).withDayOfMonth(1)
                val byMonth = bills.groupBy {
                    YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate())
                }
                (0..11).map { monthOffset ->
                    val month = base.plusMonths(monthOffset.toLong())
                    val amount = byMonth[YearMonth.from(month)].orEmpty().sumOf { it.amount }
                    TrendPoint("${month.monthValue}月", amount, "${month.year}-${month.monthValue}")
                }
            }
            PeriodType.CUSTOM -> {
                val range = custom ?: return emptyList()
                val days = ChronoUnit.DAYS.between(range.start, range.end).toInt() + 1
                (0 until days).map { dayOffset ->
                    val date = range.start.plusDays(dayOffset.toLong())
                    val amount = byDate[date].orEmpty().sumOf { it.amount }
                    TrendPoint("${date.monthValue}.${date.dayOfMonth}", amount, date.toString())
                }
            }
        }
    }

    private fun calculateBarData(
        bills: List<BillEntity>,
        type: PeriodType,
        currentOffset: Int
    ): List<BarPoint> {
        if (type == PeriodType.CUSTOM) return emptyList()
        val zone = ZoneId.systemDefault()
        val byDate = bills.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }

        fun sumDays(start: LocalDate, dayCount: Long): Double =
            (0 until dayCount).sumOf { i -> byDate[start.plusDays(i)].orEmpty().sumOf { it.amount } }

        return when (type) {
            PeriodType.CUSTOM -> emptyList()  // 已在函数入口提前返回，此分支仅为穷尽性
            PeriodType.WEEK -> {
                (-5..0).map { offset ->
                    val weekStart = LocalDate.now().plusWeeks((currentOffset + offset).toLong())
                        .with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)
                    val weekEnd = weekStart.plusDays(6)
                    val weekNumber = weekStart.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear())
                    val label = when {
                        currentOffset == 0 && offset == 0 -> "本周"
                        currentOffset == 0 && offset == -1 -> "上周"
                        else -> "${weekNumber}周"
                    }
                    val tooltip = String.format(
                        "%d.%02d.%02d~%02d",
                        weekStart.year, weekStart.monthValue, weekStart.dayOfMonth, weekEnd.dayOfMonth
                    )
                    BarPoint(label, sumDays(weekStart, 7), tooltip)
                }
            }
            PeriodType.MONTH -> {
                val byMonth = bills.groupBy {
                    YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate())
                }
                (-5..0).map { offset ->
                    val monthStart = LocalDate.now().plusMonths((currentOffset + offset).toLong()).withDayOfMonth(1)
                    val label = when (offset) {
                        0 -> "本月"
                        -1 -> "上月"
                        else -> "${monthStart.monthValue}月"
                    }
                    val tooltip = String.format("%d年%02d月", monthStart.year, monthStart.monthValue)
                    BarPoint(label, byMonth[YearMonth.from(monthStart)].orEmpty().sumOf { it.amount }, tooltip)
                }
            }
            PeriodType.YEAR -> {
                val byYear = bills.groupBy {
                    Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().year
                }
                (-5..0).map { offset ->
                    val year = LocalDate.now().plusYears((currentOffset + offset).toLong())
                    val label = when (offset) {
                        0 -> "本年"
                        -1 -> "上年"
                        else -> "${year.year}年"
                    }
                    BarPoint(label, byYear[year.year].orEmpty().sumOf { it.amount }, "${year.year}年")
                }
            }
        }
    }

    private fun getWeekRange(date: LocalDate): DateRange {
        val start = date.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)
        val end = start.plusDays(6)
        return DateRange(
            start, end,
            start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            String.format("%d.%02d.%02d~%02d.%02d", start.year, start.monthValue, start.dayOfMonth, end.monthValue, end.dayOfMonth)
        )
    }

    private fun getMonthRange(date: LocalDate): DateRange {
        val start = date.withDayOfMonth(1)
        val end = start.plusMonths(1).minusDays(1)
        return DateRange(
            start, end,
            start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            String.format("%d年%02d月", date.year, date.monthValue)
        )
    }

    private fun getYearRange(date: LocalDate): DateRange {
        val start = date.withMonth(1).withDayOfMonth(1)
        val end = date.withMonth(12).withDayOfMonth(31)
        return DateRange(
            start, end,
            start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            String.format("%d年", date.year)
        )
    }

    data class ReportUiState(
        val periodLabel: String = "",
        val periodTotal: Double = 0.0,
        val dailyAvg: Double = 0.0,
        val prevDiff: Double = 0.0,
        val trendData: List<TrendPoint> = emptyList(),
        val barData: List<BarPoint> = emptyList(),
        val currentYear: Int = java.time.LocalDate.now().year,
        val currentMonth: Int = java.time.LocalDate.now().monthValue,
        val periodStartMillis: Long = 0L,
        val periodEndMillis: Long = 0L,
        val isLoading: Boolean = true
    )

    data class TrendPoint(val label: String, val amount: Double, val dateKey: String)
    data class BarPoint(val label: String, val amount: Double, val tooltipLabel: String)
    data class DateRange(
        val start: LocalDate,
        val end: LocalDate,
        val startMillis: Long,
        val endMillis: Long,
        val label: String
    )

    enum class PeriodType { WEEK, MONTH, YEAR, CUSTOM }
}

/**
 * 日均/月均分母计算（顶层纯函数，单元测试直连）：
 * - 历史周期（offset < 0）按完整周期天数算
 * - 当前/未来周期（offset >= 0）按周期内已过去的实际天数算（含今天），
 *   年视图按已过月数（含当月）
 */
internal fun calculateElapsedDays(
    type: ReportViewModel.PeriodType,
    offset: Int,
    periodStart: LocalDate,
    periodEnd: LocalDate,
    today: LocalDate
): Long {
    return when {
        // 历史周期已完整结束，按完整周期天数算
        offset < 0 -> when (type) {
            ReportViewModel.PeriodType.WEEK -> 7L
            ReportViewModel.PeriodType.MONTH -> ChronoUnit.DAYS.between(periodStart, periodEnd) + 1
            ReportViewModel.PeriodType.YEAR -> 12L
            ReportViewModel.PeriodType.CUSTOM -> ChronoUnit.DAYS.between(periodStart, periodEnd) + 1
        }
        // 当前/未来周期：按周期内已过去的实际天数算
        else -> {
            when {
                // 周期尚未开始（未来周期）
                today.isBefore(periodStart) -> 1L
                // 周期已结束（offset>=0 的边界情况），按完整周期
                today.isAfter(periodEnd) -> when (type) {
                    ReportViewModel.PeriodType.WEEK -> 7L
                    ReportViewModel.PeriodType.MONTH -> ChronoUnit.DAYS.between(periodStart, periodEnd) + 1
                    ReportViewModel.PeriodType.YEAR -> 12L
                    ReportViewModel.PeriodType.CUSTOM -> ChronoUnit.DAYS.between(periodStart, periodEnd) + 1
                }
                // 年视图按已过月数算（含当月）
                type == ReportViewModel.PeriodType.YEAR -> today.monthValue.toLong()
                // 自定义时间段：按起点到今天（封顶到终点）的已过天数
                type == ReportViewModel.PeriodType.CUSTOM ->
                    (ChronoUnit.DAYS.between(periodStart, minOf(today, periodEnd)) + 1).coerceAtLeast(1L)
                // 周/月视图按已过天数算（含今天）
                else -> ChronoUnit.DAYS.between(periodStart, today) + 1
            }
        }
    }.coerceAtLeast(1)
}
