package com.hack.smartcam.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hack.smartcam.ui.GeminiLogo
import com.hack.smartcam.ui.Ic
import com.hack.smartcam.ui.P
import com.hack.smartcam.ui.PoweredByPresage
import com.hack.smartcam.ui.ScButton
import com.hack.smartcam.ui.ScSheet
import com.hack.smartcam.ui.SolanaLogo
import com.hack.smartcam.ui.T
import com.hack.smartcam.ui.Tab
import com.hack.smartcam.ui.glass

private data class Overview(val title: String, val intro: String, val points: List<String>)

private fun overview(tab: Tab) = when (tab) {
    Tab.Home -> Overview(
        "Your day at a glance",
        "Four widgets that grow when you tap them.",
        listOf(
            "Steps — this week's chart and a goal you set",
            "Heart rate — every reading from the last 24 hours, with the lowest and highest",
            "Stress — play a calming game picked for your stress level",
            "Nutrition — what you ate, what you burned, and a plan from Gemini for tomorrow",
        ),
    )
    Tab.Camera -> Overview(
        "Face scan",
        "Look at the selfie camera for about 35 seconds.",
        listOf(
            "Measures pulse, breathing, heart-rate variability and stress from tiny colour changes in your face",
            "Warns you if your heart rate is high",
            "Then suggests Tetris, a jigsaw or a breathing exercise, tuned to your stress",
            "And starts music that fits your mood",
        ),
    )
    Tab.Food -> Overview(
        "Food log",
        "Tap + to add what you eat.",
        listOf(
            "Scan a barcode — nutrition comes from Open Food Facts",
            "Or snap your plate — Gemini estimates the calories and nutrients",
            "Warns you before a food takes you over your daily limits",
            "Everything shows up on the SmartCam website too",
        ),
    )
    Tab.Phone -> Overview(
        "Phone habits",
        "Screen time, alarms, sleep and limits.",
        listOf(
            "Average screen time, broken down by app and by day",
            "Alarms that go straight into your Clock app",
            "Sleep history: the longest stretch your phone stayed off each night",
            "A bedtime screen lock and daily app limits",
        ),
    )
    Tab.Shop -> Overview(
        "Shop with Gemini",
        "Describe what you need; Gemini suggests options.",
        listOf(
            "Short, specific suggestions with typical prices",
            "Pay with Solana Pay — your wallet app shows the exact amount",
            "Your orders are kept here",
        ),
    )
}

/** What a guest sees on each tab: what it does, and how to start using it. */
@Composable
fun GuestOverview(tab: Tab, onSignIn: () -> Unit) {
    val o = overview(tab)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().glass().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(P.accentSoft), contentAlignment = Alignment.Center) {
                Icon(tab.icon, null, Modifier.size(28.dp), tint = P.ink)
            }
            Text(o.title, style = T.h1, color = P.ink)
            Text(o.intro, style = T.body, color = P.ink2)
            o.points.forEach { pt ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Ic.check, null, Modifier.size(18.dp).padding(top = 2.dp), tint = P.good)
                    Text(pt, style = T.body, color = P.ink, modifier = Modifier.weight(1f))
                }
            }
            when (tab) {
                Tab.Camera -> PoweredByPresage()
                Tab.Shop -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SolanaLogo(18.dp); Text("Payments by Solana", style = T.small, color = P.ink2)
                }
                else -> {}
            }
            Text("You're browsing as a guest, so nothing here is live.", style = T.small, color = P.ink2)
            ScButton("Create a free account", onSignIn, Modifier.fillMaxWidth(), icon = Ic.user)
        }
    }
}

/** Guests tapping "Ask Gemini". */
@Composable
fun GuestChatSheet(onDismiss: () -> Unit, onSignIn: () -> Unit) {
    ScSheet("Ask Gemini", onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GeminiLogo(28.dp)
            Text("Ask about your steps, scans, food, sleep and screen time, or how to use SmartCam. Answers can be read aloud.", style = T.body, color = P.ink, modifier = Modifier.weight(1f))
        }
        ScButton("Create an account to chat", { onDismiss(); onSignIn() }, Modifier.fillMaxWidth(), icon = Ic.user)
    }
}
