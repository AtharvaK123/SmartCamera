package com.hack.smartcam.phone

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class AppUsage(val pkg: String, val label: String, val minutes: Int)
data class SleepNight(val date: LocalDate, val start: Long, val end: Long) {
    val minutes get() = ((end - start) / 60000).toInt()
}

/** Screen time, the app in front, and sleep (the longest screen-off stretch each night). */
object Usage {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    fun openAccessSettings(context: Context) =
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun startOfDay(daysAgo: Long = 0): Long =
        LocalDate.now().minusDays(daysAgo).atStartOfDay(zone).toInstant().toEpochMilli()

    fun label(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))

    /** Foreground minutes per launchable app between two times, biggest first. */
    fun apps(context: Context, from: Long, to: Long = System.currentTimeMillis()): List<AppUsage> {
        if (!hasAccess(context)) return emptyList()
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val pm = context.packageManager
        return usm.queryAndAggregateUsageStats(from, to)
            .filter { (pkg, s) -> s.totalTimeInForeground >= 60_000 && pkg != context.packageName && pm.getLaunchIntentForPackage(pkg) != null }
            .map { (pkg, s) -> AppUsage(pkg, label(context, pkg), (s.totalTimeInForeground / 60_000).toInt()) }
            .sortedByDescending { it.minutes }
    }

    fun minutesToday(context: Context, pkg: String): Int = apps(context, startOfDay()).firstOrNull { it.pkg == pkg }?.minutes ?: 0

    /** Total screen time for each of the last 7 days (oldest first). */
    fun week(context: Context): List<Int> = (6 downTo 0).map { d ->
        val from = startOfDay(d.toLong())
        val to = if (d == 0) System.currentTimeMillis() else startOfDay((d - 1).toLong())
        apps(context, from, to).sumOf { it.minutes }
    }

    /** The app currently on screen, from the last "resumed" event. */
    fun foregroundApp(context: Context): String? {
        if (!hasAccess(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 30 * 60_000, now)
        val e = UsageEvents.Event()
        var current: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> current = e.packageName
                UsageEvents.Event.ACTIVITY_PAUSED -> if (e.packageName == current) current = null
            }
        }
        return current
    }

    /** Screen-off intervals between [from, to], from SCREEN_INTERACTIVE / NON_INTERACTIVE events. */
    private fun offIntervals(context: Context, from: Long, to: Long): List<Pair<Long, Long>> {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(from - 12 * 3600_000L, to)
        val e = UsageEvents.Event()
        val out = mutableListOf<Pair<Long, Long>>()
        var offSince: Long? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> if (offSince == null) offSince = e.timeStamp
                UsageEvents.Event.SCREEN_INTERACTIVE -> offSince?.let { s ->
                    val a = maxOf(s, from)
                    val b = minOf(e.timeStamp, to)
                    if (b > a) out += a to b
                    offSince = null
                }
            }
        }
        offSince?.let { s -> if (to > maxOf(s, from)) out += maxOf(s, from) to to }
        return out
    }

    /**
     * Sleep = the longest time the phone stayed off at night (8 pm → noon).
     * Android keeps these events for about a week, so we look back 7 nights.
     */
    fun sleep(context: Context, nights: Int = 7): List<SleepNight> {
        if (!hasAccess(context)) return emptyList()
        val now = System.currentTimeMillis()
        return (nights - 1 downTo 0).mapNotNull { n ->
            val morning = LocalDate.now().minusDays(n.toLong())
            val from = LocalDateTime.of(morning.minusDays(1), LocalTime.of(20, 0)).atZone(zone).toInstant().toEpochMilli()
            val to = minOf(LocalDateTime.of(morning, LocalTime.NOON).atZone(zone).toInstant().toEpochMilli(), now)
            if (to <= from) return@mapNotNull null
            offIntervals(context, from, to)
                .maxByOrNull { it.second - it.first }
                ?.takeIf { it.second - it.first >= 60 * 60_000 } // under an hour isn't sleep
                ?.let { SleepNight(morning, it.first, it.second) }
        }
    }
}
