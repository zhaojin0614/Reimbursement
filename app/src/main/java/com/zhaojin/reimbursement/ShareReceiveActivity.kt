package com.zhaojin.reimbursement

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.zhaojin.reimbursement.data.AppDatabase
import com.zhaojin.reimbursement.data.BillEntity
import com.zhaojin.reimbursement.data.BillPhotoEntity
import com.zhaojin.reimbursement.ui.components.SoftButton
import com.zhaojin.reimbursement.ui.theme.ReimbursementTheme
import com.zhaojin.reimbursement.utils.AppLogger
import com.zhaojin.reimbursement.utils.BillPhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 系统分享接收页：任意应用（截图编辑器/微信等）分享图片 → 选「维修报销」
 * → 进入选账单挂照片的轻量界面。
 *
 * 流程：onCreate 立刻把 EXTRA_STREAM 的图片字节拷进私有 pending 文件
 * （分享方的读取授权只在当下有效），用户选账单确认后经 commitPending
 * 转正压缩入库——与添加页照片同一条管线。
 */
class ShareReceiveActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 先取流：SEND 单图 / SEND_MULTIPLE 多图（在授权有效期内同步读取）
        val uris: List<android.net.Uri> = when (intent?.action) {
            Intent.ACTION_SEND_MULTIPLE ->
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                    .orEmpty().filterNotNull()
            else ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java))
        }.take(9) // 与添加页相册多选上限一致

        // 2. 立即落盘为 pending 文件（IO），期间显示加载态，完成后进入选择界面
        val staged = mutableListOf<File>()
        var stageError = 0
        setContent {
            ReimbursementTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xF2000000.toInt())),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                try {
                    BillPhotoStore.stageFromUri(applicationContext, uri)?.let { staged.add(it) }
                        ?: run { stageError++; AppLogger.log("图片", "分享接收 暂存失败 uri=$uri") }
                } catch (e: Exception) {
                    stageError++
                    AppLogger.log("图片", "分享接收 异常 uri=$uri ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            if (staged.isEmpty()) {
                AppLogger.log("图片", "分享接收失败 无有效图片（收到 ${uris.size} 张，失败 $stageError 张）")
                runOnUiThread {
                    Toast.makeText(this@ShareReceiveActivity, "未能读取分享的图片", Toast.LENGTH_SHORT).show()
                    finish()
                }
                return@launch
            }
            AppLogger.log("图片", "分享接收 ${staged.size} 张图片（失败 $stageError 张），等待选择账单")
            val bills = AppDatabase.getDatabase(applicationContext).billDao().getRecentBillsOnce(50)
            runOnUiThread { setContent { ShareReceiveScreen(bills, staged) } }
        }
    }

    @Composable
    private fun ShareReceiveScreen(bills: List<BillEntity>, staged: List<File>) {
        var selectedId by remember { mutableStateOf<Long?>(null) }
        var search by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val context = this

        ReimbursementTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF2000000.toInt()))
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // 标题行 + 关闭
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "添加到账单",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.14f), CircleShape)
                                .clickable { finish() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close, contentDescription = "关闭",
                                tint = Color.White, modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 收到的图片预览
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        staged.forEach { file ->
                            var thumb by remember(file.absolutePath) {
                                mutableStateOf<android.graphics.Bitmap?>(null)
                            }
                            lifecycleScope.launch(Dispatchers.IO) {
                                val bmp = BillPhotoStore.loadThumbnail(context, file.name, 160)
                                runOnUiThread { thumb = bmp }
                            }
                            Image(
                                bitmap = thumb?.asImageBitmap()
                                    ?: android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                                        .asImageBitmap(),
                                contentDescription = "分享的图片",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                        RoundedCornerShape(10.dp)
                                    )
                            )
                        }
                        Text(
                            text = "已接收 ${staged.size} 张",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 搜索框（过滤最近账单）
                    androidx.compose.material3.OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = {
                            Text("搜索内容 / 驾驶员 / 车牌号", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f))
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            cursorColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (bills.isEmpty()) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                text = "还没有账单，请先在应用中记一笔",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        // 账单选择列表（最近优先 + 本地过滤）
                        val keyword = search.trim()
                        val filtered = if (keyword.isEmpty()) bills else bills.filter { b ->
                            b.title.contains(keyword, true) || b.driver.contains(keyword, true) ||
                                b.plate.contains(keyword, true) ||
                                b.amount.toString().contains(keyword)
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filtered, key = { it.id }) { bill ->
                                val selected = selectedId == bill.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                            else Color.White.copy(alpha = 0.08f)
                                        )
                                        .border(
                                            if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                            else androidx.compose.foundation.BorderStroke(1.dp, Color.Transparent),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { selectedId = bill.id }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = bill.title,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatBillDate(bill.timestamp) +
                                                if (bill.plate.isNotBlank()) " · ${bill.plate}" else "",
                                            color = Color.White.copy(alpha = 0.55f),
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(
                                        text = "¥" + String.format("%.2f", bill.amount),
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (selected) {
                                        Icon(
                                            Icons.Default.Check, contentDescription = "已选",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 确认入库
                    SoftButton(
                        text = if (busy) "正在添加…" else "添加到该账单",
                        onClick = {
                            val billId = selectedId ?: return@SoftButton
                            if (busy) return@SoftButton
                            busy = true
                            val bill = bills.firstOrNull { it.id == billId }
                            lifecycleScope.launch(Dispatchers.IO) {
                                val photoDao = AppDatabase.getDatabase(applicationContext).billPhotoDao()
                                var ok = 0
                                staged.forEach { file ->
                                    BillPhotoStore.commitPending(applicationContext, file)?.let { name ->
                                        photoDao.insert(
                                            BillPhotoEntity(
                                                billId = billId, fileName = name,
                                                createdAt = System.currentTimeMillis()
                                            )
                                        )
                                        ok++
                                    } ?: BillPhotoStore.discard(file)
                                }
                                AppLogger.log(
                                    "图片",
                                    "分享接收入库 账单#$billId「${bill?.title}」 成功=$ok/${staged.size}张"
                                )
                                runOnUiThread {
                                    Toast.makeText(
                                        this@ShareReceiveActivity,
                                        "已添加 $ok 张图片到「${bill?.title ?: "账单"}」",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    finish()
                                }
                            }
                        },
                        enabled = selectedId != null && !busy
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (busy) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }
    }

    private fun formatBillDate(timestamp: Long): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").format(
            Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        )
}
