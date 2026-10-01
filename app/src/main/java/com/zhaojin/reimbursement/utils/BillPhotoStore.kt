package com.zhaojin.reimbursement.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/**
 * 账单图片存储：应用私有目录 filesDir/bill_photos/ 下的 jpg 文件。
 *
 * - 文件名入库存文件名（非全路径），目录归属固定；
 * - 存储前统一压缩（长边 1600px / JPEG 85，票据清晰度足够），历史大图由
 *   启动迁移一次性压缩（[migrateCompressAll]，幂等）；
 * - 拍照走 pending_ 前缀临时文件：成功转正、取消删除、冷启动清扫
 *   （覆盖拍照中途进程被杀的残留）；
 * - 缩略图带内存 LruCache，key 含文件 mtime，替换图片后缓存自动失效；
 * - 显示解码一律按像素预算采样（[calcInSampleSize]），避免大原图整幅解码。
 */
object BillPhotoStore {

    private const val DIR = "bill_photos"
    private const val PENDING_PREFIX = "pending_"
    private const val PREFS = "bill_photo_store"
    private const val KEY_COMPRESS_MIGRATED = "compress_migrated_v1"

    /** 存储压缩参数：长边上限（像素）与 JPEG 质量 */
    private const val STORE_MAX_DIM_PX = 1600
    private const val STORE_JPEG_QUALITY = 85

