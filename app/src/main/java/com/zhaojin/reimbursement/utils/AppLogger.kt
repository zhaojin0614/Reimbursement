package com.zhaojin.reimbursement.utils

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 应用操作日志：按天写应用私有目录 files/logs/log-yyyy-MM-dd.log，
 * 一行一条「yyyy-MM-dd HH:mm:ss [标签] 内容」。写入走单线程后台队列，
 * 不阻塞调用方；超过保留天数的日志文件在应用启动与修改保留时间时自动删除。
 *
 * 默认保留 30 天，可在设置页调整（存 SharedPreferences）。
 */
object AppLogger {

    private const val PREFS = "app_settings"
    private const val KEY_RETENTION = "log_retention_days"

    /** 可选保留天数（设置页选项），默认 30 天 */
    val RETENTION_OPTIONS = listOf(7, 15, 30, 60, 90, 180, 365)

    private const val DEFAULT_RETENTION_DAYS = 30

    private val dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val lineFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 串行写队列：保证多线程日志行完整且有序 */
    private val writeExecutor = Executors.newSingleThreadExecutor()

    fun logsDir(context: Context): File =
        File(context.filesDir, "logs").apply { mkdirs() }

    fun getRetentionDays(context: Context): Int =
        prefs(context).getInt(KEY_RETENTION, DEFAULT_RETENTION_DAYS)

    /** 修改保留天数并立即清理一次过期日志；返回实际生效的天数 */
    fun setRetentionDays(context: Context, days: Int): Int {
        val effective = if (RETENTION_OPTIONS.contains(days)) days else DEFAULT_RETENTION_DAYS
        prefs(context).edit().putInt(KEY_RETENTION, effective).apply()
        cleanUp(logsDir(context), effective, LocalDate.now())
        return effective
    }

    /** 记录一条操作日志（异步写入，失败静默——日志不能影响功能） */
    fun log(tag: String, message: String) {
        writeExecutor.execute {
            runCatching {
                val file = File(logsDir(AppHolder.context), "log-${dayFmt.format(LocalDate.now())}.log")
                FileOutputStream(file, true).use { out ->
                    out.write("${lineFmt.format(LocalDateTime.now())} [$tag] $message\n".toByteArray())
                }
            }
        }
    }

    /** 应用启动调用：清理过期日志文件 */
    fun init(context: Context) {
        AppHolder.context = context.applicationContext
        writeExecutor.execute {
            runCatching { cleanUp(logsDir(context), getRetentionDays(context), LocalDate.now()) }
        }
    }

    /**
     * 清理：按文件名中的日期判断，早于 today - retentionDays 的整文件删除
     * （文件名无法解析时按最后修改时间兜底）。返回删除的文件数。
     */
    fun cleanUp(dir: File, retentionDays: Int, today: LocalDate): Int {
        if (retentionDays <= 0) return 0
        val cutoff = today.minusDays(retentionDays.toLong())
        var deleted = 0
        dir.listFiles { f -> f.isFile && f.name.endsWith(".log") }?.forEach { f ->
            val date = parseLogDate(f.name) ?: return@forEach
            if (date.isBefore(cutoff)) {
                if (f.delete()) deleted++
            }
        }
        return deleted
    }

    /** "log-2026-10-02.log" → LocalDate；命名不符返回 null（按 mtime 兜底的场景） */
    internal fun parseLogDate(name: String): LocalDate? = runCatching {
        LocalDate.parse(name.removePrefix("log-").removeSuffix(".log"), dayFmt)
    }.getOrNull()

    /** 把全部日志打包为 zip（写入 cache/logs_export/），失败返回 null */
    fun exportZip(context: Context): File? = runCatching {
        val logs = logsDir(context).listFiles { f -> f.isFile && f.name.endsWith(".log") }
            ?.sortedBy { it.name }.orEmpty()
        if (logs.isEmpty()) return null
        val outDir = File(context.cacheDir, "logs_export").apply { mkdirs() }
        val zip = File(outDir, "维修报销_日志_${dayFmt.format(LocalDate.now())}.zip")
        ZipOutputStream(FileOutputStream(zip)).use { z ->
            logs.forEach { f ->
                z.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        zip
    }.getOrNull()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** 进程级 Context 持有：log() 无需每次传 Context */
private object AppHolder {
    @Volatile
    lateinit var context: Context
}
