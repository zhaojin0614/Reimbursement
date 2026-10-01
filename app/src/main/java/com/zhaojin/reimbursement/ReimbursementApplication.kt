package com.zhaojin.reimbursement

import android.app.Application
import com.zhaojin.reimbursement.data.AppDatabase

class ReimbursementApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}
