package com.zhaojin.reimbursement.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private const val MIN_YEAR = 2000

private fun monthKeyOf(date: LocalDate) = date.year * 12 + date.monthValue - 1

/**
 * 自绘日期选择弹窗：年份下拉、月份箭头、日网格全部限定在可选日期范围内——
 * 箭头到边界自动置灰，未来月份/无数据月份不可见，行为完全可控
 * （M3 DatePicker 无法禁用翻月箭头，故不用）。
 *
 * [selectableDays]：可选日期集合（epochDay）。传 null 表示除 [maxDate] 外
 * 全部可选（如补记历史账单）；传集合则只有集合内的日子可选（如跳转有账单
 * 的日期），年/月范围也随之收敛到这些日期的跨度。
 */
@Composable
fun BillDatePickerDialog(
    title: String,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    confirmText: String = "确定",
    selectableDays: Set<Long>? = null,
    maxDate: LocalDate = LocalDate.now()
) {
    val today = remember { LocalDate.now() }

    // 可选范围：集合模式取集合跨度，开放模式 2000-01 ~ maxDate 当月
    val minMonthKey: Int
    val maxMonthKey: Int
    val years: List<Int>
    if (selectableDays != null) {
        val keys = selectableDays.map { monthKeyOf(LocalDate.ofEpochDay(it)) }.toSet()
        minMonthKey = keys.minOrNull() ?: monthKeyOf(today)
        maxMonthKey = keys.maxOrNull() ?: monthKeyOf(today)
        // 倒序：今年在最上；集合模式只列有可选日期的年份
        years = selectableDays.map { LocalDate.ofEpochDay(it).year }.distinct()
            .sortedDescending()
    } else {
        minMonthKey = monthKeyOf(LocalDate.of(MIN_YEAR, 1, 1))
        maxMonthKey = monthKeyOf(maxDate)
        years = (maxDate.year downTo MIN_YEAR).toList()
    }

    fun isSelectable(date: LocalDate) =
        selectableDays?.contains(date.toEpochDay()) ?: (date <= maxDate)

    // 初始视图与选中：初始日不可选时只定视图、不预选
    val initialView = initialDate.let {
        val key = monthKeyOf(it).coerceIn(minMonthKey, maxMonthKey)
        LocalDate.of(key / 12, key % 12 + 1, 1)
    }
    var viewYear by remember { mutableStateOf(initialView.year) }
    var viewMonth by remember { mutableStateOf(initialView.monthValue) }
    var pickedDay by remember {
        mutableStateOf(initialDate.takeIf { isSelectable(it) })
    }

    val viewMonthKey = monthKeyOf(LocalDate.of(viewYear, viewMonth, 1))
    fun stepMonth(delta: Int) {
        val key = (viewMonthKey + delta).coerceIn(minMonthKey, maxMonthKey)
        viewYear = key / 12
        viewMonth = key % 12 + 1
    }

    GlassCompactDialog(
        onDismissRequest = onDismiss,
        title = title,
        text = {
            Column {
                // 年月导航行：上/下月箭头（边界置灰）+ 年月下拉
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { stepMonth(-1) },
                        enabled = viewMonthKey > minMonthKey
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "上一月",
                            tint = if (viewMonthKey > minMonthKey) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        var yearMenu by remember { mutableStateOf(false) }
                        TextButton(onClick = { yearMenu = true }) {
                            Text(
                                text = "${viewYear}年${viewMonth}月",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = yearMenu,
                            onDismissRequest = { yearMenu = false },
                            modifier = Modifier.heightIn(max = 300.dp)
                        ) {
                            years.forEach { year ->
                                DropdownMenuItem(
                                    text = { Text("${year}年") },
                                    modifier = Modifier.height(38.dp),
                                    onClick = {
                                        yearMenu = false
                                        viewYear = year
                                        // 集合模式：跳该年第一个有可选日期的月份
                                        val months = selectableDays
                                            ?.map { monthKeyOf(LocalDate.ofEpochDay(it)) }
                                            ?.filter { it / 12 == year }
                                            ?.toSet()
                                            .orEmpty()
                                        if (months.isNotEmpty()) {
                                            viewMonth = months.min() % 12 + 1
                                        } else if (year == maxDate.year) {
                                            viewMonth = maxDate.monthValue
                                        } else {
                                            viewMonth = 1
                                        }
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { stepMonth(1) },
                        enabled = viewMonthKey < maxMonthKey
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "下一月",
                            tint = if (viewMonthKey < maxMonthKey) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                    }
                }

                // 星期头（周一起始）
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                        Text(
                            text = w,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                // 日网格：可选日期正常色可点，其余置灰不可点，选中高亮
                val firstOfMonth = LocalDate.of(viewYear, viewMonth, 1)
                val lead = firstOfMonth.dayOfWeek.value - 1 // 周一=0 个前置空位
                val cells: List<Int?> = List(lead) { null } +
                    (1..firstOfMonth.lengthOfMonth()).map { it }
                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (i in 0 until 7) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                val day = week.getOrNull(i)
                                if (day != null) {
                                    val date = LocalDate.of(viewYear, viewMonth, day)
                                    val selectable = isSelectable(date)
                                    val picked = pickedDay == date
                                    Box(
                                        modifier = Modifier
                                            .padding(vertical = 1.dp)
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (picked) MaterialTheme.colorScheme.primary
                                                else Color.Transparent
                                            )
                                            .then(
                                                if (selectable) Modifier.clickable { pickedDay = date }
                                                else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$day",
                                            fontSize = 14.sp,
                                            fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                picked -> MaterialTheme.colorScheme.onPrimary
                                                selectable -> MaterialTheme.colorScheme.onSurface
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectableDays != null) {
                    Text(
                        text = "灰色日期没有账单",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { pickedDay?.let(onConfirm) },
                enabled = pickedDay != null
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
