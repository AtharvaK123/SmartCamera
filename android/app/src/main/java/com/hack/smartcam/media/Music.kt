package com.hack.smartcam.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import com.hack.smartcam.data.Repo
import com.hack.smartcam.data.Vitals
import com.hack.smartcam.net.Gemini
import com.hack.smartcam.net.num
import com.hack.smartcam.net.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.jsonObject

/** level: "low" | "moderate" | "high" stress. */
data class Mood(val level: String, val label: String)
data class Song(val title: String, val source: String) // "asset:music/…" or a content:// URI
data class NowPlaying(val title: String, val level: String, val playing: Boolean, val index: Int, val count: Int)

/**
 * Mood music played by SmartCam itself — no other app opens.
 * Songs come from the project's music/<level>-stress folders (built into the app)
 * plus any the user adds from their phone in Settings.
 */
object Music {
    val now = MutableStateFlow<NowPlaying?>(null)
    val mood = MutableStateFlow<Mood?>(null)

    val LEVELS = listOf("low", "moderate", "high")
    fun levelName(level: String) = when (level) { "high" -> "High stress"; "moderate" -> "Moderate stress"; else -> "Low stress" }
    private fun label(level: String) = when (level) { "high" -> "Wind down"; "moderate" -> "Ease off"; else -> "Stay in the flow" }

    fun levelFor(stress: Int) = when { stress >= 67 -> "high"; stress >= 34 -> "moderate"; else -> "low" }
    fun moodFor(v: Vitals) = Mood(levelFor(v.stress), label(levelFor(v.stress)))
    fun moodForLevel(level: String) = Mood(level, label(level))

    private val EXT = setOf("mp3", "m4a", "aac", "ogg", "wav", "flac", "opus")

    fun titleOf(file: String) = file.substringAfterLast('/').substringBeforeLast('.')
        .replace('_', ' ').replace('-', ' ').trim().replaceFirstChar { it.uppercase() }

    fun bundled(context: Context, level: String): List<Song> {
        val dir = "music/$level-stress"
        return runCatching { context.assets.list(dir).orEmpty() }.getOrDefault(emptyArray())
            .filter { it.substringAfterLast('.').lowercase() in EXT }
            .sorted()
            .map { Song(titleOf(it), "asset:$dir/$it") }
    }

    fun songs(context: Context, level: String): List<Song> =
        bundled(context, level) + Repo.state.value.settings.songs[level].orEmpty().map { Song(it.title, it.uri) }

    /* ---- playback ---- */

