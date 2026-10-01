package com.zhaojin.reimbursement.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bills",
    indices = [
        // 时间范围统计（今日/本月/报表）与按时间排序
        Index(value = ["timestamp"])
    ]
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val title: String,
    val category: String = "未分类",
    val isIncome: Boolean = false,
    val timestamp: Long
)
