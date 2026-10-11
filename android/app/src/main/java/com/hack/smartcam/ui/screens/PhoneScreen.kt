package com.hack.smartcam.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hack.smartcam.data.Alarm
import com.hack.smartcam.data.Repo
import com.hack.smartcam.phone.AppUsage
import com.hack.smartcam.phone.Alarms
import com.hack.smartcam.phone.GuardService
import com.hack.smartcam.phone.SleepNight
import com.hack.smartcam.phone.Usage
import com.hack.smartcam.ui.BarChart
import com.hack.smartcam.ui.BigNumber
import com.hack.smartcam.ui.Btn
import com.hack.smartcam.ui.Chip
import com.hack.smartcam.ui.ExpandHost
import com.hack.smartcam.ui.ExpandedColumn
import com.hack.smartcam.ui.ExpandingGrid
import com.hack.smartcam.ui.Eyebrow
import com.hack.smartcam.ui.GridCard
import com.hack.smartcam.ui.Fmt
import com.hack.smartcam.ui.HBars
import com.hack.smartcam.ui.Ic
import com.hack.smartcam.ui.IconBtn
import com.hack.smartcam.ui.Level
import com.hack.smartcam.ui.LocalWide
import com.hack.smartcam.ui.Notice
import com.hack.smartcam.ui.P
import com.hack.smartcam.ui.ScButton
import com.hack.smartcam.ui.ScCard
import com.hack.smartcam.ui.ScField
import com.hack.smartcam.ui.ScSheet
import com.hack.smartcam.ui.SectionTitle
import com.hack.smartcam.ui.T
import com.hack.smartcam.ui.Toasts
import com.hack.smartcam.ui.Toggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime

private data class PhoneData(
    val access: Boolean = false,
    val today: List<AppUsage> = emptyList(),
    val week: List<Int> = emptyList(),
    val sleep: List<SleepNight> = emptyList(),
)

private suspend fun loadPhone(context: Context) = withContext(Dispatchers.IO) {
    if (!Usage.hasAccess(context)) PhoneData()
    else PhoneData(true, Usage.apps(context, Usage.startOfDay()), Usage.week(context), Usage.sleep(context))
}

