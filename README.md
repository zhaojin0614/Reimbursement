# 维修报销 (Reimbursement)

一个专注「手动记账 + 报表统计」的轻量 Android 记账应用。从 [捕账 (AppMessageCapture)](../AppMessageCapture) 精简而来，只保留普通记账与报表功能。

## 功能

- **记账**：手动添加支出/收入账单（标题、金额、日期），按天分组展示；类型筛选（全部/支出/收入）、关键词搜索、多选删除、左滑删除、下拉统计面板、本月收支概览。
- **导出（顶栏图标）**：记账页顶部导航栏的导出按钮一键生成**单个 xlsx**——照片直接内嵌在工作簿图片层，且**精确贴合各自格子**（行高随照片 80px、图片列宽按原图宽高比自适应，多张横向排开各占一列不变形）；固定列宽 日期时间 20 / 标题 40 / 金额 10；表头浅灰底。
- **账单图片（多张）**：点击账单图标可拍照或从相册选图，一张账单可挂多张照片；图标显示第一张照片缩略图 + 张数角标；点击图标全屏翻页查看（双击缩放），查看器内支持添加图片 / 删除本张。原图存应用私有目录，缩略图带内存缓存，删除账单/照片联动清理文件。
- **报表统计**：周报/月报/年报/自定义时间段（≤366 天），收支总额、日均/月均、环比对比、收支结余、趋势折线图、近 6 周期柱状图。
- **设置**：20 种预设主题色 + HSV 自定义取色（备份入口已迁至记账页顶部）。
- **轻量化**：静态柔光背景（无动画光斑、无实时背景模糊），常驻 GPU/CPU 占用接近于零。

## 不包含（相对捕账裁剪的功能）

账单分类、商户记忆、周期账单、平台账户、预算管理、自动记账（通知捕获 / 屏幕无障碍捕获）、生日提醒、消息中心、功能开关系统。

## 技术

- Kotlin + Jetpack Compose（Material 3，液态玻璃视觉语言：玻璃卡片、静态柔光背景）
- Room 双表（`bills` + `bill_photos` 一对多，版本 4，schema 已导出至 `app/schemas/`；v3→v4 迁移把单图列搬进多图表）
- 无第三方业务依赖；xlsx 读写为内置的 `MiniXlsx`（OOXML 内联字符串 + drawing 图片层实现），备份导出即单个 xlsx 文件
- 账单列表统一按时间倒序条数分页（LIMIT），补记历史日期的账单在所有视图一致可见
- 拍照经 `ActivityResultContracts.TakePicture` 调起系统相机（无需 CAMERA 权限），相册走 `PickVisualMedia` 隐私照片选择器；图片文件管理见 `utils/BillPhotoStore.kt`
- AGP 8.6.1 / Kotlin 1.9.24 / compileSdk 35 / minSdk 26

## 构建

```bash
./gradlew :app:assembleDebug        # 产物 app/build/outputs/apk/debug/维修报销-v1.0.0.apk
./gradlew :app:testDebugUnitTest    # 单元测试（报表日均/xlsx/备份往返）
```

- 包名：`com.zhaojin.reimbursement`
- 数据库：`reimbursement_database`（与捕账相互独立）
