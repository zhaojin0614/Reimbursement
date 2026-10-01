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
- 照片导出锚点用 **oneCellAnchor 绝对尺寸**（保持原图宽高比，高 ≤84px <
  行高 65 磅 ≈86.7px、行内垂直居中）；同账单照片全部锚在「图片」列原点、
  以绝对 EMU 偏移依次横排（相邻留 4px 间隙 `PHOTO_GAP_PX`），「图片」列
  宽 = 最宽一行条带（含间隙）。**不要改回 twoCellAnchor**——会把图片拉成
  格子比例；**不要改回每图一列**——用户明确要单列方案。
- **电脑端 Excel 的行高渲染随 Windows 显示缩放走**（125% 缩放时 ht=65pt
  的行被渲染成 ≈52pt，图片尺寸却不变 → 照片「重叠」进下一行）。这是查看
  端行为，不是导出文件的问题：100% 缩放或打印/PDF 均正常。曾为此误改
  三轮布局后全部回滚，勿再为它调整导出版式。
- 图片存储统一压缩：长边 1600px / JPEG 85（`utils/BillPhotoStore.kt`），
  新入口（拍照/相册/分享接收/导入）一律走 `commitPending`/`saveBytes`。
- 无障碍/通知等捕账遗留功能已裁剪；收入概念已整体移除，勿再加回。
