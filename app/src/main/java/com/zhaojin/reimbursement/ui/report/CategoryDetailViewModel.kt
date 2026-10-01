package com.zhaojin.reimbursement.ui.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.zhaojin.reimbursement.data.AppDatabase
import com.zhaojin.reimbursement.data.BillEntity
import kotlinx.coroutines.flow.Flow

class CategoryDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val billDao = AppDatabase.getDatabase(application).billDao()

    fun getBillsInTimeRange(
        category: String,
        isIncome: Boolean,
        startTime: Long,
        endTime: Long
    ): Flow<List<BillEntity>> {
        return billDao.getBillsByCategoryAndTimeRange(isIncome, category, startTime, endTime)
    }
}
