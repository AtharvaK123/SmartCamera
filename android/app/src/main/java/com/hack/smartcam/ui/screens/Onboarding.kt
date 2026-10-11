package com.hack.smartcam.ui.screens

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.smartcam.data.Profile
import com.hack.smartcam.data.Repo
import com.hack.smartcam.ui.Btn
import com.hack.smartcam.ui.Fmt
import com.hack.smartcam.ui.Haptics
import com.hack.smartcam.ui.Ic
import com.hack.smartcam.ui.P
import com.hack.smartcam.ui.ScButton
import com.hack.smartcam.ui.ScField
import com.hack.smartcam.ui.Segmented
import com.hack.smartcam.ui.T
import com.hack.smartcam.ui.Toasts
import com.hack.smartcam.ui.glass
import java.time.LocalTime
import kotlin.math.roundToInt

private val GOALS = listOf("Less stress", "Sleep better", "More energy", "Eat better", "Move more", "Lose weight", "Build muscle", "Less screen time")
private val GENRES = listOf("Lo-fi", "Pop", "Ambient", "Classical", "Acoustic", "Jazz", "Hip-hop", "R&B", "Rock", "Electronic", "K-pop", "Country", "Indie", "Latin")
private val ACTIVITY = listOf(
    "sitting" to "Mostly sitting" to "Desk job, little walking",
    "light" to "Lightly active" to "Some walking most days",
    "active" to "Active" to "Exercise 3–5 times a week",
    "very" to "Very active" to "Hard training or a physical job",
)

