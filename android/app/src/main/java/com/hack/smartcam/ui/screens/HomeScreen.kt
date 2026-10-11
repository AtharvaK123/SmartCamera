package com.hack.smartcam.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.smartcam.data.Health
import com.hack.smartcam.data.HealthPlan
import com.hack.smartcam.data.Repo
import com.hack.smartcam.games.GameHost
import com.hack.smartcam.games.NightInk
import com.hack.smartcam.media.Music
import com.hack.smartcam.net.FoodFacts
import com.hack.smartcam.net.Gemini
import com.hack.smartcam.phone.Reminders
import com.hack.smartcam.ui.BarChart
import com.hack.smartcam.ui.BigNumber
import com.hack.smartcam.ui.Btn
import com.hack.smartcam.ui.Chip
import com.hack.smartcam.ui.ExpandHost
import com.hack.smartcam.ui.ExpandedColumn
import com.hack.smartcam.ui.ExpandedHeader
import com.hack.smartcam.ui.ExpandingGrid
import com.hack.smartcam.ui.Eyebrow
import com.hack.smartcam.ui.FactTeller
import com.hack.smartcam.ui.Fmt
import com.hack.smartcam.ui.GeminiLogo
import com.hack.smartcam.ui.GridCard
import com.hack.smartcam.ui.Ic
import com.hack.smartcam.ui.IconBtn
import com.hack.smartcam.ui.Level
import com.hack.smartcam.ui.LocalWide
import com.hack.smartcam.ui.Meter
import com.hack.smartcam.ui.Notice
import com.hack.smartcam.ui.P
import com.hack.smartcam.ui.ScButton
import com.hack.smartcam.ui.ScCard
import com.hack.smartcam.ui.ScField
import com.hack.smartcam.ui.SectionTitle
import com.hack.smartcam.ui.T
import com.hack.smartcam.ui.Tab
import com.hack.smartcam.ui.Toasts
import com.hack.smartcam.ui.levelColor
import com.hack.smartcam.ui.tourTarget
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun HomeScreen(onGo: (Tab) -> Unit) {
    val s by Repo.state.collectAsState()
    val wide = LocalWide.current
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val pad = if (wide) 24.dp else 16.dp
    val hello = when (LocalTime.now().hour) { in 0..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
    val name = s.profile?.name?.substringBefore(' ')?.takeIf { it.isNotBlank() }

    val cards = listOf(
        GridCard("steps", { StepsFace() }) { StepsOpen(it) },
        GridCard("heart", { HeartFace() }) { HeartOpen(it) { onGo(Tab.Camera) } },
        GridCard("stress", { StressFace() }) { StressOpen(it) },
        GridCard("food", { NutritionFace() }) { NutritionOpen(it) { onGo(Tab.Food) } },
    )

    // The tour spotlights the widgets only while Home is on screen.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { com.hack.smartcam.ui.TourTargets.bounds.remove("grid") } }

    ExpandHost(open != null, onClose = { open = null }) { gridModifier, dim -> androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        // Widgets grow to fill the screen under the greeting (two rows on phones, one on tablets).
        val rows = if (wide) 1 else 2
        val cell = ((maxHeight - 84.dp - 14.dp * (rows - 1)) / rows).coerceIn(if (wide) 200.dp else 184.dp, if (wide) 360.dp else 330.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pad).padding(bottom = 16.dp)) {
            Text(
                "$hello${name?.let { ", $it" } ?: ""} — ${LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US))}",
                style = T.body, color = P.ink2, modifier = Modifier.padding(vertical = 8.dp).alpha(dim),
            )
            ExpandingGrid(
                cards, open, { open = it }, gridModifier.padding(top = 4.dp, bottom = 14.dp).tourTarget("grid"),
                columns = if (wide) 4 else 2, cellHeight = cell,
                expandedHeight = null,
            )
            Column(Modifier.alpha(dim)) {
                NowPlayingCard()
                TodaysPlan(s.plan?.takeIf { it.forDate == Repo.todayKey() })
            }
        }
    } }
}

/* ---------------- small faces ---------------- */

@Composable
private fun Face(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween, content = content)

