@file:OptIn(ExperimentalMaterial3Api::class)

package com.zhaojin.reimbursement.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhaojin.reimbursement.BuildConfig
import com.zhaojin.reimbursement.ui.components.GlassCompactDialog
import com.zhaojin.reimbursement.ui.components.SoftCard
import com.zhaojin.reimbursement.ui.components.glassBorder
import com.zhaojin.reimbursement.ui.theme.AccentColor
import com.zhaojin.reimbursement.ui.theme.AccentColorRepository
import com.zhaojin.reimbursement.ui.theme.AccentVariant
import com.zhaojin.reimbursement.ui.theme.ComponentGap

/** 备份包的 MIME（zip：表格 + 图片；导入同时兼容旧版 xlsx 与捕账格式） */
private const val BACKUP_MIME = "application/zip"

/**
 * 设置页：主题色 / 备份恢复 / 版本信息。
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: BillViewModel = viewModel()

    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showAccentDialog by remember { mutableStateOf(false) }
    var showCustomPicker by remember { mutableStateOf(false) }
    // 弹窗内的草稿选择：点色块/调色板只改草稿，「使用此颜色」统一应用
    var draftAccent by remember { mutableStateOf(AccentVariant.fromPreset(AccentColor.MINT)) }
    var draftCustomColor by remember { mutableStateOf(AccentColor.MINT.primary) }
    var importOverwrite by remember { mutableStateOf(false) }

    // 主色调：全局单例状态，选色后即时生效（读取处自动订阅重组）
    val currentAccent = AccentColorRepository.current

    // 备份导出/导入的 SAF 启动器
    val backupBusy by viewModel.backupBusy.collectAsState()
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME)
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importBackup(it, importOverwrite) } }
    LaunchedEffect(Unit) {
        viewModel.backupMessage.collect { message ->
            message?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                viewModel.consumeBackupMessage()
            }
        }
    }

    // 拦截系统返回手势/按键回到记账界面，而不是退出应用
    BackHandler(enabled = true) { onBack() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SettingsGroup("外观") {
                SettingsRow(
                    icon = Icons.Default.Palette,
                    title = "主题色",
                    onClick = {
                        // 每次打开弹窗，草稿重置为当前已应用的主题色
                        draftAccent = AccentColorRepository.current
                        showCustomPicker = false
                        showAccentDialog = true
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(currentAccent.primary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentAccent.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            SettingsGroup("数据") {
                SettingsNavigateRow(
                    icon = Icons.Default.Backup,
                    title = "备份与恢复",
                    value = "导出 / 导入",
                    onClick = { showBackupDialog = true }
                )
            }

            SettingsGroup("关于") {
                SettingsValueRow(
                    icon = Icons.Default.Info,
                    title = "版本",
                    value = BuildConfig.VERSION_NAME
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showAccentDialog) {
        GlassCompactDialog(
            onDismissRequest = { showAccentDialog = false },
            title = "主题色",
            text = {
                // 可滚动：展开调色板或键盘弹出时内容不会顶飞
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "已选：${draftAccent.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 20 个预设 = 4 行 × 5 列满排，两端对齐消除右侧空白
                    AccentColor.entries.chunked(5).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            rowPresets.forEach { preset ->
                                AccentSwatch(
                                    color = preset.primary,
                                    selected = draftAccent.id == preset.name,
                                    onClick = { draftAccent = AccentVariant.fromPreset(preset) }
                                )
                            }
                        }
                    }
                    // 自定义入口：点击在下方展开/收起调色板（不再弹独立窗口）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                            .clickable {
                                if (!showCustomPicker) {
                                    draftCustomColor = if (draftAccent.isCustom) draftAccent.primary
                                    else AccentColorRepository.lastCustom ?: AccentColor.MINT.primary
                                }
                                showCustomPicker = !showCustomPicker
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        listOf(
                                            Color(0xFFE5484D), Color(0xFFE5B048), Color(0xFF7BC96A),
                                            Color(0xFF3FB6C9), Color(0xFF4C6FE5), Color(0xFFB45CE5),
                                            Color(0xFFE5484D)
                                        )
                                    )
                                )
                                .border(glassBorder(), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自定义颜色",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "调色板选取或输入十六进制色值",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (draftAccent.isCustom) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "已选自定义颜色",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Icon(
                            imageVector = if (showCustomPicker) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (showCustomPicker) "收起调色板" else "展开调色板",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // 内联调色板：展开在弹窗内部，避免多窗口叠放冲突
                    AnimatedVisibility(visible = showCustomPicker) {
                        AccentHsvPickerContent(
                            initial = draftCustomColor,
                            onColorChanged = { picked ->
                                draftCustomColor = picked
                                draftAccent = AccentVariant(AccentVariant.CUSTOM_ID, "自定义", picked)
                            }
                        )
                    }
                    Text(
                        text = "点击「使用此颜色」后生效；按钮、导航、选中态等会跟随主题色",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (draftAccent.isCustom) {
                        AccentColorRepository.setCustom(context, draftAccent.primary)
                    } else {
                        AccentColor.entries.firstOrNull { it.name == draftAccent.id }
                            ?.let { AccentColorRepository.setPreset(context, it) }
                    }
                    showAccentDialog = false
                }) { Text("使用此颜色") }
            },
            dismissButton = {
                TextButton(onClick = { showAccentDialog = false }) { Text("取消") }
            }
        )
    }

    if (showBackupDialog) {
        GlassCompactDialog(
            onDismissRequest = { showBackupDialog = false },
            title = "备份与恢复",
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackupActionRow(
                        icon = Icons.Default.TableChart,
                        title = "导出备份（含图片）",
                        subtitle = "账单表格 + 全部照片打包为 zip，可还原",
                        enabled = !backupBusy,
                        onClick = {
                            showBackupDialog = false
                            val date = java.time.LocalDate.now()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            exportBackupLauncher.launch("维修报销_备份_$date.zip")
                        }
                    )
                    BackupActionRow(
                        icon = Icons.Default.UploadFile,
                        title = "导入数据（合并）",
                        subtitle = "与现有账单去重后并入（含图片）",
                        enabled = !backupBusy,
                        onClick = {
                            showBackupDialog = false
                            importOverwrite = false
                            importBackupLauncher.launch(arrayOf(BACKUP_MIME, "application/octet-stream"))
                        }
                    )
                    BackupActionRow(
                        icon = Icons.Default.SettingsBackupRestore,
                        title = "恢复备份（覆盖）",
                        subtitle = "清空当前账单与图片后按文件重建",
                        enabled = !backupBusy,
                        onClick = {
                            showBackupDialog = false
                            showRestoreConfirm = true
                        }
                    )
                    Text(
                        text = "导入兼容 zip 备份与旧版 xlsx（含「捕账」导出，无图片列自动忽略）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackupDialog = false }) { Text("关闭") }
            }
        )
    }

    if (showRestoreConfirm) {
        GlassCompactDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = "恢复备份",
            text = { Text("将清空当前所有账单，并按所选文件重建，此操作不可撤销。确定继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirm = false
                        importOverwrite = true
                        importBackupLauncher.launch(arrayOf(BACKUP_MIME, "application/octet-stream"))
                    }
                ) { Text("确定恢复", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text("取消") }
            }
        )
    }
}

// ── 分组与行组件 ─────────────────────────────────────────────────────────

/** 主题色色块：圆形色点，选中时深色描边并显示对勾 */
@Composable
private fun AccentSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                else glassBorder(),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已选择",
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/**
 * 自定义颜色调色板：SV 选色面板（横=饱和度，纵=明度）+ 色相滑条 + 十六进制输入。
 * 颜色状态（HSV）由本组件持有，任意途径变更都通过 [onColorChanged] 回调。
 */
