package com.hack.smartcam.net

import com.hack.smartcam.Keys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    val JSON = "application/json".toMediaType()
    val json = Json { ignoreUnknownKeys = true; isLenient = true }
}

class ApiException(message: String) : Exception(message)

object Gemini {
    val available get() = Keys.gemini.isNotBlank()

    /** One turn of history: role is "user" or "model". */
    data class Turn(val role: String, val text: String)

    suspend fun generate(
        prompt: String,
        system: String? = null,
        json: Boolean = false,
        imageJpegBase64: String? = null,
        history: List<Turn> = emptyList(),
        search: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        if (!available) throw ApiException("Gemini isn't available right now")
        if (search) {
            // Google Search grounding for up-to-date answers. Free keys often have no quota for it → answer without.
            runCatching { return@withContext call(body(prompt, system, json, imageJpegBase64, history, search = true), fallbackOn = setOf(404, 503)) }
        }
        call(body(prompt, system, json, imageJpegBase64, history, search = false), fallbackOn = setOf(404, 429, 503))
    }

    private fun body(prompt: String, system: String?, json: Boolean, imageJpegBase64: String?, history: List<Turn>, search: Boolean) =
        buildJsonObject {
            put("contents", buildJsonArray {
                history.takeLast(12).forEach { t ->
                    add(buildJsonObject {
                        put("role", t.role)
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", t.text) }) })
                    })
                }
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        if (imageJpegBase64 != null) add(buildJsonObject {
                            put("inline_data", buildJsonObject { put("mime_type", "image/jpeg"); put("data", imageJpegBase64) })
                        })
                        add(buildJsonObject { put("text", prompt) })
                    })
                })
            })
            if (system != null) put("system_instruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
            })
            if (json) put("generationConfig", buildJsonObject { put("responseMimeType", "application/json") })
            if (search) put("tools", buildJsonArray { add(buildJsonObject { put("google_search", buildJsonObject {}) }) })
        }

    /** Tries the configured model first; if it's overloaded or unavailable, falls back to other Flash models. */
    private suspend fun call(body: JsonObject, fallbackOn: Set<Int>): String {
        var lastError = "Gemini error"
        for (model in models()) {
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                .header("x-goog-api-key", Keys.gemini)
                .post(body.toString().toRequestBody(Http.JSON))
                .build()
            for (attempt in 0..1) {
                val (code, text) = Http.client.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
                if (code in 200..299) {
                    val root = Http.json.parseToJsonElement(text).jsonObject
                    val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: JsonArray(emptyList())
                    return parts.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }.trim()
                }
                lastError = runCatching {
                    Http.json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                }.getOrNull() ?: "Gemini error $code"
                if (code !in fallbackOn) throw ApiException(lastError)
                if (code == 404 || code == 429) break // unavailable to this key, or out of quota → next model
                delay(700L shl attempt)
            }
        }
        throw ApiException(lastError)
    }

    private fun models() = (listOf(Keys.geminiModel) + FALLBACKS).distinct()
    private val FALLBACKS = listOf("gemini-3.7-flash", "gemini-3.6-flash", "gemini-3.5-flash", "gemini-flash-latest")

    /** Asks for JSON and parses it (tolerates ```json fences). */
    suspend fun generateJson(prompt: String, system: String? = null, imageJpegBase64: String? = null): JsonElement {
        val text = generate(prompt, system, json = true, imageJpegBase64 = imageJpegBase64)
        return Http.json.parseToJsonElement(text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
    }
}

fun JsonObject.str(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content
fun JsonObject.num(key: String): Double? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
