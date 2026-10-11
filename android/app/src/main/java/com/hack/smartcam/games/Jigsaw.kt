package com.hack.smartcam.games

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Region
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.hack.smartcam.ui.Btn
import com.hack.smartcam.ui.Haptics
import com.hack.smartcam.ui.ScButton
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private class JPiece(val r: Int, val c: Int, val edges: IntArray) {
    var x = 0f
    var y = 0f
    var locked = false
    lateinit var path: androidx.compose.ui.graphics.Path
    lateinit var region: Region
}

/** One edge from (0,0) to (s,0): 0 flat, 1 tab out, -1 blank in. tf maps local → piece coords. */
private fun Path.edge(s: Float, kind: Int, tf: (Float, Float) -> Pair<Float, Float>) {
    if (kind == 0) { val (x, y) = tf(s, 0f); lineTo(x, y); return }
    val t = s * 0.22f * kind
    val p = listOf(
        s * 0.36f to 0f, s * 0.34f to -t * 0.55f, s * 0.3f to -t, s * 0.5f to -t * 1.08f,
        s * 0.7f to -t, s * 0.66f to -t * 0.55f, s * 0.64f to 0f, s to 0f,
    ).map { tf(it.first, it.second) }
    lineTo(p[0].first, p[0].second)
    cubicTo(p[1].first, p[1].second, p[2].first, p[2].second, p[3].first, p[3].second)
    cubicTo(p[4].first, p[4].second, p[5].first, p[5].second, p[6].first, p[6].second)
    lineTo(p[7].first, p[7].second)
}

private fun piecePath(s: Float, e: IntArray): Path = Path().apply {
    moveTo(0f, 0f)
    edge(s, e[0]) { x, y -> x to y }
    edge(s, e[1]) { x, y -> (s - y) to x }
    edge(s, e[2]) { x, y -> (s - x) to (s - y) }
    edge(s, e[3]) { x, y -> y to (s - x) }
    close()
}

private val PALETTES = listOf(
    listOf(0xFFF9D9B8, 0xFFF4A68C, 0xFFFFF1D6, 0xFFC98B7A, 0xFF9C6B6B, 0xFF6E4F5F, 0xFF47394B),
    listOf(0xFFCFE6EE, 0xFF8FC1D4, 0xFFFFFBEA, 0xFF9BC59D, 0xFF6FA27A, 0xFF4B7F5E, 0xFF2F5D50),
    listOf(0xFFE5DDF5, 0xFFB9A8E0, 0xFFFFF6E5, 0xFF9C8FC4, 0xFF7568A6, 0xFF544A82, 0xFF352F5C),
    listOf(0xFFFCE9C0, 0xFFF2C14E, 0xFFFFFDF2, 0xFFE4A35C, 0xFFC27C3E, 0xFF8E5A33, 0xFF5A3B26),
)

/** A calm landscape, painted fresh for every puzzle. */
private fun paint(size: Int): ImageBitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val p = PALETTES.random().map { it.toInt() }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, 0f, size * 0.7f, p[0], p[1], Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    paint.shader = null
    val sx = size * (0.25f + Random.nextFloat() * 0.5f); val sy = size * (0.25f + Random.nextFloat() * 0.15f)
    paint.color = p[2]; paint.alpha = 90; c.drawCircle(sx, sy, size * 0.2f, paint)
    paint.alpha = 255; c.drawCircle(sx, sy, size * 0.11f, paint)
    for (i in 0 until 4) {
        val base = size * (0.5f + i * 0.13f); val amp = size * (0.07f - i * 0.008f)
        val f = 1.5f + Random.nextFloat() * 2; val ph = Random.nextFloat() * 6
        val path = Path().apply {
            moveTo(0f, size.toFloat())
            var x = 0f
            while (x <= size) {
                lineTo(x, base - sin(x / size * Math.PI.toFloat() * f + ph) * amp - sin(x / size * 9 + ph) * amp * 0.25f)
                x += 4f
            }
            lineTo(size.toFloat(), size.toFloat()); close()
        }
        paint.color = p[3 + i]; c.drawPath(path, paint)
    }
    return bmp.asImageBitmap()
}