@Composable
private fun StepsFace() {
    val s by Repo.state.collectAsState()
    val steps = s.steps[Repo.todayKey()] ?: 0
    val goal = s.settings.stepGoal
    val pct = (steps.toFloat() / goal).coerceIn(0f, 1f)
    val (lvl, word) = Fmt.stepsLevel(pct)
    val track = P.card2
    val col = levelColor(lvl)
    val k = cellScale()
    Face {
        Eyebrow("Steps", Ic.steps)
        Box(Modifier.fillMaxWidth().height(100.dp * k), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 9.dp.toPx() * k
                val d = minOf(size.width, size.height) - w
                val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
                drawArc(track, 0f, 360f, false, topLeft = tl, size = Size(d, d), style = Stroke(w))
                drawArc(col, -90f, 360f * pct, false, topLeft = tl, size = Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Fmt.num(steps), style = T.big.copy(fontSize = 21.sp * k), color = P.ink)
                Text("/ ${Fmt.num(goal)}", style = T.mono.copy(fontSize = 11.sp * k), color = P.ink2)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(word, lvl)
            if (!s.stepsFromSensor) Chip("manual", dashed = true)
        }
    }
}

@Composable
private fun HeartFace() {
    val s by Repo.state.collectAsState()
    val v = s.vitals.lastOrNull()
    val lo = 40f; val hi = 140f
    fun pos(x: Float) = ((x - lo) / (hi - lo)).coerceIn(0f, 1f)
    val a = pos(s.settings.hrLow.toFloat()); val b = pos(s.settings.hrHigh.toFloat())
    val bad = P.bad; val good = P.good; val ink = P.ink; val ring = P.sheet
    val k = cellScale()
    val g = minOf(k, 1.25f)
    val day = s.hr.filter { it.t > System.currentTimeMillis() - 86_400_000L }
    Face {
        Eyebrow("Heart rate", Ic.heart)
        Box(Modifier.fillMaxWidth().height(84.dp * g), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 9.dp.toPx()
                val r = minOf(size.width / 2, size.height - w) - w
                val tl = Offset(size.width / 2 - r, w)
                val sz = Size(r * 2, r * 2)
                drawArc(bad, 180f, 180f * a, false, tl, sz, style = Stroke(w))
                drawArc(good, 180f + 180f * a, 180f * (b - a), false, tl, sz, style = Stroke(w))
                drawArc(bad, 180f + 180f * b, 180f * (1 - b), false, tl, sz, style = Stroke(w))
                if (v != null) {
                    val ang = PI * (1 - pos(v.hr.toFloat()))
                    val c = Offset(size.width / 2 + r * cos(ang).toFloat(), w + r - r * sin(ang).toFloat())
                    drawCircle(ring, 9.dp.toPx(), c)
                    drawCircle(ink, 6.dp.toPx(), c)
                }
            }
            if (v != null) BigNumber("${v.hr}", "bpm", (30 * g).toInt()) else Text("—", style = T.big, color = P.ink3)
        }
        if (k > 1.25f && day.size >= 2) Spark(day.map { it.bpm.toFloat() }, P.bad, Modifier.fillMaxWidth().height(26.dp * (k - 0.6f)))
        if (v == null) Chip("Scan to measure", icon = Ic.camera)
        else {
            val out = when { v.hr > s.settings.hrHigh -> "Above range"; v.hr < s.settings.hrLow -> "Below range"; else -> null }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (out != null) Chip(out, Level.Bad) else Chip("In range", Level.Good)
                Text(Fmt.ago(v.t), style = T.small, color = P.ink2, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StressFace() {
    val s by Repo.state.collectAsState()
    val v = s.vitals.lastOrNull()
    val k = cellScale()
    Face {
        Eyebrow("Stress", Ic.wave)
        if (v == null) {
            Icon(Ic.gamepad, null, Modifier.size(34.dp * k).align(Alignment.CenterHorizontally), tint = P.accent)
            Text("Tap to play a calming game", style = T.small, color = P.ink2)
        } else {
            val (lvl, word) = Fmt.stressLevel(v.stress)
            Column {
                BigNumber("${v.stress}", "/ 100", (32 * minOf(k, 1.3f)).toInt())
                Text(if (v.mood != null) "Looked ${v.mood.lowercase()}" else "Lower is calmer", style = T.small, color = P.ink2)
            }
            val recent = s.vitals.takeLast(12)
            if (k > 1.2f && recent.size >= 2) Spark(recent.map { it.stress.toFloat() }, levelColor(lvl), Modifier.fillMaxWidth().height(30.dp * (k - 0.5f)), 0f, 100f)
            Meter(v.stress / 100f, lvl)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(word, lvl)
                Icon(Ic.gamepad, null, Modifier.size(16.dp), tint = P.ink2)
            }
        }
    }
}

@Composable
private fun NutritionFace() {
    val s by Repo.state.collectAsState()
    val today = Fmt.todaysFoods(s)
    val score = FoodFacts.dayScore(today)
    val k = cellScale()
    Face {
        Eyebrow("Nutrition", Ic.leaf)
        if (score == null) {
            Icon(Ic.leaf, null, Modifier.size(34.dp * k).align(Alignment.CenterHorizontally), tint = P.accent)
            Text("Log a meal to get a score", style = T.small, color = P.ink2)
        } else {
            val (lvl, word) = Fmt.foodLevel(score)
            val t = Health.totals(today)
            Column {
                BigNumber("$score", "/ 100", (32 * minOf(k, 1.3f)).toInt())
                Text("${t.kcal.roundToInt()} kcal · ${today.size} item${if (today.size == 1) "" else "s"}", style = T.small, color = P.ink2)
            }
            if (k > 1.2f) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("Protein" to t.protein, "Carbs" to t.carbs, "Fat" to t.fat).forEach { (n, g) ->
                    Row {
                        Text(n, style = T.small, color = P.ink2, modifier = Modifier.weight(1f))
                        Text("${g.roundToInt()} g", style = T.mono.copy(fontSize = 12.sp), color = P.ink)
                    }
                }
            }
            Meter(score / 100f, lvl)
            Chip(word, lvl)
        }
    }
}

/** How much taller than the classic 184 dp the widget cells are. */
@Composable
private fun cellScale() = (com.hack.smartcam.ui.LocalCellHeight.current / 184.dp).coerceIn(1f, 1.7f)

/** A small trend line, softly filled underneath. */
@Composable
private fun Spark(values: List<Float>, color: androidx.compose.ui.graphics.Color, modifier: Modifier, min: Float? = null, max: Float? = null) {
    Canvas(modifier) {
        val lo = min ?: values.min()
        val hi = (max ?: values.max()).let { if (it - lo < 1f) lo + 1f else it }
        val pts = values.mapIndexed { i, v -> Offset(size.width * i / (values.size - 1), size.height * (1 - (v - lo) / (hi - lo)).coerceIn(0.04f, 0.96f)) }
        val line = androidx.compose.ui.graphics.Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        val fill = androidx.compose.ui.graphics.Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawCircle(color, 3.dp.toPx(), pts.last())
    }
}

/* ---------------- opened widgets ---------------- */

@Composable
private fun OpenColumn(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    ExpandedColumn(title, icon, close, content)

@Composable
private fun StepsOpen(close: () -> Unit) {
    val s by Repo.state.collectAsState()
    var manual by remember { mutableStateOf((s.steps[Repo.todayKey()] ?: 0).toString()) }
    val goal = s.settings.stepGoal
    fun setGoal(g: Int) = Repo.update { it.copy(settings = it.settings.copy(stepGoal = g.coerceIn(1000, 40000))) }
    val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
    val values = days.map { (s.steps[it.toString()] ?: 0).toFloat() }
    val hit = values.count { it >= goal }

    OpenColumn("Steps this week", Ic.steps, close) {
        Row(verticalAlignment = Alignment.Bottom) {
            BigNumber(Fmt.num(values.last().toInt()), "today", 28)
            Spacer(Modifier.weight(1f))
            Text("avg ${Fmt.num(values.average().toInt())} · goal hit $hit/7", style = T.mono, color = P.ink2)
        }
        BarChart(values, days.map { Fmt.weekday(it) }, { "${Fmt.num(it.toInt())} steps" }, ref = goal.toFloat(), refLabel = "goal", height = 120.dp)
        Text("Your daily goal", style = T.h3, color = P.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBtn(Ic.minus, "Lower goal", { setGoal(goal - 500) }, bg = P.card2)
            Text(Fmt.num(goal), style = T.big.copy(fontSize = 26.sp), color = P.ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconBtn(Ic.plus, "Raise goal", { setGoal(goal + 500) }, bg = P.card2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(6000, 8000, 10000, 12000).forEach { g ->
                ScButton("${g / 1000}k", { setGoal(g) }, Modifier.weight(1f), style = if (g == goal) Btn.Primary else Btn.Ghost, small = true)
            }
        }
        if (!s.stepsFromSensor) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScField(manual, { manual = it.filter(Char::isDigit).take(6) }, Modifier.weight(1f), label = "Today's steps", number = true)
                ScButton("Save", { Repo.update { it.copy(steps = it.steps + (Repo.todayKey() to (manual.toIntOrNull() ?: 0))) }; Toasts.show("Saved") }, small = true)
            }
            Text("Allow Physical activity in Settings and your phone counts steps for you.", style = T.small, color = P.ink2)
        }
    }
}

@Composable
private fun HeartOpen(close: () -> Unit, goScan: () -> Unit) {
    val s by Repo.state.collectAsState()
    val since = System.currentTimeMillis() - 24 * 3600_000L
    val points = s.hr.filter { it.t >= since }.ifEmpty { s.vitals.filter { it.t >= since }.map { com.hack.smartcam.data.HrPoint(it.t, it.hr) } }
    OpenColumn("Heart rate · last 24 h", Ic.heart, close) {
        if (points.isEmpty()) {
            Text("No readings in the last day.", style = T.body, color = P.ink2)
            ScButton("Scan now", { close(); goScan() }, icon = Ic.camera)
            return@OpenColumn
        }
        val lo = points.minBy { it.bpm }
        val hi = points.maxBy { it.bpm }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MinMax("Min", lo.bpm, lo.t, P.good, Modifier.weight(1f))
            MinMax("Max", hi.bpm, hi.t, if (hi.bpm > s.settings.hrHigh) P.bad else P.warn, Modifier.weight(1f))
            MinMax("Average", points.map { it.bpm }.average().roundToInt(), null, P.accent, Modifier.weight(1f))
        }
        HrChart(points, s.settings.hrLow, s.settings.hrHigh, since)
        Text("${points.size} reading${if (points.size == 1) "" else "s"} from your camera scans. The shaded band is your normal range (${s.settings.hrLow}–${s.settings.hrHigh} bpm).", style = T.small, color = P.ink2)
    }
}

@Composable
private fun MinMax(label: String, bpm: Int, t: Long?, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(P.card2).padding(10.dp)) {
        Eyebrow(label, color = color)
        BigNumber("$bpm", "bpm", 22)
        Text(t?.let { Fmt.clock(it) } ?: "24 h", style = T.mono.copy(fontSize = 11.sp), color = P.ink2)
    }
}

