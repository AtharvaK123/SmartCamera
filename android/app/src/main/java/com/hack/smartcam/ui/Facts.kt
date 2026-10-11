package com.hack.smartcam.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hack.smartcam.data.Repo
import com.hack.smartcam.net.Voice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Little facts to fill the wait while a scan runs or Gemini looks at a photo. */
object Facts {
    private val ALL = listOf(
        "Your heart beats about a hundred thousand times a day.",
        "Slow breathing — about six breaths a minute — can bring your heart rate down within a few minutes.",
        "Octopuses have three hearts.",
        "Bananas are berries, but strawberries aren't.",
        "Your skeleton is constantly rebuilt — you get a mostly new one roughly every ten years.",
        "A ten-minute walk after a meal can help soften the rise in your blood sugar.",
        "Wombat poop is cube-shaped.",
        "Sea otters hold hands while they sleep so they don't drift apart.",
        "Even mild dehydration can make it harder to concentrate.",
        "A day on Venus is longer than its year.",
        "The Eiffel Tower grows about fifteen centimetres in summer, because metal expands in the heat.",
        "Babies are born with around three hundred bones; adults have two hundred and six.",
        "Your stomach replaces its inner lining every few days so it doesn't digest itself.",
        "Morning sunlight helps set your body clock, which makes falling asleep at night easier.",
        "Butterflies taste with their feet.",
        "Your brain uses about a fifth of your body's energy.",
        "Lightning is roughly five times hotter than the surface of the Sun.",
        "Peanuts aren't nuts — they're legumes, like beans and peas.",
        "Getting up and moving for a couple of minutes every half hour beats sitting for hours straight.",
        "Owls can't move their eyes, so they turn their heads — up to about 270 degrees.",
        "A ten to twenty minute nap can boost alertness without leaving you groggy.",
        "There are more possible chess games than atoms in the observable universe.",
        "Oxford University is older than the Aztec Empire.",
        "Cleopatra lived closer in time to the Moon landing than to the building of the Great Pyramid.",
        "Saturn is less dense than water — it would float in a big enough bathtub.",
        "You blink about fifteen times a minute, but far less when you stare at a screen.",
        "Gram for gram, strawberries have a little more vitamin C than oranges.",
        "A single workout can lift your mood the same day — you don't have to wait weeks.",
        "Humming slows your breathing, which can help you feel calmer.",
        "Your body is made of roughly thirty-seven trillion cells.",
        "A hummingbird's heart can beat more than a thousand times a minute.",
        "Sloths can hold their breath longer than dolphins can.",
        "Honey can last for thousands of years without spoiling.",
    )

    fun next(context: Context): String {
        val prefs = context.getSharedPreferences("facts", Context.MODE_PRIVATE)
        val order = prefs.getString("order", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == ALL.size }
            ?: ALL.indices.shuffled()
        val i = prefs.getInt("i", 0)
        val fact = ALL[order[i % order.size]]
        val nextOrder = if (i + 1 >= order.size) ALL.indices.shuffled() else order
        prefs.edit().putString("order", nextOrder.joinToString(",")).putInt("i", (i + 1) % order.size).apply()
        return fact
    }
}

/**
 * Shows a fact, typing it out letter by letter, while ElevenLabs reads it in a playful voice.
 * Moves on to the next fact once it has been read (and had a moment to sink in).
 */
@Composable
fun FactTeller(modifier: Modifier = Modifier, ink: Color = P.ink, ink2: Color = P.ink2) {
    val ctx = LocalContext.current
    val s by Repo.state.collectAsState()
    var fact by remember { mutableStateOf("") }
    val aloud = s.settings.factsAloud

    LaunchedEffect(aloud) {
        while (true) {
            fact = Facts.next(ctx)
            val shownAt = System.currentTimeMillis()
            if (aloud) {
                launch { Voice.speak(ctx, fact, "fact") }
                delay(800)
                withTimeoutOrNull(20_000) { Voice.speaking.first { it != "fact" } }
            }
            val minimum = fact.length * 38L + 3000
            val spent = System.currentTimeMillis() - shownAt
            if (spent < minimum) delay(minimum - spent)
            delay(600)
        }
    }
    DisposableEffect(Unit) { onDispose { if (Voice.speaking.value == "fact") Voice.stop(ctx) } }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Ic.spark, null, Modifier.size(14.dp), tint = ink2)
            Text("DID YOU KNOW", style = T.eyebrow, color = ink2)
        }
        if (fact.isNotEmpty()) TypeText(fact, T.bodyStrong, ink)
    }
}
