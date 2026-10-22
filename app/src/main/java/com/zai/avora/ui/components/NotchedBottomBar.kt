package com.zai.avora.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.avora.ui.theme.AvoraAppTheme

data class NotchedNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

val NotchedBarBodyHeight: Dp = 64.dp

val NotchedBarLift: Dp = 30.dp

@Composable
fun NotchedBottomBar(
    items: List<NotchedNavItem>,
    selectedRoute: String?,
    onItemClick: (NotchedNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AvoraAppTheme.colors
    val dark = colors.isDark

    val barHeight = NotchedBarBodyHeight
    val lift = NotchedBarLift
    val buttonSize = 56.dp
    val notchDepth = 35.dp
    val notchHalfWidth = 44.dp

    val density = LocalDensity.current
    val insetPx = WindowInsets.navigationBars.getBottom(density)
    val bottomInset = with(density) { insetPx.toDp() }

    val selectedIndex = items.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)

    val glassBase = if (dark) Color(0xFF121212).copy(alpha = 0.72f) else Color(0xFFF2F2F7).copy(alpha = 0.70f)
    val glassFill = if (dark) {
        listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f))
    } else {
        listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.30f))
    }
    val rim = Brush.horizontalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.60f else 0.95f),
            Color.White.copy(alpha = if (dark) 0.10f else 0.45f),
            Color.White.copy(alpha = if (dark) 0.45f else 0.85f)
        )
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight + lift + bottomInset)
    ) {
        val totalWidthPx = with(density) { maxWidth.toPx() }
        val cellWidthPx = totalWidthPx / items.size

        val targetCx = cellWidthPx * selectedIndex + cellWidthPx / 2f
        val cx by animateFloatAsState(
            targetValue = targetCx,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "notchCx"
        )

        Canvas(modifier = Modifier.fillMaxWidth().height(barHeight + lift + bottomInset)) {
            val top = lift.toPx()
            val w = size.width
            val h = size.height
            val d = notchDepth.toPx()
            val hw = notchHalfWidth.toPx()

            fun Path.topEdge() {
                moveTo(0f, top)
                lineTo((cx - hw).coerceAtLeast(0f), top)
                cubicTo(cx - hw + hw * 0.45f, top, cx - hw * 0.55f, top + d, cx, top + d)
                cubicTo(cx + hw * 0.55f, top + d, cx + hw - hw * 0.45f, top, cx + hw, top)
                lineTo(w, top)
            }

            val body = Path().apply {
                topEdge()
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(body, color = glassBase)
            drawPath(body, brush = Brush.verticalGradient(glassFill, startY = top, endY = h))
            drawPath(
                body,
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (dark) 0.20f else 0.55f),
                    1f to Color.Transparent,
                    startY = top,
                    endY = top + barHeight.toPx() * 0.6f
                )
            )

            val outline = Path().apply { topEdge() }
            drawPath(outline, brush = rim, style = Stroke(width = 1.2.dp.toPx()))
        }

        val selected = items.getOrNull(selectedIndex)
        if (selected != null) {
            val buttonPx = with(density) { buttonSize.toPx() }
            val centreY = with(density) { lift.toPx() }
            val accent = colors.textPrimary
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { (cx - buttonPx / 2f).toDp() },
                        y = with(density) { (centreY - buttonPx / 2f).toDp() }
                    )
                    .size(buttonSize),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(buttonSize)) {
                    val r = size.minDimension / 2f
                    val c = Offset(r, r)
                    drawCircle(color = if (dark) Color(0xFF1A1A1A) else Color(0xFFF7F7FA), radius = r, center = c)
                    drawCircle(
                        brush = Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = if (dark) 0.34f else 0.16f),
                                accent.copy(alpha = if (dark) 0.10f else 0.05f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height)
                        ),
                        radius = r,
                        center = c
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = if (dark) 0.55f else 0.95f), Color.Transparent),
                            center = Offset(r * 0.62f, r * 0.45f),
                            radius = r * 0.95f
                        ),
                        radius = r,
                        center = c
                    )
                    drawCircle(
                        brush = Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = if (dark) 0.90f else 1f),
                                Color.White.copy(alpha = 0.10f),
                                Color.White.copy(alpha = if (dark) 0.50f else 0.90f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height)
                        ),
                        radius = r - 0.75.dp.toPx(),
                        center = c,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
                Icon(
                    imageVector = selected.icon,
                    contentDescription = selected.title,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .offset(y = lift)
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val iconAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 0f else 1f,
                    label = "iconAlpha"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onItemClick(item) }
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = colors.textMuted,
                        modifier = Modifier
                            .size(24.dp)
                            .alpha(iconAlpha)
                    )
                    Text(
                        text = item.title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) colors.textPrimary else colors.textMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
