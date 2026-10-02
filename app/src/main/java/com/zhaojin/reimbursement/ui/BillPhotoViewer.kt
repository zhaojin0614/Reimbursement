package com.zhaojin.reimbursement.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zhaojin.reimbursement.ui.components.GlassCompactDialog
import com.zhaojin.reimbursement.ui.components.glassBorder
import com.zhaojin.reimbursement.utils.BillPhotoStore
import kotlinx.coroutines.launch

/** 查看器解码像素预算：4M 像素（ARGB_8888 下 ≈16MB），兼顾清晰与内存 */
private const val VIEWER_MAX_PIXELS = 4_000_000L

/** 缩略图条上的小图边长 */
private val THUMB_SIZE = 48.dp

/**
 * 账单图片全屏查看器（多照片），对齐主流图片查看器交互：
 * - 左右翻页浏览全部照片，顶部页码「2/3」
 * - 双击在 1x/2.5x 间切换；**双指捏合任意时刻可缩放**（1x 直接捏合放大），
 *   放大后单指拖动平移（视野钳制在图片范围内），缩回 1x 恢复翻页
 * - 底部**缩略图条**：小图横排，当前张高亮描边并自动滚动到可视区中央，
 *   点缩略图直接跳转（放大时隐藏，避免与拖动手势冲突）
 * - 底部操作：添加图片（继续拍照/选相册）/ 删除本张（需确认）
 */
@Composable
fun BillPhotoViewer(
    photoNames: List<String>,
    initialPage: Int = 0,
    onClose: () -> Unit,
    onAdd: () -> Unit,
    onDeleteCurrent: (Int) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (photoNames.size - 1).coerceAtLeast(0)),
        pageCount = { photoNames.size }
    )
    val thumbListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 每页的放大状态（key=页码），用于决定缩略图条是否隐藏
    val zoomedPages = remember { mutableStateMapOf<Int, Boolean>() }

    BackHandler { onClose() }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xF2000000.toInt())) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    ViewerPhotoPage(
                        photoName = photoNames[page],
                        onZoomChange = { zoomed -> zoomedPages[page] = zoomed }
                    )
                }

                // 页码指示（多图时显示）
                if (photoNames.size > 1) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${photoNames.size}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                // 关闭按钮（半透明胶囊，左上角）
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                        .clickable { onClose() }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 缩略图条（多图且当前页未放大时显示；微信/相册式快速跳转）
                if (photoNames.size > 1 && zoomedPages[pagerState.currentPage] != true) {
                    ThumbnailStrip(
                        photoNames = photoNames,
                        pagerState = pagerState,
                        listState = thumbListState,
                        onSelect = { index ->
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 92.dp)
                    )
                }

                // 底部操作行
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    ViewerAction(icon = Icons.Default.AddAPhoto, label = "添加图片", onClick = onAdd)
                    ViewerAction(
                        icon = Icons.Default.Delete,
                        label = "删除本张",
                        onClick = { confirmDelete = true }
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        GlassCompactDialog(
            onDismissRequest = { confirmDelete = false },
            title = "删除图片",
            text = { Text("确定删除当前这张图片吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteCurrent(pagerState.currentPage)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

/** 单页照片：双击缩放 + 双指捏合缩放（1x 亦可），放大后单指平移；1x 单指留给翻页 */
@Composable
private fun ViewerPhotoPage(
    photoName: String,
    onZoomChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val maxDimPx = with(LocalDensity.current) {
        maxOf(configuration.screenWidthDp.dp, configuration.screenHeightDp.dp).roundToPx()
    }
    val bitmap by produceState<Bitmap?>(
        initialValue = null, key1 = photoName, key2 = maxDimPx
    ) {
        value = BillPhotoStore.loadForView(context, photoName, VIEWER_MAX_PIXELS)
    }

    var scale by remember(photoName) { mutableFloatStateOf(1f) }
    var offset by remember(photoName) { mutableStateOf(Offset.Zero) }
    val zoomed = scale > 1f

    LaunchedEffect(zoomed) { onZoomChange(zoomed) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "账单原图",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(photoName) {
                        // 双击切换缩放（1x 时不拦截手势，保证左右翻页）
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(photoName) {
                        // 双指捏合任意时刻可缩放；放大后单指拖动平移；
                        // 1x 单指不消费（翻页手势照常）。平移钳制在图片视野内。
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var multiTouch = false
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                when {
                                    pressed >= 2 -> {
                                        multiTouch = true
                                        val newScale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                        scale = newScale
                                        offset = if (newScale > 1f) {
                                            val pan = event.calculatePan()
                                            clampOffset(
                                                offset + pan, newScale, size.width, size.height
                                            )
                                        } else {
                                            Offset.Zero
                                        }
                                        event.changes.forEach {
                                            if (it.positionChanged()) it.consume()
                                        }
                                    }
                                    pressed == 1 && (multiTouch || scale > 1f) -> {
                                        // 缩放中/已放大：单指拖动平移
                                        val pan = event.calculatePan()
                                        offset = clampOffset(offset + pan, scale, size.width, size.height)
                                        event.changes.forEach {
                                            if (it.positionChanged()) it.consume()
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        } else {
            // 解码中/失败：居中转圈
            CircularProgressIndicator(color = Color.White)
        }
    }
}

/** 平移钳制：放大 s 倍时位移不超过 (s-1)/2 × 视口尺寸，保证图片不出视野 */
private fun clampOffset(offset: Offset, scale: Float, width: Int, height: Int): Offset {
    val maxX = (scale - 1f) * width / 2f
    val maxY = (scale - 1f) * height / 2f
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

/**
 * 底部缩略图条：横排小图，当前张主色描边高亮，其余半透明；
 * 点缩略图跳转对应页；当前页变化时自动滚动使其居中可见。
 */
@Composable
private fun ThumbnailStrip(
    photoNames: List<String>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbPx = with(LocalDensity.current) { THUMB_SIZE.roundToPx() }

    // 当前页变化：把对应缩略图滚到条中央
    LaunchedEffect(pagerState.currentPage, photoNames.size) {
        if (photoNames.isEmpty()) return@LaunchedEffect
        val index = pagerState.currentPage.coerceIn(0, photoNames.size - 1)
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (info != null) {
            val viewportCenter = (listState.layoutInfo.viewportStartOffset +
                listState.layoutInfo.viewportEndOffset) / 2
            val itemCenter = info.offset + info.size / 2
            val delta = itemCenter - viewportCenter
            if (kotlin.math.abs(delta) > 8) listState.animateScrollBy(delta.toFloat())
        } else {
            listState.animateScrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(photoNames) { index, name ->
            val selected = index == pagerState.currentPage
            Box(
                modifier = Modifier
                    .size(THUMB_SIZE)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(index) }
            ) {
                val thumb by produceState<Bitmap?>(
                    initialValue = null, key1 = name, key2 = thumbPx
                ) {
                    value = BillPhotoStore.loadThumbnail(context, name, thumbPx)
                }
                val bmp = thumb
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "第${index + 1}张缩略图",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.12f)))
                }
            }
        }
    }
}

/**
 * 底部操作按钮：主题色图标 + 文字（随主题切换），半透明表面色胶囊 + 描边
 * ——胶囊自带底色，图片无论纯白还是深色都清晰可辨。
 */
@Composable
private fun ViewerAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(22.dp))
            .border(glassBorder(), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}
