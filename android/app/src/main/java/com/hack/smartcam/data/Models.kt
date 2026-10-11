package com.hack.smartcam.data

import kotlinx.serialization.Serializable

@Serializable
data class Food(
    val id: String,
    val t: Long,
    val name: String,
    val source: String, // barcode | photo | manual
    val barcode: String? = null,
    val image: String? = null, // https URL (Open Food Facts) or a local file path (photos)
    val basis: String = "per serving",
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val sugar: Double? = null,
    val fat: Double? = null,
    val satFat: Double? = null,
    val fiber: Double? = null,
    val salt: Double? = null,
    val grade: String? = null, // Nutri-Score a–e
)

@Serializable
data class Vitals(
    val t: Long,
    val hr: Int,
    val br: Int,
    val stress: Int,
    val hrv: Double? = null,
    val mood: String? = null,
    val source: String = "presage",
)

@Serializable
data class Alarm(val id: String, val time: String, val label: String, val on: Boolean = true)

@Serializable
data class ChatMsg(val id: String, val role: String, val text: String)

@Serializable
data class Product(val name: String, val price: Double, val why: String)

@Serializable
data class Order(val id: String, val t: Long, val name: String, val usd: Double, val sol: Double?, val status: String)

/** Answers from the first-run questions. */
@Serializable
data class Profile(
    val name: String = "",
    val age: Int? = null,
    val sex: String? = null, // female | male | other
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val activity: String = "light", // sitting | light | active | very
    val goal: String = "feel better", // free text: lose weight, sleep more, …
    val diet: String = "", // vegetarian, allergies, …
    val music: List<String> = emptyList(), // genres they like
    val sleepHours: Double = 8.0,
)

/** One heart-rate sample (from Presage). */
@Serializable
data class HrPoint(val t: Long, val bpm: Int)

@Serializable
data class PlanItem(val time: String, val title: String, val detail: String = "", val kind: String = "exercise") // exercise | meal | habit

/** Gemini's plan for a day: food limits (used for warnings when logging food) and a schedule. */
@Serializable
data class HealthPlan(
    val forDate: String,
    val created: Long,
    val summary: String,
    val kcal: Int,
    val proteinMin: Int,
    val sugarMax: Int,
    val fatMax: Int,
    val saltMax: Double,
    val activeKcal: Int,
    val items: List<PlanItem> = emptyList(),
)

@Serializable
data class UserSong(val title: String, val uri: String)

@Serializable
data class Settings(
    val stepGoal: Int = 8000,
    val hrLow: Int = 50,
    val hrHigh: Int = 100,
    val autoSpeak: Boolean = true,
    val factsAloud: Boolean = true,
    val voiceId: String = "", // ElevenLabs voice for Gemini's answers ("" = the one in server/.env)
    val haptics: Boolean = true,
    val hapticStrength: String = "strong", // light | medium | strong
    val autoMusic: Boolean = true,
    val songs: Map<String, List<UserSong>> = emptyMap(), // stress level (low | moderate | high) → songs added from the phone
    val sleepLock: String = "22:30",
    val sleepUnlock: String = "06:45",
    val guardOn: Boolean = false,
    val appLimits: Map<String, Int> = emptyMap(), // package → minutes per day
    val theme: String = "auto",
    val demoVitals: Boolean = false,
)

@Serializable
data class AppState(
    val steps: Map<String, Int> = emptyMap(),
    val stepsFromSensor: Boolean = false,
    val vitals: List<Vitals> = emptyList(),
    val foods: List<Food> = emptyList(),
    val alarms: List<Alarm> = listOf(Alarm("a1", "07:00", "Wake up")),
    val chat: List<ChatMsg> = emptyList(),
    val shopQuery: String = "",
    val products: List<Product> = emptyList(),
    val orders: List<Order> = emptyList(),
    val settings: Settings = Settings(),
    val profile: Profile? = null,
    val hr: List<HrPoint> = emptyList(),
    val plan: HealthPlan? = null,
    val planDeclined: String? = null,
    val tutorialDone: Boolean = false, // the first-sign-in tour // date the user said "no" to a plan, so we don't keep asking
    val updatedAt: Long = 0,
)