@Composable
private fun AccentHsvPickerContent(
    initial: Color,
    onColorChanged: (Color) -> Unit
) {
    val initialHsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var valueF by remember { mutableFloatStateOf(initialHsv[2]) }
    var hexText by remember {
        mutableStateOf("#%06X".format(0xFFFFFF and android.graphics.Color.HSVToColor(initialHsv)))
    }

    fun push(h: Float = hue, s: Float = sat, v: Float = valueF, syncHex: Boolean = true) {
        hue = h.coerceIn(0f, 360f)
        sat = s.coerceIn(0f, 1f)
        valueF = v.coerceIn(0f, 1f)
        if (syncHex) {
            hexText = "#%06X".format(
                0xFFFFFF and android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, valueF))
            )
        }
        onColorChanged(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, valueF))))
    }

    // 必须是单一根节点：AnimatedVisibility 等单槽容器会把多个平级子组件叠放在同一位置
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // SV 面板：底层白→纯色横渐变，叠加透明→黑纵渐变
        val pureHue = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(glassBorder(), RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        push(s = pos.x / size.width, v = 1f - pos.y / size.height)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        push(s = change.position.x / size.width, v = 1f - change.position.y / size.height)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, pureHue)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                // 圆环指示器夹在面板内，拖到边缘时不被裁剪
                val r = 8.dp.toPx()
                drawCircle(
                    Color.White,
                    radius = r,
                    center = Offset(
                        (sat * size.width).coerceIn(r, size.width - r),
                        ((1f - valueF) * size.height).coerceIn(r, size.height - r)
                    ),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // 色相滑条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        push(h = pos.x / size.width * 360f)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        push(h = change.position.x / size.width * 360f)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    Brush.horizontalGradient(
                        List(13) { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 30f, 1f, 1f))) }
                    )
                )
                drawCircle(
                    Color.White,
                    radius = 7.dp.toPx(),
                    center = Offset(hue / 360f * size.width, size.height / 2f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // 当前颜色预览 + 十六进制输入
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, valueF))))
                    .border(glassBorder(), CircleShape)
            )
            TextField(
                value = hexText,
                onValueChange = { input ->
                    hexText = input
                    parseHexColor(input)?.let { argb ->
                        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }
                        push(h = hsv[0], s = hsv[1], v = hsv[2], syncHex = false)
                    }
                },
                placeholder = {
                    Text(
                        text = "#RRGGBB",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = MaterialTheme.colorScheme.outline,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )
        }
    }
}