@Composable
fun PhoneScreen() {
    val ctx = LocalContext.current
    val s by Repo.state.collectAsState()
    val wide = LocalWide.current
    var data by remember { mutableStateOf<PhoneData?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<String?>(null) }

    // Reload when the tab opens and whenever the user comes back from Settings.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(refresh) { data = loadPhone(ctx) }

    val d = data ?: PhoneData()
    val avg = if (d.week.isNotEmpty()) d.week.average().toInt() else 0
    val next = nextAlarm(s.alarms)
    val lastNight = d.sleep.lastOrNull()
    val overLimit = d.today.count { a -> s.settings.appLimits[a.pkg]?.let { a.minutes >= it } == true }

    val cards = listOf(
        GridCard("screen", {
            PhoneFace(Ic.screen, if (d.access) Fmt.minutes(avg) else "—", "Avg screen time", extra = if (d.access && d.week.isNotEmpty()) { { MiniBars(d.week) } } else null)
        }) { ScreenTimeOpen(d, it) },
        GridCard("alarms", {
            PhoneFace(Ic.alarm, next?.let { Fmt.minutes(it.second) } ?: "Off", next?.let { "Next alarm · ${Fmt.time12(it.first.time)}" } ?: "No alarms on", extra = {
                MiniList(s.alarms.filter { it.on }.sortedBy { it.time }.map { a -> Fmt.time12(a.time) + (a.label.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") }, "Tap to add one")
            })
        }) { AlarmsOpen(it) },
        GridCard("sleep", {
            PhoneFace(Ic.moon, lastNight?.let { Fmt.minutes(it.minutes) } ?: "—", lastNight?.let { "Slept ${Fmt.clock(it.start)}–${Fmt.clock(it.end)}" } ?: "Sleep",
                extra = {
                    if (d.sleep.size >= 2) MiniBars(d.sleep.takeLast(7).map { it.minutes })
                    else MiniList(emptyList(), if (d.access) "Shows after a night with your phone put down" else "Allow Usage access to see your sleep")
                })
        }) { SleepOpen(d, it) },
        GridCard("limits", {
            PhoneFace(Ic.lock, if (s.settings.guardOn) "On" else "Off", "Screen lock · ${s.settings.appLimits.size} limit${if (s.settings.appLimits.size == 1) "" else "s"}", extra = {
                MiniList(listOf("Locks at ${Fmt.time12(s.settings.sleepLock)}") + d.today.filter { s.settings.appLimits[it.pkg] != null }.map { "${it.label} · ${it.minutes}/${s.settings.appLimits[it.pkg]} min" }, "")
            })
        }) {
            LimitsOpen(d) { it(); refresh++ }
        },
    )

    ExpandHost(open != null, onClose = { open = null }) { gridModifier, dim -> androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        // Widgets grow to fill the screen (two rows on phones, one on tablets).
        val rows = if (wide) 1 else 2
        val notices = (if (data != null && !d.access) 84.dp else 0.dp) + (if (overLimit > 0 && !s.settings.guardOn) 76.dp else 0.dp)
        val cell = ((maxHeight - 40.dp - notices - 14.dp * (rows - 1)) / rows).coerceIn(160.dp, if (wide) 340.dp else 320.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 24.dp else 16.dp).padding(bottom = 16.dp)) {
            if (data != null && !d.access) {
                Notice("Allow SmartCam under Usage access to see screen time and sleep.", Level.Warn, Ic.lock, Modifier.padding(top = 8.dp).alpha(dim)) {
                    ScButton("Allow", { Usage.openAccessSettings(ctx) }, small = true)
                }
            }
            ExpandingGrid(
                cards, open, { open = it }, gridModifier.padding(top = 8.dp, bottom = 14.dp),
                columns = if (wide) 4 else 2, cellHeight = cell,
            )
            if (overLimit > 0 && !s.settings.guardOn) Notice(
                "$overLimit app${if (overLimit > 1) "s are" else " is"} over today's limit. Turn on the screen lock to enforce it.", Level.Warn,
                modifier = Modifier.alpha(dim),
            )
        }
    } }
}

private fun nextAlarm(alarms: List<Alarm>): Pair<Alarm, Int>? {
    val now = LocalTime.now().let { it.hour * 60 + it.minute }
    return alarms.filter { it.on }.map { a ->
        val t = LocalTime.parse(a.time).let { it.hour * 60 + it.minute }
        a to (t - now).let { if (it <= 0) it + 1440 else it }
    }.minByOrNull { it.second }
}

@Composable
private fun PhoneFace(icon: ImageVector, value: String, label: String, extra: (@Composable () -> Unit)? = null) {
    val k = (com.hack.smartcam.ui.LocalCellHeight.current / 160.dp).coerceIn(1f, 1.8f)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Icon(icon, null, Modifier.size(28.dp * minOf(k, 1.3f)), tint = P.accent)
        if (k > 1.25f && extra != null) Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 10.dp), contentAlignment = Alignment.Center) { extra() }
        Column {
            BigNumber(value, size = (28 * minOf(k, 1.25f)).toInt())
            Spacer(Modifier.height(4.dp))
            Eyebrow(label)
        }
    }
}

/** Seven slim bars, today's highlighted. */
@Composable
private fun MiniBars(values: List<Int>) {
    if (values.isEmpty()) return
    val top = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    val on = P.accent; val off = P.accent.copy(alpha = 0.35f)
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        val n = values.size
        val gap = 6.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        values.forEachIndexed { i, v ->
            val h = (size.height * v / top).coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                if (i == n - 1) on else off,
                topLeft = androidx.compose.ui.geometry.Offset(i * (bw + gap), size.height - h),
                size = androidx.compose.ui.geometry.Size(bw, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
            )
        }
    }
}

@Composable
private fun MiniList(lines: List<String>, empty: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (lines.isEmpty()) Text(empty, style = T.small, color = P.ink3)
        lines.take(3).forEach { Text(it, style = T.small, color = P.ink2, maxLines = 1) }
    }
}

private fun pickTime(context: Context, initial: String, onPick: (String) -> Unit) {
    val t = runCatching { LocalTime.parse(initial) }.getOrDefault(LocalTime.of(7, 0))
    TimePickerDialog(context, { _, h, m -> onPick("%02d:%02d".format(h, m)) }, t.hour, t.minute, false).show()
}

