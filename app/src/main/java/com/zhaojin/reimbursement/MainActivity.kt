package com.zhaojin.reimbursement

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import com.zhaojin.reimbursement.ui.BillScreen
import com.zhaojin.reimbursement.ui.components.AmbientBackground
import com.zhaojin.reimbursement.ui.components.GlassBackdropRoot
import com.zhaojin.reimbursement.ui.components.SliderStiffness
import com.zhaojin.reimbursement.ui.components.glassBorder
import com.zhaojin.reimbursement.ui.components.glassFill
import com.zhaojin.reimbursement.ui.report.ReportScreen
import com.zhaojin.reimbursement.ui.theme.AccentColorRepository
import com.zhaojin.reimbursement.ui.theme.ReimbursementTheme
import kotlinx.coroutines.launch

/**
 * 主导航 Tab：记账（手动账单管理）与报表（收支统计分析）。
 */
enum class AppTab(
    val label: String,
    val icon: ImageVector,
    val iconFilled: ImageVector
) {
    Bills("记账", Icons.Outlined.Receipt, Icons.Filled.Receipt),
    Report("报表", Icons.Outlined.BarChart, Icons.Filled.BarChart)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AccentColorRepository.init(this)
        enableEdgeToEdge()

        setContent {
            ReimbursementTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Single ambient background for the whole app: the bottom nav area and
    // every transparent screen share the same gradient layer, so the
    // floating nav pill has no solid strip on either side.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (androidx.compose.foundation.isSystemInDarkTheme()) {
                        listOf(Color(0xFF162521), Color(0xFF1B2030), Color(0xFF211B16))
                    } else {
                        listOf(Color(0xFFD9F2EC), Color(0xFFE3E9F8), Color(0xFFF6EDE2))
                    }
                )
            )
    ) {
        AmbientBackground()
        // Real-time backdrop blur (iOS-style): screen content is captured
        // to an off-screen layer, blurred underneath, then redrawn sharp —
        // transparent glass components reveal the blurred copy.
        GlassBackdropRoot(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    SoftNavBar(
                        tabs = AppTab.entries.toList(),
                        selectedIndex = selectedTab,
                        onSelect = { selectedTab = it }
                    )
                }
            ) { _ ->
                // No bottom padding: like the report screen, tab content
                // extends behind the floating nav pill and scrolls beneath it.
                // SaveableStateProvider keeps each tab's scroll position and
                // remember state alive across tab switches.
                // Crossfade：切 tab 时内容轻微淡入淡出，与滑块滑动节奏配合。
                val stateHolder = rememberSaveableStateHolder()
                Crossfade(
                    targetState = AppTab.entries.getOrNull(selectedTab) ?: AppTab.Bills,
                    animationSpec = tween(100, easing = FastOutSlowInEasing),
                    label = "tabContentFade"
                ) { tab ->
                    when (tab) {
                        AppTab.Bills -> stateHolder.SaveableStateProvider(key = "tab_bills") { BillScreen() }
                        AppTab.Report -> stateHolder.SaveableStateProvider(key = "tab_report") { ReportScreen() }
                    }
                }
            }
        }
    }
}

// ============================================================
// Soft floating pill navigation bar — signature of the UI
// ============================================================

@Composable
private fun SoftNavBar(
    tabs: List<AppTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // 导航项几何（px，相对内容区原点）；首次布局测量后直接落位，之后切换才滑动
        var itemLefts by remember(tabs) { mutableStateOf(FloatArray(tabs.size)) }
        var itemWidths by remember(tabs) { mutableStateOf(IntArray(tabs.size)) }
        var measured by remember(tabs) { mutableStateOf(false) }
        val sliderLeft = remember { Animatable(0f) }
        val sliderWidth = remember { Animatable(0f) }
        val accent = AccentColorRepository.current

        LaunchedEffect(itemLefts, itemWidths, selectedIndex) {
            if (!measured) return@LaunchedEffect
            val targetLeft = itemLefts[selectedIndex]
            val targetWidth = itemWidths[selectedIndex].toFloat()
            if (sliderWidth.value == 0f) {
                // 首次定位：直接落位，避免从 0 起步的开场滑入
                sliderLeft.snapTo(targetLeft)
                sliderWidth.snapTo(targetWidth)
            } else {
                // 滑块滑动：位置与宽度并行动画，≈200ms 到位
                launch {
                    sliderLeft.animateTo(
                        targetLeft,
                        spring(Spring.DampingRatioNoBouncy, SliderStiffness)
                    )
                }
                sliderWidth.animateTo(
                    targetWidth,
                    spring(Spring.DampingRatioNoBouncy, SliderStiffness)
                )
            }
        }

        Box(
            modifier = Modifier
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color(0x26000000),
                    spotColor = Color(0x33000000)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(glassFill())
                .border(glassBorder(), RoundedCornerShape(28.dp))
                .drawBehind {
                    // 滑块画在玻璃底色之上、导航项之下，切换时在两个 tab 之间滑动
                    if (!measured || sliderWidth.value <= 0f) return@drawBehind
                    val pad = 6.dp.toPx() // drawBehind 坐标系含容器内边距，需补回
                    val topLeft = Offset(pad + sliderLeft.value, pad)
                    val size = Size(sliderWidth.value, this.size.height - pad * 2)
                    val corner = CornerRadius(22.dp.toPx())
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(accent.primary, accent.gradientEnd)),
                        topLeft = topLeft,
                        size = size,
                        cornerRadius = corner
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.35f),
                        topLeft = topLeft,
                        size = size,
                        cornerRadius = corner,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                .padding(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { index, tab ->
                    SoftNavItem(
                        tab = tab,
                        selected = selectedIndex == index,
                        onGeometry = { left, width ->
                            if (itemLefts[index] != left || itemWidths[index] != width) {
                                val newLefts = itemLefts.copyOf(); newLefts[index] = left
                                val newWidths = itemWidths.copyOf(); newWidths[index] = width
                                itemLefts = newLefts
                                itemWidths = newWidths
                                measured = true
                            }
                        },
                        onClick = { onSelect(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SoftNavItem(
    tab: AppTab,
    selected: Boolean,
    onGeometry: (Float, Int) -> Unit,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .onGloballyPositioned { coords ->
                onGeometry(coords.positionInParent().x, coords.size.width)
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (selected) tab.iconFilled else tab.icon,
                contentDescription = tab.label,
                tint = if (selected) Color.White else scheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            // 标签瞬时切换（不做展开动画）：item 宽度在一次布局内到位，
            // 滑块只对「最终几何」做一次干净的滑动动画，
            // 若标签逐帧展开，滑块弹簧每帧被打断重启，观感卡顿
            if (selected) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}
