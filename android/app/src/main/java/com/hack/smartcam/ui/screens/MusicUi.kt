package com.hack.smartcam.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hack.smartcam.data.Repo
import com.hack.smartcam.data.UserSong
import com.hack.smartcam.media.Mood
import com.hack.smartcam.media.Music
import com.hack.smartcam.media.NowPlaying
import com.hack.smartcam.ui.Btn
import com.hack.smartcam.ui.Ic
import com.hack.smartcam.ui.IconBtn
import com.hack.smartcam.ui.MusicLogo
import com.hack.smartcam.ui.P
import com.hack.smartcam.ui.ScButton
import com.hack.smartcam.ui.ScSheet
import com.hack.smartcam.ui.Segmented
import com.hack.smartcam.ui.T
import com.hack.smartcam.ui.Toasts
import kotlinx.coroutines.launch

@Composable
fun MiniPlayer(n: NowPlaying, moodLabel: String?, onDark: Boolean = false) {
    val ink = if (onDark) Color.White else P.ink
    val ink2 = if (onDark) Color.White.copy(alpha = 0.7f) else P.ink2
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MusicLogo(40.dp)
        Column(Modifier.weight(1f)) {
            Text(n.title, style = T.bodyStrong, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(moodLabel, "${n.index + 1} of ${n.count}").joinToString(" · "),
                style = T.small, color = ink2, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconBtn(Ic.prev, "Previous", { Music.previous() }, tint = ink, size = 38.dp)
        IconBtn(if (n.playing) Ic.pause else Ic.play, if (n.playing) "Pause" else "Play", { Music.toggle() }, tint = P.accentInk, bg = P.accent, size = 42.dp)
        IconBtn(Ic.next, "Next", { Music.next() }, tint = ink, size = 38.dp)
    }
}

/** Music controls + "play for my mood" from the camera tab. */
@Composable
fun MusicSheet(suggested: Mood?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val now by Music.now.collectAsState()
    val mood by Music.mood.collectAsState()
    val scope = rememberCoroutineScope()
    var level by remember { mutableStateOf(suggested?.level ?: mood?.level ?: "low") }
    ScSheet("Mood music", onDismiss) {
        val n = now
        if (n != null) MiniPlayer(n, mood?.label)
        else Text("Nothing is playing.", style = T.body, color = P.ink2)
        if (suggested != null) {
            ScButton("Play “${suggested.label}”", { scope.launch { Toasts.show(Music.playForMe(ctx, suggested)) } }, Modifier.fillMaxWidth(), icon = Ic.music, style = Btn.Pop)
        }
        Text("Or pick a mix", style = T.h3, color = P.ink)
        Segmented(listOf("low" to "Low stress", "moderate" to "Moderate", "high" to "High"), level) { level = it }
        ScButton("Play ${Music.levelName(level).lowercase()} mix", { Toasts.show(Music.play(ctx, Music.moodForLevel(level))) }, Modifier.fillMaxWidth(), icon = Ic.play, style = Btn.Ghost)
        Text("Gemini picks songs that match the music you chose at sign-up. Add your own in Settings → Mood music.", style = T.small, color = P.ink2)
    }
}

/** The music pill in the camera's D bar: shows what's playing, opens the music controls. */
@Composable
fun MusicChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val now by Music.now.collectAsState()
    val mood by Music.mood.collectAsState()
    Row(
        modifier.clip(CircleShape).background(com.hack.smartcam.ui.Brand.midnight).clickable(onClick = onClick)
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MusicLogo(22.dp)
        Text(
            now?.takeIf { it.playing }?.title ?: mood?.label ?: "Music",
            style = T.small, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Settings: the songs for each stress level — the bundled ones plus any added from the phone. */
@Composable
fun SongsManager() {
    val ctx = LocalContext.current
    val s by Repo.state.collectAsState()
    var level by remember { mutableStateOf("high") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val added = uris.map { uri ->
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = runCatching {
                ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull() ?: "Song"
            UserSong(Music.titleOf(name), uri.toString())
        }
        Repo.update { st ->
            val m = st.settings.songs.toMutableMap()
            m[level] = (m[level].orEmpty() + added).distinctBy { it.uri }
            st.copy(settings = st.settings.copy(songs = m))
        }
        Toasts.show("Added ${added.size} song${if (added.size == 1) "" else "s"}")
    }
    Segmented(listOf("high" to "High stress", "moderate" to "Moderate", "low" to "Low"), level) { level = it }
    val bundled = remember(level) { Music.bundled(ctx, level) }
    val mine = s.settings.songs[level].orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (bundled.isEmpty() && mine.isEmpty()) Text("No songs yet for this level.", style = T.small, color = P.ink2)
        bundled.forEach { SongRow(it.title, "Built in", null) }
        mine.forEach { song ->
            SongRow(song.title, "From your phone") {
                Repo.update { st ->
                    val m = st.settings.songs.toMutableMap()
                    m[level] = m[level].orEmpty().filterNot { it.uri == song.uri }
                    st.copy(settings = st.settings.copy(songs = m))
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScButton("Add songs", { pick.launch(arrayOf("audio/*")) }, Modifier.weight(1f), icon = Ic.plus, style = Btn.Ghost, small = true)
        ScButton("Preview", { Toasts.show(Music.play(ctx, Music.moodForLevel(level))) }, icon = Ic.play, style = Btn.Ghost, small = true)
    }
}

@Composable
private fun SongRow(title: String, sub: String, onRemove: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(P.card2), contentAlignment = Alignment.Center) {
            Icon(Ic.music, null, Modifier.size(16.dp), tint = P.ink2)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = T.bodyStrong, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = T.small, color = P.ink3)
        }
        if (onRemove != null) IconBtn(Ic.trash, "Remove", onRemove, tint = P.ink2, size = 36.dp)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(P.line))
}
