package com.hack.smartcam.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.smartcam.ui.Btn
import com.hack.smartcam.ui.Haptics
import com.hack.smartcam.ui.Mono
import com.hack.smartcam.ui.ScButton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

private const val COLS = 10

private val SHAPES = mapOf(
    'I' to listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1), 'O' to listOf(1 to 0, 2 to 0, 1 to 1, 2 to 1),
    'T' to listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1), 'S' to listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1),
    'Z' to listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1), 'J' to listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1),
    'L' to listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1),
)
private val COLORS = mapOf(
    'I' to Color(0xFF7FC8D9), 'O' to Color(0xFFF2C14E), 'T' to Color(0xFFB79CED), 'S' to Color(0xFF9BC59D),
    'Z' to Color(0xFFF78154), 'J' to Color(0xFF7EA6E0), 'L' to Color(0xFFF2A65A),
)

private class Piece(val k: Char, var cells: List<Pair<Int, Int>>, var x: Int = 3, var y: Int = -1) {
    fun blocks(dx: Int = 0, dy: Int = 0, c: List<Pair<Int, Int>> = cells) = c.map { (a, b) -> (x + a + dx) to (y + b + dy) }
}

/** Calm Tetris: soft colours, ghost piece, no speed panic. Stress sets the pace. */
/** [rows] grows with the space available so the board fills the whole game area. */
private class Tetris(val tier: Tier, val rows: Int) {
    val board = Array(rows) { arrayOfNulls<Char>(COLS) }
    private var bag = mutableListOf<Char>()
    var cur: Piece? = null
    var next: Char = take()
    var score = 0
    var lines = 0
    var over = false
    var running = false
    var acc = 0L
    val flashes = mutableListOf<Pair<Int, Long>>()

    private val base = when (tier) { Tier.High -> 1150L; Tier.Moderate -> 900L; Tier.Low -> 750L }
    private val floorMs = when (tier) { Tier.High -> 900L; Tier.Moderate -> 520L; Tier.Low -> 300L }
    val interval get() = max(floorMs, base - (lines / 10) * 60L)

    private fun take(): Char {
        if (bag.isEmpty()) bag = SHAPES.keys.shuffled().toMutableList()
        return bag.removeAt(bag.lastIndex)
    }

    private fun fits(p: Piece, dx: Int, dy: Int, c: List<Pair<Int, Int>> = p.cells) =
        p.blocks(dx, dy, c).all { (x, y) -> x in 0 until COLS && y < rows && (y < 0 || board[y][x] == null) }

    fun reset() {
        board.forEach { it.fill(null) }
        score = 0; lines = 0; over = false; running = true; acc = 0
        spawn()
    }

    private fun spawn() {
        val k = next
        next = take()
        cur = Piece(k, SHAPES[k]!!)
        if (!fits(cur!!, 0, 0)) { over = true; running = false; Haptics.warning() }
    }

    fun rotate() {
        val p = cur ?: return
        if (p.k == 'O') return
        val rot = p.cells.map { (x, y) -> (2 - y) to x }
        for (kick in listOf(0, -1, 1, -2, 2)) if (fits(p, kick, 0, rot)) { p.cells = rot; p.x += kick; Haptics.click(); return }
    }

    fun move(dx: Int) { val p = cur ?: return; if (fits(p, dx, 0)) { p.x += dx; Haptics.tick() } }
    fun drop(): Boolean { val p = cur ?: return false; if (fits(p, 0, 1)) { p.y++; return true }; lock(); return false }
    fun hardDrop() { val p = cur ?: return; var n = 0; while (fits(p, 0, 1)) { p.y++; n++ }; score += n; lock(hard = true) }
    fun ghost(): Int { val p = cur ?: return 0; var d = 0; while (fits(p, 0, d + 1)) d++; return d }

    private fun lock(hard: Boolean = false) {
        val p = cur ?: return
        for ((x, y) in p.blocks()) {
            if (y < 0) { over = true; running = false; Haptics.warning(); return }
            board[y][x] = p.k
        }
        val full = (0 until rows).filter { y -> board[y].all { it != null } }
        // Landing thuds (harder for a flick-drop); clearing lines ripples instead.
        if (full.isNotEmpty()) Haptics.lines(full.size) else Haptics.thud(if (hard) 1f else 0.7f)
        if (full.isNotEmpty()) {
            val now = System.currentTimeMillis()
            full.forEach { y ->
                flashes += y to now
                for (r in y downTo 1) board[r] = board[r - 1].copyOf()
                board[0] = arrayOfNulls(COLS)
            }
            lines += full.size
            score += listOf(0, 100, 300, 500, 800)[full.size]
        }
        spawn()
    }

    fun tick(dt: Long) {
        if (!running || over) return
        acc += dt
        if (acc >= interval) { acc = 0; drop() }
    }
}