/** Heart rate over the last day: a line through every sample, with the normal range shaded. */
@Composable
private fun HrChart(points: List<com.hack.smartcam.data.HrPoint>, low: Int, high: Int, since: Long) {
    val p = P
    val measurer = rememberTextMeasurer()
    val top = maxOf(points.maxOf { it.bpm }, high) + 10
    val bottom = minOf(points.minOf { it.bpm }, low) - 10
    val span = 24 * 3600_000f
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val left = 30.dp.toPx()
        val w = size.width - left
        val h = size.height - 18.dp.toPx()
        fun x(t: Long) = left + w * ((t - since) / span).coerceIn(0f, 1f)
        fun y(b: Int) = h * (1 - (b - bottom).toFloat() / (top - bottom))
        drawRect(p.good.copy(alpha = 0.10f), Offset(left, y(high)), Size(w, y(low) - y(high)))
        listOf(low, high).forEach { v ->
            drawLine(p.ink3.copy(alpha = 0.5f), Offset(left, y(v)), Offset(size.width, y(v)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            drawText(measurer, "$v", Offset(0f, y(v) - 8.dp.toPx()), TextStyle(fontSize = 10.sp, color = p.ink2))
        }
        // Time labels every 6 hours.
        for (k in 0..4) {
            val t = since + k * 6 * 3600_000L
            drawText(measurer, if (k == 4) "now" else Fmt.clock(t).replace(":00", ""), Offset((x(t) - 14.dp.toPx()).coerceIn(left, size.width - 34.dp.toPx()), h + 3.dp.toPx()), TextStyle(fontSize = 10.sp, color = p.ink2))
        }
        val sorted = points.sortedBy { it.t }
        val path = Path()
        sorted.forEachIndexed { i, pt ->
            // Break the line between scans more than 20 minutes apart.
            if (i == 0 || pt.t - sorted[i - 1].t > 20 * 60_000) path.moveTo(x(pt.t), y(pt.bpm)) else path.lineTo(x(pt.t), y(pt.bpm))
        }
        drawPath(path, p.bad, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        sorted.forEach { pt -> drawCircle(p.bad, 3.dp.toPx(), Offset(x(pt.t), y(pt.bpm))) }
    }
}

@Composable
private fun StressOpen(close: () -> Unit) {
    val s by Repo.state.collectAsState()
    val stress = s.vitals.lastOrNull()?.stress ?: 50
    Column(Modifier.fillMaxWidth()) {
        ExpandedHeader("Unwind", Ic.gamepad, close)
        Text(
            if (s.vitals.isEmpty()) "No scan yet, so the games are set for medium stress." else "Tuned to your last stress reading ($stress/100).",
            style = T.small, color = P.ink2, modifier = Modifier.padding(horizontal = 16.dp),
        )
        // A full-size play area (Tetris gets ~14 rows) instead of squeezing into the grid.
        Box(Modifier.fillMaxWidth().height(470.dp).padding(10.dp).clip(RoundedCornerShape(18.dp)).background(NightInk)) {
            GameHost(stress, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun NutritionOpen(close: () -> Unit, goFood: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Repo.state.collectAsState()
    var making by remember { mutableStateOf(false) }
    val today = Fmt.todaysFoods(s)
    val t = Health.totals(today)
    val l = Health.limits(s)
    val b = Health.burned(s)
    val enough = Health.exercisedEnough(s)
    val upcoming = s.plan?.takeIf { it.forDate >= Repo.todayKey() }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun makePlan() {
        if (!Gemini.available) { Toasts.show("Gemini isn't available right now", true); return }
        making = true
        scope.launch {
            runCatching { Health.makePlan(Repo.state.value) }
                .onSuccess { plan -> Repo.update { it.copy(plan = plan) }; Toasts.show("Your plan for tomorrow is ready") }
                .onFailure { Toasts.show("Couldn't make a plan (${it.message})", true) }
            making = false
        }
    }

    OpenColumn("Today's food & activity", Ic.leaf, close) {
        Text("Eaten today${if (l.fromPlan) " · limits from your plan" else ""}", style = T.h3, color = P.ink)
        NutrientRow("Calories", t.kcal, l.kcal.toDouble(), "kcal", max = true)
        NutrientRow("Protein", t.protein, l.proteinMin.toDouble(), "g", max = false)
        NutrientRow("Sugar", t.sugar, l.sugarMax.toDouble(), "g", max = true)
        NutrientRow("Fat", t.fat, l.fatMax.toDouble(), "g", max = true)
        NutrientRow("Salt", t.salt, l.saltMax, "g", max = true)
        Text("Carbs ${t.carbs.roundToInt()} g · fibre ${t.fiber.roundToInt()} g · ${today.size} item${if (today.size == 1) "" else "s"}", style = T.mono.copy(fontSize = 12.sp), color = P.ink2)
        if (today.isEmpty()) ScButton("Log food", { close(); goFood() }, icon = Ic.plus, style = Btn.Ghost, small = true)

        Text("Burned today", style = T.h3, color = P.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BurnStat("Resting", b.resting, Modifier.weight(1f))
            BurnStat("Moving", b.active, Modifier.weight(1f))
            BurnStat("Total", b.total, Modifier.weight(1f))
        }
        val net = t.kcal.roundToInt() - b.total
        Text(
            if (net > 0) "You've eaten $net kcal more than you've burned so far." else "You've burned ${-net} kcal more than you've eaten so far.",
            style = T.small, color = P.ink2,
        )
        if (enough) Notice("Nice — that's a good amount of movement today.", null, Ic.check)
        else Notice("Not much exercise yet today (${b.active} of ${l.activeKcal} kcal from moving).", Level.Warn, Ic.flame)

        when {
            making -> ScCard(shadow = false) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeminiLogo(18.dp)
                    Text("Gemini is reading your chats and today's numbers…", style = T.small, color = P.ink2)
                }
                Spacer(Modifier.height(10.dp))
                FactTeller()
            }
            upcoming != null -> PlanCard(upcoming, onRemind = {
                if (Build.VERSION.SDK_INT >= 33) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                val n = Reminders.schedule(ctx, upcoming)
                Toasts.show(if (n > 0) "$n reminder${if (n == 1) "" else "s"} set" else "Those times have already passed")
            }, onRedo = { makePlan() })
            !enough && s.planDeclined != Repo.todayKey() -> ScCard(shadow = false) {
                Text("Want a plan for tomorrow?", style = T.h3, color = P.ink)
                Text("Gemini looks at your chats, food and activity and sets a simple schedule plus food limits. SmartCam warns you when a new food goes over them.", style = T.small, color = P.ink2)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScButton("Yes, make one", { makePlan() }, Modifier.weight(1f), icon = Ic.calendar, small = true)
                    ScButton("Not now", { Repo.update { it.copy(planDeclined = Repo.todayKey()) } }, style = Btn.Ghost, small = true)
                }
            }
            else -> ScButton("Make a plan for tomorrow", { makePlan() }, Modifier.fillMaxWidth(), icon = Ic.calendar, style = Btn.Ghost, small = true)
        }
    }
}

@Composable
private fun NutrientRow(label: String, value: Double, limit: Double, unit: String, max: Boolean) {
    val frac = if (limit > 0) (value / limit).toFloat() else 0f
    val lvl = if (max) when { frac > 1f -> Level.Bad; frac > 0.85f -> Level.Warn; else -> Level.Good }
    else when { frac >= 1f -> Level.Good; frac >= 0.5f -> Level.Warn; else -> Level.Bad }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, style = T.bodyStrong, color = P.ink, modifier = Modifier.weight(1f))
            val v = if (unit == "g" && limit < 10) "%.1f".format(value) else value.roundToInt().toString()
            val lim = if (unit == "g" && limit < 10) "%.1f".format(limit) else limit.roundToInt().toString()
            Text("$v / $lim $unit${if (max) "" else " min"}", style = T.mono.copy(fontSize = 12.sp), color = if (lvl == Level.Bad && max) P.bad else P.ink2)
        }
        Meter(frac, lvl)
    }
}

