package com.hack.smartcam.phone

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.AlarmClock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.coroutines.resume

/** Steps from the phone's step-counter sensor. */
object Steps {
    fun available(context: Context) =
        context.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    /**
     * The sensor counts since the phone booted, so we store the first reading of each day
     * and report the difference. Returns null when there's no sensor or no reading.
     */
    suspend fun today(context: Context): Int? {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        val total = withTimeoutOrNull(4000) {
            suspendCancellableCoroutine<Long> { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values[0].toLong())
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
            }
        } ?: return null
        val prefs = context.getSharedPreferences("steps", Context.MODE_PRIVATE)
        val key = "base_" + LocalDate.now()
        var base = prefs.getLong(key, -1)
        if (base < 0 || total < base) base = total // first reading today, or the phone rebooted
        prefs.edit().putLong(key, base).apply()
        return (total - base).toInt()
    }
}

/** Real alarms in the phone's Clock app. */
object Alarms {
    fun set(context: Context, hour: Int, minute: Int, label: String): Boolean = runCatching {
        context.startActivity(
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    fun openClock(context: Context) = runCatching {
        context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
