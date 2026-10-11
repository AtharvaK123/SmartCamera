package com.hack.smartcam.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * The signed-in person's data: a JSON file per account on the phone, mirrored to Tiger Data
 * under their username. Guests get an empty state that's never saved.
 */
object Repo {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var dir: File
    private var file: File? = null
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state
    private var saveJob: Job? = null

    /** Username whose data is loaded ("" for guests / signed out). */
    var user: String = ""
        private set

    fun init(context: Context) {
        dir = File(context.filesDir, "users").apply { mkdirs() }
    }

    /** Loads one account's data (local first, then newer data from Tiger if there is any). */
    fun open(username: String) {
        saveJob?.cancel()
        user = username
        val f = File(dir, "$username.json")
        // Data saved before accounts existed goes to the first account that signs in.
        val legacy = File(dir.parentFile, "state.json")
        if (!f.exists() && legacy.exists()) runCatching { legacy.renameTo(f) }
        file = f
        _state.value = runCatching { json.decodeFromString(AppState.serializer(), f.readText()) }.getOrDefault(AppState())
        scope.launch {
            Tiger.connect()
            Tiger.fetchState(username)?.let { remote ->
                if (remote.updatedAt > _state.value.updatedAt && user == username) {
                    _state.value = remote.copy(foods = _state.value.foods, chat = _state.value.chat.ifEmpty { remote.chat })
                    save()
                }
            }
            pullFoods()
        }
    }

    /** Guest mode or signed out: nothing is stored. */
    fun close() {
        saveJob?.cancel()
        user = ""
        file = null
        _state.value = AppState()
    }

    val isGuest get() = file == null

    fun update(fn: (AppState) -> AppState) {
        _state.update { fn(it).copy(updatedAt = System.currentTimeMillis()) }
        if (file == null) return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(300)
            save()
            delay(1500)
            Tiger.pushState(user, _state.value)
        }
    }

    private fun save() {
        val f = file ?: return
        runCatching { f.writeText(json.encodeToString(AppState.serializer(), _state.value)) }
    }

    fun deleteLocal(username: String) {
        File(dir, "$username.json").delete()
    }

    fun newId(prefix: String) = prefix + UUID.randomUUID().toString().take(12)
    fun todayKey(): String = LocalDate.now().toString()

    /* ---- foods: also written row-by-row to Tiger so the website shows them ---- */

    fun addFood(f: Food) {
        update { it.copy(foods = it.foods + f) }
        val u = user
        if (!isGuest) scope.launch { Tiger.upsertFood(u, f) }
    }

    fun removeFood(id: String) {
        update { s -> s.copy(foods = s.foods.filterNot { it.id == id }) }
        val u = user
        if (!isGuest) scope.launch { Tiger.deleteFood(u, id) }
    }

    /** Merge foods logged elsewhere (e.g. the website) into the phone's list. */
    suspend fun pullFoods() {
        val u = user.takeIf { !isGuest } ?: return
        val remote = Tiger.fetchFoods(u) ?: return
        if (u != user) return
        update { s ->
            val ids = s.foods.map { it.id }.toSet()
            s.copy(foods = s.foods + remote.filter { it.id !in ids })
        }
    }

    fun addVitals(v: Vitals) {
        update { it.copy(vitals = (it.vitals + v).takeLast(300)) }
        val u = user
        if (!isGuest) scope.launch { Tiger.insertVitals(u, v) }
    }

    /** Heart-rate samples for the Home chart; keeps about two days. */
    fun addHr(bpm: Int) {
        val now = System.currentTimeMillis()
        update { s -> s.copy(hr = (s.hr.filter { now - it.t < 48 * 3600_000L } + HrPoint(now, bpm)).takeLast(3000)) }
    }

    fun setSteps(steps: Int) = update { it.copy(steps = it.steps + (todayKey() to steps), stepsFromSensor = true) }
}