@Composable
private fun BurnStat(label: String, kcal: Int, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(P.card2).padding(10.dp)) {
        Eyebrow(label)
        BigNumber(Fmt.num(kcal), "kcal", 20)
    }
}

@Composable
private fun PlanCard(plan: HealthPlan, onRemind: () -> Unit, onRedo: () -> Unit) {
    ScCard(shadow = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GeminiLogo(18.dp)
            Text(if (plan.forDate == Repo.todayKey()) "Today's plan" else "Tomorrow's plan", style = T.h3, color = P.ink, modifier = Modifier.weight(1f))
        }
        if (plan.summary.isNotBlank()) Text(plan.summary, style = T.small, color = P.ink2, modifier = Modifier.padding(top = 6.dp))
        Text(
            "${Fmt.num(plan.kcal)} kcal · protein ≥ ${plan.proteinMin} g · sugar ≤ ${plan.sugarMax} g · fat ≤ ${plan.fatMax} g · burn ${plan.activeKcal} kcal moving",
            style = T.mono.copy(fontSize = 11.sp), color = P.ink, modifier = Modifier.padding(vertical = 8.dp),
        )
        PlanItems(plan)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScButton("Remind me", onRemind, Modifier.weight(1f), icon = Ic.alarm, small = true)
            ScButton("Redo", onRedo, icon = Ic.refresh, style = Btn.Ghost, small = true)
        }
    }
}

