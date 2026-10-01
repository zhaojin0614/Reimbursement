package com.zhaojin.reimbursement.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bills",
    indices = [
        // 时间范围统计（本月/报表）与按时间倒序分页
        Index(value = ["timestamp"])
    ]
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val title: String,
    val isIncome: Boolean = false,
    val timestamp: Long,
    /**
     * 账单图片文件名（位于应用私有目录 bill_photos/ 下），null = 无图片。
     * 只存文件名不存路径：目录归属固定，换设备/迁移只需拼目录。
     * 有图片时账单图标显示图片缩略图，点击可查看原图。
     */
    val photoPath: String? = null
)
