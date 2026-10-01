# 维修报销 (Reimbursement)

一个专注「手动记账 + 报表统计」的轻量 Android 记账应用。从 [捕账 (AppMessageCapture)](../AppMessageCapture) 精简而来，只保留普通记账与报表功能。

## 功能

- **记账**：手动添加支出/收入账单（标题、金额、日期、分类），按天分组展示；类型/分类筛选、关键词搜索、多选删除、左滑删除、下拉统计面板、本月收支概览。
- **报表统计**：周报/月报/年报/自定义时间段（≤366 天），收支总额、日均/月均、环比对比、收支结余、趋势折线图、近 6 周期柱状图、分类构成环形图，点击分类可查看明细账单。
- **设置**：20 种预设主题色 + HSV 自定义取色；账单备份导出/导入（xlsx，兼容「捕账」导出的备份文件，多余列自动忽略）；合并导入按 时间+金额+标题+类型 指纹去重，恢复覆盖则清空重建。

## 不包含（相对捕账裁剪的功能）

商户记忆、周期账单、平台账户、预算管理、自动记账（通知捕获 / 屏幕无障碍捕获）、生日提醒、消息中心、功能开关系统。

## 技术

- Kotlin + Jetpack Compose（Material 3，液态玻璃视觉语言：实时背景模糊、环境光斑、玻璃卡片）
- Room 单表（`bills`，版本 1，schema 已导出至 `app/schemas/`）
- 无第三方业务依赖；xlsx 读写为内置的 `MiniXlsx`（OOXML 内联字符串实现）
- AGP 8.6.1 / Kotlin 1.9.24 / compileSdk 35 / minSdk 26

## 构建

```bash
./gradlew :app:assembleDebug        # 产物 app/build/outputs/apk/debug/维修报销-v1.0.0.apk
./gradlew :app:testDebugUnitTest    # 单元测试（分类/报表日均/xlsx/备份往返）
```

- 包名：`com.zhaojin.reimbursement`
- 数据库：`reimbursement_database`（与捕账相互独立）
