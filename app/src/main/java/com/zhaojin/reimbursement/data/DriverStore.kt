package com.zhaojin.reimbursement.data

import android.content.Context
import com.zhaojin.reimbursement.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 驾驶员记忆库：添加账单保存时自动记录驾驶员及其车牌（去重），供下次
 * 添加时联想选用；设置页可增删改查。
 *
 * - 每条记录：姓名 + 车牌列表（首位=最近使用，同一车牌只记一份）；
 * - 列表本身按最近使用排序（最近保存的驾驶员在前），联想结果沿用此顺序；
 * - 存 filesDir/drivers.txt 行式文本（原子写：tmp+rename），与账单草稿同套机制；
 * - 记忆只影响输入辅助，删除驾驶员/车牌不影响任何已保存账单。
 */
object DriverStore {

    /** 驾驶员记录：[plates] 首位为最近使用的车牌 */
    data class DriverEntry(val name: String, val plates: List<String> = emptyList())

    private const val FILE_NAME = "drivers.txt"
    private const val MAGIC = "drivers_v1"
    private val ioMutex = Mutex()

    suspend fun load(context: Context): List<DriverEntry> = withContext(Dispatchers.IO) {
        ioMutex.withLock { read(context) }
    }

    /** 全量保存（设置页维护用），调用方已做好重名等校验 */
    suspend fun save(context: Context, entries: List<DriverEntry>): Unit = withContext(Dispatchers.IO) {
        ioMutex.withLock { write(context, entries) }
    }

    /** 账单保存时记忆：姓名必填，车牌可空；去重并把最近使用的排到最前 */
    suspend fun rememberUsage(context: Context, name: String, plate: String) = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            write(context, rememberUsagePure(read(context), name, plate))
        }
    }

    private fun read(context: Context): List<DriverEntry> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return runCatching { decodeDrivers(file.readText()) }.getOrNull().orEmpty()
    }

    private fun write(context: Context, entries: List<DriverEntry>) {
        val file = File(context.filesDir, FILE_NAME)
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        runCatching {
            tmp.writeText(encodeDrivers(entries))
            if (!tmp.renameTo(file)) tmp.delete()
        }.onFailure {
            tmp.delete()
            AppLogger.log("驾驶员", "记忆库保存失败 ${it.javaClass.simpleName}: ${it.message}")
        }
    }
}

/** 纯函数：记忆一笔使用——同名去重、车牌置首（去重）、该驾驶员置首；姓名空白时原样返回 */
internal fun rememberUsagePure(
    entries: List<DriverStore.DriverEntry>,
    name: String,
    plate: String
): List<DriverStore.DriverEntry> {
    val n = name.trim()
    if (n.isEmpty()) return entries
    val p = plate.trim()
    val existing = entries.firstOrNull { it.name == n }
    val plates = existing?.plates.orEmpty()
    val newPlates = if (p.isEmpty()) plates else listOf(p) + plates.filter { it != p }
    return listOf(DriverStore.DriverEntry(n, newPlates)) + entries.filter { it.name != n }
}

/** 纯函数：记录列表 → 行式文本。姓名来自 singleLine 输入，不含换行 */
internal fun encodeDrivers(entries: List<DriverStore.DriverEntry>): String = buildString {
    appendLine("drivers_v1")
    entries.forEach { e ->
        appendLine("driver=${e.name}")
        e.plates.forEach { appendLine("plate=$it") }
    }
}

/** 纯函数：行式文本 → 记录列表；magic 不符返回 null，无主车牌行跳过 */
internal fun decodeDrivers(text: String): List<DriverStore.DriverEntry>? {
    val lines = text.lines().filter { it.isNotBlank() }
    if (lines.firstOrNull() != "drivers_v1") return null
    val entries = mutableListOf<DriverStore.DriverEntry>()
    var currentName: String? = null
    val plates = mutableListOf<String>()
    fun flush() {
        currentName?.let { entries.add(DriverStore.DriverEntry(it, plates.toList())) }
        plates.clear()
    }
    for (line in lines.drop(1)) {
        val idx = line.indexOf('=')
        if (idx <= 0) continue
        val value = line.substring(idx + 1)
        when (line.substring(0, idx)) {
            "driver" -> {
                flush()
                currentName = value
            }
            "plate" -> if (currentName != null && value.isNotBlank()) plates.add(value)
        }
    }
    flush()
    return entries
}