@Composable
private fun ScreenTimeOpen(d: PhoneData, onClose: () -> Unit) {
    ExpandedColumn("Screen time", Ic.screen, onClose) {
        if (!d.access) { Notice("Allow Usage access to see this.", Level.Warn); return@ExpandedColumn }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column { Eyebrow("Today"); BigNumber(Fmt.minutes(d.today.sumOf { it.minutes })) }
            Column { Eyebrow("Daily average"); BigNumber(Fmt.minutes(d.week.average().toInt())) }
        }
        SectionTitle("Today by app")
        if (d.today.isEmpty()) Text("No app has been open for a full minute yet today.", style = T.small, color = P.ink2)
        else HBars(d.today.take(10).map { it.label to it.minutes }, Fmt::minutes)
        SectionTitle("This week")
        val days = (6 downTo 0).map { java.time.LocalDate.now().minusDays(it.toLong()) }
        BarChart(d.week.map { it.toFloat() }, days.map { Fmt.weekday(it) }, { Fmt.minutes(it.toInt()) }, ref = 240f, refLabel = "4h target")
    }
}

@Composable
private fun AlarmsOpen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val s by Repo.state.collectAsState()
    var time by remember { mutableStateOf("07:00") }
    var label by remember { mutableStateOf("") }
    ExpandedColumn("Alarms", Ic.alarm, onClose) {
        s.alarms.sortedBy { it.time }.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BigNumber(Fmt.time12(a.time), size = 24)
                    Text(a.label, style = T.small, color = P.ink2)
                }
                Toggle(a.on) { on -> Repo.update { st -> st.copy(alarms = st.alarms.map { if (it.id == a.id) it.copy(on = on) else it }) } }
                IconBtn(Ic.trash, "Delete alarm", { Repo.update { st -> st.copy(alarms = st.alarms.filterNot { it.id == a.id }) } })
            }
        }
        ScCard(shadow = false) {
            Text("New alarm", style = T.h3, color = P.ink)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ScButton(Fmt.time12(time), { pickTime(ctx, time) { time = it } }, icon = Ic.alarm, style = Btn.Ghost, small = true)
                ScField(label, { label = it.take(40) }, Modifier.weight(1f), placeholder = "Label")
            }
            Spacer(Modifier.height(10.dp))
            ScButton("Add alarm", {
                val l = label.trim().ifEmpty { "Alarm" }
                Repo.update { it.copy(alarms = it.alarms + Alarm(Repo.newId("a"), time, l)) }
                val (h, m) = time.split(':').map(String::toInt)
                if (Alarms.set(ctx, h, m, l)) Toasts.show("Alarm set for ${Fmt.time12(time)} in your Clock app")
                else Toasts.show("No Clock app could set the alarm", true)
                label = ""
            }, Modifier.fillMaxWidth(), icon = Ic.plus)
        }
        Notice("Alarms are added to your Clock app so they ring even when SmartCam is closed. Turning one off here doesn't remove it from Clock.")
        ScButton("Open Clock app", { Alarms.openClock(ctx) }, Modifier.fillMaxWidth(), icon = Ic.alarm, style = Btn.Ghost)
    }
}

@Composable
private fun SleepOpen(d: PhoneData, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val s by Repo.state.collectAsState()
    ExpandedColumn("Sleep", Ic.moon, onClose) {
        Text("Sleep is the longest stretch your phone stayed off each night (8 pm – noon).", style = T.body, color = P.ink2)
        if (!d.access) Notice("Allow Usage access to see your sleep history.", Level.Warn)
        else if (d.sleep.isEmpty()) Notice("No nights recorded yet. Check back tomorrow morning.")
        else {
            val avg = d.sleep.map { it.minutes }.average().toInt()
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column { Eyebrow("Last night"); BigNumber(Fmt.minutes(d.sleep.last().minutes)) }
                Column { Eyebrow("Average"); BigNumber(Fmt.minutes(avg)) }
            }
            BarChart(d.sleep.map { it.minutes / 60f }, d.sleep.map { Fmt.weekday(it.date) }, { "%.1f h".format(it) }, ref = 8f, refLabel = "8 h")
            d.sleep.reversed().forEach { n ->
                Row {
                    Text(Fmt.weekday(n.date), style = T.body, color = P.ink, modifier = Modifier.weight(1f))
                    Text("${Fmt.clock(n.start)} – ${Fmt.clock(n.end)}  ·  ${Fmt.minutes(n.minutes)}", style = T.mono, color = P.ink2)
                }
            }
        }
        SectionTitle("Bedtime lock")
        Text("Between these times SmartCam locks the screen if you pick up your phone.", style = T.small, color = P.ink2)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScButton("Lock ${Fmt.time12(s.settings.sleepLock)}", {
                pickTime(ctx, s.settings.sleepLock) { t -> Repo.update { it.copy(settings = it.settings.copy(sleepLock = t)) } }
            }, Modifier.weight(1f), icon = Ic.moon, style = Btn.Ghost, small = true)
            ScButton("Unlock ${Fmt.time12(s.settings.sleepUnlock)}", {
                pickTime(ctx, s.settings.sleepUnlock) { t -> Repo.update { it.copy(settings = it.settings.copy(sleepUnlock = t)) } }
            }, Modifier.weight(1f), icon = Ic.alarm, style = Btn.Ghost, small = true)
        }
        GuardSwitch()
    }
}

