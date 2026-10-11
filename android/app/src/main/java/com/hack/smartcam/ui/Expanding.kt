package com.hack.smartcam.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex

/** One widget: its small face, and what it shows when it grows. */
class GridCard(
    val key: String,
    val compact: @Composable () -> Unit,
    val expanded: @Composable (close: () -> Unit) -> Unit,
)

/** Height of the widget cells, so a face can show more when the grid is tall. */
val LocalCellHeight = androidx.compose.runtime.compositionLocalOf { 184.dp }

private val Grow = spring<Float>(dampingRatio = 0.84f, stiffness = 340f)

/**
 * Wraps a screen that has an [ExpandingGrid]. While a card is open, a tap anywhere outside
 * the grid (or the back button) closes it, and [content] gets a dim factor for the rest of the screen.
 */
@Composable
fun ExpandHost(open: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable (gridModifier: Modifier, dim: Float) -> Unit) {
    val root = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val grid = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val dim by animateFloatAsState(if (open) 0.35f else 1f, Grow, label = "dim")
    BackHandler(enabled = open, onBack = onClose)
    Box(
        modifier.fillMaxSize()
            .onGloballyPositioned { root.value = it }
            .pointerInput(open) {
                if (!open) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    val r = root.value; val g = grid.value
                    val inside = r != null && g != null && g.isAttached && r.localBoundingBoxOf(g).contains(down.position)
                    if (!inside) { down.consume(); Haptics.tick(); onClose() }
                }
            },
    ) {
        content(Modifier.onGloballyPositioned { grid.value = it }, dim)
    }
}

/**
 * Widgets in a grid. Opening one grows it from its cell to cover the whole grid,
 * in front of the others; closing shrinks it back. The open card is as tall as its content
 * (never shorter than the grid), so nothing inside it has to scroll.
 */
@Composable
fun ExpandingGrid(
    cards: List<GridCard>,
    open: String?,
    onOpen: (String?) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    cellHeight: Dp = 184.dp,
    gap: Dp = 14.dp,
    expandedHeight: Dp? = null,
) {
    val rows = (cards.size + columns - 1) / columns
    val gridHeight = cellHeight * rows + gap * (rows - 1)
    val minOpen = maxOf(gridHeight, expandedHeight ?: gridHeight)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measured = remember { androidx.compose.runtime.mutableStateMapOf<String, Dp>() }
    fun openHeight(key: String?) = maxOf(minOpen, key?.let { measured[it] } ?: minOpen)
    // Remember the last open card so the grid can shrink back smoothly after it closes.
    val last = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    if (open != null) last.value = open
    val fullHeight = openHeight(last.value)
    // While a card is open the grid can be taller than the cells (e.g. 4 widgets in one row on tablets).
    val anyOpen by animateFloatAsState(if (open != null) 1f else 0f, Grow, label = "grid")
    BoxWithConstraints(modifier.fillMaxWidth().height(lerp(gridHeight, fullHeight, anyOpen))) {
        val fullW = maxWidth
        val cellW = (fullW - gap * (columns - 1)) / columns
        cards.forEachIndexed { i, card ->
            val col = i % columns
            val row = i / columns
            val isOpen = open == card.key
            val p by animateFloatAsState(if (isOpen) 1f else 0f, Grow, label = card.key)
            val others by animateFloatAsState(if (open != null && !isOpen) 1f else 0f, Grow, label = "o" + card.key)
            val x = lerp((cellW + gap) * col, 0.dp, p)
            val y = lerp((cellHeight + gap) * row, 0.dp, p)
            val w = lerp(cellW, fullW, p)
            val h = lerp(cellHeight, openHeight(card.key), p)
            Box(
                Modifier.offset(x, y).size(w, h)
                    .zIndex(if (p > 0.001f) 1f + p else 0f)
                    .graphicsLayer {
                        alpha = 1f - others * 0.6f
                        scaleX = 1f - others * 0.05f; scaleY = scaleX
                    }
                    // Frosted while small; nearly solid once open so the widgets behind don't show through.
                    .glass(fill = androidx.compose.ui.graphics.lerp(P.card, P.sheet.copy(alpha = 0.97f), p))
                    .clickable(enabled = open == null, role = Role.Button) { Haptics.click(); onOpen(card.key) },
            ) {
                if (p < 0.6f) Box(Modifier.requiredSize(cellW, cellHeight).align(Alignment.TopStart).alpha((1f - p * 2.2f).coerceIn(0f, 1f)).padding(14.dp)) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalCellHeight provides cellHeight) { card.compact() }
                }
                if (isOpen || p > 0.001f) Box(
                    // Laid out at its natural height (at least the grid's), then revealed as the card grows.
                    Modifier.requiredWidth(fullW).wrapContentHeight(Alignment.Top, unbounded = true).requiredHeightIn(min = minOpen)
                        .onSizeChanged { measured[card.key] = with(density) { it.height.toDp() } }
                        .align(Alignment.TopStart).alpha(((p - 0.3f) / 0.7f).coerceIn(0f, 1f)),
                ) {
                    card.expanded { Haptics.tick(); onOpen(null) }
                }
            }
        }
    }
}

/** Header row for an open widget: title and a close button. */
@Composable
fun ExpandedHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClose: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(icon, null, Modifier.size(18.dp), tint = P.ink2)
        androidx.compose.material3.Text(title, style = T.h2, color = P.ink, modifier = Modifier.padding(start = 8.dp).weight(1f))
        IconBtn(Ic.close, "Close", onClose)
    }
}

/** Body of an open widget, under its header. The card grows to fit it. */
@Composable
fun ExpandedColumn(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    close: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
        ExpandedHeader(title, icon, close)
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
