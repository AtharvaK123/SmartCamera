package com.hack.smartcam.phone

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hack.smartcam.MainActivity
import com.hack.smartcam.R
import com.hack.smartcam.data.HealthPlan
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Gentle notifications for the items in Gemini's plan (no exact-alarm permission needed). */
object Reminders {
    const val CHANNEL = "plan"

    fun channel(context: Context) = context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(CHANNEL, "Plan reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Reminders for the workouts and meals in your SmartCam plan"
        },
    )

    /** Returns how many reminders were scheduled. */
    fun schedule(context: Context, plan: HealthPlan): Int {
        val am = context.getSystemService(AlarmManager::class.java)
        val day = LocalDate.parse(plan.forDate)
        var n = 0
        plan.items.forEachIndexed { i, item ->
            val at = runCatching { day.atTime(LocalTime.parse(item.time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull() ?: return@forEachIndexed
            if (at < System.currentTimeMillis()) return@forEachIndexed
            val pi = PendingIntent.getBroadcast(
                context, 100 + i,
                Intent(context, ReminderReceiver::class.java).putExtra("title", item.title).putExtra("detail", item.detail).putExtra("id", 100 + i),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 5 * 60_000L, pi)
            n++
        }
        return n
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(context, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_smartcam)
            .setContentTitle(intent.getStringExtra("title") ?: "Time for your plan")
            .setContentText(intent.getStringExtra("detail").orEmpty())
            .setStyle(Notification.BigTextStyle().bigText(intent.getStringExtra("detail").orEmpty()))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(intent.getIntExtra("id", 100), n) }
    }
}
