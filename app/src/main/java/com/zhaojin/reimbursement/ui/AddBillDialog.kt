@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.zhaojin.reimbursement.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhaojin.reimbursement.data.BillEntity
import com.zhaojin.reimbursement.ui.components.PillToggle
import com.zhaojin.reimbursement.ui.components.SoftButton
import com.zhaojin.reimbursement.ui.components.glassBorder
import com.zhaojin.reimbursement.ui.components.glassHighlightBrush
import com.zhaojin.reimbursement.ui.components.isDarkTheme
import com.zhaojin.reimbursement.ui.theme.ExpenseRed
import com.zhaojin.reimbursement.ui.theme.IncomeGreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBillDialog(
    onAdd: (BillEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var isIncome by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf(today) }
    var showDatePicker by remember { mutableStateOf(false) }
    val expenseCategories = ExpenseCategories.all
    val incomeCategories = IncomeCategories.all
    val categories = if (isIncome) incomeCategories else expenseCategories
    var selectedCategory by remember(isIncome) {
        mutableStateOf(categories.first())
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Frosted glass: translucent surface lets the animated ambient
        // background glow show through, highlight keeps the glass look.
        containerColor = MaterialTheme.colorScheme.surface.copy(
            alpha = if (isDarkTheme()) 0.82f else 0.88f
        ),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Top light reflection (liquid glass highlight)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(glassHighlightBrush())
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "添加账单",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Income / Expense toggle
                PillToggle(
                    options = listOf("支出" to ExpenseRed, "收入" to IncomeGreen),
                    selectedIndex = if (isIncome) 1 else 0,
                    onSelect = { isIncome = it == 1; selectedCategory = categories.first() },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = title,
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                    onValueChange = { title = it },
                    label = { Text("标题 (如: 维修费)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                TextField(
                    value = amountText,
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                    onValueChange = { amountText = it },
                    label = { Text("金额") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    prefix = { Text("¥") },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Date picker row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showDatePicker = true }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "选择日期",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "日期",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    val dateLabel = when (selectedDate) {
                        today -> "今天"
                        today.minusDays(1) -> "昨天"
                        today.plusDays(1) -> "明天"
                        else -> selectedDate.format(DateTimeFormatter.ofPattern("MM月dd日"))
                    }
                    val weekDay = selectedDate.format(DateTimeFormatter.ofPattern(" EEE"))
                    Text(
                        text = "$dateLabel$weekDay",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selectedDate != today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Category grid
                Text(
                    text = "选择分类",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 分类网格：外层已是 verticalScroll，改用普通分行布局避免
                // 嵌套同向滚动的手势冲突（条目固定且少，无需 lazy）。
                // 支出 12 项 3 行 / 收入 8 项 2 行，切换时高度变化交给
                // animateContentSize 平滑过渡，避免底部面板整体跳动。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = 500f
                            )
                        )
                ) {
                    categories.chunked(4).forEach { rowCategories ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowCategories.forEach { cat ->
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CategoryGridItem(
                                        label = cat,
                                        isSelected = selectedCategory == cat,
                                        onClick = { selectedCategory = cat }
                                    )
                                }
                            }
                            // 末行不足4个时补齐占位保持等宽
                            repeat(4 - rowCategories.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }

                    SoftButton(
                        text = "添加",
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            if (title.isNotBlank() && amt > 0) {
                                val ts = if (selectedDate == today) {
                                    System.currentTimeMillis()
                                } else {
                                    selectedDate
                                        .atTime(12, 0)
                                        .atZone(ZoneId.systemDefault())
                                        .toInstant()
                                        .toEpochMilli()
                                }
                                onAdd(
                                    BillEntity(
                                        amount = amt,
                                        title = title,
                                        category = selectedCategory,
                                        isIncome = isIncome,
                                        timestamp = ts
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        backgroundColor = if (isIncome) IncomeGreen else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val todayMillis = remember {
            LocalDate.now()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis <= todayMillis
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            modifier = Modifier.border(glassBorder(), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface.copy(
                    alpha = if (isDarkTheme()) 0.90f else 0.93f
                )
            ),
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
