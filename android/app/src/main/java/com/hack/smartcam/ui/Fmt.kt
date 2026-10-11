package com.hack.smartcam.ui

import com.hack.smartcam.data.AppState
import com.hack.smartcam.data.Food
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val zone get() = ZoneId.systemDefault()
    private val hm = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun minutes(m: Int): String {
        val h = m / 60
        val r = m % 60
        return if (h > 0) (if (r > 0) "${h}h ${r}m" else "${h}h") else "${r}m"
    }

    fun time12(hhmm: String): String = runCatching { LocalTime.parse(hhmm).format(hm).lowercase() }.getOrDefault(hhmm)
    fun clock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().format(hm).lowercase()
    fun num(n: Number): String = NumberFormat.getIntegerInstance().format(n)

    fun ago(ms: Long): String {
        val s = (System.currentTimeMillis() - ms) / 1000
        return when {
            s < 60 -> "just now"
            s < 3600 -> "${s / 60} min ago"
            s < 86400 -> "${s / 3600} h ago"
            else -> Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
        }
    }

    fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun weekdayLetter(d: LocalDate) = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.US)
    fun weekday(d: LocalDate) = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.US)

    fun todaysFoods(s: AppState): List<Food> = s.foods.filter { dayOf(it.t) == LocalDate.now() }

    fun stressLevel(s: Int) = when { s < 34 -> Level.Good to "Low"; s < 67 -> Level.Warn to "Moderate"; else -> Level.Bad to "High" }
    fun foodLevel(s: Int) = when { s >= 70 -> Level.Good to "Good"; s >= 45 -> Level.Warn to "Fair"; else -> Level.Bad to "Poor" }
    fun stepsLevel(f: Float) = when { f >= 0.75f -> Level.Good to "On track"; f >= 0.4f -> Level.Warn to "Moderate"; else -> Level.Bad to "Low" }
}
