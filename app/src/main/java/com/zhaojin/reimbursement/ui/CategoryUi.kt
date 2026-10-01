package com.zhaojin.reimbursement.ui

import androidx.compose.ui.graphics.Color
import com.zhaojin.reimbursement.R
import com.zhaojin.reimbursement.ui.theme.CategoryBeauty
import com.zhaojin.reimbursement.ui.theme.CategoryEducation
import com.zhaojin.reimbursement.ui.theme.CategoryEntertainment
import com.zhaojin.reimbursement.ui.theme.CategoryFinance
import com.zhaojin.reimbursement.ui.theme.CategoryFood
import com.zhaojin.reimbursement.ui.theme.CategoryInvestment
import com.zhaojin.reimbursement.ui.theme.CategoryLiving
import com.zhaojin.reimbursement.ui.theme.CategoryMedical
import com.zhaojin.reimbursement.ui.theme.CategoryOtherIncome
import com.zhaojin.reimbursement.ui.theme.CategoryParttime
import com.zhaojin.reimbursement.ui.theme.CategoryPet
import com.zhaojin.reimbursement.ui.theme.CategoryRedPacket
import com.zhaojin.reimbursement.ui.theme.CategoryRefund
import com.zhaojin.reimbursement.ui.theme.CategoryReimbursement
import com.zhaojin.reimbursement.ui.theme.CategoryRental
import com.zhaojin.reimbursement.ui.theme.CategorySalary
import com.zhaojin.reimbursement.ui.theme.CategoryShopping
import com.zhaojin.reimbursement.ui.theme.CategorySocial
import com.zhaojin.reimbursement.ui.theme.CategoryTransport
import com.zhaojin.reimbursement.ui.theme.CategoryUncategorized

/**
 * 分类 → 颜色/图标 的唯一映射。
 *
 * 此前 BillScreen 与 RecurringBillScreen 各持有一份逐行等价的复制粘贴实现，
 * ReportScreen 又跨包引用——现在统一到本文件。
 */
fun getCategoryColor(category: String): Color {
    return when (category) {
        // 支出类别
        ExpenseCategories.FOOD -> CategoryFood
        ExpenseCategories.TRANSPORT -> CategoryTransport
        ExpenseCategories.SHOPPING -> CategoryShopping
        ExpenseCategories.ENTERTAINMENT -> CategoryEntertainment
        ExpenseCategories.LIVING -> CategoryLiving
        ExpenseCategories.MEDICAL -> CategoryMedical
        ExpenseCategories.EDUCATION -> CategoryEducation
        ExpenseCategories.SOCIAL -> CategorySocial
        ExpenseCategories.BEAUTY -> CategoryBeauty
        ExpenseCategories.PET -> CategoryPet
        ExpenseCategories.FINANCE -> CategoryFinance
        ExpenseCategories.OTHER -> CategoryUncategorized

        // 收入类别
        IncomeCategories.SALARY -> CategorySalary
        IncomeCategories.PARTTIME -> CategoryParttime
        IncomeCategories.INVESTMENT -> CategoryInvestment
        IncomeCategories.RENTAL -> CategoryRental
        IncomeCategories.REFUND -> CategoryRefund
        IncomeCategories.RED_PACKET -> CategoryRedPacket
        IncomeCategories.REIMBURSEMENT -> CategoryReimbursement
        IncomeCategories.OTHER -> CategoryOtherIncome

        else -> CategoryUncategorized
    }
}

fun getCategoryIconRes(category: String): Int {
    return when (category) {
        // 支出类别
        ExpenseCategories.FOOD -> R.drawable.ic_category_food
        ExpenseCategories.TRANSPORT -> R.drawable.ic_category_transport
        ExpenseCategories.SHOPPING -> R.drawable.ic_category_shopping
        ExpenseCategories.ENTERTAINMENT -> R.drawable.ic_category_entertainment
        ExpenseCategories.LIVING -> R.drawable.ic_category_living
        ExpenseCategories.MEDICAL -> R.drawable.ic_category_medical
        ExpenseCategories.EDUCATION -> R.drawable.ic_category_education
        ExpenseCategories.SOCIAL -> R.drawable.ic_category_social
        ExpenseCategories.BEAUTY -> R.drawable.ic_category_beauty
        ExpenseCategories.PET -> R.drawable.ic_category_pet
        ExpenseCategories.FINANCE -> R.drawable.ic_category_finance
        ExpenseCategories.OTHER -> R.drawable.ic_category_other_expense

        // 收入类别
        IncomeCategories.SALARY -> R.drawable.ic_category_salary
        IncomeCategories.PARTTIME -> R.drawable.ic_category_parttime
        IncomeCategories.INVESTMENT -> R.drawable.ic_category_investment
        IncomeCategories.RENTAL -> R.drawable.ic_category_rental
        IncomeCategories.REFUND -> R.drawable.ic_category_refund
        IncomeCategories.RED_PACKET -> R.drawable.ic_category_redpacket
        IncomeCategories.REIMBURSEMENT -> R.drawable.ic_category_reimbursement
        IncomeCategories.OTHER -> R.drawable.ic_category_other_income

        else -> 0
    }
}
