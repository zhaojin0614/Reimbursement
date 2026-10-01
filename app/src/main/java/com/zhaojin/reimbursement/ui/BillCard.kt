package com.zhaojin.reimbursement.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Color
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

@Composable
fun BillCard(
    bill: BillEntity,
    photoNames: List<String>,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    /** 点击图标：无图 → 添加图片（拍照/相册）；有图 → 查看全部图片 */
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
            photoNames = photoNames,
            fallbackText = bill.title.take(1).uppercase(),
            enabled = !isSelectionMode,
            onClick = onIconClick
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 单行内容：标题居左，金额居右（日期由分组标题表达，行内不再重复）
        val amountColor = if (bill.isIncome) IncomeGreen else ExpenseRed
        val amountPrefix = if (bill.isIncome) "+" else "-"
        Text(
            text = bill.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$amountPrefix¥${String.format("%.2f", bill.amount)}",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = amountColor
        )
    }
}

/**
 * 账单图标：有图片时显示第一张照片缩略图（多张时右上角角标计数），
 * 无图片时显示标题首字。多选模式下不响应图标点击。
 */
@Composable
private fun BillIconBox(
    photoNames: List<String>,
    fallbackText: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val firstName = photoNames.firstOrNull()

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(gradientBrush(MaterialTheme.colorScheme.primaryContainer, alpha = 0.75f))
            .border(glassBorder(), RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (firstName != null) {
            val thumb by produceState<ImageBitmap?>(
                initialValue = null, key1 = firstName, key2 = sizePx
            ) {
                value = BillPhotoStore.loadThumbnail(context, firstName, sizePx)?.asImageBitmap()
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
                Text(
                    text = fallbackText,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (photoNames.size > 1) {
                // 多图角标：右上角张数
                Text(
                    text = "${photoNames.size}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 4.dp)
                )
            }
        } else {
            Text(
                text = fallbackText,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
