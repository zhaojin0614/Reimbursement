@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.zhaojin.reimbursement.ui

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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
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
import com.zhaojin.reimbursement.ui.components.PillToggle
import com.zhaojin.reimbursement.ui.components.SoftCard
import com.zhaojin.reimbursement.ui.components.SoftFab
import com.zhaojin.reimbursement.ui.components.SoftGradientCard
import com.zhaojin.reimbursement.ui.components.SwipeableItem
import com.zhaojin.reimbursement.ui.components.SwipeableItemCoordinator
import com.zhaojin.reimbursement.ui.theme.ComponentGap
import com.zhaojin.reimbursement.ui.theme.ExpenseRed
import com.zhaojin.reimbursement.ui.theme.GradientExpenseEnd
import com.zhaojin.reimbursement.ui.theme.GradientExpenseStart
import com.zhaojin.reimbursement.ui.theme.GradientIncomeEnd
import com.zhaojin.reimbursement.ui.theme.GradientIncomeStart
import com.zhaojin.reimbursement.ui.theme.IncomeGreen
import com.zhaojin.reimbursement.utils.BillPhotoStore
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillScreen(
    modifier: Modifier = Modifier,
    viewModel: BillViewModel = viewModel()
) {
    val bills by viewModel.bills.collectAsState()
    val photosByBill by viewModel.photosByBill.collectAsState()
    val totalExpense by viewModel.totalExpense.collectAsState()
    val totalIncome by viewModel.totalIncome.collectAsState()
    val monthExpense by viewModel.monthExpense.collectAsState()
    val monthIncome by viewModel.monthIncome.collectAsState()
    val expenseCount by viewModel.expenseCount.collectAsState()
    val incomeCount by viewModel.incomeCount.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var billToDelete by remember { mutableStateOf<BillEntity?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) } // null/全部, 支出, 收入
    var showAddScreen by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var billToEdit by remember { mutableStateOf<BillEntity?>(null) }

    // 设置页
    var showSettings by remember { mutableStateOf(false) }
    // 账单搜索
    var showSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                    if (name != null) viewModel.addBillPhoto(billId, name)
                    else BillPhotoStore.discard(file)
                }
            } else {
                BillPhotoStore.discard(file)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val billId = galleryBillId
        galleryBillId = null
        if (uri != null && billId != null) {
            scope.launch {
                // 相册授权是一次性的：复制进私有目录持久保存
                val name = BillPhotoStore.importFromUri(context, uri)
                if (name != null) viewModel.addBillPhoto(billId, name)
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

    if (showAddScreen) {
        AddBillScreen(
            onBack = { showAddScreen = false },
            onAdd = { bill ->
                viewModel.addBill(bill)
                showAddScreen = false
            }
        )
        return
    }

    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    // 类型筛选下沉到 ViewModel 直接查库（类型变化即重查）
    LaunchedEffect(selectedType) {
        viewModel.setTypeFilter(selectedType)
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
                                text = "记账",
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
                        // 返回顶部：滚过 5 项后滑入
                        AnimatedVisibility(
                            visible = showScrollToTop,
                            enter = fadeIn() + slideInVertically { it },
                            exit = fadeOut() + slideOutVertically { it }
                        ) {
                            SoftFab(
                                icon = Icons.Default.ArrowUpward,
                                contentDescription = stringResource(R.string.scroll_to_top),
                                onClick = {
                                    scope.launch {
                                        listState.animateScrollToItem(index = 0)
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
                // Pull-down stats panel
                BillPullDownStatsPanel(
                    pullOffset = pullOffset.value,
                    maxPullOffset = maxPullOffsetPx,
                    expenseCount = expenseCount,
                    incomeCount = incomeCount,
                    totalExpense = totalExpense,
                    totalIncome = totalIncome
                )

                // Income / Expense Summary Cards
                IncomeExpenseSummary(
                    monthExpense = monthExpense,
                    monthIncome = monthIncome
                )

                Spacer(modifier = Modifier.height(ComponentGap))

                // Type Filter (全部/支出/收入) — equal-width pill toggle
                PillToggle(
                    options = listOf(
                        "全部" to MaterialTheme.colorScheme.primary,
                        "支出" to ExpenseRed,
                        "收入" to IncomeGreen
                    ),
                    selectedIndex = when (selectedType) {
                        "支出" -> 1
                        "收入" -> 2
                        else -> 0
                    },
                    onSelect = { index ->
                        selectedType = when (index) {
                            1 -> "支出"
                            2 -> "收入"
                            else -> null
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

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
                                                text = "搜索标题 / 金额",
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
                                        showEditDialog = true
                                    }
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
                }
            }
        }
    }

    // Edit bill dialog：标题/金额一个界面改完
    if (showEditDialog && billToEdit != null) {
        val bill = billToEdit!!
        var editTitle by remember(bill.id) { mutableStateOf(bill.title) }
        var editAmount by remember(bill.id) {
            mutableStateOf(
                if (bill.amount % 1.0 == 0.0) bill.amount.toLong().toString()
                else bill.amount.toString()
            )
        }

        val titleChanged = editTitle.isNotBlank() && editTitle.trim() != bill.title
        val amountChanged = (editAmount.toDoubleOrNull() ?: bill.amount) != bill.amount
        val hasChanges = titleChanged || amountChanged

        GlassCompactDialog(
            onDismissRequest = {
                showEditDialog = false
                billToEdit = null
            },
            title = if (bill.isIncome) "编辑收入账单" else "编辑支出账单",
            text = {
                Column {
                    TextField(
                        value = editTitle,
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                        onValueChange = { editTitle = it },
                        label = { Text("标题") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = editAmount,
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                        onValueChange = { newValue ->
                            // 仅允许数字与小数点
                            if (newValue.all { it.isDigit() || it == '.' }) {
                                editAmount = newValue
                            }
                        },
                        label = { Text("金额") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        prefix = { Text("¥") },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (titleChanged) viewModel.updateTitle(bill.id, editTitle.trim())
                        if (amountChanged) {
                            editAmount.toDoubleOrNull()?.let { viewModel.updateAmount(bill.id, it) }
                        }
                        showEditDialog = false
                        billToEdit = null
                    },
                    enabled = hasChanges
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEditDialog = false
                    billToEdit = null
                }) {
                    Text("取消")
                }
            }
        )
    }

    // ── 账单图片：来源选择弹窗（无图图标点击 / 查看器「添加图片」共用）──
    sourcePickerFor?.let { (billId, title) ->
        GlassCompactDialog(
            onDismissRequest = { sourcePickerFor = null },
            title = "添加账单图片",
            text = { Text("为「$title」添加图片，图片将显示为账单图标，可添加多张。") },
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
    incomeCount: Int,
    totalExpense: Double,
    totalIncome: Double
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
                    label = "支出笔数",
                    alpha = contentAlpha
                )
                BillStatItem(
                    value = "¥${String.format("%.2f", totalExpense)}",
                    label = "累计支出",
                    alpha = contentAlpha,
                    valueColor = ExpenseRed.copy(alpha = contentAlpha)
                )
                BillStatItem(
                    value = "¥${String.format("%.2f", totalIncome)}",
                    label = "累计收入",
                    alpha = contentAlpha,
                    valueColor = IncomeGreen.copy(alpha = contentAlpha)
                )
                BillStatItem(
                    value = incomeCount.toString(),
                    label = "收入笔数",
                    alpha = contentAlpha
                )
            }
        }
    }
}

@Composable
fun IncomeExpenseSummary(
    monthExpense: Double,
    monthIncome: Double
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Month Expense
            SoftGradientCard(
                modifier = Modifier.weight(1f).heightIn(min = 96.dp),
                brush = Brush.linearGradient(
                    colors = listOf(GradientExpenseStart, GradientExpenseEnd)
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = 14.dp
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MonetizationOn,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "本月支出",
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

            // Month Income
            SoftGradientCard(
                modifier = Modifier.weight(1f).heightIn(min = 96.dp),
                brush = Brush.linearGradient(
                    colors = listOf(GradientIncomeStart, GradientIncomeEnd)
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = 14.dp
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "本月收入",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "¥${String.format("%.2f", monthIncome)}",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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
    onDelete: (BillEntity) -> Unit
) {
    val dayExpense = remember(bills) { bills.filter { !it.isIncome }.sumOf { it.amount } }
    val dayIncome = remember(bills) { bills.filter { it.isIncome }.sumOf { it.amount } }

    SoftCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            // Header: date + daily summary
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
                Text(
                    text = "支${String.format("%.2f", dayExpense)} 收${String.format("%.2f", dayIncome)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                text = "点击右下角 ＋ 记一笔维修、报销或日常收支",
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
