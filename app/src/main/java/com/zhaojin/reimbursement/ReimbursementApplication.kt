package com.zhaojin.reimbursement

import android.app.Application
import com.zhaojin.reimbursement.data.AppDatabase
import com.zhaojin.reimbursement.data.DriverStore
import com.zhaojin.reimbursement.utils.AppLogger
import com.zhaojin.reimbursement.utils.BillDraftStore
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
        // 清扫拍照中断遗留的 pending 临时文件 + 历史大图一次性压缩迁移（IO，不阻塞启动）。
        // 草稿（添加页未保存内容）登记的照片保留——还在等账单保存转正
        CoroutineScope(Dispatchers.IO).launch {
            val keep = BillDraftStore.registeredPhotoNames(this@ReimbursementApplication)
            BillPhotoStore.sweepPending(this@ReimbursementApplication, keep)
            // 历史账单的驾驶员/车牌一次性迁入记忆库（幂等，仅首次执行）
            DriverStore.migrateFromBills(
                this@ReimbursementApplication,
                database.billDao().getAllBillsOnce()
            )?.let { count ->
                AppLogger.log("驾驶员", "历史账单驾驶员迁移完成，共 $count 位")
            }
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