/** Turns the screen lock on: needs device admin (lock only), usage access and notifications. */
@Composable
fun GuardSwitch() {
    val ctx = LocalContext.current
    val s by Repo.state.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    val admin = remember(tick) { GuardService.adminActive(ctx) }
    val usage = remember(tick) { Usage.hasAccess(ctx) }
    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    ScCard(shadow = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Screen lock", style = T.h3, color = P.ink)
                Text("At bedtime, and when an app goes over its daily limit.", style = T.small, color = P.ink2)
            }
            Toggle(s.settings.guardOn) { on ->
                if (on && !admin) { adminLauncher.launch(GuardService.adminIntent(ctx)); return@Toggle }
                if (on && !usage) { Usage.openAccessSettings(ctx); return@Toggle }
                if (on && Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                Repo.update { it.copy(settings = it.settings.copy(guardOn = on)) }
                if (on) GuardService.start(ctx) else GuardService.stop(ctx)
                Toasts.show(if (on) "Screen lock on" else "Screen lock off")
            }
        }
        if (!admin || !usage) {
            Spacer(Modifier.height(8.dp))
            Text(
                listOfNotNull(if (!admin) "• Allow SmartCam to lock the screen (asked when you turn this on)" else null, if (!usage) "• Allow Usage access" else null).joinToString("\n"),
                style = T.small, color = P.ink2,
            )
        }
        if (s.settings.guardOn) {
            Spacer(Modifier.height(8.dp))
            Text("Need your phone? Tap “Give me 15 min” on the SmartCam notification — it works from the lock screen.", style = T.small, color = P.ink2)
        }
    }
}

private val LIMITS = listOf<Int?>(null, 15, 30, 45, 60, 90, 120)

@Composable
private fun LimitsOpen(d: PhoneData, onClose: () -> Unit) {
    val s by Repo.state.collectAsState()
    ExpandedColumn("Manage screen time", Ic.lock, onClose) {
        GuardSwitch()
        Text("Set a daily limit for any app. When it runs out, opening that app locks the screen.", style = T.small, color = P.ink2)
        if (!d.access) Notice("Allow Usage access to see your apps.", Level.Warn)
        d.today.take(15).forEach { a ->
            val lim = s.settings.appLimits[a.pkg]
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(a.label, style = T.bodyStrong, color = P.ink)
                    Text("${Fmt.minutes(a.minutes)} today", style = T.mono.copy(fontSize = 12.sp), color = P.ink2)
                }
                when {
                    lim == null -> {}
                    a.minutes >= lim -> Chip("Over", Level.Bad)
                    a.minutes >= lim * 0.8 -> Chip("Close", Level.Warn)
                    else -> Chip("OK", Level.Good)
                }
                Box(
                    Modifier.background(P.card2, androidx.compose.foundation.shape.CircleShape)
                        .clickable {
                            val nextLim = LIMITS[(LIMITS.indexOf(lim) + 1) % LIMITS.size]
                            Repo.update { st ->
                                val m = st.settings.appLimits.toMutableMap()
                                if (nextLim == null) m.remove(a.pkg) else m[a.pkg] = nextLim
                                st.copy(settings = st.settings.copy(appLimits = m))
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(lim?.let { "${Fmt.minutes(it)}/day" } ?: "No limit", style = T.small, color = P.ink)
                }
            }
        }
        Text("Tap a limit to change it.", style = T.small, color = P.ink3)
    }
}

