package com.zhaojin.reimbursement.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * 分类配色：按分类名稳定映射（哈希取模）——同一分类在任何界面、任何主题下
 * 颜色一致；新增分类自动获得配色，与主题色无关，便于在账单卡片/筛选/
 * 选择器/报表饼图中区分不同分类。
 * 色板与报表分类构成使用同一组 8 色；浅色主题下可直接作文字色，
 * 深色主题下文字用 [categoryTextColor] 提亮。
 */
private val CATEGORY_PALETTE = listOf(
    Color(0xFF5B8DEF), // 蓝
    Color(0xFF66BB6A), // 绿
    Color(0xFFFFA726), // 橙
    Color(0xFFAB47BC), // 紫
    Color(0xFFEF5350), // 红
    Color(0xFF26C6DA), // 青
    Color(0xFFEC407A), // 粉
    Color(0xFF8D6E63)  // 棕
)

/** 分类名 → 稳定颜色（同一分类永远同色） */
fun categoryColor(name: String): Color {
    if (name.isBlank()) return CATEGORY_PALETTE[0]
    val idx = ((name.hashCode() % CATEGORY_PALETTE.size) + CATEGORY_PALETTE.size) % CATEGORY_PALETTE.size
    return CATEGORY_PALETTE[idx]
}

/** 深色主题下把分类色提亮，保证作为文字可读 */
fun categoryTextColor(name: String, dark: Boolean): Color =
    if (dark) lerp(categoryColor(name), Color.White, 0.30f) else categoryColor(name)
