@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.zhaojin.reimbursement.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.zhaojin.reimbursement.data.BillEntity
import com.zhaojin.reimbursement.ui.components.GlassCompactDialog
import com.zhaojin.reimbursement.utils.BillPhotoStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

/** 车牌省份简称（含「使」馆牌） */
private val PLATE_PROVINCES =
    listOf("京", "津", "沪", "渝", "冀", "豫", "云", "辽", "黑", "湘", "皖", "鲁", "新", "苏", "浙",
        "赣", "鄂", "桂", "甘", "晋", "蒙", "陕", "吉", "闽", "贵", "粤", "青", "藏", "川", "宁", "琼", "使")

/** 车牌序号字符：字母（不含易混淆的 I/O）+ 数字 + 常见后缀 */
private val PLATE_CHARS =
    ('A'..'Z').filter { it != 'I' && it != 'O' }.map { it.toString() } +
        ('0'..'9').map { it.toString() } + listOf("学", "挂", "警", "港", "澳")

/** 车牌总长上限：省份简称 1 位 + 序号最多 7 位 */
private const val PLATE_MAX_LEN = 8

/**
 * 添加账单独立页面。五行：日期 → 驾驶员 → 车牌号（自绘车牌键盘，
 * 不调系统输入法）→ 内容 → 金额。顶栏返回即取消，键盘/面板弹出时
 * 表单可滚动不被遮挡。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBillScreen(
    onBack: () -> Unit,
    onAdd: (BillEntity, List<File>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var driver by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var showPlateBoard by remember { mutableStateOf(false) }
    // 暂存照片（pending 文件，保存账单时统一转正压缩入库；可选）
    val stagedPhotos = remember { mutableStateListOf<File>() }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf(today) }
    var showDatePicker by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val ioScope = remember { CoroutineScope(Dispatchers.IO) }

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

    BackHandler(enabled = true) {
        discardStaged()
        onBack()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "添加账单",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 第 1 行：日期
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
                    fontWeight = FontWeight.Medium,
                    color = if (selectedDate != today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 第 2 行：驾驶员
            TextField(
                value = driver,
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                onValueChange = { driver = it },
                label = { Text("驾驶员") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 第 3 行：车牌号（点击弹出自绘车牌键盘，不调系统输入法）
            PlateField(
                plate = plate,
                expanded = showPlateBoard,
                onClick = {
                    showPlateBoard = !showPlateBoard
                    if (showPlateBoard) keyboard?.hide()
                }
            )

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

            // 第 4 行：内容（原「标题」）
            TextField(
                value = title,
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                onValueChange = { title = it },
                label = { Text("内容") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 第 5 行：金额
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
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                prefix = { Text("¥") },
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 第 6 行：账单图片（可选，保存时随账单一并入库）
            StagedPhotosRow(
                photos = stagedPhotos,
                onAddClick = { showPhotoSourceDialog = true },
                onRemove = { file ->
                    BillPhotoStore.discard(file)
                    stagedPhotos.remove(file)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            SoftButton(
                text = "添加",
                onClick = {
                    // 所有字段均可留空（金额留空按 0 记）；仅当填了非法金额时提示
                    val amt = if (amountText.isBlank()) 0.0 else amountText.toDoubleOrNull()
                    if (amt == null) {
                        Toast.makeText(context, "请填写有效金额", Toast.LENGTH_SHORT).show()
                        return@SoftButton
                    }
                    val ts = if (selectedDate == today) {
                        System.currentTimeMillis()
                    } else {
                        selectedDate
                            .atTime(12, 0)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    }
                    onAdd(
                        BillEntity(
                            amount = amt,
                            title = title.trim(),
                            driver = driver.trim(),
                            plate = plate.trim(),
                            isIncome = false,
                            timestamp = ts
                        ),
                        stagedPhotos.toList()
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
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
}

/** 车牌号输入行：仿实体车牌的展示框，点击展开/收起自绘键盘 */
@Composable
private fun PlateField(
    plate: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
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
                    text = "点击输入",
                    fontSize = 14.sp,
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
    val keys = if (pickingProvince) PLATE_PROVINCES else PLATE_CHARS

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
        keys.chunked(if (pickingProvince) 8 else 9).forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowKeys.forEach { key ->
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

/** 暂存照片行：已选小图（可移除）+ 添加入口；图片为可选项 */
@Composable
private fun StagedPhotosRow(
    photos: SnapshotStateList<File>,
    onAddClick: () -> Unit,
    onRemove: (File) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        photos.forEach { file ->
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
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                // 移除角标
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .clickable { onRemove(file) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "图片",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

}
