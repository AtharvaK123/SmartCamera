package com.hack.smartcam.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hack.smartcam.net.Voice
import com.hack.smartcam.ui.Haptics
import com.hack.smartcam.ui.Ic
import com.hack.smartcam.ui.IconBtn
import com.hack.smartcam.ui.P
import com.hack.smartcam.ui.Segmented
import com.hack.smartcam.ui.T
import kotlinx.coroutines.launch

/**
 * ElevenLabs voices to choose from: pick Woman or Man, then an accent. Tapping a row selects it and
 * plays a short hello in that voice; the speaker button replays it.
 */
@Composable
fun VoicePicker(selected: String, name: String?, onSelect: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val speaking by Voice.speaking.collectAsState()
    fun preview(id: String, voice: String) = scope.launch {
        Voice.speak(ctx, "Hi${name?.let { " $it" } ?: ""}, I'm $voice. I'll read Gemini's answers to you.", "preview-$id", id)
    }
    DisposableEffect(Unit) { onDispose { if (speaking?.startsWith("preview-") == true) Voice.stop(ctx) } }
    var gender by remember { mutableStateOf(Voice.OPTIONS.firstOrNull { it.id == selected }?.gender ?: "woman") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Segmented(listOf("woman" to "Woman", "man" to "Man"), gender) { gender = it }
        Voice.OPTIONS.filter { it.gender == gender }.forEach { v ->
            val on = v.id == selected
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) P.accentSoft else P.card2)
                    .border(if (on) 1.5.dp else 1.dp, if (on) P.accent else P.line, RoundedCornerShape(16.dp))
                    .clickable(role = Role.RadioButton) { Haptics.tick(); onSelect(v.id); preview(v.id, v.name) }
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(v.accent, style = T.bodyStrong, color = P.ink)
                    Text(v.name, style = T.small, color = P.ink2)
                }
                val playing = speaking == "preview-${v.id}"
                IconBtn(if (playing) Ic.stop else Ic.speaker, if (playing) "Stop" else "Hear ${v.name}", {
                    if (playing) Voice.stop(ctx) else preview(v.id, v.name)
                }, tint = if (on) P.accent else P.ink2)
            }
        }
    }
}
