package com.zhaojin.reimbursement.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhaojin.reimbursement.data.BillEntity
import com.zhaojin.reimbursement.ui.components.glassBorder
import com.zhaojin.reimbursement.ui.components.gradientBrush
import com.zhaojin.reimbursement.ui.theme.ExpenseRed
import com.zhaojin.reimbursement.ui.theme.IncomeGreen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BillCard(
    bill: BillEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onCategoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val categoryColor = getCategoryColor(bill.category)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            // 选中态只由勾选框表达，不再加背景层
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        // 分类图标块（纯手动记账无来源 App，直接以分类图标标识账单）
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(gradientBrush(MaterialTheme.colorScheme.primaryContainer, alpha = 0.75f))
                .border(glassBorder(), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            val iconRes = getCategoryIconRes(bill.category)
            if (iconRes != 0) {
                androidx.compose.material3.Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = bill.category,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = bill.title.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Row 1: Title + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bill.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                val amountColor = if (bill.isIncome) IncomeGreen else ExpenseRed
                val amountPrefix = if (bill.isIncome) "+" else "-"
                Text(
                    text = "$amountPrefix¥${String.format("%.2f", bill.amount)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
            }

            // Row 2: Category tag + Time
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = categoryColor.copy(alpha = 0.12f),
                    modifier = Modifier.clickable { onCategoryClick() }
                ) {
                    Text(
                        text = bill.category,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = categoryColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatBillTimeOnly(bill.timestamp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private val billTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatBillTimeOnly(timestamp: Long): String {
    val zoned = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    return billTimeFormatter.format(zoned)
}
