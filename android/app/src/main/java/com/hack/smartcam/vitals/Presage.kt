package com.hack.smartcam.vitals

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hack.smartcam.Keys
import com.hack.smartcam.data.Vitals
import com.presagetech.smartspectra.CameraSelection
import com.presagetech.smartspectra.SmartSpectraConfig
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.proto.MetricTypesProto.MetricType
import com.presagetech.smartspectra.proto.MetricsProto.ExpressionType
import com.presagetech.smartspectra.proto.MetricsProto.Metrics
import kotlin.math.ln
import kotlin.math.roundToInt

/** Presage SmartSpectra: pulse, breathing, HRV (incl. Baevsky stress index) and facial expression from the selfie camera. */
object Presage {
    private var configured = false

    val sdk: SmartSpectraSdk
        get() = SmartSpectraSdk.shared.also {
            if (!configured) {
                it.config.apiKey = Keys.presage
                it.config.imageOutputEnabled = false
                it.useCamera(CameraSelection.Front)
                it.config.requestedMetrics = SmartSpectraConfig.breathingMetrics + SmartSpectraConfig.cardioMetrics + listOf(MetricType.EXPRESSIONS)
                configured = true
            }
        }

    val available get() = Keys.presage.isNotBlank()

    fun expressionName(t: ExpressionType): String? = when (t) {
        ExpressionType.ANGRY -> "Angry"
        ExpressionType.CONTEMPT -> "Contempt"
        ExpressionType.DISGUST -> "Disgust"
        ExpressionType.FEAR -> "Fear"
        ExpressionType.HAPPY -> "Happy"
        ExpressionType.NEUTRAL -> "Neutral"
        ExpressionType.SAD -> "Sad"
        ExpressionType.SURPRISE -> "Surprise"
        else -> null
    }

    /**
     * 0–100 stress. Best: Baevsky stress index from HRV (≈30 relaxed … 600+ very stressed, log scale).
     * Next: RMSSD (low = stressed). Last resort: heart and breathing rate.
     */
    fun stressScore(hr: Int, br: Int, rmssd: Double?, baevsky: Double?): Int {
        if (baevsky != null && baevsky > 0) return ((ln(baevsky) - ln(30.0)) / (ln(600.0) - ln(30.0)) * 100).roundToInt().coerceIn(3, 97)
        if (rmssd != null && rmssd > 0) return (100 - (rmssd - 15) * 1.6).roundToInt().coerceIn(3, 97)
        return ((hr - 55) * 1.1 + (br - 12) * 3).roundToInt().coerceIn(5, 95)
    }
}

/** The latest values seen during a capture; kept between metric updates (lists can be empty in between). */
class LiveVitals {
    var hr by mutableStateOf<Int?>(null)
    var br by mutableStateOf<Int?>(null)
    var rmssd by mutableStateOf<Double?>(null)
    var baevsky by mutableStateOf<Double?>(null)
    var expression by mutableStateOf<String?>(null)
    private val expressionCounts = mutableMapOf<String, Int>()

    fun reset() {
        hr = null; br = null; rmssd = null; baevsky = null; expression = null
        expressionCounts.clear()
    }

    fun absorb(m: Metrics) {
        if (m.hasCardio()) {
            m.cardio.pulseRateList.lastOrNull { it.timestamp > 0 }?.value?.toDouble()?.roundToInt()?.takeIf { it in 30..220 }?.let { hr = it }
            m.cardio.hrvList.lastOrNull()?.let { h ->
                if (h.rmssd > 0) rmssd = h.rmssd
                if (h.baevsky > 0) baevsky = h.baevsky
            }
        }
        if (m.hasBreathing() && m.breathing.rateCount > 0) {
            m.breathing.rateList.last().value.toDouble().roundToInt().takeIf { it in 3..60 }?.let { br = it }
        }
        if (m.hasFace()) {
            val top = m.face.expressionList.lastOrNull()?.scoresList?.filter { it.confidence > 0f }?.maxByOrNull { it.confidence }
            Presage.expressionName(top?.type ?: return)?.let { name ->
                expressionCounts[name] = (expressionCounts[name] ?: 0) + 1
                expression = name
            }
        }
    }

    /** Most common expression over the scan — steadier than the last frame. */
    val mood: String? get() = expressionCounts.maxByOrNull { it.value }?.key ?: expression

    fun toVitals(): Vitals? {
        val h = hr ?: return null
        val b = br ?: 15
        return Vitals(
            t = System.currentTimeMillis(), hr = h, br = b,
            stress = Presage.stressScore(h, b, rmssd, baevsky),
            hrv = rmssd, mood = mood, source = "presage",
        )
    }

    /** For demos without a face in front of the camera (Settings → Presage → demo readings). */
    fun fakeFill() {
        hr = 64 + (0..20).random(); br = 12 + (0..6).random(); rmssd = 25.0 + (0..40).random()
        expression = listOf("Neutral", "Happy", "Sad", "Neutral").random()
        expressionCounts[expression!!] = 1
    }
}
