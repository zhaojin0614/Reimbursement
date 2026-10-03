@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.zhaojin.reimbursement.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhaojin.reimbursement.R
import com.zhaojin.reimbursement.data.BillEntity
import com.zhaojin.reimbursement.data.BillPhotoEntity
import com.zhaojin.reimbursement.ui.components.GlassCompactDialog
import com.zhaojin.reimbursement.ui.components.BillDatePickerDialog
import com.zhaojin.reimbursement.ui.components.SoftCard
import com.zhaojin.reimbursement.ui.components.SoftFab
import com.zhaojin.reimbursement.ui.components.SoftGradientCard
import com.zhaojin.reimbursement.ui.components.SwipeableItem
import com.zhaojin.reimbursement.ui.components.SwipeableItemCoordinator
import com.zhaojin.reimbursement.ui.components.glassBorder
import com.zhaojin.reimbursement.ui.components.isDarkTheme
import com.zhaojin.reimbursement.data.CategoryStore
import com.zhaojin.reimbursement.ui.theme.AccentColorRepository
import com.zhaojin.reimbursement.ui.theme.ComponentGap
import com.zhaojin.reimbursement.ui.theme.ExpenseRed
import com.zhaojin.reimbursement.ui.theme.GradientExpenseEnd
import com.zhaojin.reimbursement.ui.theme.GradientExpenseStart
import com.zhaojin.reimbursement.utils.AppLogger
import com.zhaojin.reimbursement.utils.BillPhotoStore
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** 分享的 xlsx MIME：单个表格文件（照片内嵌在工作簿里） */
private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** 相册一次可选照片上限（系统照片选择器多选） */
private const val GALLERY_MAX_PICK = 9

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillScreen(
    modifier: Modifier = Modifier,
    viewModel: BillViewModel = viewModel()
) {
    val bills by viewModel.bills.collectAsState()
    val photosByBill by viewModel.photosByBill.collectAsState()
    val totalExpense by viewModel.totalExpense.collectAsState()
    val monthExpense by viewModel.monthExpense.collectAsState()
    val expenseCount by viewModel.expenseCount.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var billToDelete by remember { mutableStateOf<BillEntity?>(null) }
    var showAddScreen by remember { mutableStateOf(false) }
    var billToEdit by remember { mutableStateOf<BillEntity?>(null) }

    // 设置页
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 分类筛选胶囊的选项（null = 全部）；关闭设置后重载，增删改即时生效
    val categoryOptions = remember(showSettings) {
        com.zhaojin.reimbursement.data.CategoryStore.load(context)
    }
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    // 账单搜索
    var showSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    // 跳转到指定日期：锚点窗口模式（查那天附近两页，滚动两端按需扩页）
    val jumpAnchor by viewModel.jumpAnchor.collectAsState()
    val windowReady by viewModel.windowReady.collectAsState()
    var showJumpPicker by remember { mutableStateOf(false) }
    // 跳转后首次窗口就绪时定位一次（防扩页导致的 size 变化反复拉回锚点）
    var pendingJumpScroll by remember { mutableStateOf(false) }
    // 有账单的日期集合（epochDay）：打开选择器前加载，空日期置灰不可选
    var billDays by remember { mutableStateOf<Set<Long>?>(null) }

    // ── 账单图片（多张）：拍照 / 相册 / 查看器状态 ──────────────────────
    // 无图图标点击 → sourcePickerFor（选择来源）；有图图标点击 → viewerBill
    // sourcePickerFor = (billId, title)，查看器内「添加图片」复用同一弹窗
    var sourcePickerFor by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var viewerBill by remember { mutableStateOf<Pair<Long, String>?>(null) }
    // 进行中的拍照任务（账单 + pending 文件），回调里按它落库/清理
    var pendingCaptureBillId by remember { mutableStateOf<Long?>(null) }
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    // 相册选择的目标账单（添加或追加共用一个选择器）
    var galleryBillId by remember { mutableStateOf<Long?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val file = pendingCaptureFile
        val billId = pendingCaptureBillId
        pendingCaptureFile = null
        pendingCaptureBillId = null
        if (file != null && billId != null) {
            if (ok) {
                scope.launch {
                    // pending 转正为正式文件并入库；转正失败则丢弃
                    val name = BillPhotoStore.commitPending(context, file)
                    if (name != null) {
                        viewModel.addBillPhoto(billId, name, "拍照")
                    } else {
                        BillPhotoStore.discard(file)
                        AppLogger.log("图片", "拍照转正失败已丢弃 账单#$billId")
                    }
                }
            } else {
                BillPhotoStore.discard(file)
                AppLogger.log("图片", "拍照取消 账单#$billId")
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(GALLERY_MAX_PICK)
    ) { uris ->
        val billId = galleryBillId
        galleryBillId = null
        if (uris.isNotEmpty() && billId != null) {
            AppLogger.log("图片", "相册选图 ${uris.size} 张 账单#$billId")
            scope.launch {
                // 相册授权是一次性的：逐张复制进私有目录持久保存
                uris.forEach { uri ->
                    val name = BillPhotoStore.importFromUri(context, uri)
                    if (name != null) viewModel.addBillPhoto(billId, name, "相册")
                }
            }
        }
    }

    fun startCameraCapture(billId: Long) {
        val file = BillPhotoStore.newPendingFile(context)
        pendingCaptureBillId = billId
        pendingCaptureFile = file
        cameraLauncher.launch(BillPhotoStore.uriFor(context, file))
    }

    fun launchGalleryPick(billId: Long) {
        galleryBillId = billId
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // ── 导出账单（按日时间段，照片内嵌 xlsx，生成后弹系统分享面板）────────
    val backupBusy by viewModel.backupBusy.collectAsState()
    var showExportDialog by remember { mutableStateOf(false) }
    // 弹窗内的范围草稿：每次点导出时重置为 最早账单日 ~ 今天
    var exportStart by remember { mutableStateOf(java.time.LocalDate.now()) }
    var exportEnd by remember { mutableStateOf(java.time.LocalDate.now()) }
    var exportPicking by remember { mutableStateOf<String?>(null) } // "start" / "end"
    // 导出分类多选草稿：空 = 全部分类
    val exportCategories = remember { mutableStateListOf<String>() }

    fun openExportDialog() {
        scope.launch {
            exportStart = viewModel.earliestBillDate() ?: java.time.LocalDate.now()
            exportEnd = java.time.LocalDate.now()
            exportCategories.clear() // 每次打开重置为 全部分类
            showExportDialog = true
        }
    }
    LaunchedEffect(Unit) {
        viewModel.backupMessage.collect { message ->
            message?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                viewModel.consumeBackupMessage()
            }
        }
    }

    fun launchExport(start: java.time.LocalDate, end: java.time.LocalDate) {
        val zone = ZoneId.systemDefault()
        viewModel.exportBackup(
            start.atStartOfDay(zone).toInstant().toEpochMilli(),
            end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
            exportCategories.toList().ifEmpty { null }
        ) { file ->
            // 生成成功直接弹系统分享面板（微信/文件管理器等均可接收）
            com.zhaojin.reimbursement.utils.FileShare.share(
                context, file, XLSX_MIME, "分享账单表格"
            )
        }
    }

    if (showAddScreen || billToEdit != null) {
        // 添加/修改共用一个界面：billToEdit 非空即编辑模式（预填原值、原位更新）
        AddBillScreen(
            editing = billToEdit,
            existingPhotos = billToEdit?.let { bill ->
                photosByBill[bill.id].orEmpty().map { it.fileName }
            }.orEmpty(),
            // 新增时预选列表页当前筛选的分类（全部则用默认第一个）
            defaultCategory = if (billToEdit == null) selectedCategory else null,
            onBack = {
                showAddScreen = false
                billToEdit = null
            },
            onSave = { bill, stagedPhotos, removedExisting ->
                if (billToEdit != null) {
                    viewModel.updateBill(bill, stagedPhotos, removedExisting)
                } else {
                    viewModel.addBill(bill, stagedPhotos)
                }
                showAddScreen = false
                billToEdit = null
            }
        )
        return
    }

    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    // Pull-down stats panel
    val listState = rememberLazyListState()
    val pullOffset = remember { Animatable(0f) }
    val maxPullOffsetPx = with(LocalDensity.current) { 80.dp.toPx() }

    // Load more when scrolling near bottom
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= totalItems - 2 && totalItems > 0
        }
    }

    // Scroll-to-top visibility（滚过 5 项后出现）
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 5 }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta > 0 && listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0 &&
                    pullOffset.value < maxPullOffsetPx
                ) {
                    scope.launch {
                        pullOffset.snapTo(
                            (pullOffset.value + delta).coerceAtMost(maxPullOffsetPx)
                        )
                    }
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && pullOffset.value > 0f) {
            pullOffset.animateTo(0f, animationSpec = tween(250))
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if ((listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) && pullOffset.value > 0f) {
            pullOffset.animateTo(0f, animationSpec = tween(200))
        }
    }

    // Pressing back during selection mode exits selection, not the app
    BackHandler(enabled = isSelectionMode) {
        viewModel.exitSelectionMode()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier,
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        if (isSelectionMode) {
                            Text(
                                text = "已选择 ${selectedIds.size} 项",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        } else {
                            Text(
                                text = "维修报销",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    actions = {
                        if (isSelectionMode) {
                            TextButton(onClick = {
                                val visibleIds = bills.map { it.id }
                                if (selectedIds.containsAll(visibleIds)) {
                                    viewModel.exitSelectionMode()
                                } else {
                                    viewModel.selectAll(visibleIds)
                                }
                            }) {
                                Text(
                                    text = if (selectedIds.containsAll(bills.map { it.id })) "取消全选" else "全选",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.exportSelected(selectedIds) { file ->
                                        com.zhaojin.reimbursement.utils.FileShare.share(
                                            context, file, XLSX_MIME, "分享选中的账单"
                                        )
                                    }
                                },
                                enabled = !backupBusy && selectedIds.isNotEmpty()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = "导出选中",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { showDeleteSelectedDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "删除选中",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(onClick = { viewModel.exitSelectionMode() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "取消",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                // 先取有账单的日期集合（选择器据此置灰空日期），再弹窗
                                scope.launch {
                                    billDays = viewModel.loadBillDaySet(viewModel.selectedCategory.value)
                                    showJumpPicker = true
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "跳转到日期",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { if (!backupBusy) openExportDialog() },
                                enabled = !backupBusy
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = "导出账单",
                                    tint = if (backupBusy) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                showSearch = !showSearch
                                if (!showSearch) {
                                    searchText = ""
                                    viewModel.setSearchQuery("")
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "搜索账单",
                                    tint = if (showSearch) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { showSettings = true }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "设置",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (!isSelectionMode) {
                    Column(
                        modifier = Modifier.padding(bottom = 88.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        // 返回顶部：滚过 5 项后滑入；跳转锚点模式常驻，
                        // 点击清除锚点回到最新（图标统一为置顶对齐样式）
                        AnimatedVisibility(
                            visible = showScrollToTop || jumpAnchor != null,
                            enter = fadeIn() + slideInVertically { it },
                            exit = fadeOut() + slideOutVertically { it }
                        ) {
                            SoftFab(
                                icon = Icons.Default.VerticalAlignTop,
                                contentDescription = if (jumpAnchor != null) "回到最新"
                                else stringResource(R.string.scroll_to_top),
                                onClick = {
                                    scope.launch {
                                        if (jumpAnchor != null) {
                                            viewModel.clearJumpAnchor()
                                            listState.scrollToItem(index = 0)
                                        } else {
                                            listState.animateScrollToItem(index = 0)
                                        }
                                    }
                                },
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        SoftFab(
                            icon = Icons.Default.Add,
                            contentDescription = "添加账单",
                            onClick = { showAddScreen = true }
                        )
                    }
                }
            }
            ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // 分类筛选胶囊：全部 + 各分类（选中后列表与统计只看该分类）
                CategoryFilterBar(
                    options = categoryOptions,
                    selected = selectedCategory,
                    onSelect = { viewModel.setCategoryFilter(it) }
                )

                // Pull-down stats panel
                BillPullDownStatsPanel(
                    pullOffset = pullOffset.value,
                    maxPullOffset = maxPullOffsetPx,
                    expenseCount = expenseCount,
                    totalExpense = totalExpense
                )

                // 本月维修报销概览
                MonthExpenseSummary(monthExpense = monthExpense)

                Spacer(modifier = Modifier.height(ComponentGap))

                if (showSearch) {
                    // 与日卡片同款液态玻璃面板；用 BasicTextField 自控高度，
                    // M3 TextField 有最小内容高度，强行压矮会裁掉文字
                    SoftCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        BasicTextField(
                            value = searchText,
                            onValueChange = {
                                searchText = it
                                viewModel.setSearchQuery(it)
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (searchText.isEmpty()) {
                                            Text(
                                                text = "搜索内容 / 金额 / 驾驶员 / 车牌 / 地区 / 分类",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (searchText.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                searchText = ""
                                                viewModel.setSearchQuery("")
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "清除搜索",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(ComponentGap))
                }

                // Bill List
                if (bills.isEmpty()) {
                    EmptyBillState()
                } else {
                    val groupedBills = remember(bills) {
                        bills.groupBy {
                            Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                        }.toList().sortedByDescending { it.first }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection),
                        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 0.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(ComponentGap)
                    ) {
                        items(
                            items = groupedBills,
                            key = { "day_${it.first}" }
                        ) { (date, dayBills) ->
                            DayGroupCard(
                                date = date,
                                bills = dayBills,
                                photosByBill = photosByBill,
                                isSelectionMode = isSelectionMode,
                                selectedIds = selectedIds,
                                onBillClick = { bill ->
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(bill.id)
                                    } else {
                                        billToEdit = bill
                                    }
                                },
                                onToggleDaySelection = { ids, checked ->
                                    viewModel.setDaySelection(ids, checked)
                                },
                                onBillLongClick = { bill ->
                                    if (!isSelectionMode) {
                                        viewModel.enterSelectionMode(bill.id)
                                    }
                                },
                                onIconClick = { bill ->
                                    // 图标点击：有图 → 翻页查看全部图片；无图 → 选择图片来源
                                    if (photosByBill[bill.id].isNullOrEmpty()) {
                                        sourcePickerFor = bill.id to bill.title
                                    } else {
                                        viewerBill = bill.id to bill.title
                                    }
                                },
                                onDelete = { bill ->
                                    billToDelete = bill
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }

                    // 跳转定位：锚点窗口首次就绪后定位一次（之后扩页不拉回）。
                    // 窗口本身总是「离目标最近的账单」，这里只负责落位与提示：
                    // 目标当天有账单 → 静默定位；当天没有 → 定位到最近的账单日
                    // 并提示；目标比最新/最早记录还远 → 回到窗口顶（最新页顶/
                    // 最早一批账单）并说明
                    LaunchedEffect(jumpAnchor, pendingJumpScroll, windowReady, bills.size) {
                        // 窗口未就绪时 bills 还是旧数据（如最新一页），定位必错，等待下一次触发
                        if (!pendingJumpScroll || bills.isEmpty() || !windowReady) return@LaunchedEffect
                        val target = jumpAnchor ?: run {
                            pendingJumpScroll = false
                            return@LaunchedEffect
                        }
                        val newest = groupedBills.first().first
                        val oldest = groupedBills.last().first
                        val idx = groupedBills.indexOfFirst { it.first <= target }
                        when {
                            idx >= 0 -> {
                                listState.scrollToItem(idx)
                                val hit = groupedBills[idx].first
                                if (hit != target) {
                                    Toast.makeText(
                                        context, "$target 当天没有账单，已定位到附近的 $hit",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            target > newest -> {
                                listState.scrollToItem(0)
                                Toast.makeText(
                                    context, "$target 尚无账单，已回到最新", Toast.LENGTH_SHORT
                                ).show()
                            }
                            else -> {
                                // 目标早于全库最早记录，窗口即最早的一批账单
                                listState.scrollToItem(0)
                                Toast.makeText(
                                    context, "$target 早于最早记录，已跳到最早的 $oldest",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        pendingJumpScroll = false
                    }

                    // 锚点窗口：滚近列表顶部时向更新方向扩一页（非锚点模式内部忽略）
                    val shouldLoadNewer by remember {
                        derivedStateOf { listState.firstVisibleItemIndex <= 1 }
                    }
                    LaunchedEffect(shouldLoadNewer) {
                        if (shouldLoadNewer) viewModel.loadNewer()
                    }
                }
            }
        }
    }

    // ── 账单图片：来源选择弹窗（无图图标点击 / 查看器「添加图片」共用）──
    sourcePickerFor?.let { (billId, title) ->
        GlassCompactDialog(
            onDismissRequest = { sourcePickerFor = null },
            title = "添加账单图片",
            text = { Text("为「$title」添加图片，图片将显示为账单图标；从相册可一次选多张。") },
            confirmButton = {
                TextButton(onClick = {
                    sourcePickerFor = null
                    startCameraCapture(billId)
                }) { Text("拍照") }
            },
            dismissButton = {
                TextButton(onClick = {
                    sourcePickerFor = null
                    launchGalleryPick(billId)
                }) { Text("从相册选择") }
            }
        )
    }

    // ── 账单图片：全屏翻页查看器 ─────────────────────────────────────────
    viewerBill?.let { (billId, title) ->
        val viewerPhotos = photosByBill[billId].orEmpty()
        // 最后一张被删除后自动关闭查看器
        LaunchedEffect(viewerPhotos.size) {
            if (viewerPhotos.isEmpty()) viewerBill = null
        }
        BillPhotoViewer(
            photoNames = viewerPhotos.map { it.fileName },
            onClose = { viewerBill = null },
            onAdd = { sourcePickerFor = billId to title },
            onDeleteCurrent = { index ->
                viewerPhotos.getOrNull(index)?.let { viewModel.deleteBillPhoto(it) }
            }
        )
    }

    // ── 导出：按日时间段选择 ─────────────────────────────────────────────
    // 跳转日期：可选范围=有账单的日期（年/月随之收敛，未来月份不可见）
    if (showJumpPicker) {
        val billDaySet = billDays.orEmpty()
        val initial = jumpAnchor?.takeIf { billDaySet.contains(it.toEpochDay()) }
            ?: LocalDate.ofEpochDay(billDaySet.maxOrNull() ?: LocalDate.now().toEpochDay())
        BillDatePickerDialog(
            title = "跳转到日期",
            initialDate = initial,
            selectableDays = billDaySet,
            confirmText = "跳转",
            onDismiss = { showJumpPicker = false },
            onConfirm = {
                viewModel.jumpToDate(it)
                pendingJumpScroll = true
                showJumpPicker = false
            }
        )
    }

    if (showExportDialog) {
        val dateFmt = remember { java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd") }
        GlassCompactDialog(
            onDismissRequest = { showExportDialog = false },
            title = "导出账单",
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "选择要导出的时间段（按日，含当天）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ExportDateRow("开始日期", exportStart, dateFmt) { exportPicking = "start" }
                    ExportDateRow("结束日期", exportEnd, dateFmt) { exportPicking = "end" }
                    Text(
                        text = "选择要导出的分类（不选 = 全部分类）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 「全部」胶囊：未选任何分类时即全部
                        val allSelected = exportCategories.isEmpty()
                        ExportCategoryChip(
                            label = "全部",
                            selected = allSelected,
                            onClick = { exportCategories.clear() }
                        )
                        categoryOptions.forEach { option ->
                            val selected = option in exportCategories
                            ExportCategoryChip(
                                label = option,
                                selected = selected,
                                onClick = {
                                    if (selected) exportCategories.remove(option) else exportCategories.add(option)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    launchExport(exportStart, exportEnd)
                }) { Text("导出") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("取消") }
            }
        )
    }

    // ── 导出：日期选择器（开始/结束联动：不允许开始晚于结束）──────────────
    if (exportPicking != null) {
        key(exportPicking) {
            val initial = if (exportPicking == "start") exportStart else exportEnd
            BillDatePickerDialog(
                title = if (exportPicking == "start") "选择开始日期" else "选择结束日期",
                initialDate = initial,
                onDismiss = { exportPicking = null },
                onConfirm = { picked ->
                    // 起止联动：不允许开始晚于结束
                    if (exportPicking == "start") {
                        exportStart = picked
                        if (exportEnd < picked) exportEnd = picked
                    } else {
                        exportEnd = picked
                        if (exportStart > picked) exportStart = picked
                    }
                    exportPicking = null
                }
            )
        }
    }

    // Single item delete confirmation
    if (showDeleteDialog && billToDelete != null) {        GlassCompactDialog(
            onDismissRequest = {
                showDeleteDialog = false
                billToDelete = null
            },
            title = "删除账单",
            text = { Text("确定要删除这条账单记录吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        SwipeableItemCoordinator.reset()
                        billToDelete?.let { viewModel.deleteBill(it.id) }
                        showDeleteDialog = false
                        billToDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    billToDelete = null
                }) {
                    Text("取消")
                }
            }
        )
    }

    // Multi-select delete confirmation
    if (showDeleteSelectedDialog) {
        GlassCompactDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = "删除选中账单",
            text = { Text("确定要删除选中的 ${selectedIds.size} 条账单记录吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected()
                        showDeleteSelectedDialog = false
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun BillPullDownStatsPanel(
    pullOffset: Float,
    maxPullOffset: Float,
    expenseCount: Int,
    totalExpense: Double
) {
    if (pullOffset <= 0f) return

    val progress = (pullOffset / maxPullOffset).coerceIn(0f, 1f)
    val panelHeight = with(LocalDensity.current) { pullOffset.toDp() }
    val contentAlpha = progress.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight),
        contentAlignment = Alignment.Center
    ) {
        if (contentAlpha > 0f) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BillStatItem(
                    value = expenseCount.toString(),
                    label = "维修报销笔数",
                    alpha = contentAlpha
                )
                BillStatItem(
                    value = "¥${String.format("%.2f", totalExpense)}",
                    label = "累计维修报销",
                    alpha = contentAlpha,
                    valueColor = ExpenseRed.copy(alpha = contentAlpha)
                )
            }
        }
    }
}

/** 本月维修报销概览：单卡片满宽 */
@Composable
fun MonthExpenseSummary(monthExpense: Double) {
    SoftGradientCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = 96.dp),
        brush = Brush.linearGradient(
            colors = listOf(GradientExpenseStart, GradientExpenseEnd)
        ),
        shape = RoundedCornerShape(16.dp),
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MonetizationOn,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "本月维修报销",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "¥${String.format("%.2f", monthExpense)}",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BillStatItem(
    value: String,
    label: String,
    alpha: Float,
    valueColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
        )
    }
}

@Composable
fun DayGroupCard(
    date: LocalDate,
    bills: List<BillEntity>,
    photosByBill: Map<Long, List<BillPhotoEntity>>,
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    onBillClick: (BillEntity) -> Unit,
    onBillLongClick: (BillEntity) -> Unit,
    onIconClick: (BillEntity) -> Unit = {},
    onDelete: (BillEntity) -> Unit,
    onToggleDaySelection: (List<Long>, Boolean) -> Unit = { _, _ -> }
) {
    val dayTotal = remember(bills) { bills.sumOf { it.amount } }

    SoftCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            // Header: date + daily summary（多选模式带当天全选勾选框）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDayHeader(date),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "¥${String.format("%.2f", dayTotal)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isSelectionMode) {
                        val dayIds = bills.map { it.id }
                        val dayAllSelected = dayIds.isNotEmpty() && dayIds.all { it in selectedIds }
                        Checkbox(
                            checked = dayAllSelected,
                            onCheckedChange = { checked -> onToggleDaySelection(dayIds, checked) },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            Spacer(modifier = Modifier.height(4.dp))

            // Bills inside day card
            bills.forEachIndexed { index, bill ->
                // key 让卡片组合状态（滑动偏移）跟随账单本身：
                // 新账单插入列表头部时若不 key，Compose 会按位置复用旧槽位，
                // 下面的卡片会继承上一条账单的内部状态
                key(bill.id) {
                    SwipeableItem(
                        isSelectionMode = isSelectionMode,
                        onDelete = { onDelete(bill) },
                        itemKey = bill.id
                    ) {
                        BillCard(
                            bill = bill,
                            photoNames = photosByBill[bill.id].orEmpty().map { it.fileName },
                            isSelected = selectedIds.contains(bill.id),
                            isSelectionMode = isSelectionMode,
                            onClick = { onBillClick(bill) },
                            onLongClick = { onBillLongClick(bill) },
                            onIconClick = { onIconClick(bill) }
                        )
                    }
                }
                if (index < bills.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 50.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyBillState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无账单",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(ComponentGap))
            Text(
                text = "点击右下角 ＋ 记一笔维修报销",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }
    }
}

private fun formatDayHeader(date: LocalDate): String {
    val now = LocalDate.now()
    val weekDays = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
    val dayOfWeek = weekDays[date.dayOfWeek.value - 1]
    val dayStr = String.format("%d.%02d.%02d", date.year, date.monthValue, date.dayOfMonth)
    return when (date) {
        now -> "$dayStr 今天"
        now.minusDays(1) -> "$dayStr 昨天"
        else -> "$dayStr $dayOfWeek"
    }
}

/** 导出行组件：日期选择行（标签 + 右侧当前日期，点击弹出选择器） */
@Composable
private fun ExportDateRow(
    label: String,
    date: LocalDate,
    fmt: java.time.format.DateTimeFormatter,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = date.format(fmt),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}


/**
 * 分类筛选：每个分类一枚独立胶囊按钮（含「全部」），横向可滑动——
 * 分类多时不会挤在一屏里。选中项主题色渐变填充，未选中项玻璃描边。
 */
@Composable
private fun CategoryFilterBar(
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    val accent = AccentColorRepository.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val items: List<String?> = listOf<String?>(null) + options
        items.forEach { option ->
            val isSelected = option == selected || (option == null && selected == null)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) Brush.horizontalGradient(listOf(accent.primary, accent.gradientEnd))
                        else SolidColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    )
                    .border(
                        if (isSelected) BorderStroke(0.dp, Color.Transparent) else glassBorder(),
                        RoundedCornerShape(50)
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option ?: "全部",
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}


/** 导出弹窗的分类胶囊：选中为主题色渐变填充（随主题切换） */
@Composable
private fun ExportCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = AccentColorRepository.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) Brush.horizontalGradient(listOf(accent.primary, accent.gradientEnd))
                else SolidColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}