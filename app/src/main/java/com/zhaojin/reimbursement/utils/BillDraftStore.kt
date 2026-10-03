package com.zhaojin.reimbursement.utils

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 添加账单草稿：新增账单页表单内容 + 暂存照片的持久化层。
 *
 * - 表单内容存 filesDir/bill_draft.txt（行式文本，原子写：tmp + rename）；
 * - 照片沿用 BillPhotoStore 的 pending_ 临时文件，文件名逐行记录在草稿里，
 *   冷启动清扫（[BillPhotoStore.sweepPending]）会保留这些登记过的文件；
 * - 跨界面交接点：分享接收页不与添加页实时通信，把图片追加进草稿
 *   （[attachPhotos]）即可——添加页存活时照片直接进运行时列表（自动保存
 *   随后落盘），否则直接写进草稿文件；
 * - 生命周期：账单保存成功 / 用户放弃时删除草稿；进程被杀则留在磁盘，
 *   下次进添加页恢复。
 */
object BillDraftStore {

    /** 草稿快照（photos 为 pending 文件名，文件在 [BillPhotoStore.photoDir] 下） */
    data class Draft(
        val category: String = "",
        val dateIso: String = "",
        val driver: String = "",
        val plate: String = "",
        val region: String = "",
        val title: String = "",
        val amountText: String = "",
        val photos: List<String> = emptyList()
    ) {
        /** 是否无有效内容（分类/日期有默认值，不算内容） */
        val isEmpty: Boolean
            get() = title.isBlank() && amountText.isBlank() && driver.isBlank() &&
                plate.isBlank() && region.isBlank() && photos.isEmpty()
    }

    // ── 运行时会话状态（仅新增账单页使用；编辑已有账单不进草稿）────────

    /** 添加页（新增模式）是否存活：分享接收页据此把照片送进运行时列表 */
    var sessionActive by mutableStateOf(false)
        private set

    /** 运行时暂存照片：添加页与分享页共享同一列表，Compose 可观察 */
    val stagedPhotos = mutableStateListOf<File>()

    /** 添加页进入/退出（onDispose）时调用；退出时清空运行时照片引用 */
    fun setSession(active: Boolean) {
        sessionActive = active
        if (!active) stagedPhotos.clear()
    }

    private const val FILE_NAME = "bill_draft.txt"
    private const val MAGIC = "bill_draft_v1"
    private val ioMutex = Mutex()

    /**
     * 分享接收页：把暂存图片挂进草稿。
     * 添加页存活 → 进运行时列表（页面立即可见，自动保存随后落盘）；
     * 否则只落盘。两种情况都同步 merge 文件名进草稿文件，防止添加页
     * 自动保存落盘前进程被杀导致照片失去登记、被冷启动清扫掉。
     */
    suspend fun attachPhotos(context: Context, files: List<File>) = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext
        ioMutex.withLock {
            if (sessionActive) stagedPhotos.addAll(files)
            val current = readDraftFile(context) ?: Draft()
            val merged = current.copy(photos = (current.photos + files.map { it.name }).distinct())
            writeDraftFile(context, merged)
            AppLogger.log(
                "图片",
                "分享挂载到草稿 +${files.size}张 合计${merged.photos.size}张 会话存活=$sessionActive"
            )
        }
    }

    suspend fun save(context: Context, draft: Draft): Unit = withContext(Dispatchers.IO) {
        ioMutex.withLock { writeDraftFile(context, draft) }
    }

    /** 读取草稿；无草稿或草稿无有效内容时返回 null */
    suspend fun load(context: Context): Draft? = withContext(Dispatchers.IO) {
        ioMutex.withLock { readDraftFile(context) }
    }

    /** 删除草稿文件（照片文件由调用方按需 discard / 移交） */
    suspend fun clear(context: Context): Unit = withContext(Dispatchers.IO) {
        ioMutex.withLock { File(context.filesDir, FILE_NAME).delete() }
    }

    /** 冷启动清扫用：草稿登记的照片文件名（保留不扫） */
    suspend fun registeredPhotoNames(context: Context): Set<String> = withContext(Dispatchers.IO) {
        ioMutex.withLock { readDraftFile(context)?.photos.orEmpty().toSet() }
    }

    private fun writeDraftFile(context: Context, draft: Draft) {
        val file = File(context.filesDir, FILE_NAME)
        if (draft.isEmpty) {
            file.delete()
            return
        }
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        runCatching {
            tmp.writeText(encodeDraft(draft))
            if (!tmp.renameTo(file)) tmp.delete()
        }.onFailure {
            tmp.delete()
            AppLogger.log("账单", "草稿保存失败 ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private fun readDraftFile(context: Context): Draft? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return runCatching { decodeDraft(file.readText()) }.getOrNull()?.takeUnless { it.isEmpty }
    }
}

/** 纯函数：草稿 → 行式文本。字段全部来自 singleLine 输入/固定选项，不含换行。 */
internal fun encodeDraft(d: BillDraftStore.Draft): String = buildString {
    appendLine("bill_draft_v1")
    appendLine("category=${d.category}")
    appendLine("date=${d.dateIso}")
    appendLine("driver=${d.driver}")
    appendLine("plate=${d.plate}")
    appendLine("region=${d.region}")
    appendLine("title=${d.title}")
    appendLine("amount=${d.amountText}")
    d.photos.forEach { appendLine("photo=$it") }
}

/** 纯函数：行式文本 → 草稿；magic 不符返回 null，缺字段按空串/空列表容错 */
internal fun decodeDraft(text: String): BillDraftStore.Draft? {
    val lines = text.lines().filter { it.isNotBlank() }
    if (lines.firstOrNull() != "bill_draft_v1") return null
    var category = ""
    var date = ""
    var driver = ""
    var plate = ""
    var region = ""
    var title = ""
    var amount = ""
    val photos = mutableListOf<String>()
    for (line in lines.drop(1)) {
        val idx = line.indexOf('=')
        if (idx <= 0) continue
        val value = line.substring(idx + 1)
        when (line.substring(0, idx)) {
            "category" -> category = value
            "date" -> date = value
            "driver" -> driver = value
            "plate" -> plate = value
            "region" -> region = value
            "title" -> title = value
            "amount" -> amount = value
            "photo" -> photos.add(value)
        }
    }
    return BillDraftStore.Draft(category, date, driver, plate, region, title, amount, photos)
}
