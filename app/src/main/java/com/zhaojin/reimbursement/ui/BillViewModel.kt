package com.zhaojin.reimbursement.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhaojin.reimbursement.data.AppDatabase
import com.zhaojin.reimbursement.data.BillBackupManager
import com.zhaojin.reimbursement.data.BillEntity
import com.zhaojin.reimbursement.data.BillPhotoEntity
import com.zhaojin.reimbursement.utils.AppLogger
import com.zhaojin.reimbursement.utils.BillPhotoStore
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
    private val photoDao = AppDatabase.getDatabase(application).billPhotoDao()

    /** 全部图片记录按账单分组（key = billId，值按添加时间正序） */
    val photosByBill: StateFlow<Map<Long, List<BillPhotoEntity>>> = photoDao.getAll()
        .map { list -> list.groupBy { it.billId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ── 备份与恢复 ──────────────────────────────────────────────────────
    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy
    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage

    /**
     * 导出时间段账单（照片内嵌）为单个 xlsx 到缓存目录，成功后经
     * [onExported] 回调交给 UI 弹系统分享面板；[startMillis, endMillis] 为按日闭区间。
     */
    fun exportBackup(startMillis: Long, endMillis: Long, onExported: (java.io.File) -> Unit) {
        if (_backupBusy.value) return
        viewModelScope.launch {
            _backupBusy.value = true
            val started = System.currentTimeMillis()
            try {
                val context = getApplication<Application>()
                val bills = billDao.getBillsBetween(startMillis, endMillis)
                val billIds = bills.mapTo(HashSet()) { it.id }
                val photos = photoDao.getAllOnce()
                    .groupBy { it.billId }
                    .filterKeys { it in billIds }
                    .mapValues { e -> e.value.mapNotNull { photo -> readExportPhoto(context, photo.fileName) } }
                val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val zone = java.time.ZoneId.systemDefault()
                val rangeStr =
                    "${java.time.Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate().format(fmt)}" +
                    "~${java.time.Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate().format(fmt)}"
                AppLogger.log("导出", "开始导出 范围=$rangeStr 账单数=${bills.size}")
                BillBackupManager.exportToCache(context, "维修报销账单_$rangeStr.xlsx", bills, photos)
                    .onSuccess { file ->
                        val photoCount = photos.values.sumOf { it.size }
                        _backupMessage.value =
                            "已生成 $rangeStr 共 ${bills.size} 条账单" + if (photoCount > 0) "、$photoCount 张图片" else ""
                        AppLogger.log(
                            "导出",
                            "导出成功 文件=${file.name} 账单=${bills.size} 图片=$photoCount " +
                                "大小=${file.length()}B 耗时=${System.currentTimeMillis() - started}ms"
                        )
                        onExported(file)
                    }
                    .onFailure {
                        _backupMessage.value = "导出失败：${it.message}"
                        AppLogger.log("导出", "导出失败 范围=$rangeStr 错误=${it.message}")
                    }
            } finally {
                _backupBusy.value = false
            }
        }
    }

    /** 读照片字节并解码像素宽高（只读边界不解码整图），供导出按比例排版 */
    private fun readExportPhoto(context: android.content.Context, name: String): BillBackupManager.ExportPhoto? {
        val bytes = BillPhotoStore.readExportBytes(context, name) ?: return null
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return BillBackupManager.ExportPhoto(
            data = bytes,
            widthPx = bounds.outWidth.takeIf { it > 0 } ?: 1,
            heightPx = bounds.outHeight.takeIf { it > 0 } ?: 1
        )
    }

    fun consumeBackupMessage() {
        _backupMessage.value = null
    }

    companion object {
        /** 列表每页条数（按时间倒序的 LIMIT 分页） */
        private const val PAGE_SIZE = 30
    }

    // ── 记账页筛选状态（null = 全部，不过滤类型）────────────────────────
    private val _typeFilter = MutableStateFlow<Boolean?>(null)     // true=收入 false=支出
    private val _limit = MutableStateFlow(PAGE_SIZE)
    private val _searchQuery = MutableStateFlow("")

    /** UI 调用：切换 全部/支出/收入 类型筛选（重置分页） */
    fun setTypeFilter(typeLabel: String?) {
        _typeFilter.value = when (typeLabel) {
            "收入" -> true
            "支出" -> false
            else -> null
        }
        _limit.value = PAGE_SIZE
    }

    /** UI 调用：设置账单搜索关键词（空 = 关闭搜索） */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _limit.value = PAGE_SIZE
    }

    /** 一次账单列表查询的全部参数（任一变化即重查） */
    private data class BillQuery(
        val type: Boolean?,
        val limit: Int,
        val query: String
    )

    /**
     * 记账页账单列表：统一按时间倒序条数分页（LIMIT）。
     * 不用时间窗口切「全部」视图——窗口外补记的历史账单会在「全部」里
     * 隐身（而筛选视图能看到），条数分页没有此不一致问题。
     */
    val bills: StateFlow<List<BillEntity>> = combine(
        _typeFilter, _limit, _searchQuery, ::BillQuery
    ).flatMapLatest { q ->
        when {
            // 搜索优先：关键词命中标题/金额文本
            q.query.isNotBlank() ->
                billDao.searchBills(q.query.trim(), q.type, q.limit)
            else -> billDao.getBillsFiltered(q.type, q.limit)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 滚动到底部加载更多：增大条数分页 */
    fun loadMore() {
        _limit.value += PAGE_SIZE
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
                ids.forEach { id ->
                    photoDao.getByBillOnce(id).forEach {
                        BillPhotoStore.delete(getApplication(), it.fileName)
                    }
                    photoDao.deleteByBill(id)
                    billDao.deleteById(id)
                }
                AppLogger.log("账单", "批量删除 ${ids.size} 条账单 id=$ids")
            }
            _selectedIds.value = emptySet()
        }
    }

    fun addBill(bill: BillEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            billDao.insert(bill)
            AppLogger.log(
                "账单",
                "添加账单 「${bill.title}」 ¥${bill.amount} ${if (bill.isIncome) "收入" else "支出"} " +
                    "日期=${java.time.Instant.ofEpochMilli(bill.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}"
            )
        }
    }

    fun updateTitle(id: Long, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val old = billDao.getBillById(id)
            billDao.updateTitle(id, title)
            AppLogger.log("账单", "修改账单 #$id 标题 「${old?.title}」→「$title」")
        }
    }

    /** 修改账单金额 */
    fun updateAmount(id: Long, amount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val old = billDao.getBillById(id)
            billDao.updateAmount(id, amount)
            AppLogger.log("账单", "修改账单 #$id 金额 ¥${old?.amount}→¥$amount")
        }
    }

    /** 账单追加一张图片（拍照/相册导入完成、文件已落盘后调用） */
    fun addBillPhoto(billId: Long, fileName: String, source: String) {
        viewModelScope.launch(Dispatchers.IO) {
            photoDao.insert(
                BillPhotoEntity(billId = billId, fileName = fileName, createdAt = System.currentTimeMillis())
            )
            AppLogger.log("图片", "添加图片 账单#$billId 来源=$source 文件=$fileName")
        }
    }

    /** 删除单张图片：删记录 + 删文件 */
    fun deleteBillPhoto(photo: BillPhotoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            photoDao.delete(photo)
            BillPhotoStore.delete(getApplication(), photo.fileName)
            AppLogger.log("图片", "删除图片 账单#${photo.billId} 文件=${photo.fileName}")
        }
    }

    fun deleteBill(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            // 先删图片记录与文件，再删账单记录
            val photos = photoDao.getByBillOnce(id)
            photos.forEach {
                BillPhotoStore.delete(getApplication(), it.fileName)
            }
            photoDao.deleteByBill(id)
            val bill = billDao.getBillById(id)
            billDao.deleteById(id)
            AppLogger.log(
                "账单",
                "删除账单 #$id「${bill?.title}」¥${bill?.amount} 含图片${photos.size}张"
            )
        }
    }
}
