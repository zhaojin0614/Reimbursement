@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zhaojin.reimbursement.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipboardManager
import android.widget.Toast
import com.zhaojin.reimbursement.data.BillEntity
import com.zhaojin.reimbursement.data.CategoryStore
import com.zhaojin.reimbursement.data.DriverStore
import com.zhaojin.reimbursement.data.RegionStore
import com.zhaojin.reimbursement.ui.components.GlassCompactDialog
import com.zhaojin.reimbursement.utils.AppLogger
import com.zhaojin.reimbursement.utils.BillDraftStore
import com.zhaojin.reimbursement.utils.BillPhotoStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.zhaojin.reimbursement.ui.components.SoftButton
import com.zhaojin.reimbursement.ui.components.glassBorder
import com.zhaojin.reimbursement.ui.components.isDarkTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 会调起输入法的文本字段（用于输入法弹出时自动滚动对位） */
private enum class ImeField { Driver, Title, Amount }

/** 车牌省份简称（含「使」馆牌） */
private val PLATE_PROVINCES =
    listOf("京", "津", "沪", "渝", "冀", "豫", "云", "辽", "黑", "湘", "皖", "鲁", "新", "苏", "浙",
        "赣", "鄂", "桂", "甘", "晋", "蒙", "陕", "吉", "闽", "贵", "粤", "青", "藏", "川", "宁", "琼", "使")

/**
 * 车牌序号键盘的固定行布局：字母（不含易混淆的 I/O）三行排满后空三格，
 * 数字 1-9 独占一行，0 与常用后缀（挂/港/澳/学/警）平分最后一行。
 * 空串为占位空格。
 */
private val PLATE_KEY_ROWS = listOf(
    listOf("A", "B", "C", "D", "E", "F", "G", "H", "J"),
    listOf("K", "L", "M", "N", "P", "Q", "R", "S", "T"),
    listOf("U", "V", "W", "X", "Y", "Z", "", "", ""),
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9"),
    listOf("0", "挂", "港", "澳", "学", "警")
)

/** 车牌总长上限：省份简称 1 位 + 序号最多 7 位 */
internal const val PLATE_MAX_LEN = 8

/**
 * 剪贴板文本 → 车牌可用字符：去空格与常见分隔符、全角转半角、
 * 字母统一大写，仅保留汉字（省份简称/学挂警港澳等）与字母数字，上限 8 位。
 * （添加页粘贴与设置页维护车牌共用）
 */
internal fun sanitizePlateInput(raw: String): String {
    val cleaned = raw.replace(Regex("[\\s·.\\-—_\\u3000\\u00A0]"), "")
    val sb = StringBuilder()
    for (ch in cleaned) {
        if (sb.length >= PLATE_MAX_LEN) break
        // 全角（Ａ-Ｚ、０-９等）转半角后再判断
        val c = if (ch.code in 0xFF01..0xFF5E) (ch.code - 0xFEE0).toChar() else ch
        when {
            c.code in 0x3400..0x9FFF -> sb.append(c) // 汉字
            c in 'A'..'Z' -> sb.append(c)
            c in 'a'..'z' -> sb.append(c.uppercaseChar())
            c in '0'..'9' -> sb.append(c)
        }
    }
    return sb.toString()
}

