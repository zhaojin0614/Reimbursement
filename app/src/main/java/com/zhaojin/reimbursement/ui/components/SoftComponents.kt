package com.zhaojin.reimbursement.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhaojin.reimbursement.ui.theme.GradientBrandEnd
import com.zhaojin.reimbursement.ui.theme.GradientBrandStart
import com.zhaojin.reimbursement.ui.theme.MistBlue
import com.zhaojin.reimbursement.ui.theme.SandGold
import kotlinx.coroutines.launch

/**
 * Soft UI shared components — the single visual language used across
 * Messages / Bills / Report / Birthday screens.
 */

// ---------- Glass helpers ----------

@Composable
fun isDarkTheme(): Boolean = isSystemInDarkTheme()

/**
 * 玻璃渐变填充：由单一颜色生成"左亮 → 中实 → 右暗"的水平渐变，
 * 用于彩色按钮/卡片/图标块，替代纯实色背景，保留透明度透出底层光斑。
 */
@Composable
fun gradientBrush(
    color: Color,
    alpha: Float = 1f,
    highlight: Float = 0.30f,
    shade: Float = 0.10f
): Brush {
    val base = color.copy(alpha = alpha)
    return Brush.horizontalGradient(
        listOf(
            lerp(base, Color.White, highlight),
            base,
            lerp(base, Color.Black, shade)
        )
    )
}

/** Frosted-glass panel fill — a faint translucent white tint so the
 *  glass surface reads as a distinct pane against the background while
 *  the animated backdrop blobs still shine through (no solid white
 *  panels, just a hint of glass body). */
@Composable
fun glassFill(): Color {
    val alpha = if (isDarkTheme()) 0.04f else 0.10f
    return Color.White.copy(alpha = alpha)
}

/** Edge light for glass surfaces — a gradient rim (brightest at the
 *  top edge, fading down into the background) instead of a uniform
 *  outline, so surfaces blend into the backdrop like liquid glass
 *  instead of drawing a hard divider line. */
@Composable
fun glassBorder(): BorderStroke {
    val dark = isDarkTheme()
    return BorderStroke(
        1.dp,
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.22f else 0.45f),
            0.5f to Color.White.copy(alpha = if (dark) 0.12f else 0.25f),
            1f to Color.White.copy(alpha = if (dark) 0.07f else 0.15f)
        )
    )
}

/** Top-edge light reflection used inside glass surfaces: a bright
 *  catch-light band along the top edge that fades down into the pane,
 *  giving the liquid-glass rim its signature sheen. */
@Composable
fun glassHighlightBrush(): Brush {
    val dark = isDarkTheme()
    return Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (dark) 0.15f else 0.34f),
        0.10f to Color.White.copy(alpha = if (dark) 0.06f else 0.15f),
        1f to Color.Transparent,
        startY = 0f,
        endY = 240f
    )
}

// ---------- SoftCard: liquid-glass card ----------

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(glassFill())
            .border(glassBorder(), shape)
    ) {
        // Top light reflection (liquid glass highlight)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(glassHighlightBrush())
        )
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

// ---------- SoftGradientCard: gradient hero card (bento style) ----------

@Composable
fun SoftGradientCard(
    brush: Brush,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    contentColor: Color = Color.White,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = shape,
                ambientColor = brush.toAmbientColor(),
                spotColor = brush.toAmbientColor()
            )
            .clip(shape)
            .background(brush)
            .border(glassBorder(), shape)
    ) {
        // Top light reflection (liquid glass highlight)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(glassHighlightBrush())
        )
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

private fun Brush.toAmbientColor(): Color = when (this) {
    is androidx.compose.ui.graphics.SolidColor -> value.copy(alpha = 0.25f)
    else -> Color(0x1F000000)
}

// ---------- SoftButton: unified primary/secondary button ----------

