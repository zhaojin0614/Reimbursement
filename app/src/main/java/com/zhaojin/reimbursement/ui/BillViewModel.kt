package com.zhaojin.reimbursement.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhaojin.reimbursement.data.AppDatabase
import com.zhaojin.reimbursement.data.BillBackupManager
import com.zhaojin.reimbursement.data.BillEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BillViewModel(application: Application) : AndroidViewModel(application) {

    private val billDao = AppDatabase.getDatabase(application).billDao()

    // ── 备份与恢复 ──────────────────────────────────────────────────────
    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy
    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage

    /** 导出全部账单为 xlsx 工作簿（用户经 SAF 选择位置） */
    fun exportBackup(uri: Uri) {
        if (_backupBusy.value) return
        viewModelScope.launch {
            _backupBusy.value = true
            try {
                val bills = billDao.getAllBillsOnce()
                BillBackupManager.exportToUri(getApplication(), uri, bills)
                    .onSuccess { _backupMessage.value = "已导出 $it 条账单" }
                    .onFailure { _backupMessage.value = "导出失败：${it.message}" }
            } finally {
                _backupBusy.value = false
            }
        }
    }

    /**
     * 从 xlsx 工作簿导入。overwrite=false 合并（指纹去重）；
     * true 恢复覆盖（清空账单后按快照重建）。
     */
    fun importBackup(uri: Uri, overwrite: Boolean) {
        if (_backupBusy.value) return
        viewModelScope.launch {
            _backupBusy.value = true
            try {
                val context = getApplication<Application>()
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("无法读取所选文件")
                val parsed = BillBackupManager.parseWorkbook(bytes)
                val result = BillBackupManager.importBills(billDao, parsed, overwrite)
                _backupMessage.value = (if (overwrite) "恢复完成：" else "导入完成：") + result.summary()
            } catch (e: Exception) {
                _backupMessage.value = (if (overwrite) "恢复失败：" else "导入失败：") + (e.message ?: "未知错误")
            } finally {
                _backupBusy.value = false
            }
        }
    }

    fun consumeBackupMessage() {
        _backupMessage.value = null
    }

    private val _weeksToLoad = MutableStateFlow(1)

    companion object {
        /** 筛选视图每页条数（按时间倒序的 LIMIT 分页） */
        private const val FILTER_PAGE_SIZE = 30
    }

    // ── 记账页筛选状态（null = 不过滤该维度）────────────────────────────
    private val _typeFilter = MutableStateFlow<Boolean?>(null)     // true=收入 false=支出
    private val _categoryFilter = MutableStateFlow<String?>(null)
    private val _filterLimit = MutableStateFlow(FILTER_PAGE_SIZE)
    private val _searchQuery = MutableStateFlow("")

    /** UI 调用：切换 全部/支出/收入 类型筛选（重置筛选分页） */
    fun setTypeFilter(typeLabel: String?) {
        _typeFilter.value = when (typeLabel) {
            "收入" -> true
            "支出" -> false
            else -> null
        }
        _filterLimit.value = FILTER_PAGE_SIZE
    }

    /** UI 调用：切换分类筛选（null/「全部」= 不过滤；重置筛选分页） */
    fun setCategoryFilter(category: String?) {
        _categoryFilter.value = category?.takeIf { it != "全部" }
        _filterLimit.value = FILTER_PAGE_SIZE
    }

    /** UI 调用：设置账单搜索关键词（空 = 关闭搜索） */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _filterLimit.value = FILTER_PAGE_SIZE
    }

    /**
     * 记账页账单列表，两种分页策略：
     *
     * - 无任何筛选：按周时间窗口分页（[_weeksToLoad]，默认最近 1 周，
     *   滚动到底加载更多周），避免全量列表常驻内存；
     * - 有筛选：按条数 LIMIT 分页（[_filterLimit]，默认 30 条，滚动到底
     *   增大 limit）。不能用周窗口切筛选——窗口内没有目标类型账单时
     *   结果会恒空（如最近一周无收入却筛选收入），条数分页没有此问题。
     */
    /** 一次账单列表查询的全部参数（任一变化即重查） */
    private data class BillQuery(
        val type: Boolean?,
        val category: String?,
        val weeks: Int,
        val filterLimit: Int,
        val query: String
    )

    val bills: StateFlow<List<BillEntity>> = combine(
        _typeFilter, _categoryFilter, _weeksToLoad, _filterLimit, _searchQuery, ::BillQuery
    ).flatMapLatest { q ->
        when {
            // 搜索优先：关键词命中标题/分类/金额文本
            q.query.isNotBlank() ->
                billDao.searchBills(q.query.trim(), q.type, q.category, q.filterLimit)
            q.type == null && q.category == null -> {
                val since = System.currentTimeMillis() - q.weeks * 7L * 24 * 60 * 60 * 1000
                billDao.getBillsSince(since)
            }
            else -> billDao.getBillsFiltered(q.type, q.category, q.filterLimit)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 滚动到底部加载更多：无筛选时扩周窗口，有筛选时增大条数分页 */
    fun loadMore() {
        if (_typeFilter.value != null || _categoryFilter.value != null || _searchQuery.value.isNotBlank()) {
            _filterLimit.value += FILTER_PAGE_SIZE
        } else {
            loadMoreWeeks()
        }
    }

    fun loadMoreWeeks() {
        // 上限 520 周（约 10 年），防止异常数据导致窗口无限膨胀
        if (_weeksToLoad.value < 520) _weeksToLoad.value += 1
    }

    val totalExpense: StateFlow<Double> = billDao.getTotalExpense()
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalIncome: StateFlow<Double> = billDao.getTotalIncome()
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /**
     * Reactive startOfMonth that re-emits at every month boundary,
     * ensuring this month's stats stay correct across month changes.
     */
    private val reactiveStartOfMonth: StateFlow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            val start = computeStartOfMonth(now)
            emit(start)
            // 下月 1 日零点触发刷新
            val nextMonthStart = computeStartOfMonth(start + 35L * 24 * 60 * 60 * 1000)
            delay(nextMonthStart - now + 1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), computeStartOfMonth(System.currentTimeMillis()))

    private fun computeStartOfMonth(nowMillis: Long): Long =
        java.time.Instant.ofEpochMilli(nowMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .withDayOfMonth(1)
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    /** 本月支出合计（记账界面大卡片展示） */
    val monthExpense: StateFlow<Double> = reactiveStartOfMonth
        .flatMapLatest { start ->
            billDao.getMonthExpense(start).map { it ?: 0.0 }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** 本月收入合计（记账界面大卡片展示） */
    val monthIncome: StateFlow<Double> = reactiveStartOfMonth
        .flatMapLatest { start ->
            billDao.getMonthIncome(start).map { it ?: 0.0 }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val expenseCount: StateFlow<Int> = billDao.getExpenseCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val incomeCount: StateFlow<Int> = billDao.getIncomeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    val isSelectionMode: StateFlow<Boolean> = _selectedIds.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedIds.value = current
    }

    fun enterSelectionMode(id: Long) {
        _selectedIds.value = setOf(id)
    }

    fun exitSelectionMode() {
        _selectedIds.value = emptySet()
    }

    fun selectAll(ids: List<Long>) {
        _selectedIds.value = ids.toSet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                ids.forEach { billDao.deleteById(it) }
            }
            _selectedIds.value = emptySet()
        }
    }

    fun addBill(bill: BillEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            billDao.insert(bill)
        }
    }

    fun updateCategory(id: Long, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            billDao.updateCategory(id, category)
        }
    }

    fun updateTitle(id: Long, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            billDao.updateTitle(id, title)
        }
    }

    /** 修改账单金额 */
    fun updateAmount(id: Long, amount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            billDao.updateAmount(id, amount)
        }
    }

    fun deleteBill(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            billDao.deleteById(id)
        }
    }
}