/**
 * First-run questions. Everything here makes the app smarter: calories burned and food limits
 * (height, weight, age, sex, activity), Gemini's advice (goals, diet), and mood music (genres).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Onboarding(editing: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val start = remember { Repo.state.value }
    val p0 = start.profile ?: Profile()
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(p0.name) }
    var age by remember { mutableStateOf(p0.age?.toString().orEmpty()) }
    var sex by remember { mutableStateOf(p0.sex ?: "") }
    var metric by remember { mutableStateOf(true) }
    var cm by remember { mutableStateOf(p0.heightCm?.roundToInt()?.toString().orEmpty()) }
    var ft by remember { mutableStateOf(p0.heightCm?.let { (it / 30.48).toInt().toString() }.orEmpty()) }
    var inch by remember { mutableStateOf(p0.heightCm?.let { ((it / 2.54) % 12).roundToInt().toString() }.orEmpty()) }
    var kg by remember { mutableStateOf(p0.weightKg?.roundToInt()?.toString().orEmpty()) }
    var lb by remember { mutableStateOf(p0.weightKg?.let { (it * 2.20462).roundToInt().toString() }.orEmpty()) }
    var activity by remember { mutableStateOf(p0.activity) }
    var goals by remember { mutableStateOf(p0.goal.split(", ").filter { it in GOALS }.toSet()) }
    var diet by remember { mutableStateOf(p0.diet) }
    var music by remember { mutableStateOf(p0.music.toSet()) }
    var sleepHours by remember { mutableStateOf(p0.sleepHours) }
    var lock by remember { mutableStateOf(start.settings.sleepLock) }
    var unlock by remember { mutableStateOf(start.settings.sleepUnlock) }
    var stepGoal by remember { mutableIntStateOf(start.settings.stepGoal) }
    var voice by remember { mutableStateOf(start.settings.voiceId.ifBlank { com.hack.smartcam.net.Voice.OPTIONS.first().id }) }
    val ageOk = age.isEmpty() || age.toIntOrNull() in 1..100

    val heightCm = if (metric) cm.toDoubleOrNull() else ft.toIntOrNull()?.let { f -> f * 30.48 + (inch.toDoubleOrNull() ?: 0.0) * 2.54 }
    val weightKg = if (metric) kg.toDoubleOrNull() else lb.toDoubleOrNull()?.let { it / 2.20462 }
    val steps = 8

    fun finish() {
        val profile = Profile(
            name = name.trim(), age = age.toIntOrNull()?.takeIf { it in 1..100 }, sex = sex.ifEmpty { null },
            heightCm = heightCm?.let { (it * 10).roundToInt() / 10.0 }, weightKg = weightKg?.let { (it * 10).roundToInt() / 10.0 },
            activity = activity, goal = goals.joinToString(", ").ifEmpty { "Feel better" }, diet = diet.trim(),
            music = music.toList(), sleepHours = sleepHours,
        )
        Repo.update { it.copy(profile = profile, settings = it.settings.copy(sleepLock = lock, sleepUnlock = unlock, stepGoal = stepGoal, voiceId = voice)) }
        Toasts.show(if (editing) "Saved" else "Welcome${profile.name.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}!")
        onDone()
    }

    val canNext = when (step) {
        0 -> name.isNotBlank()
        1 -> ageOk
        2 -> (heightCm ?: 0.0) in 80.0..250.0 && (weightKg ?: 0.0) in 25.0..350.0
        else -> true
    }
    BackHandler(enabled = step > 0 || editing) { if (step > 0) step-- else onDone() }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Progress: one bar per question.
        Row(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(steps) { i ->
                val f by animateFloatAsState(if (i <= step) 1f else 0f, tween(400), label = "bar$i")
                Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(P.card2)) {
                    Box(Modifier.fillMaxWidth(f).height(5.dp).clip(CircleShape).background(P.accent))
                }
            }
        }
        Text("${step + 1} of $steps", style = T.mono, color = P.ink2)
        Spacer(Modifier.height(12.dp))

        AnimatedContent(
            step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(420)) { it / 3 * dir } + fadeIn(tween(420))) togetherWith (slideOutHorizontally(tween(300)) { -it / 3 * dir } + fadeOut(tween(250)))
            },
            modifier = Modifier.weight(1f).widthIn(max = 520.dp).fillMaxWidth(),
            label = "onboarding",
        ) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (s) {
                    0 -> Question("What should we call you?", "We'll use it to greet you, and Gemini will know who it's talking to.") {
                        ScField(name, { name = it.take(30) }, placeholder = "Your first name")
                    }
                    1 -> Question("A little about you", "Age and sex help estimate how many calories you burn at rest.") {
                        ScField(age, { age = it.filter(Char::isDigit).take(3) }, label = "Age", number = true)
                        if (!ageOk) Text("Please put a valid age", style = T.small, color = P.bad)
                        Text("Sex", style = T.small, color = P.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("female" to "Female", "male" to "Male", "other" to "Other", "" to "Prefer not to say").forEach { (k, l) ->
                                Pick(l, sex == k) { sex = k }
                            }
                        }
                    }
                    2 -> Question("Height and weight", "Used for calories burned and sensible food limits. Only you can see it.") {
                        Segmented(listOf("metric" to "cm · kg", "imperial" to "ft · lb"), if (metric) "metric" else "imperial") { metric = it == "metric" }
                        if (metric) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ScField(cm, { cm = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), label = "Height (cm)", number = true)
                                ScField(kg, { kg = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, Modifier.weight(1f), label = "Weight (kg)", number = true)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ScField(ft, { ft = it.filter(Char::isDigit).take(1) }, Modifier.weight(1f), label = "Feet", number = true)
                                ScField(inch, { inch = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = "Inches", number = true)
                                ScField(lb, { lb = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, Modifier.weight(1f), label = "Pounds", number = true)
                            }
                        }
                    }
                    3 -> Question("How active are you?", "On a normal week.") {
                        ACTIVITY.forEach { (kl, sub) ->
                            val (k, l) = kl
                            BigPick(l, sub, activity == k, Ic.steps) { activity = k }
                        }
                    }
                    4 -> Question("What would you like help with?", "Pick as many as you like. Gemini uses these for your plans.") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GOALS.forEach { g -> Pick(g, g in goals) { goals = if (g in goals) goals - g else goals + g } }
                        }
                        ScField(diet, { diet = it.take(120) }, label = "Anything about how you eat? (optional)")
                    }
                    5 -> Question("What music do you like?", "SmartCam plays music for your mood after a scan.") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GENRES.forEach { g -> Pick(g, g in music) { music = if (g in music) music - g else music + g } }
                        }
                    }
                    6 -> Question("Pick a voice", "ElevenLabs reads Gemini's answers aloud in this voice. Tap one to hear it; you can change it later in Settings.") {
                        VoicePicker(voice, name.trim().substringBefore(' ').ifBlank { null }) { voice = it }
                    }
                    else -> Question("Sleep and steps", "SmartCam can lock your screen at bedtime (turn it on later in the Phone tab).") {
                        Text("Hours of sleep you aim for", style = T.small, color = P.ink2)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ScButton("−", { sleepHours = (sleepHours - 0.5).coerceAtLeast(4.0) }, style = Btn.Ghost, small = true)
                            Text("${if (sleepHours % 1.0 == 0.0) sleepHours.toInt() else sleepHours} h", style = T.big.copy(fontSize = 24.sp), color = P.ink)
                            ScButton("+", { sleepHours = (sleepHours + 0.5).coerceAtMost(12.0) }, style = Btn.Ghost, small = true)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ScButton("Bed ${Fmt.time12(lock)}", { pick(ctx, lock) { lock = it } }, Modifier.weight(1f), icon = Ic.moon, style = Btn.Ghost, small = true)
                            ScButton("Up ${Fmt.time12(unlock)}", { pick(ctx, unlock) { unlock = it } }, Modifier.weight(1f), icon = Ic.alarm, style = Btn.Ghost, small = true)
                        }
                        Text("Daily step goal", style = T.small, color = P.ink2)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5000, 8000, 10000, 12000).forEach { g ->
                                ScButton(Fmt.num(g), { stepGoal = g }, Modifier.weight(1f), style = if (stepGoal == g) Btn.Primary else Btn.Ghost, small = true)
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) ScButton("Back", { step-- }, style = Btn.Ghost)
            else if (editing) ScButton("Cancel", onDone, style = Btn.Ghost)
            Spacer(Modifier.weight(1f))
            if (step in 1 until steps - 1 && step != 2 && !(step == 1 && !ageOk)) ScButton("Skip", { step++ }, style = Btn.Ghost)
            ScButton(
                if (step == steps - 1) (if (editing) "Save" else "Start using SmartCam") else "Next",
                { if (step == steps - 1) finish() else step++ },
                icon = if (step == steps - 1) Ic.check else Ic.chevronRight, enabled = canNext,
            )
        }
    }
}

private fun pick(context: android.content.Context, initial: String, onPick: (String) -> Unit) {
    val t = runCatching { LocalTime.parse(initial) }.getOrDefault(LocalTime.of(22, 30))
    TimePickerDialog(context, { _, h, m -> onPick("%02d:%02d".format(h, m)) }, t.hour, t.minute, false).show()
}

@Composable
private fun Question(title: String, sub: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = T.h1, color = P.ink)
    Text(sub, style = T.body, color = P.ink2)
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun Pick(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, style = T.bodyStrong.copy(fontSize = 14.sp),
        color = if (on) P.accentInk else P.ink,
        modifier = Modifier.clip(CircleShape).background(if (on) P.accent else P.card2).border(1.dp, if (on) P.accent else P.line, CircleShape)
            .clickable(role = Role.Checkbox) { Haptics.tick(); onClick() }.padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun BigPick(title: String, sub: String, on: Boolean, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) P.accentSoft else P.card2)
            .border(if (on) 1.5.dp else 1.dp, if (on) P.accent else P.line, RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton) { Haptics.tick(); onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (on) P.accent else P.ink2)
        Column(Modifier.weight(1f)) {
            Text(title, style = T.bodyStrong, color = P.ink)
            Text(sub, style = T.small, color = P.ink2)
        }
        if (on) Icon(Ic.check, null, Modifier.size(20.dp), tint = P.accent)
    }
}