@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    height: Dp = 46.dp
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(
                if (enabled) gradientBrush(backgroundColor, alpha = 0.88f)
                else gradientBrush(backgroundColor, alpha = 0.4f)
            )
            .border(glassBorder(), shape)
            .softClickable(
                onClick = onClick,
                enabled = enabled
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun Modifier.softClickable(
    onClick: () -> Unit,
    enabled: Boolean = true
): Modifier {
    val interactionSource = androidx.compose.runtime.remember {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}

// ---------- PillToggle: segmented control ----------

/** 滑块滑动统一节奏：spring stiffness ≈200ms 到位（底部导航/分段控件共用） */
const val SliderStiffness = 500f

/**
 * 分段控件：选中胶囊是独立「滑块」，用 drawBehind 画在底色之上、文字之下，
 * 切换时以弹簧动画在选项间滑动（与底部导航栏同款效果）。
 * 选项文字瞬时变色，几何一次布局到位，滑块只对最终位置做一次干净滑动。
 */
@Composable
fun PillToggle(
    options: List<Pair<String, Color>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp)
) {
    // 各选项几何（px，相对内容区原点）；首次测量直接落位，之后切换才滑动
    var itemLefts by remember { mutableStateOf(FloatArray(options.size)) }
    var itemWidths by remember { mutableStateOf(IntArray(options.size)) }
    var measured by remember { mutableStateOf(false) }
    val sliderLeft = remember { Animatable(0f) }
    val sliderWidth = remember { Animatable(0f) }
    val safeIndex = selectedIndex.coerceIn(0, options.lastIndex)

    LaunchedEffect(itemLefts, itemWidths, safeIndex) {
        if (!measured) return@LaunchedEffect
        val targetLeft = itemLefts[safeIndex]
        val targetWidth = itemWidths[safeIndex].toFloat()
        if (sliderWidth.value == 0f) {
            sliderLeft.snapTo(targetLeft)
            sliderWidth.snapTo(targetWidth)
        } else {
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

    val selectedBrush = gradientBrush(options[safeIndex].second, alpha = 0.92f)

    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
            .border(glassBorder(), shape)
            .drawBehind {
                // 滑块画在底色之上、文字之下；坐标系含 3dp 内边距，需补回
                if (!measured || sliderWidth.value <= 0f) return@drawBehind
                val pad = 3.dp.toPx()
                drawRoundRect(
                    brush = selectedBrush,
                    topLeft = Offset(pad + sliderLeft.value, pad),
                    size = Size(sliderWidth.value, this.size.height - pad * 2),
                    cornerRadius = CornerRadius(11.dp.toPx())
                )
            }
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { index, (label, _) ->
            val selected = safeIndex == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { coords ->
                        val left = coords.positionInParent().x
                        val width = coords.size.width
                        if (itemLefts[index] != left || itemWidths[index] != width) {
                            val newLefts = itemLefts.copyOf(); newLefts[index] = left
                            val newWidths = itemWidths.copyOf(); newWidths[index] = width
                            itemLefts = newLefts
                            itemWidths = newWidths
                            measured = true
                        }
                    }
                    .clip(RoundedCornerShape(11.dp))
                    .softClickable(onClick = { onSelect(index) })
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 紧凑版玻璃弹窗：M3 AlertDialog 的按钮区自带上下约 24dp 固定留白且无法
 * 通过参数调小，这里改用玻璃容器 + 右对齐紧凑操作行统一弹窗观感。
 * [text] 承载任意内容（表单/列表均可），[title] 为空时不占标题区。
 */
@Composable
fun GlassCompactDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: String? = null,
    text: (@Composable () -> Unit)? = null,
    properties: androidx.compose.ui.window.DialogProperties =
        androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(
                alpha = if (isDarkTheme()) 0.90f else 0.93f
            ),
            modifier = modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .border(glassBorder(), RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)) {
                if (title != null) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (text != null) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) { text() }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                // 取消 48dp 最小点击目标对按钮行的抬高，弹窗按钮本身已有 40dp 高度
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dismissButton != null) {
                            dismissButton()
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        confirmButton()
                    }
                }
            }
        }
    }
}

// ---------- SoftFab: floating circular action button ----------

/**
 * Floating circular action button — a plain glass circle with the icon
 * centered, nothing else. Replaces Material 3 FloatingActionButton whose
 * newer spec draws an extra inner tonal icon container (the octagon-like
 * shape in the middle of the button).
 */
@Composable
fun SoftFab(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    iconTint: Color = Color.White,
    size: Dp = 56.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = Color(0x1F000000),
                spotColor = Color(0x2E000000)
            )
            .clip(CircleShape)
            .background(gradientBrush(backgroundColor, alpha = 0.88f))
            .border(glassBorder(), CircleShape)
            .softClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
    }
}

// ---------- SoftEmptyState: unified empty view ----------

@Composable
fun SoftEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 88.dp
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(iconSize / 2.5f))
                .background(gradientBrush(MaterialTheme.colorScheme.primaryContainer, alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(iconSize * 0.45f)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------- StatPill: small value+label pill for stats strips ----------

@Composable
fun StatPill(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onBackground
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
// ---------- StaticBackground: static soft glow backdrop ----------

/**
 * 静态背景：固定位置的柔光斑（无动画）。
 *
 * 原实现为 9 个光斑 + 极光底色的无限动画，每帧都在重组/重绘，持续消耗
 * GPU/CPU；现改为一次布局、一次绘制的静态光斑，玻璃面板的透出色彩层次
 * 保持一致，而后台常驻资源占用接近于零。底色渐变由 MainApp 根布局提供。
 */
@Composable
fun StaticBackground(modifier: Modifier = Modifier) {
    val dark = isDarkTheme()
    Box(modifier = modifier.fillMaxSize()) {
        StaticBlob(
            modifier = Modifier.align(Alignment.TopStart).offset((-80).dp, (-110).dp),
            size = 470.dp, color = GradientBrandStart, dark = dark, alphaLight = 0.66f
        )
        StaticBlob(
            modifier = Modifier.align(Alignment.TopEnd).offset(90.dp, 40.dp),
            size = 430.dp, color = MistBlue, dark = dark, alphaLight = 0.60f
        )
        StaticBlob(
            modifier = Modifier.align(Alignment.BottomStart).offset((-70).dp, 70.dp),
            size = 450.dp, color = SandGold, dark = dark, alphaLight = 0.54f
        )
        StaticBlob(
            modifier = Modifier.align(Alignment.BottomEnd).offset((-90).dp, (-50).dp),
            size = 410.dp, color = Color(0xFF9B8CE8), dark = dark, alphaLight = 0.48f
        )
        StaticBlob(
            modifier = Modifier.align(Alignment.Center),
            size = 390.dp, color = Color(0xFF3FAE7E), dark = dark, alphaLight = 0.48f
        )
    }
}

/** 单个静态柔光斑：径向渐变圆，中心实色向外淡出 */
@Composable
private fun StaticBlob(
    modifier: Modifier = Modifier,
    size: Dp,
    color: Color,
    dark: Boolean,
    alphaLight: Float
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        color.copy(alpha = if (dark) alphaLight * 0.72f else alphaLight),
                        Color.Transparent
                    )
                )
            )
    )
}
