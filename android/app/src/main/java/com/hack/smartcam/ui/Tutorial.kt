package com.hack.smartcam.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Where things are on screen, so the tour can spotlight them. */
object TourTargets {
    val bounds = mutableStateMapOf<String, Rect>()
}

fun Modifier.tourTarget(key: String): Modifier = onGloballyPositioned { TourTargets.bounds[key] = it.boundsInRoot() }

private data class Stop(val target: String?, val title: String, val text: String)

/**
 * First-sign-in tour: dims the app, cuts a soft spotlight around one part at a time,
 * and explains it in a card next to it. Taps on the app are blocked until it's done or skipped.
 */
@Composable
fun Tutorial(name: String?, onDone: () -> Unit) {
    val stops = remember(name) {
        listOf(
            Stop(null, "Welcome${name?.let { ", $it" } ?: ""}!", "Here's a 30-second tour of SmartCam. You can replay it any time from Settings."),
            Stop("tabs", "Five tabs", "Home is your day at a glance. Camera scans your face, Food logs meals, Phone covers screen time and sleep, and Shop pays with Solana."),
            Stop("grid", "Tap a widget", "Each one grows to show more: your week of steps, today's heart rate, a calming game, or what you ate. Tap outside to shrink it back."),
            Stop("ask", "Ask Gemini", "Questions about your health, your day, or how to use SmartCam. Answers are read aloud in the voice you picked."),
            Stop("settings", "Settings", "Mood music, voice, vibration strength, screen lock and your account live here."),
            Stop(null, "Try a face scan", "Open the Camera tab and look at the screen for about 35 seconds. You'll get your pulse, breathing and stress, plus a game and music to match."),
        )
    }
    var i by remember { mutableIntStateOf(0) }
    val stop = stops[i]
    val target = stop.target?.let { TourTargets.bounds[it] }
    val density = LocalDensity.current
    val pad = with(density) { 8.dp.toPx() }
    val p = P

    fun next() {
        Haptics.tick()
        if (i < stops.lastIndex) i++ else { Haptics.success(); onDone() }
    }
    BackHandler { if (i > 0) i-- else onDone() }

    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }) {
        val screen = Rect(0f, 0f, with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() })
        // With no target the "hole" shrinks to the screen centre and disappears.
        val hole = target?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) } ?: Rect(screen.center, screen.center)
        val spot by animateRectAsState(hole, spring(dampingRatio = 0.85f, stiffness = 260f), label = "spot")

        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Brand.midnight.copy(alpha = 0.62f))
            if (spot.width > 1f) {
                val r = CornerRadius(minOf(spot.height / 2, 28.dp.toPx()))
                drawRoundRect(Color.Black, spot.topLeft, spot.size, r, blendMode = BlendMode.Clear)
                drawRoundRect(Color.White.copy(alpha = 0.85f), spot.topLeft, spot.size, r, style = Stroke(2.dp.toPx()))
            }
        }

        // The card sits below the spotlight if there's room, otherwise above it; centred when there's no target.
        val cardTop = with(density) {
            when {
                target == null -> (maxHeight / 2 - 120.dp)
                spot.bottom.toDp() + 260.dp < maxHeight -> spot.bottom.toDp() + 16.dp
                else -> (spot.top.toDp() - 250.dp).coerceAtLeast(24.dp)
            }
        }
        Column(
            Modifier.align(Alignment.TopCenter).offset(y = cardTop).padding(horizontal = 20.dp).widthIn(max = 460.dp).fillMaxWidth()
                .glass(fill = p.sheet.copy(alpha = 0.97f)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedContent(i, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) }, label = "tour") { k ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stops[k].title, style = T.h2, color = p.ink)
                    Text(stops[k].text, style = T.body, color = p.ink2)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    stops.indices.forEach { k ->
                        Box(Modifier.size(if (k == i) 18.dp else 7.dp, 7.dp).clip(CircleShape).background(if (k == i) p.accent else p.line))
                    }
                }
                if (i < stops.lastIndex) ScButton("Skip", { onDone() }, style = Btn.Ghost, small = true)
                Spacer(Modifier.size(8.dp))
                ScButton(if (i < stops.lastIndex) "Next" else "Let's go", { next() }, small = true)
            }
        }
    }
}
