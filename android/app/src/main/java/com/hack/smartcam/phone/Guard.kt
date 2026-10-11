package com.hack.smartcam.phone

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.hack.smartcam.MainActivity
import com.hack.smartcam.R
import com.hack.smartcam.data.Repo
import java.time.LocalTime

/** Device admin with only the "lock the screen" policy (res/xml/device_admin.xml). */
class GuardAdmin : DeviceAdminReceiver()

/**
 * Locks the screen during bedtime, and whenever an app that's over its daily limit is opened.
 * Runs as a foreground service so it keeps working while SmartCam is closed.
 */
class GuardService : Service() {
    companion object {
        const val CHANNEL = "guard"
        private const val ID = 7
        private const val SNOOZE_MS = 15 * 60_000L

        fun start(context: Context) = context.startForegroundService(Intent(context, GuardService::class.java))
        fun stop(context: Context) = context.stopService(Intent(context, GuardService::class.java))

        fun admin(context: Context) = ComponentName(context, GuardAdmin::class.java)
        fun adminActive(context: Context) =
            context.getSystemService(DevicePolicyManager::class.java).isAdminActive(admin(context))

        fun adminIntent(context: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(context))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "SmartCam locks the screen at bedtime and when an app's daily limit runs out. It can't see or change anything else.")

        fun snooze(context: Context) = context.getSharedPreferences("guard", MODE_PRIVATE).edit()
            .putLong("snoozeUntil", System.currentTimeMillis() + SNOOZE_MS).apply()

        private fun snoozed(context: Context) =
            context.getSharedPreferences("guard", MODE_PRIVATE).getLong("snoozeUntil", 0) > System.currentTimeMillis()

        fun inWindow(now: LocalTime, from: String, to: String): Boolean {
            val a = LocalTime.parse(from)
            val b = LocalTime.parse(to)
            return if (a <= b) now >= a && now < b else now >= a || now < b // window may cross midnight
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastReason = ""

    private val tick = object : Runnable {
        override fun run() {
            runCatching { check() }
            handler.postDelayed(this, 5000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = notification("Watching bedtime and app limits")
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, n)
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun check() {
        val s = Repo.state.value.settings
        if (!s.guardOn) { stopSelf(); return }
        if (!getSystemService(PowerManager::class.java).isInteractive || snoozed(this) || !adminActive(this)) return

        val reason = when {
            inWindow(LocalTime.now(), s.sleepLock, s.sleepUnlock) -> "It's bedtime — your phone unlocks at ${com.hack.smartcam.ui.Fmt.time12(s.sleepUnlock)}."
            else -> Usage.foregroundApp(this)?.let { pkg ->
                val limit = s.appLimits[pkg] ?: return@let null
                if (Usage.minutesToday(this, pkg) >= limit) "${Usage.label(this, pkg)} is over its ${limit}-minute daily limit." else null
            }
        } ?: return

        getSystemService(DevicePolicyManager::class.java).lockNow()
        if (reason != lastReason) {
            lastReason = reason
            getSystemService(NotificationManager::class.java).notify(ID, notification(reason))
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val snooze = PendingIntent.getBroadcast(
            this, 1, Intent(this, GuardActions::class.java).setAction("snooze"), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_smartcam)
            .setContentTitle("SmartCam screen lock")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Give me 15 min", snooze).build())
            .build()
    }
}

/** "Give me 15 min" button on the notification — works from the lock screen. */
class GuardActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "snooze") GuardService.snooze(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Repo.state.value.settings.guardOn) GuardService.start(context)
    }
}
