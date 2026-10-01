package com.zhaojin.reimbursement.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {

    /**
     * 记账页账单列表：按日倒序分组，同一天内按添加顺序（id 正序）。
     * 时间只取到日（localtime），同日账单不分先后时分。所有筛选视图共用，
     * 传 null 表示该维度不过滤。条数分页对回填旧日期的账单同样可见。
     */
    @Query(
        """SELECT * FROM bills
           WHERE (:type IS NULL OR isIncome = :type)
           ORDER BY date(timestamp / 1000, 'unixepoch', 'localtime') DESC, id ASC
           LIMIT :limit"""
    )
    fun getBillsFiltered(type: Boolean?, limit: Int): Flow<List<BillEntity>>

    /** 报表时间范围查询：自某时点（如当前周期往前 5 个周期）起的全部账单 */
    @Query("SELECT * FROM bills WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getBillsSince(since: Long): Flow<List<BillEntity>>

    /** 全量账单（备份导入时做指纹去重用） */
    @Query("SELECT * FROM bills")
    suspend fun getAllBillsOnce(): List<BillEntity>

    /** 导出时间范围查询：[start, end] 闭区间（毫秒），按时间正序 */
    @Query("SELECT * FROM bills WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    suspend fun getBillsBetween(start: Long, end: Long): List<BillEntity>

    /** 关键词搜索：标题模糊匹配 + 金额文本匹配，带类型过滤；排序同列表（日倒序+添加顺序） */
    @Query(
        """
        SELECT * FROM bills
        WHERE (
            title LIKE '%' || :query || '%'
            OR CAST(amount AS TEXT) LIKE '%' || :query || '%'
        )
        AND (:type IS NULL OR isIncome = :type)
        ORDER BY date(timestamp / 1000, 'unixepoch', 'localtime') DESC, id ASC
        LIMIT :limit
        """
    )
    fun searchBills(query: String, type: Boolean?, limit: Int): Flow<List<BillEntity>>

    @Query("SELECT SUM(amount) FROM bills WHERE isIncome = 0")
    fun getTotalExpense(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM bills WHERE isIncome = 1")
    fun getTotalIncome(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM bills WHERE isIncome = 0 AND timestamp >= :startOfMonth")
    fun getMonthExpense(startOfMonth: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM bills WHERE isIncome = 1 AND timestamp >= :startOfMonth")
    fun getMonthIncome(startOfMonth: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM bills WHERE isIncome = 0")
    fun getExpenseCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM bills WHERE isIncome = 1")
    fun getIncomeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bill: BillEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bills: List<BillEntity>)

    @Update
    suspend fun update(bill: BillEntity)

    @Query("UPDATE bills SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE bills SET amount = :amount WHERE id = :id")
    suspend fun updateAmount(id: Long, amount: Double)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bills")
    suspend fun deleteAll()
}
