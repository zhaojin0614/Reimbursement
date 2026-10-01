package com.zhaojin.reimbursement.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {

    @Query("SELECT * FROM bills WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getBillsSince(since: Long): Flow<List<BillEntity>>

    /**
     * 按类型/分类直接查库（记账页筛选用），按条数分页（LIMIT）。
     * 与「全部」视图的时间窗口分页不同：筛选若也按周窗口切，
     * 窗口内没有目标类型账单时结果会恒空（如最近一周无收入却筛选收入），
     * 故按时间倒序取前 [limit] 条匹配记录，滚动到底再增大 limit。
     * 传 null 表示该维度不过滤。
     */
    @Query(
        """SELECT * FROM bills
           WHERE (:type IS NULL OR isIncome = :type)
             AND (:category IS NULL OR category = :category)
           ORDER BY timestamp DESC
           LIMIT :limit"""
    )
    fun getBillsFiltered(type: Boolean?, category: String?, limit: Int): Flow<List<BillEntity>>

    /** 全量账单（备份导入时做指纹去重用） */
    @Query("SELECT * FROM bills")
    suspend fun getAllBillsOnce(): List<BillEntity>

    /** 关键词搜索：标题/分类模糊匹配 + 金额文本匹配，带类型/分类过滤与条数分页 */
    @Query(
        """
        SELECT * FROM bills
        WHERE (
            title LIKE '%' || :query || '%'
            OR category LIKE '%' || :query || '%'
            OR CAST(amount AS TEXT) LIKE '%' || :query || '%'
        )
        AND (:type IS NULL OR isIncome = :type)
        AND (:category IS NULL OR category = :category)
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    fun searchBills(query: String, type: Boolean?, category: String?, limit: Int): Flow<List<BillEntity>>

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

    @Query("UPDATE bills SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String)

    @Query("UPDATE bills SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE bills SET amount = :amount WHERE id = :id")
    suspend fun updateAmount(id: Long, amount: Double)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM bills WHERE category = :category AND isIncome = :isIncome AND timestamp >= :startTime AND timestamp < :endTime ORDER BY timestamp DESC")
    fun getBillsByCategoryAndTimeRange(
        isIncome: Boolean,
        category: String,
        startTime: Long,
        endTime: Long
    ): Flow<List<BillEntity>>

    @Query("DELETE FROM bills")
    suspend fun deleteAll()
}
