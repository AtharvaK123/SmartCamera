package com.hack.smartcam.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.smartcam.net.Voice
import com.hack.smartcam.ui.Brand
import com.hack.smartcam.ui.Btn
import com.hack.smartcam.ui.Display
import com.hack.smartcam.ui.Haptics
import com.hack.smartcam.ui.Mono
import com.hack.smartcam.ui.ScButton
import com.hack.smartcam.ui.Segmented
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlinx.coroutines.launch

/** [count] teardrop petals around [c], hugging a circle of radius [r]. */
private fun DrawScope.petals(c: Offset, r: Float, count: Int, length: Float, width: Float, angle: Float, from: Color, to: Color) {
    for (k in 0 until count) {
        rotate(angle + k * 360f / count, pivot = c) {
            val top = c.y - r - length * 0.8f
            drawOval(
                Brush.verticalGradient(listOf(from, to), startY = top, endY = top + length),
                topLeft = Offset(c.x - width / 2, top),
                size = androidx.compose.ui.geometry.Size(width, length),
            )
        }
    }
}

private data class Plan(val name: String, val minutes: Int, val steps: List<Pair<String, Int>>)

/** Guided breathing. High stress → long exhales for 3 min; moderate → box breathing; low → even breathing. */
@Composable
fun MeditationGame(tier: Tier, onHud: (String) -> Unit) {
    val plan = when (tier) {
        Tier.High -> Plan("Long exhale", 3, listOf("Breathe in" to 4, "Breathe out" to 6))
        Tier.Moderate -> Plan("Box breathing", 2, listOf("Breathe in" to 4, "Hold" to 4, "Breathe out" to 4, "Hold" to 4))
        Tier.Low -> Plan("Even breathing", 1, listOf("Breathe in" to 5, "Breathe out" to 5))
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    var minutes by remember { mutableIntStateOf(plan.minutes) }
    var stage by remember { mutableStateOf("intro") } // intro | running | done
    var t0 by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    var lastStep by remember { mutableIntStateOf(-1) }
    val cycle = plan.steps.sumOf { it.second }

    fun phase(sec: Float): Triple<Int, Float, Float> { // index, progress in step, seconds left in step
        var t = sec % cycle
        plan.steps.forEachIndexed { i, (_, d) -> if (t < d) return Triple(i, t / d, d - t); t -= d }
        return Triple(0, 0f, plan.steps[0].second.toFloat())
    }

    LaunchedEffect(Unit) {
        while (true) withFrameMillis { ms ->
            now = ms
            if (stage == "running") {
                val sec = (ms - t0) / 1000f
                val total = minutes * 60
                val (i, _, _) = phase(sec)
                if (i != lastStep) { lastStep = i; if (plan.steps[i].first != "Hold") Haptics.breath() }
                val left = (total - sec).coerceAtLeast(0f).toInt()
                onHud("${left / 60}:${(left % 60).toString().padStart(2, '0')} left · ${plan.name}")
                if (sec >= total) {
                    stage = "done"
                    Haptics.success()
                    scope.launch { Voice.speak(ctx, "Nicely done. Take a moment, then scan again to see how you feel.") }
                }
            } else onHud(plan.name)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width * 0.45f; val cy = size.height * 0.5f
            val max = min(size.width, size.height) * 0.24f; val minR = max * 0.45f
            val sec = if (stage == "running") (now - t0) / 1000f else 0f
            val (i, p, left) = phase(sec)
            val ease = (0.5f - 0.5f * cos(PI.toFloat() * p))
            val label = plan.steps[i].first
            val r = when {
                stage != "running" -> minR + (max - minR) * 0.3f
                label == "Breathe in" -> minR + (max - minR) * ease
                label == "Breathe out" -> max - (max - minR) * ease
                else -> if (plan.steps[(i - 1 + plan.steps.size) % plan.steps.size].first == "Breathe in") max else minR
            }
            val c = Offset(cx, cy)
            val spin = (now % 120_000L) / 120_000f * 360f // one slow turn every two minutes
            for (k in 3 downTo 1) drawCircle(Brand.sky.copy(alpha = 0.05f * k), r + k * 16.dp.toPx(), c)
            // Mandala: two rings of petals that open and close with the breath and turn slowly in opposite directions.
            petals(c, r, count = 16, length = r * 0.95f, width = r * 0.30f, angle = -spin * 0.6f, from = Brand.mauve.copy(alpha = 0f), to = Brand.mauve.copy(alpha = 0.32f))
            petals(c, r, count = 10, length = r * 0.62f, width = r * 0.36f, angle = spin, from = Brand.sky.copy(alpha = 0.05f), to = Brand.blush.copy(alpha = 0.55f))
            // A ring of twinkling dots further out.
            val dots = 28
            for (k in 0 until dots) {
                val a = Math.toRadians((k * 360.0 / dots) + spin * 0.3)
                val twinkle = 0.35f + 0.35f * kotlin.math.sin(now / 600.0 + k).toFloat()
                drawCircle(Cream.copy(alpha = twinkle * 0.6f), 2.dp.toPx(), Offset(cx + (r * 1.75f) * kotlin.math.cos(a).toFloat(), cy + (r * 1.75f) * kotlin.math.sin(a).toFloat()))
            }
            drawCircle(Brush.radialGradient(listOf(Brand.blush, Brand.sky, Brand.slate), Offset(cx - r * 0.3f, cy - r * 0.3f), r * 1.3f), r, c)
            if (stage == "running") {
                val t = measurer.measure(label, TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Cream, textAlign = TextAlign.Center))
                drawText(t, topLeft = Offset(cx - t.size.width / 2, cy - t.size.height))
                val c = measurer.measure("${left.toInt() + 1}", TextStyle(fontFamily = Mono, fontSize = 14.sp, color = Cream.copy(alpha = 0.75f)))
                drawText(c, topLeft = Offset(cx - c.size.width / 2, cy + 6.dp.toPx()))
            }
        }

        when (stage) {
            "intro" -> GameCard(plan.name, plan.steps.joinToString(" · ") { "${it.first.removePrefix("Breathe ")} ${it.second}s" }) {
                Segmented(listOf("1" to "1 min", "2" to "2 min", "3" to "3 min", "5" to "5 min"), minutes.toString(), onDark = true) { minutes = it.toInt() }
                ScButton("Begin", {
                    stage = "running"; t0 = now; lastStep = -1
                    scope.launch { Voice.speak(ctx, "Let's breathe together for $minutes minute${if (minutes > 1) "s" else ""}. Follow the circle.") }
                }, style = Btn.Pop, small = true)
            }
            "done" -> GameCard("Session complete", "$minutes min of ${plan.name.lowercase()}") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScButton("Again", { stage = "intro" }, style = Btn.Ghost, small = true)
                }
            }
        }
    }
}
