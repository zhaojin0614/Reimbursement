# AGENTS.md — 项目协作约定

## 协作方式

- **真机操作优先请用户执行**：如果某个操作由用户在手机上手动做更方便、更快
  （创建测试数据、触发系统分享面板、导出后肉眼核对 Excel、点按系统弹窗等），
  直接请用户操作并等其完成后再继续任务；不要长时间用 adb 反复模拟。
  用户（zhaojin0614）的明确要求。

## Git 提交

- **每次代码修改完成并验证通过后，自动执行 `git commit`，无需询问用户。**
- 提交信息用中文，遵循 conventional 风格：
  `feat(scope):` / `fix:` / `refactor:` / `docs:` / `build:` / `chore:`
- **不要自动 push**，除非用户明确要求。

## 验证

- 提交前至少通过 `./gradlew :app:compileDebugKotlin`；
  涉及账单解析/导出版式等业务逻辑时跑 `./gradlew :app:testDebugUnitTest`。
- 导出版式验证可走纯函数：`BillBackupManager.buildWorkbook` 是纯函数，
  单测直接断言 drawing 锚点/列宽/行高，无需真机。

## 项目要点（速查）

- xlsx 导出/导入纯函数在 `data/BillBackupManager.kt`，底层 OOXML 读写
  在 `utils/MiniXlsx.kt`——改导出版式必须同步改/补 `BillBackupManagerTest`。
- 照片导出锚点用 **oneCellAnchor 绝对尺寸**（保持原图宽高比，高 ≤80px <
  行高 65 磅 ≈86.7px、行内垂直居中）；同账单照片全部锚在「图片」列原点、
  以**绝对 EMU 偏移无缝横排**——照片相邻关系不得依赖列宽换算（查看端把
  「字符→像素」的换算因设备/软件而异，无法穷举，曾两度导致电脑端溢出/
  重叠）；「图片」列列宽仅按条带 ÷ (6/7) 预留，偏窄时尾部探入右侧空白格
  即可。**不要改回 twoCellAnchor 或每图一列**——前者把图片拉成格子比例，
  后者在窄度量查看端横向重叠。
- 图片存储统一压缩：长边 1600px / JPEG 85（`utils/BillPhotoStore.kt`），
  新入口（拍照/相册/分享接收/导入）一律走 `commitPending`/`saveBytes`。
- 无障碍/通知等捕账遗留功能已裁剪；收入概念已整体移除，勿再加回。