/**
 * 添加/修改账单共用页面：[editing] 传待改账单即进入编辑模式（预填全部
 * 字段、保存时按原 id 原位更新；图片仍在查看器中管理）。行序：分类 →
 * 日期 → 地区 → 车牌号（自绘车牌键盘，不调系统输入法）→ 内容 → 金额 →
 * 图片。
 * 顶栏返回即取消，键盘/面板弹出时表单可滚动不被遮挡。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBillScreen(
    editing: BillEntity? = null,
    existingPhotos: List<String> = emptyList(),
    defaultCategory: String? = null,
    onBack: () -> Unit,
    onSave: (BillEntity, List<File>, List<String>) -> Unit
) {
    // 编辑模式预填（保存按原 id 原位更新）
    var title by remember { mutableStateOf(editing?.title.orEmpty()) }
    var amountText by remember {
        mutableStateOf(
            editing?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: ""
        )
    }
    // TextFieldValue：点选联想项整体替换文字时需要显式把光标移到末尾
    var driver by remember { mutableStateOf(TextFieldValue(editing?.driver.orEmpty())) }
    var plate by remember { mutableStateOf(editing?.plate.orEmpty()) }
    var region by remember { mutableStateOf(editing?.region.orEmpty()) }
    var showPlateBoard by remember { mutableStateOf(false) }
    // 暂存照片（pending 文件，保存账单时统一转正压缩入库；可选）。
    // 新增模式与 BillDraftStore 共享运行时列表——分享接收页把图片挂进草稿时
    // 直接进这里，页面立即可见；编辑已有账单不进草稿，仍用局部列表。
    val editStaged = remember { mutableStateListOf<File>() }
    val stagedPhotos = if (editing == null) BillDraftStore.stagedPhotos else editStaged
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    val today = remember { LocalDate.now() }
    var selectedDate by remember {
        mutableStateOf(
            editing?.timestamp?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            } ?: today
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val ioScope = remember { CoroutineScope(Dispatchers.IO) }
    val regionOptions = remember { RegionStore.load(context) }
    val categoryOptions = remember { CategoryStore.load(context) }

    // 输入法弹出时自动把焦点字段滚进可视区（否则最下方的金额会被键盘盖住）：
    // 每次 IME 高度变化都重新触发一次 bringIntoView，动画全程持续对位
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    var focusedField by remember { mutableStateOf<ImeField?>(null) }
    val fieldRequesters = remember {
        ImeField.entries.associateWith { BringIntoViewRequester() }
    }
    LaunchedEffect(imeBottom, focusedField) {
        val field = focusedField ?: return@LaunchedEffect
        if (imeBottom > 0) fieldRequesters.getValue(field).bringIntoView()
    }
    // 分类默认取第一个选项；编辑时预填账单原分类（空则回退）
    var category by remember {
        mutableStateOf(
            editing?.category?.takeIf { it.isNotBlank() }
                ?: defaultCategory?.takeIf { it.isNotBlank() && it in categoryOptions }
                ?: categoryOptions.firstOrNull().orEmpty()
        )
    }
    // 已有照片的移除清单：保存时才真正删除，返回不保存即撤销
    val removedExisting = remember { mutableStateListOf<String>() }
    val shownExisting = existingPhotos.filter { it !in removedExisting }
    val viewerPhotoNames = shownExisting + stagedPhotos.map { it.name }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    // 长按车牌行粘贴：读系统剪贴板，清洗为车牌字符后整体填入
    fun pastePlateFromClipboard() {
        val cm = context.getSystemService(ClipboardManager::class.java)
        val text = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        val cleaned = sanitizePlateInput(text)
        if (cleaned.isEmpty()) {
            Toast.makeText(context, "剪贴板中没有可用的车牌内容", Toast.LENGTH_SHORT).show()
        } else {
            plate = cleaned
            Toast.makeText(context, "已粘贴车牌：$cleaned", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val file = pendingCaptureFile
        pendingCaptureFile = null
        if (file != null) {
            if (ok) stagedPhotos.add(file) else BillPhotoStore.discard(file)
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris ->
        if (uris.isNotEmpty()) {
            ioScope.launch {
                uris.forEach { uri ->
                    BillPhotoStore.stageFromUri(context, uri)?.let { file ->
                        withContext(Dispatchers.Main) { stagedPhotos.add(file) }
                    }
                }
            }
        }
    }

    // 离开页面时丢弃暂存照片（pending 文件不留在托管目录）
    fun discardStaged() {
        stagedPhotos.forEach { BillPhotoStore.discard(it) }
        stagedPhotos.clear()
        pendingCaptureFile?.let { BillPhotoStore.discard(it) }
    }

    // ── 新增模式草稿会话：恢复 → 自动保存（返回即存） → 横幅可丢弃 ────
    // 编辑已有账单不进草稿（原账单数据在库里，放弃修改即回原值）
    var showDraftBanner by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    // 恢复完成前拦截自动保存，避免进入页面瞬间空表单把磁盘草稿覆盖为空
    var draftRestoreDone by remember { mutableStateOf(editing != null) }

    if (editing == null) {
        // 会话注册：分享接收页据此把照片送进运行时列表；退出时注销并清引用
        // （文件所有权已在各退出路径显式闭环：保存=移交入库、放弃=discard、
        // 存草稿=文件留磁盘且登记在草稿里）
        DisposableEffect(Unit) {
            BillDraftStore.setSession(true)
            onDispose { BillDraftStore.setSession(false) }
        }
        // 进入页面恢复草稿：照片文件仍在磁盘 pending 态，重新挂回列表
        LaunchedEffect(Unit) {
            val draft = BillDraftStore.load(context) ?: run {
                draftRestoreDone = true
                return@LaunchedEffect
            }
            showDraftBanner = true
            title = draft.title
            amountText = draft.amountText
            driver = TextFieldValue(draft.driver)
            plate = draft.plate
            region = draft.region.takeIf { it.isNotBlank() && it in regionOptions } ?: ""
            category = draft.category.takeIf { it.isNotBlank() && it in categoryOptions } ?: category
            draft.dateIso.takeIf { it.isNotBlank() }?.let { iso ->
                runCatching { LocalDate.parse(iso) }.getOrNull()?.let { selectedDate = it }
            }
            val dir = BillPhotoStore.photoDir(context)
            draft.photos.forEach { name ->
                File(dir, name).takeIf { it.exists() }?.let { stagedPhotos.add(it) }
            }
            AppLogger.log(
                "账单",
                "恢复草稿 内容=「${draft.title}」 金额=${draft.amountText} 照片=${stagedPhotos.size}/${draft.photos.size}"
            )
            draftRestoreDone = true
        }
        // 自动保存：任一字段/照片变化后 500ms 落盘（effect 重启即防抖），
        // 进程被杀也不丢输入；表单清空则连草稿文件一并删除
        LaunchedEffect(
            draftRestoreDone, category, selectedDate, driver.text, plate, region, title, amountText,
            stagedPhotos.joinToString("|") { it.name }
        ) {
            if (!draftRestoreDone) return@LaunchedEffect
            delay(500)
            val snapshot = BillDraftStore.Draft(
                category = category,
                dateIso = selectedDate.toString(),
                driver = driver.text,
                plate = plate,
                region = region,
                title = title,
                amountText = amountText,
                photos = stagedPhotos.map { it.name }
            )
            if (snapshot.isEmpty) BillDraftStore.clear(context) else BillDraftStore.save(context, snapshot)
        }
    }

    // ── 驾驶员记忆：输入联想 + 车牌联动 ──────────────────────────────
    val driverMemory = remember { mutableStateListOf<DriverStore.DriverEntry>() }
    LaunchedEffect(Unit) {
        driverMemory.addAll(DriverStore.load(context))
    }
    // 选定联想项后收起列表；再次输入或点击输入框时重新弹出
    var driverSuggestionsHidden by remember { mutableStateOf(false) }
    var driverFocused by remember { mutableStateOf(false) }
    val driverInteraction = remember { MutableInteractionSource() }
    // 点击已聚焦的输入框也重新弹出（选定收起后，再点一次可重新选择）
    LaunchedEffect(driverInteraction) {
        driverInteraction.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) driverSuggestionsHidden = false
        }
    }

    // 联想候选：包含匹配（不区分大小写）；未输入时聚焦即列出全部，
    // 沿用记忆库顺序（最近使用在前）
    val driverQuery = driver.text.trim()
    val driverSuggestions = driverMemory.filter {
        driverQuery.isEmpty() || it.name.contains(driverQuery, true)
    }

    // 点选联想项：带出姓名；当前车牌为空或不是该驾驶员已记录的车牌时，
    // 自动填入其最近使用的车牌（已输入的合法组合不动，保存后会记入列表）
    fun applyDriverSuggestion(entry: DriverStore.DriverEntry) {
        driver = TextFieldValue(entry.name, TextRange(entry.name.length))
        val current = plate.trim()
        if (entry.plates.isNotEmpty() &&
            (current.isEmpty() || entry.plates.none { it.equals(current, true) })
        ) {
            plate = entry.plates.first()
        }
        driverSuggestionsHidden = true
    }

    // 返回即自动保存草稿（不再询问；恢复横幅的「丢弃」有二次确认）
    fun requestExit() {
        if (editing == null) {
            // 点击时就地快照（异步保存前列表可能被 onDispose 清空）
            val draft = BillDraftStore.Draft(
                category = category,
                dateIso = selectedDate.toString(),
                driver = driver.text,
                plate = plate,
                region = region,
                title = title,
                amountText = amountText,
                photos = stagedPhotos.map { it.name }
            )
            if (draft.isEmpty) {
                discardStaged()
                ioScope.launch { BillDraftStore.clear(context) }
            } else {
                ioScope.launch { BillDraftStore.save(context, draft) }
            }
            onBack()
        } else {
            discardStaged()
            onBack()
        }
    }

    BackHandler(enabled = true) { requestExit() }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (editing == null) "添加账单" else "修改账单",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { requestExit() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
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
                // imePadding 必须套在 verticalScroll 外层：滚动视口整体抬到
                // 键盘上方，焦点字段的 bringIntoView 才能滚到键盘以上可见
                // （放内侧只变成内容底部留白，视口仍在键盘下面，字段被盖住）
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 草稿恢复横幅：提示内容来源，可一键丢弃回到全新表单
            if (showDraftBanner) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已恢复上次未保存的草稿",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "丢弃",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showDiscardDialog = true }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 第 1 行：分类（点选标签；选项列表在设置界面维护，导出 Excel 按分类分表）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "分类",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categoryOptions.forEach { option ->
                        val selected = option == category
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(glassBorder(), RoundedCornerShape(8.dp))
                                .clickable { category = option }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = option,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 第 2 行：日期
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        showPlateBoard = false
                        showDatePicker = true
                    }
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "选择日期",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "日期",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                val dateLabel = when (selectedDate) {
                    today -> "今天"
                    today.minusDays(1) -> "昨天"
                    today.plusDays(1) -> "明天"
                    else -> selectedDate.format(DateTimeFormatter.ofPattern("MM月dd日"))
                }
                val weekDay = selectedDate.format(DateTimeFormatter.ofPattern(" EEE"))
                Text(
                    text = "$dateLabel$weekDay",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedDate != today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 第 2 行：驾驶员（聚焦即弹联想列表，输入实时过滤，点选自动带出车牌）
            TextField(
                value = driver,
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                onValueChange = {
                    driver = it
                    driverSuggestionsHidden = false
                },
                label = { Text("驾驶员") },
                textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold),
                interactionSource = driverInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusEvent {
                        driverFocused = it.isFocused
                        if (it.isFocused) focusedField = ImeField.Driver
                    }
                    .bringIntoViewRequester(fieldRequesters.getValue(ImeField.Driver)),
                singleLine = true
            )

            // 驾驶员联想列表：高度受限，超出部分在框内滑动；点选联动车牌，
            // 右侧预览将填入的最近车牌
            if (driverFocused && !driverSuggestionsHidden && driverSuggestions.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .border(glassBorder(), RoundedCornerShape(12.dp))
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    driverSuggestions.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { applyDriverSuggestion(entry) }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            entry.plates.firstOrNull()?.let {
                                Text(
                                    text = it,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 第 3 行：地区（可选，点选标签；选项列表在设置界面维护）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "地区",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    regionOptions.forEach { option ->
                        val selected = option == region
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(glassBorder(), RoundedCornerShape(8.dp))
                                .clickable { region = if (selected) "" else option }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = option,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "可选",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 第 4 行：车牌号（点击弹出自绘车牌键盘，长按粘贴，不调系统输入法）
            PlateField(
                plate = plate,
                expanded = showPlateBoard,
                onClick = {
                    showPlateBoard = !showPlateBoard
                    if (showPlateBoard) keyboard?.hide()
                },
                onLongPress = { pastePlateFromClipboard() }
            )

            // 该驾驶员的历史车牌快捷切换（记录多于一个时才显示，点击直接填入）
            val currentDriverPlates = driverMemory
                .firstOrNull { it.name == driver.text.trim() }?.plates.orEmpty()
            if (currentDriverPlates.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    currentDriverPlates.forEach { candidate ->
                        val selected = candidate.equals(plate.trim(), true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(glassBorder(), RoundedCornerShape(8.dp))
                                .clickable { plate = candidate }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = candidate,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (showPlateBoard) {
                Spacer(modifier = Modifier.height(8.dp))
                PlateKeyboard(
                    plate = plate,
                    onChar = { ch ->
                        if (plate.length < PLATE_MAX_LEN) plate += ch
                    },
                    onDelete = { if (plate.isNotEmpty()) plate = plate.dropLast(1) },
                    onDone = { showPlateBoard = false }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 第 5 行：内容（原「标题」）
            TextField(
                value = title,
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                onValueChange = { title = it },
                label = { Text("内容") },
                textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusEvent { if (it.isFocused) focusedField = ImeField.Title }
                    .bringIntoViewRequester(fieldRequesters.getValue(ImeField.Title)),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 第 6 行：金额
            TextField(
                value = amountText,
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                onValueChange = { newValue ->
                    // 仅允许数字与小数点
                    if (newValue.all { it.isDigit() || it == '.' }) {
                        amountText = newValue
                    }
                },
                label = { Text("金额") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusEvent { if (it.isFocused) focusedField = ImeField.Amount }
                    .bringIntoViewRequester(fieldRequesters.getValue(ImeField.Amount)),
                singleLine = true,
                prefix = { Text("¥") },
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 第 7 行：账单图片（可选，保存时随账单一并入库；修改模式同时展示已有照片）
            StagedPhotosRow(
                existingPhotos = shownExisting,
                photos = stagedPhotos,
                onAddClick = { showPhotoSourceDialog = true },
                onRemove = { file ->
                    BillPhotoStore.discard(file)
                    stagedPhotos.remove(file)
                },
                onRemoveExisting = { name -> removedExisting.add(name) },
                onPhotoClick = { idx -> viewerIndex = idx }
            )

            Spacer(modifier = Modifier.height(24.dp))

            SoftButton(
                text = if (editing == null) "添加" else "保存",
                onClick = {
                    // 内容、金额必填；地区/驾驶员/车牌号/图片可选
                    if (title.isBlank()) {
                        Toast.makeText(context, "请填写内容", Toast.LENGTH_SHORT).show()
                        return@SoftButton
                    }
                    val amt = amountText.toDoubleOrNull()
                    if (amt == null || amt <= 0) {
                        Toast.makeText(context, "请填写有效金额", Toast.LENGTH_SHORT).show()
                        return@SoftButton
                    }
                    // 编辑模式日期未变则保留原时间戳；改了日期取新日期正午
                    val editingDate = editing?.timestamp?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    val ts = when {
                        editing != null && selectedDate == editingDate -> editing.timestamp
                        selectedDate == today -> System.currentTimeMillis()
                        else -> selectedDate
                            .atTime(12, 0)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    }
                    // 文件随快照移交入库流程（异步 commitPending）；新增模式
                    // 草稿使命完成，运行时列表与草稿文件一并清掉
                    val photosToSave = stagedPhotos.toList()
                    stagedPhotos.clear()
                    if (editing == null) ioScope.launch { BillDraftStore.clear(context) }
                    // 驾驶员-车牌记忆（去重、最近使用优先），异步落盘不阻塞保存
                    val rememberedDriver = driver.text.trim()
                    if (rememberedDriver.isNotEmpty()) {
                        val rememberedPlate = plate.trim()
                        ioScope.launch {
                            DriverStore.rememberUsage(context, rememberedDriver, rememberedPlate)
                        }
                    }
                    onSave(
                        BillEntity(
                            id = editing?.id ?: 0,
                            amount = amt,
                            title = title.trim(),
                            category = category.trim(),
                            driver = driver.text.trim(),
                            plate = plate.trim(),
                            region = region.trim(),
                            isIncome = false,
                            timestamp = ts
                        ),
                        photosToSave,
                        removedExisting.toList()
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 底部导航是悬浮胶囊（约 70dp：上下外距 20 + 胶囊本体 50），表单从其
            // 下方穿过——多留这段净空，车牌键盘撑开内容滚到底时按钮不被胶囊挡住
            Spacer(modifier = Modifier.height(84.dp))
        }
    }

    // 照片全屏查看器：点缩略图进入，添加/删除与界面联动（最后一张删除后自动关闭）
    viewerIndex?.let { idx ->
        BillPhotoViewer(
            photoNames = viewerPhotoNames,
            initialPage = idx,
            onClose = { viewerIndex = null },
            onAdd = {
                viewerIndex = null
                showPhotoSourceDialog = true
            },
            onDeleteCurrent = { index ->
                val existingCount = shownExisting.size
                if (index < existingCount) {
                    removedExisting.add(shownExisting[index])
                } else {
                    val stagedIdx = index - existingCount
                    stagedPhotos.getOrNull(stagedIdx)?.let { BillPhotoStore.discard(it) }
                    stagedPhotos.removeAt(stagedIdx)
                }
            }
        )
        LaunchedEffect(viewerPhotoNames.size) {
            if (viewerPhotoNames.isEmpty()) viewerIndex = null
        }
    }
    }

    // Date picker dialog
    if (showDatePicker) {
        val todayMillis = remember {
            LocalDate.now()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis <= todayMillis
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            modifier = Modifier.border(glassBorder(), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface.copy(
                    alpha = if (isDarkTheme()) 0.90f else 0.93f
                )
            ),
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 图片来源弹窗（拍照 / 相册多选；照片为可选项）
    if (showPhotoSourceDialog) {
        GlassCompactDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = "添加账单图片",
            text = { Text("为本账单添加照片，可一次选多张（可选）。") },
            confirmButton = {
                TextButton(onClick = {
                    showPhotoSourceDialog = false
                    val file = BillPhotoStore.newPendingFile(context)
                    pendingCaptureFile = file
                    cameraLauncher.launch(BillPhotoStore.uriFor(context, file))
                }) { Text("拍照") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPhotoSourceDialog = false
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) { Text("从相册选择") }
            }
        )
    }

    // 丢弃草稿二次确认：内容与暂存照片一并删除，无法恢复
    if (showDiscardDialog) {
        GlassCompactDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = "丢弃草稿",
            text = {
                Text("确定丢弃当前草稿吗？已输入的内容与暂存照片将一并删除，无法恢复。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    discardStaged()
                    title = ""
                    amountText = ""
                    driver = TextFieldValue("")
                    plate = ""
                    region = ""
                    category = categoryOptions.firstOrNull().orEmpty()
                    selectedDate = today
                    showDraftBanner = false
                    ioScope.launch { BillDraftStore.clear(context) }
                }) { Text("丢弃") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("取消") }
            }
        )
    }
}

/** 车牌号输入行：仿实体车牌的展示框，点击展开/收起自绘键盘，长按粘贴剪贴板内容 */
@Composable
private fun PlateField(
    plate: String,
    expanded: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "车牌号",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        // 仿车牌展示框：首字符绿字、其余白字（蓝底牌照风格）
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .border(glassBorder(), RoundedCornerShape(6.dp))
                .background(Color(0xFF17408B))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (plate.isEmpty()) {
                Text(
                    text = "点击输入 · 长按粘贴",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            } else {
                Text(
                    text = plate.take(1),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB7F5C4),
                    modifier = Modifier.padding(end = 2.dp)
                )
                Text(
                    text = plate.drop(1),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (expanded) "收起" else "键盘",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 自绘车牌键盘：车牌为空自动显示省份简称面板，非空显示字母/数字面板；
 * 可手动切换。全程不调系统输入法。
 */
@Composable
private fun PlateKeyboard(
    plate: String,
    onChar: (String) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit
) {
    // null = 自动（空牌选省份，否则选序号）；点切换键后手动锁定
    var manualProvinceMode by remember { mutableStateOf<Boolean?>(null) }
    val pickingProvince = manualProvinceMode ?: plate.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(glassBorder(), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = if (pickingProvince) "选择省份简称" else "选择序号（字母/数字）",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        // 键位网格：省份面板 8 列均分；序号面板按固定行布局（字母排满后空三格，末行平分整行）
        val rows: List<List<String>> =
            if (pickingProvince) PLATE_PROVINCES.chunked(8) else PLATE_KEY_ROWS
        rows.forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowKeys.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(glassBorder(), RoundedCornerShape(6.dp))
                                .clickable { onChar(key) }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
        // 底部操作行：面板切换 + 删除 + 完成
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeyboardActionKey(
                text = if (pickingProvince) "字母" else "省份",
                modifier = Modifier.weight(1f)
            ) {
                manualProvinceMode = !pickingProvince
            }
            KeyboardActionKey(
                text = "删除",
                modifier = Modifier.weight(1f)
            ) { onDelete() }
            KeyboardActionKey(
                text = "完成",
                modifier = Modifier.weight(1f)
            ) { onDone() }
        }
    }
}

/** 键盘底部操作键 */
@Composable
private fun KeyboardActionKey(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 账单图片行：已有照片（可查看/移除）+ 新增暂存照片（可查看/移除）+ 添加入口；过多时横向滚动 */
@Composable
private fun StagedPhotosRow(
    existingPhotos: List<String>,
    photos: SnapshotStateList<File>,
    onAddClick: () -> Unit,
    onRemove: (File) -> Unit,
    onRemoveExisting: (String) -> Unit,
    onPhotoClick: (Int) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 已有照片：点击全屏查看，× 移除（保存时生效）
            existingPhotos.forEachIndexed { idx, name ->
                var thumb by remember(name) { mutableStateOf<android.graphics.Bitmap?>(null) }
                LaunchedEffect(name) {
                    thumb = BillPhotoStore.loadThumbnail(context, name, 120)
                }
                Box(modifier = Modifier.size(56.dp)) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onPhotoClick(idx) }
                    ) {
                        val bmp = thumb
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "已有照片",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    // 移除角标（保存时才真正删除）
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .clickable { onRemoveExisting(name) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "移除",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
            photos.forEachIndexed { idx, file ->
                var thumb by remember(file.absolutePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
                LaunchedEffect(file.absolutePath) {
                    thumb = BillPhotoStore.loadThumbnail(context, file.name, 120)
                }
                Box(modifier = Modifier.size(56.dp)) {
                    val bmp = thumb
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "暂存照片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPhotoClick(existingPhotos.size + idx) }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                    // 移除角标（关闭图标按几何中心摆放，文字 × 字形有偏移）
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .clickable { onRemove(file) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "移除",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
            // 添加图片入口（可选）
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(glassBorder(), RoundedCornerShape(8.dp))
                    .clickable(onClick = onAddClick),
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "图片",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

}
