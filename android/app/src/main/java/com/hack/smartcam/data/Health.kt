package com.hack.smartcam.data

import com.hack.smartcam.net.Gemini
import com.hack.smartcam.net.num
import com.hack.smartcam.net.str
import com.hack.smartcam.ui.Fmt
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/** Calories in and out, daily limits, and Gemini's plan for tomorrow. */
object Health {
    data class Totals(val kcal: Double, val protein: Double, val carbs: Double, val sugar: Double, val fat: Double, val satFat: Double, val fiber: Double, val salt: Double)
    data class Limits(val kcal: Int, val proteinMin: Int, val sugarMax: Int, val fatMax: Int, val saltMax: Double, val activeKcal: Int, val fromPlan: Boolean)
    data class Burned(val resting: Int, val active: Int) { val total get() = resting + active }

    private fun weight(p: Profile?) = p?.weightKg ?: 70.0

    /** Resting calories per day (Mifflin–St Jeor). */
    fun bmr(p: Profile?): Double {
        val w = weight(p); val h = p?.heightCm ?: 170.0; val a = (p?.age ?: 30).toDouble()
        val base = 10 * w + 6.25 * h - 5 * a
        return base + when (p?.sex) { "male" -> 5.0; "female" -> -161.0; else -> -78.0 }
    }

    private fun factor(activity: String?) = when (activity) { "sitting" -> 1.2; "active" -> 1.55; "very" -> 1.725; else -> 1.375 }

    fun totals(foods: List<Food>) = Totals(
        foods.sumOf { it.kcal ?: 0.0 }, foods.sumOf { it.protein ?: 0.0 }, foods.sumOf { it.carbs ?: 0.0 },
        foods.sumOf { it.sugar ?: 0.0 }, foods.sumOf { it.fat ?: 0.0 }, foods.sumOf { it.satFat ?: 0.0 },
        foods.sumOf { it.fiber ?: 0.0 }, foods.sumOf { it.salt ?: 0.0 },
    )

    /** Today's plan if Gemini made one, otherwise sensible defaults from the profile. */
    fun limits(s: AppState): Limits {
        s.plan?.takeIf { it.forDate == LocalDate.now().toString() }?.let {
            return Limits(it.kcal, it.proteinMin, it.sugarMax, it.fatMax, it.saltMax, it.activeKcal, true)
        }
        val kcal = (bmr(s.profile) * factor(s.profile?.activity) / 10).roundToInt() * 10
        return Limits(kcal, (0.8 * weight(s.profile)).roundToInt(), 50, (kcal * 0.33 / 9).roundToInt(), 5.0, 300, false)
    }

    /** Resting burn so far today plus walking (≈0.04 kcal per step for a 70 kg person). */
    fun burned(s: AppState): Burned {
        val now = LocalTime.now()
        val dayFraction = (now.hour * 60 + now.minute) / 1440.0
        val steps = s.steps[LocalDate.now().toString()] ?: 0
        return Burned((bmr(s.profile) * dayFraction).roundToInt(), (steps * 0.04 * weight(s.profile) / 70).roundToInt())
    }

    fun exercisedEnough(s: AppState): Boolean {
        val steps = s.steps[LocalDate.now().toString()] ?: 0
        return steps >= s.settings.stepGoal * 0.8 || burned(s).active >= limits(s).activeKcal
    }

    /** What logging [extra] would push over today's limits. */
    fun warnings(s: AppState, extra: Food): List<String> {
        val t = totals(Fmt.todaysFoods(s) + extra)
        val l = limits(s)
        return buildList {
            if (t.kcal > l.kcal) add("Calories: ${t.kcal.roundToInt()} of ${l.kcal} kcal (${(t.kcal - l.kcal).roundToInt()} over)")
            if (t.sugar > l.sugarMax) add("Sugar: ${t.sugar.roundToInt()} of ${l.sugarMax} g")
            if (t.fat > l.fatMax) add("Fat: ${t.fat.roundToInt()} of ${l.fatMax} g")
            if (t.salt > l.saltMax) add("Salt: ${"%.1f".format(t.salt)} of ${"%.1f".format(l.saltMax)} g")
        }
    }

