package com.hack.smartcam.net

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.hack.smartcam.Keys
import com.hack.smartcam.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/** One of the ElevenLabs voices people can pick during setup or in Settings. */
data class VoiceOption(val id: String, val name: String, val accent: String, val gender: String) // gender: woman | man

/** Reads text aloud with ElevenLabs; falls back to the phone's own voice. Ducks music while talking. */
object Voice {
    /**
     * English voices with different accents, a woman and a man for each, all from the ElevenLabs
     * Voice Library (picked for natural, conversational delivery; each one tested with this key).
     */
    val OPTIONS = listOf(
        VoiceOption("tnSpp4vdxKPjI9w0GnoV", "Hope", "American", "woman"),
        VoiceOption("UgBBYS2sOqTuMpoF3BR0", "Mark", "American", "man"),
        VoiceOption("Se2Vw1WbHmGbBbyWTuu4", "Allison", "British", "woman"),
        VoiceOption("G17SuINrv2H9FC6nvetn", "Christopher", "British", "man"),
        VoiceOption("2zRM7PkgwBPiau2jvVXc", "Monika", "Indian", "woman"),
        VoiceOption("2iAXJEMO2o0PqUHzvZwQ", "Ranbir", "Indian", "man"),
        VoiceOption("GCPLhb1XrVwcoKUJYcvz", "Nadya", "Russian", "woman"),
        VoiceOption("XaEUesE01wKIKaa0xI0h", "Alexey", "Russian", "man"),
        VoiceOption("tvWD4i07Hg5L4uEvbxYV", "Mary", "Spanish", "woman"),
        VoiceOption("86ZLAUcyPNBrbdJKn3u6", "Chris", "Spanish", "man"),
        VoiceOption("dTmTLshIypwp08eftJH6", "Sylvie", "French", "woman"),
        VoiceOption("xTZlmU8dKXdyk4XGYGFg", "Antoine", "French", "man"),
        VoiceOption("VSy05caiuOBJdp42Y45T", "Petra", "German", "woman"),
        VoiceOption("Jvf6TAXwMUVTSR20U0f9", "Klaus", "German", "man"),
        VoiceOption("75MqelvgFq5upx0r44WK", "Ginevra", "Italian", "woman"),
        VoiceOption("yowh82B72eMNrxcxHgBh", "Lorenzo", "Italian", "man"),
        VoiceOption("Z8dg0fyk7p6js7cQ7lgi", "Ololade", "Nigerian", "woman"),
        VoiceOption("zwbf3iHXH6YGoTCPStfx", "Dayo", "Nigerian", "man"),
        VoiceOption("gsm4lUH9bnZ3pjR1Pw7w", "Claire", "South African", "woman"),
        VoiceOption("fPVZbr0RJBH9KL47pnxU", "Ren", "South African", "man"),
        VoiceOption("bYPMZKhwcPc8RK0LuBpt", "Salma", "Arabic", "woman"),
        VoiceOption("Os2frcqCuUz8b9F93RuI", "Mahmoud", "Arabic", "man"),
        VoiceOption("zmcVlqmyk3Jpn5AVYcAL", "Sapphire", "Chinese", "woman"),
        VoiceOption("h1i3CVVBUuF6s46cxUGG", "Andy", "Chinese", "man"),
        VoiceOption("HBr48ROZd1B2dv74C8bN", "Sakura", "Japanese", "woman"),
        VoiceOption("Lci8YeL6PAFHJjNKvwXq", "Yusuke", "Japanese", "man"),
        VoiceOption("Zjb2Dbq5IbWDKpVOllIo", "Hanna", "Korean", "woman"),
        VoiceOption("wstmCVSVzHzNM3bbwPQw", "Ken", "Korean", "man"),
        VoiceOption("aEO01A4wXwd1O8GPgGlF", "Arabella", "Australian", "woman"),
        VoiceOption("DYkrAHD8iwork3YSUBbs", "Tom", "Australian", "man"),
        VoiceOption("t6WJWTYTryhcQnrBHdCr", "Louisamay", "Irish", "woman"),
        VoiceOption("9TYDukkUVpJPDSIuv3ir", "Darren", "Irish", "man"),
    )

    /** The signed-in person's chosen voice, or the default from server/.env. */
    val chosen: String get() = Repo.state.value.settings.voiceId.ifBlank { Keys.voiceId }

    /** Id of the message being read right now (for the speaker buttons), or null. */
    val speaking = MutableStateFlow<String?>(null)

    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var focus: AudioFocusRequest? = null

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    suspend fun elevenLabs(text: String, voice: String = Keys.voiceId): ByteArray = withContext(Dispatchers.IO) {
        if (Keys.elevenLabs.isBlank()) throw ApiException("ElevenLabs key missing")
        val body = buildJsonObject {
            put("text", text.take(1500))
            put("model_id", "eleven_multilingual_v2")
        }.toString().toRequestBody(Http.JSON)
        val req = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$voice?output_format=mp3_44100_128")
            .header("xi-api-key", Keys.elevenLabs)
            .post(body)
            .build()
        Http.client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw ApiException("ElevenLabs error ${res.code}")
            res.body!!.bytes()
        }
    }

    suspend fun speak(context: Context, text: String, id: String = "x", voice: String = chosen) {
        stop(context)
        if (text.isBlank()) return
        speaking.value = id
        requestFocus(context)
        try {
            val bytes = elevenLabs(text, voice)
            if (speaking.value != id) return // stopped while we were waiting
            val f = File(context.cacheDir, "speech.mp3").apply { writeBytes(bytes) }
            withContext(Dispatchers.Main) {
                player = MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(f.path)
                    setOnCompletionListener { done(context) }
                    setOnErrorListener { _, _, _ -> done(context); true }
                    prepare()
                    start()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { phoneVoice(context, text) }
        }
    }

    private fun phoneVoice(context: Context, text: String) {
        val say = {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) = done(context)
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = done(context)
            })
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "smartcam")
        }
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) say() else done(context)
            }
        } else say()
    }

    private fun requestFocus(context: Context) {
        val am = context.getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .build()
            .also { am.requestAudioFocus(it) }
    }

    private fun done(context: Context) {
        speaking.value = null
        focus?.let { context.getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
    }

    fun stop(context: Context) {
        player?.runCatching { stop(); release() }
        player = null
        tts?.stop()
        done(context)
    }
}
