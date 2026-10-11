package com.hack.smartcam.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hack.smartcam.data.ChatMsg
import com.hack.smartcam.data.Repo
import com.hack.smartcam.net.FoodFacts
import com.hack.smartcam.net.Gemini
import com.hack.smartcam.net.Voice
import com.hack.smartcam.phone.Usage
import com.hack.smartcam.ui.Fmt
import com.hack.smartcam.ui.GeminiMark
import com.hack.smartcam.ui.Haptics
import com.hack.smartcam.ui.Ic
import com.hack.smartcam.ui.IconBtn
import com.hack.smartcam.ui.P
import com.hack.smartcam.ui.T
import com.hack.smartcam.ui.Toasts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SYSTEM = """
You are Gemini, the assistant inside the SmartCam app. Only help with things related to SmartCam:
how to use the app and its features (face scan and vitals, stress games, mood music, food logging and barcodes,
calorie and nutrition limits, the Gemini plan, steps, heart rate, screen time, sleep, alarms, screen lock,
the Solana shop, accounts and settings), and the user's own data in the app: their health, food, activity,
sleep and habits, with practical wellness advice based on it.
If a question is about anything else (general knowledge, homework, coding, news, entertainment and so on),
do not answer it. Reply in one friendly sentence that you can only help with SmartCam and their health data here,
and suggest something you can help with.
Answers are shown in a chat bubble and may be read aloud: plain text, no markdown, under 90 words.
For worrying health readings, suggest seeing a professional.
Where things are in SmartCam (use this for how-to questions; never invent menus):
Home tab has four widgets; tap one to open it: Steps (this week's chart, set a daily goal), Heart rate (last 24 hours, min and max),
Stress (play Tetris, Jigsaw or Meditate without the camera), Nutrition (eaten vs limits, calories burned, a Gemini plan for tomorrow with reminders).
Camera tab: a 35-second face scan by Presage measures pulse, breathing, heart-rate variability and stress; then a game fills the screen,
the camera shrinks to a small window, mood music starts, and a warning shows if heart rate is high.
Food tab: tap + to scan a barcode, snap a photo of a meal for Gemini to estimate, or type it in; enter the grams eaten; it warns before going over limits.
Phone tab widgets: Average screen time (by app and by day), Alarms (added to the Clock app), Sleep (longest time the phone was off each night, plus bedtime lock times),
Screen lock (turn it on, then tap an app's limit to cycle 15 min to 2 h per day; opening an app over its limit locks the screen; "Give me 15 min" on the notification pauses it).
Shop tab: describe what you want, Gemini suggests products, pay with a Solana wallet.
Settings (the three dots, top right): account and setup answers, permissions, mood music songs, Gemini's voice, fun facts aloud, haptics and strength,
screen lock, connections, theme, and replaying the tour.
The user's SmartCam data:
""".trimIndent().replace('\n', ' ') + " Data: "