    /** Gemini reads the profile, today's numbers and the chat, and writes tomorrow's plan. */
    suspend fun makePlan(s: AppState): HealthPlan {
        val today = Fmt.todaysFoods(s)
        val t = totals(today)
        val b = burned(s)
        val p = s.profile
        val chat = s.chat.takeLast(20).joinToString("\n") { "${if (it.role == "user") "User" else "Assistant"}: ${it.text.take(300)}" }
        val tomorrow = LocalDate.now().plusDays(1)
        val r = Gemini.generateJson(
            buildString {
                append("Make a simple, realistic plan for tomorrow (${tomorrow.dayOfWeek.name.lowercase()}) to help this person be healthier.\n")
                append("Profile: name ${p?.name ?: "?"}, age ${p?.age ?: "?"}, sex ${p?.sex ?: "?"}, height ${p?.heightCm ?: "?"} cm, weight ${p?.weightKg ?: "?"} kg, ")
                append("activity ${p?.activity ?: "?"}, goal \"${p?.goal ?: ""}\", diet notes \"${p?.diet ?: ""}\", usual sleep ${p?.sleepHours ?: 8} h.\n")
                append("Today: ate ${t.kcal.roundToInt()} kcal (protein ${t.protein.roundToInt()} g, sugar ${t.sugar.roundToInt()} g, fat ${t.fat.roundToInt()} g, salt ${"%.1f".format(t.salt)} g) ")
                append("from ${today.joinToString { it.name }.ifEmpty { "nothing logged" }}; ${s.steps[LocalDate.now().toString()] ?: 0} steps; burned about ${b.total} kcal so far (${b.active} from activity).\n")
                s.vitals.lastOrNull()?.let { append("Last scan: heart rate ${it.hr}, stress ${it.stress}/100.\n") }
                append("Bedtime lock ${s.settings.sleepLock}, wake ${s.settings.sleepUnlock}.\n")
                if (chat.isNotBlank()) append("Recent chat with the user (use it to understand their habits and preferences):\n$chat\n")
                append(
                    "Return JSON: {\"summary\": \"2 short sentences, friendly\", \"kcal\": daily calorie target, \"proteinMin\": grams, \"sugarMax\": grams, " +
                        "\"fatMax\": grams, \"saltMax\": grams, \"activeKcal\": calories to burn through activity, " +
                        "\"items\": [{\"time\": \"HH:MM\" 24h, \"title\": \"short\", \"detail\": \"one sentence\", \"kind\": \"exercise\"|\"meal\"|\"habit\"}]} " +
                        "with 5 to 8 items spread across the day (meals, 1–2 easy workouts, a wind-down). No medical claims.",
                )
            },
            system = "You are a supportive health coach. Keep plans gentle and doable for a beginner.",
        ).jsonObject
        val items = r["items"]?.jsonArray?.mapNotNull { e ->
            val o = e.jsonObject
            val time = o.str("time")?.takeIf { Regex("^\\d{1,2}:\\d{2}$").matches(it) }?.padStart(5, '0') ?: return@mapNotNull null
            PlanItem(time, o.str("title") ?: return@mapNotNull null, o.str("detail").orEmpty(), o.str("kind") ?: "habit")
        }.orEmpty().sortedBy { it.time }
        val d = limits(s)
        return HealthPlan(
            forDate = tomorrow.toString(),
            created = System.currentTimeMillis(),
            summary = r.str("summary").orEmpty(),
            kcal = r.num("kcal")?.roundToInt() ?: d.kcal,
            proteinMin = r.num("proteinMin")?.roundToInt() ?: d.proteinMin,
            sugarMax = r.num("sugarMax")?.roundToInt() ?: d.sugarMax,
            fatMax = r.num("fatMax")?.roundToInt() ?: d.fatMax,
            saltMax = r.num("saltMax") ?: d.saltMax,
            activeKcal = r.num("activeKcal")?.roundToInt() ?: d.activeKcal,
            items = items,
        )
    }
}