@Composable
private fun PlanItems(plan: HealthPlan) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        plan.items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Fmt.time12(item.time), style = T.mono.copy(fontSize = 12.sp), color = P.ink2, modifier = Modifier.width(62.dp))
                Box(Modifier.padding(top = 5.dp).size(8.dp).clip(RoundedCornerShape(4.dp)).background(
                    when (item.kind) { "exercise" -> P.bad; "meal" -> P.good; else -> P.accent },
                ))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = T.bodyStrong, color = P.ink)
                    if (item.detail.isNotBlank()) Text(item.detail, style = T.small, color = P.ink2)
                }
            }
        }
    }
}

/** On the day a plan is for, it shows on Home under the widgets. */
@Composable
private fun TodaysPlan(plan: HealthPlan?) {
    plan ?: return
    SectionTitle("Today's plan")
    ScCard { PlanItems(plan) }
}

/** What SmartCam is playing, with controls. */
@Composable
fun NowPlayingCard(modifier: Modifier = Modifier) {
    val now by Music.now.collectAsState()
    val mood by Music.mood.collectAsState()
    val n = now ?: return
    ScCard(modifier.fillMaxWidth(), padding = 10.dp) {
        MiniPlayer(n, mood?.label)
    }
}

@Composable
fun CenteredEmpty(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(40.dp), tint = P.ink3)
        Spacer(Modifier.height(8.dp))
        Text(title, style = T.h2, color = P.ink, textAlign = TextAlign.Center)
        Text(text, style = T.body, color = P.ink2, textAlign = TextAlign.Center)
    }
}
