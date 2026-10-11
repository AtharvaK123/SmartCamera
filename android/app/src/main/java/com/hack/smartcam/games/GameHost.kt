package com.hack.smartcam.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.smartcam.ui.Haptics
import com.hack.smartcam.ui.T

enum class Tier { Low, Moderate, High }

val Cream = Color(0xFFF5ECF2)
val NightInk = Color(0xFF0B1B32)

/** The game area (A) after a scan. Stress picks the suggested game and tunes each one. */
@Composable
fun GameHost(stress: Int, modifier: Modifier = Modifier) {
    val tier = when { stress >= 67 -> Tier.High; stress >= 34 -> Tier.Moderate; else -> Tier.Low }
    val suggested = when (tier) { Tier.High -> "meditate"; Tier.Moderate -> "jigsaw"; Tier.Low -> "tetris" }
    var game by remember(stress) { mutableStateOf(suggested) }
    var hud by remember { mutableStateOf("") }

    Box(modifier) {
        // The game takes everything under the picker row.
        Box(Modifier.fillMaxSize().padding(top = 50.dp)) {
            key(game) {
                when (game) {
                    "tetris" -> TetrisGame(tier) { hud = it }
                    "jigsaw" -> JigsawGame(tier) { hud = it }
                    else -> MeditationGame(tier) { hud = it }
                }
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.clip(CircleShape).background(NightInk.copy(alpha = 0.66f)).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                listOf("tetris" to "Tetris", "jigsaw" to "Jigsaw", "meditate" to "Meditate").forEach { (id, label) ->
                    val on = id == game
                    Box(
                        Modifier.clip(CircleShape).background(if (on) Cream else Color.Transparent)
                            .clickable(role = Role.Tab) { if (game != id) Haptics.tick(); game = id }.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(label, style = T.small.copy(fontWeight = FontWeight.SemiBold), color = if (on) NightInk else Cream.copy(alpha = 0.75f))
                        if (id == suggested) Box(Modifier.align(Alignment.TopEnd).padding(start = 4.dp).size(6.dp).clip(CircleShape).background(com.hack.smartcam.ui.Brand.mauve))
                    }
                }
            }
            if (hud.isNotEmpty()) Text(
                hud, style = T.mono.copy(fontSize = 11.sp), color = Cream.copy(alpha = 0.85f),
                modifier = Modifier.clip(CircleShape).background(NightInk.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** Start / finish card shown over a game. */
@Composable
fun GameCard(title: String, text: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(NightInk.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).clip(RoundedCornerShape(20.dp)).background(NightInk.copy(alpha = 0.85f)).padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = T.h1.copy(fontSize = 22.sp), color = Cream)
            Text(text, style = T.small, color = Cream.copy(alpha = 0.8f))
            actions()
        }
    }
}
