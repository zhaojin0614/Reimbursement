package com.zhaojin.reimbursement.data

import android.content.Context

/**
 * 「分类」选项维护：添加账单页从选项中点选（默认 维修报销/出险记录），
 * 选项列表在设置页增/改/删。存于 app_settings（| 分隔）；已有账单保存的
 * 是选定时的文本，选项后续改名/删除不影响历史账单，导出时按账单上的
 * 分类文本分表。
 */
object CategoryStore {
    private const val PREFS = "app_settings"
    private const val KEY = "category_options"
    private const val DEFAULTS = "维修报销|出险记录"

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, DEFAULTS) ?: DEFAULTS
        return raw.split('|').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun save(context: Context, options: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, options.joinToString("|"))
            .apply()
    }
}
