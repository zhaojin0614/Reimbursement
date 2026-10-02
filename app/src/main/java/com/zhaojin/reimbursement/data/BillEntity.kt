package com.zhaojin.reimbursement.data

import androidx.room.ColumnInfo
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
    /** 驾驶员（可选） */
    @ColumnInfo(defaultValue = "")
    val driver: String = "",
    /** 车牌号（可选，如 京A12345） */
    @ColumnInfo(defaultValue = "")
    val plate: String = "",
    /** 地区（可选标签，选项列表在设置中维护） */
    @ColumnInfo(defaultValue = "")
    val region: String = "",
    /** 分类（选项在设置中维护；导出 Excel 按分类分表） */
    @ColumnInfo(defaultValue = "维修报销")
    val category: String = "维修报销",
    val isIncome: Boolean = false,
    val timestamp: Long
)
