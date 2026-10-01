package com.zhaojin.reimbursement.data

import android.content.Context

/**
 * 「地区」可选标签的选项维护：添加账单页从选项中点选，选项列表在设置页
 * 增/改/删。存于 app_settings（与日志保留时间同一份 SharedPreferences），
 * 以 | 分隔；已有账单保存的是选定时的文本，选项后续改名/删除不影响历史账单。
 */
object RegionStore {
    private const val PREFS = "app_settings"
    private const val KEY = "region_options"
    private const val DEFAULTS = "扬州|昆山|邮政"

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
