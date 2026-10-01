package com.zhaojin.reimbursement.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhaojin.reimbursement.data.BillEntity
import com.zhaojin.reimbursement.ui.components.glassBorder
import com.zhaojin.reimbursement.ui.components.gradientBrush
import com.zhaojin.reimbursement.ui.theme.ExpenseRed
import com.zhaojin.reimbursement.ui.theme.IncomeGreen
import com.zhaojin.reimbursement.utils.BillPhotoStore
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
    /** 点击图标：无图 → 添加图片（拍照/相册）；有图 → 查看原图 */
    onIconClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

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
        BillIconBox(
            bill = bill,
            enabled = !isSelectionMode,
            onClick = onIconClick
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Row 1: Title + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
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

            // Row 2: Time
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatBillTimeOnly(bill.timestamp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * 账单图标：有图片时显示照片缩略图（点击查看原图），
 * 无图片时显示标题首字（点击添加图片）。多选模式下不响应图标点击。
 */
@Composable
private fun BillIconBox(
    bill: BillEntity,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { 40.dp.roundToPx() }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(gradientBrush(MaterialTheme.colorScheme.primaryContainer, alpha = 0.75f))
            .border(glassBorder(), RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val photoName = bill.photoPath
        if (photoName != null) {
            val thumb by produceState<ImageBitmap?>(
                initialValue = null, key1 = photoName, key2 = sizePx
            ) {
                value = BillPhotoStore.loadThumbnail(context, photoName, sizePx)?.asImageBitmap()
            }
            val bmp = thumb
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "账单图片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 缩略图解码中：先显示首字占位
                IconFallbackText(bill = bill)
            }
        } else {
            IconFallbackText(bill = bill)
        }
    }
}

@Composable
private fun IconFallbackText(bill: BillEntity) {
    Text(
        text = bill.title.take(1).uppercase(),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer
    )
}

private val billTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatBillTimeOnly(timestamp: Long): String {
    val zoned = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    return billTimeFormatter.format(zoned)
}