    private var player: MediaPlayer? = null
    private var queue: List<Song> = emptyList()
    private var index = 0
    private var level = "low"
    private var focus: AudioFocusRequest? = null
    private var pausedByFocus = false
    private lateinit var app: Context

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        val p = player ?: return@OnAudioFocusChangeListener
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (p.isPlaying) { p.pause(); pausedByFocus = change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT; publish() }
            AudioManager.AUDIOFOCUS_GAIN -> if (pausedByFocus) { pausedByFocus = false; p.start(); publish() }
        }
    }

    /** Artist and genre tags read from the audio file itself (cached). */
    private val tags = mutableMapOf<String, Pair<String?, String?>>()

    private fun tagsOf(context: Context, song: Song): Pair<String?, String?> = tags.getOrPut(song.source) {
        runCatching {
            val r = MediaMetadataRetriever()
            try {
                if (song.source.startsWith("asset:")) context.assets.openFd(song.source.removePrefix("asset:")).use { r.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                else r.setDataSource(context, Uri.parse(song.source))
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) to r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            } finally { r.release() }
        }.getOrDefault(null to null)
    }

    /**
     * Mood music picked for this person: Gemini looks at the music taste from setup, the mood,
     * and every song available (title, artist, genre, stress level), and chooses what to play first.
     * Falls back to a shuffled playlist for the mood if Gemini is slow or unavailable.
     */
    suspend fun playForMe(context: Context, m: Mood): String {
        val taste = Repo.state.value.profile?.music.orEmpty()
        val all = withContext(Dispatchers.IO) { LEVELS.flatMap { lv -> songs(context, lv).map { lv to it } } }
        if (all.isEmpty() || !Gemini.available) return play(context, m)
        val catalog = withContext(Dispatchers.IO) {
            all.mapIndexed { i, (lv, song) ->
                val (artist, genre) = tagsOf(context, song)
                "$i. \"${song.title}\"" + (artist?.let { " by $it" } ?: "") + (genre?.let { " [$it]" } ?: "") + " — for $lv stress"
            }
        }
        val pick = withTimeoutOrNull(6000) {
            runCatching {
                Gemini.generateJson(
                    "Pick ONE song for someone whose stress is ${m.level} (goal: \"${m.label}\"). " +
                        "Their favourite genres: ${taste.ifEmpty { listOf("no preference") }.joinToString()}. " +
                        "Prefer songs that match their genres; if none do, pick the one that best fits the mood. Songs:\n" +
                        catalog.joinToString("\n") +
                        "\nReturn JSON {\"index\": number, \"why\": \"under 8 words\"}.",
                ).jsonObject
            }.getOrNull()
        }
        val i = pick?.num("index")?.toInt()?.takeIf { it in all.indices } ?: return play(context, m)
        val first = all[i].second
        play(context, m, first)
        val why = pick.str("why")?.takeIf { it.isNotBlank() }
        return "Gemini picked “${first.title}”" + (why?.let { " — $it" } ?: "")
    }

    /** Starts the playlist for a mood (optionally starting with [first]). Returns a short message for the UI. */
    fun play(context: Context, m: Mood, first: Song? = null): String {
        app = context.applicationContext
        mood.value = m
        level = m.level
        val rest = songs(context, m.level).filterNot { it.source == first?.source }.shuffled()
        queue = listOfNotNull(first) + rest
        if (queue.isEmpty()) {
            stop()
            return "No songs for ${levelName(m.level).lowercase()} yet — add some in Settings"
        }
        index = 0
        start()
        return "Playing “${m.label}”"
    }

    private fun start() {
        val song = queue.getOrNull(index) ?: return
        player?.runCatching { release() }
        player = null
        runCatching {
            val mp = MediaPlayer()
            mp.setAudioAttributes(attrs)
            if (song.source.startsWith("asset:")) {
                app.assets.openFd(song.source.removePrefix("asset:")).use { fd -> mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            } else mp.setDataSource(app, Uri.parse(song.source))
            mp.setOnCompletionListener { next() }
            mp.setOnErrorListener { _, _, _ -> next(); true }
            mp.prepare()
            if (requestFocus()) mp.start()
            player = mp
        }.onFailure { // unreadable file (e.g. permission revoked): skip it
            queue = queue.filterIndexed { i, _ -> i != index }
            if (queue.isNotEmpty()) { index %= queue.size; start() } else stop()
            return
        }
        publish()
    }

    private fun requestFocus(): Boolean {
        val am = app.getSystemService(AudioManager::class.java)
        if (focus == null) focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setWillPauseWhenDucked(false) // Android lowers the volume itself while ElevenLabs talks
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        return am.requestAudioFocus(focus!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun publish() {
        val song = queue.getOrNull(index)
        now.value = if (player == null || song == null) null else NowPlaying(song.title, level, player?.isPlaying == true, index, queue.size)
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else if (requestFocus()) p.start()
        pausedByFocus = false
        publish()
    }

    fun next() {
        if (queue.isEmpty()) return
        index = (index + 1) % queue.size
        start()
    }

    fun previous() {
        val p = player ?: return
        if (p.currentPosition > 4000) { p.seekTo(0); return }
        index = (index - 1 + queue.size) % queue.size
        start()
    }

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null
        if (::app.isInitialized) focus?.let { app.getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        publish()
    }
}