/** 解析 #RRGGBB / RRGGBB 十六进制颜色，非法输入返回 null */
private fun parseHexColor(input: String): Int? {
    val clean = input.trim().removePrefix("#")
    val valid = clean.length == 6 && clean.all {
        it.isDigit() || it in 'a'..'f' || it in 'A'..'F'
    }
    return if (valid) 0xFF000000.toInt() or clean.toInt(16) else null
}

/** 分组卡片：组标题 + 圆角玻璃卡片内的若干行 */
@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // 组间距遵循全局 ComponentGap（Dimens.kt），标题与所属卡片之间
        // 用半间距派生值，让标题在视觉上紧贴自己的卡片
        modifier = Modifier.padding(start = 4.dp, top = ComponentGap, bottom = ComponentGap / 2)
    )
    SoftCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(content = content)
    }
}

/** 行骨架：圆底图标 + 标题 + 右侧内容（可点击） */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

/** 导航行：右侧值 + 箭头 */
@Composable
private fun SettingsNavigateRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    SettingsRow(icon = icon, title = title, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** 值行：右侧纯文本（不可点击） */
@Composable
private fun SettingsValueRow(
    icon: ImageVector,
    title: String,
    value: String
) {
    SettingsRow(icon = icon, title = title) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 备份弹窗的操作行：图标 + 标题 + 说明 */
@Composable
private fun BackupActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
