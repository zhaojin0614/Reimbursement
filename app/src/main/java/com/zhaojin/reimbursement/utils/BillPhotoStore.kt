package com.zhaojin.reimbursement.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 账单图片存储：应用私有目录 filesDir/bill_photos/ 下的 jpg 文件。
 *
 * - 文件名入库存文件名（非全路径），目录归属固定；
 * - 拍照走 pending_ 前缀临时文件：成功转正、取消删除、冷启动清扫
 *   （覆盖拍照中途进程被杀的残留）；
 * - 缩略图带内存 LruCache，key 含文件 mtime，替换图片后缓存自动失效；
 * - 解码一律按像素预算采样（[calcInSampleSize]），避免大原图整幅解码。
 */
object BillPhotoStore {

    private const val DIR = "bill_photos"
    private const val PENDING_PREFIX = "pending_"

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

    /** 拍照成功：pending 文件转正，返回正式文件名；失败返回 null */
    fun commitPending(context: Context, pending: File): String? {
        val name = pending.name.removePrefix(PENDING_PREFIX)
        return if (pending.renameTo(fileFor(context, name))) name else null
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

    /** 把字节数据（zip 备份导入等）存为新图片文件，返回文件名；失败返回 null */
    fun saveBytes(context: Context, data: ByteArray): String? = runCatching {
        val name = UUID.randomUUID().toString() + ".jpg"
        fileFor(context, name).writeBytes(data)
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
     * 从相册/文档 URI 导入图片：字节原样复制进私有目录（保留原图质量），
     * 返回正式文件名；读取失败返回 null。
     */
    suspend fun importFromUri(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = UUID.randomUUID().toString() + ".jpg"
                val out = fileFor(context, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                name
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