private fun contextJson(screenMinutes: Int?): String {
    val s = Repo.state.value
    val v = s.vitals.lastOrNull()
    val foods = Fmt.todaysFoods(s)
    return buildString {
        val p = s.profile
        append("{now: ${java.time.LocalDateTime.now()}, name: ${p?.name ?: "?"}, age: ${p?.age ?: "?"}, heightCm: ${p?.heightCm ?: "?"}, weightKg: ${p?.weightKg ?: "?"}, ")
        append("goal: \"${p?.goal ?: ""}\", diet: \"${p?.diet ?: ""}\", musicTaste: ${p?.music ?: emptyList<String>()}, steps: ${s.steps[Repo.todayKey()] ?: 0}, stepGoal: ${s.settings.stepGoal}, ")
        append("lastScan: ${v?.let { "{heartRate: ${it.hr}, breathing: ${it.br}, stress: ${it.stress}, hrvRmssd: ${it.hrv}, expression: ${it.mood}, when: ${Fmt.ago(it.t)}}" } ?: "none"}, ")
        append("foodToday: [${foods.joinToString { "${it.name} (${it.kcal ?: "?"} kcal ${it.basis}, score ${FoodFacts.score(it)})" }}], ")
        append("nutritionScore: ${FoodFacts.dayScore(foods)}, screenTimeTodayMin: ${screenMinutes ?: "unknown"}, ")
        append("bedtimeLock: ${s.settings.sleepLock}-${s.settings.sleepUnlock}}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = { Voice.stop(ctx); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = P.sheet,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.88f)) { ChatPanel(onClose = { Voice.stop(ctx); onDismiss() }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatPanel(onClose: (() -> Unit)?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Repo.state.collectAsState()
    val speaking by Voice.speaking.collectAsState()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val p = P

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || busy) return
        input = ""
        busy = true
        Repo.update { it.copy(chat = (it.chat + ChatMsg(Repo.newId("m"), "user", text)).takeLast(40)) }
        scope.launch {
            val reply = runCatching {
                if (!Gemini.available) error("Gemini isn't available right now")
                val screen = withContext(Dispatchers.IO) { if (Usage.hasAccess(ctx)) Usage.apps(ctx, Usage.startOfDay()).sumOf { it.minutes } else null }
                val history = Repo.state.value.chat.dropLast(1).takeLast(12).map { Gemini.Turn(it.role, it.text) }
                Gemini.generate(text, system = SYSTEM + contextJson(screen), history = history)
            }.getOrElse { "Sorry, I couldn't reach Gemini (${it.message})." }
            val msg = ChatMsg(Repo.newId("m"), "model", reply)
            Repo.update { it.copy(chat = (it.chat + msg).takeLast(40)) }
            busy = false
            Haptics.tick() // the answer arrived
            // ElevenLabs reads the answer out loud as soon as it arrives (toggle with the speaker in the header).
            if (Repo.state.value.settings.autoSpeak) Voice.speak(ctx, reply, msg.id)
        }
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { send(it) }
        }
    }

    LaunchedEffect(s.chat.size, busy) { if (s.chat.isNotEmpty()) list.animateScrollToItem(s.chat.size + 1) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GeminiMark()
            Text("Gemini", style = T.h2, color = p.ink, modifier = Modifier.padding(start = 10.dp).weight(1f))
            val auto = s.settings.autoSpeak
            IconBtn(
                if (auto) Ic.speaker else Ic.speakerOff,
                if (auto) "Read answers aloud: on" else "Read answers aloud: off",
                {
                    Repo.update { it.copy(settings = it.settings.copy(autoSpeak = !auto)) }
                    if (auto) Voice.stop(ctx)
                    Toasts.show(if (!auto) "Answers will be read aloud" else "Read aloud off")
                },
                tint = if (auto) p.accentInk else p.ink, bg = if (auto) p.accent else androidx.compose.ui.graphics.Color.Transparent,
            )
            if (onClose != null) IconBtn(Ic.close, "Close", onClose)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 0.dp).background(p.line).size(1.dp))

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.chat.isEmpty()) {
                item {
                    Bubble("Hi! I can help with SmartCam and your health: your steps, scans, food, sleep and screen time, or how to use any part of the app.", user = false)
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("How am I doing today?", "Why is my stress high?", "What should I eat next?", "How do I set a screen limit?").forEach { q ->
                            Text(
                                q, style = T.small, color = p.ink,
                                modifier = Modifier.clip(CircleShape).border(1.dp, p.line, CircleShape).background(p.card2)
                                    .clickable { send(q) }.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }
            items(s.chat, key = { it.id }) { m ->
                if (m.role == "user") Bubble(m.text, user = true)
                else Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Bubble(m.text, user = false, modifier = Modifier.weight(1f, fill = false))
                    val on = speaking == m.id
                    IconBtn(if (on) Ic.stop else Ic.speaker, if (on) "Stop reading" else "Read aloud", {
                        if (on) Voice.stop(ctx) else scope.launch { Voice.speak(ctx, m.text, m.id) }
                    }, tint = if (on) p.pop else p.ink2, size = 36.dp)
                }
            }
            if (busy) item { Bubble("Thinking…", user = false, thinking = true) }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicTextField(
                input, { input = it }, Modifier.weight(1f).clip(CircleShape).background(p.card2).border(1.dp, p.line, CircleShape).padding(horizontal = 16.dp, vertical = 13.dp),
                textStyle = T.body.copy(color = p.ink), cursorBrush = SolidColor(p.accent), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input) }),
                decorationBox = { inner -> if (input.isEmpty()) Text("Ask about SmartCam or your day…", style = T.body, color = p.ink3); inner() },
            )
            IconBtn(Ic.mic, "Speak", {
                runCatching {
                    speech.launch(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask Gemini"),
                    )
                }.onFailure { Toasts.show("Voice input isn't available on this phone", true) }
            })
            IconBtn(Ic.send, "Send", { send(input) })
        }
    }
}

@Composable
private fun Bubble(text: String, user: Boolean, modifier: Modifier = Modifier, thinking: Boolean = false) {
    if (user) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { BubbleText(text, true, modifier, thinking) }
    else BubbleText(text, false, modifier, thinking)
}

@Composable
private fun BubbleText(text: String, user: Boolean, modifier: Modifier, thinking: Boolean) {
    val p = P
    Text(
        text,
        style = T.body.copy(fontStyle = if (thinking) FontStyle.Italic else FontStyle.Normal),
        color = if (user) p.accentInk else if (thinking) p.ink2 else p.ink,
        modifier = modifier.widthIn(max = 320.dp)
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (user) 18.dp else 6.dp, bottomEnd = if (user) 6.dp else 18.dp))
            .background(if (user) p.accent else p.card2).border(1.dp, if (user) androidx.compose.ui.graphics.Color.Transparent else p.line, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (user) 18.dp else 6.dp, bottomEnd = if (user) 6.dp else 18.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
