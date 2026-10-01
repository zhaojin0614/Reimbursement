package com.zhaojin.reimbursement.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * 应用私有文件分享：走系统分享面板（ACTION_SEND + FileProvider），
 * 微信 / 文件管理器等均可接收。ClipData 同步授权，兼容多目标选择器。
 */
object FileShare {

    fun share(context: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
