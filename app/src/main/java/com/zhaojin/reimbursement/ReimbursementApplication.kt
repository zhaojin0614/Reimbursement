package com.zhaojin.reimbursement

import android.app.Application
import com.zhaojin.reimbursement.data.AppDatabase
import com.zhaojin.reimbursement.utils.BillPhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReimbursementApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        // 清扫拍照中断遗留的 pending 临时文件（IO 一次性任务，不阻塞启动）
        CoroutineScope(Dispatchers.IO).launch {
            BillPhotoStore.sweepPending(this@ReimbursementApplication)
        }
    }
}

