package com.zhaojin.reimbursement.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import com.zhaojin.reimbursement.utils.BillPhotoStore

/** 查看器解码像素预算：4M 像素（ARGB_8888 下 ≈16MB），兼顾清晰与内存 */
private const val VIEWER_MAX_PIXELS = 4_000_000L

/**
 * 账单图片全屏查看器（多照片）：
 * - 左右翻页浏览全部照片，右上角页码「2/3」（仅多图时显示）
 * - 单击不响应翻页冲突；双击在 1x/2.5x 间切换，放大后可拖动/双指缩放
 * - 底部操作：添加图片（继续拍照/选相册）/ 删除本张（需确认）
 */
@Composable
fun BillPhotoViewer(
    photoNames: List<String>,
    onClose: () -> Unit,
    onAdd: () -> Unit,
    onDeleteCurrent: (Int) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { photoNames.size })

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
                    ViewerPhotoPage(photoName = photoNames[page])
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

/** 单页照片：双击缩放，放大后支持双指缩放/拖动；1x 时不拦截翻页手势 */
@Composable
private fun ViewerPhotoPage(photoName: String) {
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

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "账单原图",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
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
                    .then(
                        if (zoomed) {
                            Modifier.pointerInput(photoName) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    offset = if (scale > 1f) offset + pan else Offset.Zero
                                }
                            }
                        } else Modifier
                    )
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

/** 底部操作按钮：图标 + 文字，白字半透明黑底胶囊（无涟漪，与全局观感一致） */
@Composable
private fun ViewerAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}
