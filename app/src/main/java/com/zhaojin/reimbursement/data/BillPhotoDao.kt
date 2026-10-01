package com.zhaojin.reimbursement.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BillPhotoDao {

    /** 全部图片记录（按添加时间正序），内存中按 billId 分组给列表用 */
    @Query("SELECT * FROM bill_photos ORDER BY createdAt ASC, id ASC")
    fun getAll(): Flow<List<BillPhotoEntity>>

    @Query("SELECT * FROM bill_photos ORDER BY createdAt ASC, id ASC")
    suspend fun getAllOnce(): List<BillPhotoEntity>

    @Query("SELECT * FROM bill_photos WHERE billId = :billId ORDER BY createdAt ASC, id ASC")
    suspend fun getByBillOnce(billId: Long): List<BillPhotoEntity>

    @Insert
    suspend fun insert(photo: BillPhotoEntity): Long

    @Delete
    suspend fun delete(photo: BillPhotoEntity)

    @Query("DELETE FROM bill_photos WHERE billId = :billId")
    suspend fun deleteByBill(billId: Long)

    @Query("DELETE FROM bill_photos")
    suspend fun deleteAll()
}
