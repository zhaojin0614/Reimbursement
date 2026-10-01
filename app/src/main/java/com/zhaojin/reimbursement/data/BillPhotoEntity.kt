package com.zhaojin.reimbursement.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 账单图片记录：一张账单可挂多张图片（一对多）。
 * fileName 为应用私有目录 bill_photos/ 下的文件名；createdAt 决定
 * 查看器翻页顺序，账单图标展示最早添加的第一张。
 */
@Entity(
    tableName = "bill_photos",
    indices = [Index(value = ["billId"])]
)
data class BillPhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val billId: Long,
    val fileName: String,
    val createdAt: Long
)