@Composable
fun JigsawGame(tier: Tier, onHud: (String) -> Unit) {
    val n = when (tier) { Tier.High -> 3; Tier.Moderate -> 4; Tier.Low -> 5 }
    var round by remember { mutableIntStateOf(0) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableLongStateOf(0L) }
    var done by remember(round) { mutableStateOf(false) }
    var elapsed by remember(round) { mutableLongStateOf(0L) }

    // Layout + pieces, rebuilt when the area or round changes.
    val layout = remember(area, round) {
        if (area.width == 0) return@remember null
        val w = area.width.toFloat(); val h = area.height.toFloat()
        val tall = h > w * 1.1f
        val boardSize = if (tall) minOf(w * 0.66f, h * 0.55f) else minOf(h * 0.86f, w * 0.5f)
        val cell = boardSize / n
        val bx = if (tall) 12f else w * 0.05f
        val by = if (tall) 6f else (h - boardSize) / 2
        val img = paint(boardSize.roundToInt().coerceAtLeast(64))
        val hz = Array(n - 1) { IntArray(n) { if (Random.nextBoolean()) 1 else -1 } }
        val vt = Array(n) { IntArray(n - 1) { if (Random.nextBoolean()) 1 else -1 } }
        val trayX: Float; val trayY: Float; val trayW: Float; val trayH: Float
        if (tall) { trayX = 8f; trayY = by + boardSize + cell * 0.3f; trayW = w - 16 - cell; trayH = h - trayY - cell - 6 }
        else { trayX = bx + boardSize + cell * 0.4f; trayY = h * 0.28f; trayW = w - trayX - cell - 8; trayH = h * 0.72f - cell - 10 }
        val clip = Region(-cell.toInt(), -cell.toInt(), (cell * 2).toInt(), (cell * 2).toInt())
        val pieces = buildList {
            for (r in 0 until n) for (c in 0 until n) {
                val e = intArrayOf(
                    if (r > 0) -vt[c][r - 1] else 0,
                    if (c < n - 1) hz[c][r] else 0,
                    if (r < n - 1) vt[c][r] else 0,
                    if (c > 0) -hz[c - 1][r] else 0,
                )
                add(JPiece(r, c, e).apply {
                    val ap = piecePath(cell, e)
                    path = ap.asComposePath()
                    region = Region().apply { setPath(ap, clip) }
                    x = trayX + Random.nextFloat() * trayW.coerceAtLeast(1f)
                    y = trayY + Random.nextFloat() * trayH.coerceAtLeast(1f)
                })
            }
        }.shuffled().toMutableList()
        Triple(Triple(cell, bx, by), img, pieces)
    }

    LaunchedEffect(round, layout) {
        val t0 = System.currentTimeMillis()
        while (true) androidx.compose.runtime.withFrameMillis {
            frame = it
            if (!done) elapsed = System.currentTimeMillis() - t0
            onHud("${layout?.third?.count { p -> p.locked } ?: 0} / ${n * n} pieces")
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { area = it }) {
        val l = layout ?: return@Box
        val (geo, img, pieces) = l
        val (cell, bx, by) = geo
        var drag by remember(l) { mutableStateOf<JPiece?>(null) }

        Canvas(
            Modifier.fillMaxSize().pointerInput(l) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (done) return@awaitEachGesture
                    val p = pieces.lastOrNull { !it.locked && it.region.contains((down.position.x - it.x).toInt(), (down.position.y - it.y).toInt()) }
                        ?: return@awaitEachGesture
                    pieces.remove(p); pieces.add(p) // bring to front
                    drag = p
                    val dx = down.position.x - p.x; val dy = down.position.y - p.y
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        p.x = ch.position.x - dx; p.y = ch.position.y - dy
                        ch.consume()
                    }
                    drag = null
                    val tx = bx + p.c * cell; val ty = by + p.r * cell
                    if (hypot(p.x - tx, p.y - ty) < cell * 0.32f) {
                        p.x = tx; p.y = ty; p.locked = true
                        if (pieces.all { it.locked }) { done = true; Haptics.success() } else Haptics.thud(0.6f)
                    }
                }
            },
        ) {
            frame
            val board = cell * n
            drawImage(img, dstOffset = IntOffset(bx.toInt(), by.toInt()), dstSize = IntSize(board.toInt(), board.toInt()), alpha = 0.14f)
            drawRect(Cream.copy(alpha = 0.35f), Offset(bx, by), Size(board, board), style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f))))
            val k = img.width / board
            fun drawPiece(p: JPiece, lift: Boolean) = translate(p.x, p.y) {
                if (lift) translate(4f, 8f) { drawPath(p.path, Color.Black.copy(alpha = 0.35f)) }
                clipPath(p.path) {
                    drawImage(
                        img,
                        srcOffset = IntOffset(((p.c * cell - cell * 0.5f) * k).roundToInt(), ((p.r * cell - cell * 0.5f) * k).roundToInt()),
                        srcSize = IntSize((cell * 2 * k).roundToInt(), (cell * 2 * k).roundToInt()),
                        dstOffset = IntOffset((-cell * 0.5f).roundToInt(), (-cell * 0.5f).roundToInt()),
                        dstSize = IntSize((cell * 2).roundToInt(), (cell * 2).roundToInt()),
                    )
                }
                drawPath(p.path, Color.White.copy(alpha = if (p.locked) 0.12f else 0.55f), style = Stroke(1.2f))
            }
            pieces.filter { it.locked }.forEach { drawPiece(it, false) }
            pieces.filter { !it.locked && it !== drag }.forEach { drawPiece(it, false) }
            drag?.let { drawPiece(it, true) }
        }

        if (done) {
            val secs = elapsed / 1000
            GameCard("Solved", "${n * n} pieces in ${secs / 60}:${(secs % 60).toString().padStart(2, '0')}") {
                ScButton("New puzzle", { round++ }, style = Btn.Pop, small = true)
            }
        }
    }
}
