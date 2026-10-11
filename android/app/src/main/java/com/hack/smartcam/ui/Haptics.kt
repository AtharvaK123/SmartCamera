package com.hack.smartcam.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.hack.smartcam.data.Repo

/**
 * Deliberate vibrations, strong by default.
 *  - Strong: longer full-power motor pulses (the most you can feel).
 *  - Medium / Light: the phone's crisp "primitive" haptics, scaled down.
 * Settings → Feel → Haptics turns them off; Strength picks Light, Medium or Strong.
 */
object Haptics {
    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }

    private val settings get() = Repo.state.value.settings
    private val on get() = settings.haptics && vibrator?.hasVibrator() == true

    /** 0.55 light · 0.8 medium · 1.0 strong. */
    private val power get() = when (settings.hapticStrength) { "light" -> 0.55f; "medium" -> 0.8f; else -> 1f }
    private val strong get() = settings.hapticStrength == "strong"

    private fun supports(vararg p: Int) = Build.VERSION.SDK_INT >= 30 && vibrator?.areAllPrimitivesSupported(*p) == true

    /** Plays primitives (type, scale, delay ms); scales are multiplied by the chosen strength. Strong skips them for full-power pulses. */
    private fun compose(vararg steps: Triple<Int, Float, Int>): Boolean {
        if (strong || Build.VERSION.SDK_INT < 30 || !supports(*steps.map { it.first }.toIntArray())) return false
        val c = VibrationEffect.startComposition()
        steps.forEach { (prim, scale, delay) -> c.addPrimitive(prim, (scale * power).coerceIn(0f, 1f), delay) }
        vibrator?.vibrate(c.compose())
        return true
    }

    /** Motor pulses: timings alternate off/on (ms), amplitudes 0–255. Strong stretches each pulse to feel heavier. */
    private fun wave(timings: LongArray, amps: IntArray) {
        val v = vibrator ?: return
        val t = if (strong) LongArray(timings.size) { if (amps[it] > 0) (timings[it] * 1.6).toLong() else timings[it] } else timings
        val a = if (v.hasAmplitudeControl()) IntArray(amps.size) { if (amps[it] == 0) 0 else if (strong) 255 else (amps[it] * power).toInt().coerceIn(1, 255) } else amps
        v.vibrate(VibrationEffect.createWaveform(t, a, -1))
    }

    private const val CLICK = VibrationEffect.Composition.PRIMITIVE_CLICK
    private const val TICK = VibrationEffect.Composition.PRIMITIVE_TICK
    private const val THUD = VibrationEffect.Composition.PRIMITIVE_THUD
    private const val RISE = VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
    private const val SLOW_RISE = VibrationEffect.Composition.PRIMITIVE_SLOW_RISE

    /** Switching tabs, toggles, moving a Tetris piece. */
    fun tick() {
        if (!on) return
        if (!compose(Triple(if (strong) CLICK else TICK, if (strong) 0.6f else 1f, 0))) wave(longArrayOf(0, 22), intArrayOf(0, 200))
    }

    /** Buttons, rotating a piece, opening a widget. */
    fun click() {
        if (!on) return
        if (!compose(Triple(CLICK, 1f, 0))) wave(longArrayOf(0, 32), intArrayOf(0, 255))
    }

    /** Something settles into place: a Tetris piece lands, a jigsaw piece snaps in. */
    fun thud(strength: Float = 1f) {
        if (!on) return
        if (!compose(Triple(THUD, strength, 0), Triple(CLICK, 0.8f * strength, 10))) wave(longArrayOf(0, 55), intArrayOf(0, (255 * strength).toInt().coerceAtLeast(150)))
    }

    /** Lines cleared: a rising ripple, one beat per line, ending in a big hit. */
    fun lines(n: Int) {
        val beats = n.coerceIn(1, 4)
        if (!on) return
        val steps = (0 until beats).map { i -> Triple(CLICK, 0.7f + 0.1f * i, if (i == 0) 0 else 55) } +
            Triple(RISE, 1f, 30) + Triple(THUD, 1f, 20)
        if (!compose(*steps.toTypedArray())) {
            val t = mutableListOf(0L); val a = mutableListOf(0)
            repeat(beats) { t += 40L; a += 220; t += 50L; a += 0 }
            t += 110L; a += 255
            wave(t.toLongArray(), a.toIntArray())
        }
    }

    /** Done: scan finished, puzzle solved, food saved. */
    fun success() {
        if (!on) return
        if (!compose(Triple(CLICK, 0.8f, 0), Triple(CLICK, 1f, 100), Triple(THUD, 0.7f, 60))) {
            wave(longArrayOf(0, 40, 90, 70), intArrayOf(0, 220, 0, 255))
        }
    }

    /** Heads-up: high heart rate, food over a limit, game over. */
    fun warning() {
        if (!on) return
        if (!compose(Triple(THUD, 1f, 0), Triple(THUD, 1f, 150), Triple(THUD, 1f, 150))) {
            wave(longArrayOf(0, 110, 110, 110, 110, 110), intArrayOf(0, 255, 0, 255, 0, 255))
        }
    }

    /** A breath changing direction in meditation: a slow swell you can feel without looking. */
    fun breath() {
        if (!on) return
        if (!compose(Triple(SLOW_RISE, 1f, 0))) wave(longArrayOf(0, 80, 80, 80, 80), intArrayOf(0, 90, 150, 210, 255))
    }

    /** Preview used by the Strength picker. */
    fun sample() = click()
}
