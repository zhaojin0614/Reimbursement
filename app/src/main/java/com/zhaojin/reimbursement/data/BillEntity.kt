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
    val timestamp: Long
)