    /** 缩略图缓存：按位图字节数计费，上限约 8MB */
    private val thumbnailCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun photoDir(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    fun newPendingFile(context: Context): File =
        File(photoDir(context), PENDING_PREFIX + UUID.randomUUID() + ".jpg")

    fun fileFor(context: Context, name: String): File =
        File(photoDir(context), name)

    /** 供系统相机写入的 content URI（FileProvider） */
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

    /** 拍照成功：pending 文件转正并压缩，返回正式文件名；失败返回 null */
    suspend fun commitPending(context: Context, pending: File): String? =
        withContext(Dispatchers.IO) {
            val name = pending.name.removePrefix(PENDING_PREFIX)
            val target = fileFor(context, name)
            if (!pending.renameTo(target)) return@withContext null
            runCatching {
                val raw = target.readBytes()
                compressForStorage(raw)?.let { if (it.size < raw.size) target.writeBytes(it) }
            }
            name
        }

    /** 拍照取消：丢弃 pending 文件 */
    fun discard(pending: File) {
        pending.delete()
    }

    /** 删除图片文件并清空缓存（换图/删账单联动调用） */
    fun delete(context: Context, name: String) {
        if (name.isNotBlank()) fileFor(context, name).delete()
        thumbnailCache.evictAll()
    }

    /** 把字节数据（备份导入等）存为新图片文件（压缩后落盘），返回文件名；失败返回 null */
    fun saveBytes(context: Context, data: ByteArray): String? = runCatching {
        val compressed = compressForStorage(data) ?: data
        val name = UUID.randomUUID().toString() + ".jpg"
        fileFor(context, name).writeBytes(compressed)
        name
    }.getOrNull()

    /** 恢复覆盖前清空全部已托管图片文件（pending 临时文件保留） */
    fun clearAll(context: Context) {
        photoDir(context).listFiles { f -> !f.name.startsWith(PENDING_PREFIX) }?.forEach { it.delete() }
        thumbnailCache.evictAll()
    }

    /** 冷启动清扫：删除拍照中断遗留的 pending 临时文件 */
    fun sweepPending(context: Context) {
        photoDir(context).listFiles { f -> f.name.startsWith(PENDING_PREFIX) }?.forEach { it.delete() }
    }

    /**
     * 一次性迁移：把历史存储的超大图片全部压缩（文件名不变，数据库无需改动）。
     * 幂等——完成后记入 SharedPreferences 不再执行；返回 null 表示已迁移过，
     * 否则返回「张数, 压缩前字节, 压缩后字节」供日志记录。
     */
    suspend fun migrateCompressAll(context: Context): Triple<Int, Long, Long>? =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_COMPRESS_MIGRATED, false)) return@withContext null
            var count = 0
            var before = 0L
            var after = 0L
            photoDir(context).listFiles { f -> f.isFile && !f.name.startsWith(PENDING_PREFIX) }
                ?.forEach { f ->
                    runCatching {
                        val raw = f.readBytes()
                        before += raw.size
                        val compressed = compressForStorage(raw)
                        if (compressed != null && compressed.size < raw.size) {
                            f.writeBytes(compressed)
                            after += compressed.size
                            count++
                        } else {
                            after += raw.size
                        }
                    }
                }
            prefs.edit().putBoolean(KEY_COMPRESS_MIGRATED, true).apply()
            thumbnailCache.evictAll()
            Triple(count, before, after)
        }

    /**
     * 存储用压缩：已是 JPEG 且长边 ≤1600px 时原样返回（避免二次有损）；
     * 其余（超大 JPEG / PNG / HEIC 等）先按长边采样解码、再精确缩放，
     * 统一重编为 JPEG 质量 85。解码失败返回 null。
     */
    fun compressForStorage(data: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        val isJpeg = data.size >= 3 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() &&
            data[2] == 0xFF.toByte()
        if (isJpeg && longEdge <= STORE_MAX_DIM_PX) return data

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeForLongEdge(bounds.outWidth, bounds.outHeight, STORE_MAX_DIM_PX)
        }
        val decoded = BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: return null
        val scaled = run {
            val le = maxOf(decoded.width, decoded.height)
            if (le > STORE_MAX_DIM_PX) {
                val ratio = STORE_MAX_DIM_PX.toFloat() / le
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).roundToInt().coerceAtLeast(1),
                    (decoded.height * ratio).roundToInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }
        }
        if (scaled !== decoded) decoded.recycle()
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, STORE_JPEG_QUALITY, out)
        if (scaled !== decoded) scaled.recycle()
        return out.toByteArray()
    }

    /** 长边采样：取最大的 2 的幂使采样后长边仍 ≥ maxLongEdge（剩余差距交给精确缩放） */
    private fun sampleSizeForLongEdge(width: Int, height: Int, maxLongEdge: Int): Int {
        var s = 1
        while (maxOf(width, height) / (s * 2L) >= maxLongEdge) s *= 2
        return s
    }

    /**
     * 从相册/文档 URI 导入图片：读取后压缩进私有目录（不再保留原始大图），
     * 返回正式文件名；读取失败返回 null。
     */
    suspend fun importFromUri(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@runCatching null
                val data = compressForStorage(raw) ?: raw
                val name = UUID.randomUUID().toString() + ".jpg"
                fileFor(context, name).writeBytes(data)
                name
            }.getOrNull()
        }

    /** 备份导出用：读取原图字节并归一化（HEIC 等转 JPEG），文件缺失或解码失败返回 null */
    fun readExportBytes(context: Context, name: String): ByteArray? {
        val file = fileFor(context, name)
        if (!file.exists()) return null
        return normalizeImageBytes(file.readBytes())
    }

    /**
     * 导出归一化：JPEG/PNG 原样返回；其余格式（HEIC 等 Excel 不支持的）
     * 解码后重编为 JPEG，失败返回 null。（存储已统一为 JPEG，此函数只为
     * 兼容极早期的历史文件。）
     */
    fun normalizeImageBytes(data: ByteArray): ByteArray? {
        val isJpeg = data.size >= 3 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() &&
            data[2] == 0xFF.toByte()
        val isPng = data.size >= 8 && data[0] == 0x89.toByte() && data[1] == 0x50.toByte() &&
            data[2] == 0x4E.toByte() && data[3] == 0x47.toByte()
        if (isJpeg || isPng) return data
        return runCatching {
            val bmp = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return null
            ByteArrayOutputStream().also { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }.toByteArray()
        }.getOrNull()
    }

    /** 加载账单图标缩略图（带缓存），文件不存在返回 null */
    suspend fun loadThumbnail(context: Context, name: String, sizePx: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = fileFor(context, name)
            if (!file.exists()) return@withContext null
            val key = "$name@${file.lastModified()}@$sizePx"
            thumbnailCache.get(key)?.let { return@withContext it }
            decodeSampled(file.absolutePath, sizePx.toLong() * sizePx)?.also {
                thumbnailCache.put(key, it)
            }
        }

    /** 加载查看器大图（按像素预算采样），文件不存在返回 null */
    suspend fun loadForView(context: Context, name: String, maxPixels: Long): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = fileFor(context, name)
            if (!file.exists()) return@withContext null
            decodeSampled(file.absolutePath, maxPixels)
        }
}

/**
 * 纯函数：按最大像素预算计算 inSampleSize（2 的幂）。
 * 解码后的位图不超过 [maxPixels]，从而限制单张原图的解码内存
 * （ARGB_8888 下 4M 像素 ≈ 16MB）。
 */
internal fun calcInSampleSize(width: Int, height: Int, maxPixels: Long): Int {
    if (width <= 0 || height <= 0 || maxPixels <= 0) return 1
    var inSampleSize = 1
    while (width.toLong() * height / (inSampleSize.toLong() * inSampleSize) > maxPixels) {
        inSampleSize *= 2
    }
    return inSampleSize
}

private fun decodeSampled(path: String, maxPixels: Long): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, maxPixels)
    }
    return BitmapFactory.decodeFile(path, options)
}