@Composable
fun TetrisGame(tier: Tier, onHud: (String) -> Unit) {
    // Ten columns across the full width; as many rows as fit the height.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rows = (maxHeight / (maxWidth / COLS)).toInt().coerceIn(8, 32)
        key(rows) { TetrisBoard(tier, rows, onHud) }
    }
}

@Composable
private fun TetrisBoard(tier: Tier, rows: Int, onHud: (String) -> Unit) {
    val game = remember { Tetris(tier, rows) }
    var frame by remember { mutableLongStateOf(0L) }
    var started by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var cellPx by remember { mutableStateOf(40f) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameMillis { t ->
            if (last != 0L) game.tick(t - last)
            last = t
            frame = t
            gameOver = game.over
            onHud(if (!started) "Tap start" else "${game.score} · ${game.lines} line${if (game.lines == 1) "" else "s"}")
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                // Tap = rotate · drag sideways = move · drag down = soft drop · flick down = drop
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!game.running) return@awaitEachGesture
                    val start = down.position
                    var mx = 0; var my = 0; var moved = false
                    val step = max(cellPx, with(density) { 18.dp.toPx() })
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            val dt = ch.uptimeMillis - down.uptimeMillis
                            val dy = ch.position.y - start.y
                            val dx = ch.position.x - start.x
                            if (dy > 60.dp.toPx() && dt < 250 && abs(dy) > abs(dx) * 1.5f) game.hardDrop()
                            else if (!moved && dt < 300) game.rotate()
                            break
                        }
                        val ddx = ch.position.x - start.x - mx * step
                        if (abs(ddx) >= step) { val d = sign(ddx).toInt(); game.move(d); mx += d; moved = true }
                        val ddy = ch.position.y - start.y - my * step
                        if (ddy >= step) { game.drop(); my++; moved = true; game.acc = 0 }
                        ch.consume()
                    }
                }
            },
        ) {
            frame // redraw every frame
            // Full width; only if the area is very short do the cells shrink (and the board centres).
            val cell = minOf(size.width / COLS, size.height / rows)
            cellPx = cell
            val ox = (size.width - cell * COLS) / 2
            val oy = size.height - cell * rows // any leftover sliver sits at the top

            for (x in 1 until COLS) drawLine(Cream.copy(alpha = 0.05f), Offset(ox + x * cell, oy), Offset(ox + x * cell, size.height))
            for (y in 1 until rows) drawLine(Cream.copy(alpha = 0.03f), Offset(ox, oy + y * cell), Offset(ox + cell * COLS, oy + y * cell))

            fun cellAt(x: Int, y: Int, c: Color, alpha: Float = 1f) {
                val px = ox + x * cell; val py = oy + y * cell
                drawRoundRect(c.copy(alpha = alpha), Offset(px + 1.5f, py + 1.5f), Size(cell - 3, cell - 3), CornerRadius(cell * 0.22f))
                drawRoundRect(Color.White.copy(alpha = 0.28f * alpha), Offset(px + 4, py + 4), Size(cell - 8, cell * 0.18f), CornerRadius(2f))
            }
            for (y in 0 until rows) for (x in 0 until COLS) game.board[y][x]?.let { cellAt(x, y, COLORS[it]!!) }
            game.cur?.takeIf { !game.over }?.let { p ->
                val g = game.ghost()
                p.blocks(0, g).forEach { (x, y) -> if (y >= 0) cellAt(x, y, COLORS[p.k]!!, 0.2f) }
                p.blocks().forEach { (x, y) -> if (y >= 0) cellAt(x, y, COLORS[p.k]!!) }
            }
            val now = System.currentTimeMillis()
            game.flashes.removeAll { now - it.second > 420 }
            game.flashes.forEach { (y, t) ->
                val a = 1 - (now - t) / 420f
                drawRect(Color.White.copy(alpha = a * 0.75f), Offset(ox, oy + y * cell), Size(cell * COLS, cell))
            }
            // Next piece, small and faint in the bottom-left corner so it never hides the action.
            val small = max(8f, cell * 0.42f)
            val px = 10.dp.toPx()
            val py = size.height - small * 2 - 10.dp.toPx()
            drawText(measurer, "NEXT", Offset(px, py - 16.dp.toPx()), TextStyle(fontFamily = Mono, fontSize = 10.sp, color = Cream.copy(alpha = 0.45f)))
            SHAPES[game.next]!!.forEach { (x, y) ->
                drawRoundRect(COLORS[game.next]!!.copy(alpha = 0.6f), Offset(px + x * small, py + y * small), Size(small - 2, small - 2), CornerRadius(3f))
            }
        }
        if (!started || gameOver) {
            GameCard(
                if (gameOver) "Nice run" else "Calm Tetris",
                if (gameOver) "${game.score} points · ${game.lines} lines" else "Tap to turn · drag to move · flick down to drop",
            ) {
                ScButton(if (gameOver) "Play again" else "Start", { started = true; game.reset() }, style = Btn.Pop, small = true)
            }
        }
    }
}
