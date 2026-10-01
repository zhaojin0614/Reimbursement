package com.zhaojin.reimbursement

import android.app.Application
import com.zhaojin.reimbursement.data.AppDatabase
import com.zhaojin.reimbursement.utils.AppLogger
import com.zhaojin.reimbursement.utils.BillPhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReimbursementApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        // 操作日志：初始化（清理超过保留期的日志文件）并记录启动
        AppLogger.init(this)
        AppLogger.log("应用", "应用启动 版本=${BuildConfig.VERSION_NAME}")
        // 清扫拍照中断遗留的 pending 临时文件 + 历史大图一次性压缩迁移（IO，不阻塞启动）
        CoroutineScope(Dispatchers.IO).launch {
            BillPhotoStore.sweepPending(this@ReimbursementApplication)
            BillPhotoStore.migrateCompressAll(this@ReimbursementApplication)?.let { (count, before, after) ->
                if (count > 0) {
                    AppLogger.log(
                        "图片",
                        "历史图片压缩迁移：$count 张，${before / 1024}KB → ${after / 1024}KB"
                    )
                }
            }
        }
    }
}

