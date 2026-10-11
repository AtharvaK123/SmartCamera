package com.hack.smartcam.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hack.smartcam.MainActivity
import com.hack.smartcam.data.Accounts
import com.hack.smartcam.data.Repo
import com.hack.smartcam.data.Role
import com.hack.smartcam.net.Gemini
import com.hack.smartcam.net.Voice
import com.hack.smartcam.phone.Usage
import com.hack.smartcam.ui.Backdrop
import com.hack.smartcam.ui.Btn
import com.hack.smartcam.ui.Chip
import com.hack.smartcam.ui.ElevenLabsLogo
import com.hack.smartcam.ui.Fmt
import com.hack.smartcam.ui.Haptics
import com.hack.smartcam.ui.Ic
import com.hack.smartcam.ui.IconBtn
import com.hack.smartcam.ui.Level
import com.hack.smartcam.ui.MusicLogo
import com.hack.smartcam.ui.P
import com.hack.smartcam.ui.ScButton
import com.hack.smartcam.ui.Segmented
import com.hack.smartcam.ui.T
import com.hack.smartcam.ui.Toasts
import com.hack.smartcam.ui.Toggle
import com.hack.smartcam.ui.glass
import kotlinx.coroutines.launch

/** Full-screen settings panel (AppRoot slides it in). Each category is its own glass card. */
@Composable
fun SettingsPanel(onDismiss: () -> Unit, onEditProfile: () -> Unit, onSignIn: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Repo.state.collectAsState()
    val session by Accounts.session.collectAsState()
    val role = session?.role ?: Role.Guest
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        tick++
        if (ok) (ctx as? MainActivity)?.refreshSteps()
    }
    var privacyOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = !privacyOpen, onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        Backdrop()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", style = T.h1, color = P.ink, modifier = Modifier.weight(1f))
                IconBtn(Ic.close, "Close settings", onDismiss, bg = P.card2)
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val m = Modifier.widthIn(max = 640.dp).fillMaxWidth()

                Group("Account", Ic.user, m) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(P.accent), contentAlignment = Alignment.Center) {
                            Text((s.profile?.name?.takeIf { it.isNotBlank() } ?: session?.username ?: "?").take(1).uppercase(), style = T.h2, color = P.accentInk)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(if (role == Role.Guest) "Guest" else s.profile?.name?.takeIf { it.isNotBlank() } ?: session?.username.orEmpty(), style = T.bodyStrong, color = P.ink)
                            Text(if (role == Role.Guest) "Looking around — nothing is saved" else "@${session?.username}", style = T.small, color = P.ink2)
                        }
                        Chip(when (role) { Role.Admin -> "Admin"; Role.User -> "Member"; Role.Guest -> "Guest" }, if (role == Role.Admin) Level.Good else null, icon = if (role == Role.Admin) Ic.shield else null)
                    }
                    Divider()
                    if (role == Role.Guest) {
                        ScButton("Sign in or create an account", onSignIn, Modifier.fillMaxWidth(), icon = Ic.user)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ScButton("Edit my answers", onEditProfile, Modifier.weight(1f), icon = Ic.pencil, style = Btn.Ghost, small = true)
                            ScButton("Tour", { onDismiss(); Repo.update { it.copy(tutorialDone = false) } }, icon = Ic.spark, style = Btn.Ghost, small = true)
                            ScButton("Sign out", { onDismiss(); Accounts.signOut() }, icon = Ic.logout, style = Btn.Danger, small = true)
                        }
                    }
                }

                if (role == Role.Admin) Group("Admin · accounts", Ic.shield, m) { AdminAccounts() }

                if (role != Role.Guest) {
                    Group("Phone permissions", Ic.phone, m) {
                        androidx.compose.runtime.key(tick) {
                            PermRow("Camera", "Vitals scan and barcodes", granted(Manifest.permission.CAMERA)) { ask.launch(Manifest.permission.CAMERA) }
                            Divider()
                            PermRow("Microphone", "Talk to Gemini", granted(Manifest.permission.RECORD_AUDIO)) { ask.launch(Manifest.permission.RECORD_AUDIO) }
                            Divider()
                            PermRow("Physical activity", "Count your steps", granted(Manifest.permission.ACTIVITY_RECOGNITION)) { ask.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
                            if (Build.VERSION.SDK_INT >= 33) {
                                Divider()
                                PermRow("Notifications", "Screen-lock notices and plan reminders", granted(Manifest.permission.POST_NOTIFICATIONS)) { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            }
                            Divider()
                            PermRow("Usage access", "Screen time and sleep", Usage.hasAccess(ctx)) { Usage.openAccessSettings(ctx) }
                        }
                    }

                    Group("Mood music", Ic.music, m, logo = { MusicLogo(20.dp) }) {
                        SettingToggle("Play after a scan", "Presage reads your stress, then a matching playlist starts", s.settings.autoMusic) { v ->
                            Repo.update { it.copy(settings = it.settings.copy(autoMusic = v)) }
                        }
                        Divider()
                        SongsManager()
                    }

                    Group("Voice", Ic.speaker, m, logo = { ElevenLabsLogo(16.dp) }) {
                        SettingToggle("Read Gemini's answers aloud", "You can also tap the speaker next to any answer", s.settings.autoSpeak) { v ->
                            Repo.update { it.copy(settings = it.settings.copy(autoSpeak = v)) }
                        }
                        Divider()
                        SettingToggle("Read fun facts aloud", "During face scans and while Gemini looks at food", s.settings.factsAloud) { v ->
                            Repo.update { it.copy(settings = it.settings.copy(factsAloud = v)) }
                        }
                        Divider()
                        var picking by remember { mutableStateOf(false) }
                        val current = Voice.OPTIONS.firstOrNull { it.id == Voice.chosen }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Gemini's voice", style = T.bodyStrong, color = P.ink)
                                Text(current?.let { "${it.accent} ${it.gender} · ${it.name}" } ?: "Default voice", style = T.small, color = P.ink2)
                            }
                            ScButton(if (picking) "Done" else "Change", { picking = !picking }, small = true, style = Btn.Ghost)
                        }
                        if (picking) VoicePicker(Voice.chosen, s.profile?.name?.substringBefore(' ')?.ifBlank { null }) { id ->
                            Repo.update { it.copy(settings = it.settings.copy(voiceId = id)) }
                        }
                    }

                    Group("Screen lock", Ic.lock, m) { GuardSwitch() }
                }

                Group("Feel", Ic.phone, m) {
                    SettingToggle("Haptics", "Vibrations for taps, tabs, games and results", s.settings.haptics) { v ->
                        Repo.update { it.copy(settings = it.settings.copy(haptics = v)) }
                        if (v) Haptics.success()
                    }
                    if (s.settings.haptics) {
                        Divider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Strength", style = T.bodyStrong, color = P.ink, modifier = Modifier.weight(1f))
                            Segmented(listOf("light" to "Light", "medium" to "Medium", "strong" to "Strong"), s.settings.hapticStrength) { v ->
                                Repo.update { it.copy(settings = it.settings.copy(hapticStrength = v)) }
                                Haptics.sample()
                            }
                        }
                    }
                }

                Group("Privacy", Ic.shield, m) {
                    Row(
                        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) { Haptics.click(); privacyOpen = true }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Privacy Policy", style = T.bodyStrong, color = P.ink)
                            Text("What SmartCam collects, who sees it and your choices", style = T.small, color = P.ink2)
                        }
                        Icon(Ic.chevronRight, null, Modifier.size(18.dp), tint = P.ink2)
                    }
                    if (role == Role.User) {
                        Divider()
                        var confirmDelete by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                                Text("Delete my account", style = T.bodyStrong, color = P.ink)
                                Text(
                                    if (confirmDelete) "This permanently removes your account and all its data, on this phone and in the cloud. It can't be undone."
                                    else "Removes your account and everything saved under it",
                                    style = T.small, color = if (confirmDelete) P.bad else P.ink2,
                                )
                            }
                            if (confirmDelete) ScButton("Delete", {
                                Haptics.warning()
                                onDismiss()
                                Accounts.deleteMe { Toasts.show("Your account and data were deleted") }
                            }, small = true, style = Btn.Danger)
                            else ScButton("Delete", { confirmDelete = true }, small = true, style = Btn.Ghost)
                        }
                        if (confirmDelete) ScButton("Keep my account", { confirmDelete = false }, Modifier.fillMaxWidth(), small = true, style = Btn.Ghost)
                    }
                }

                Group("Appearance", Ic.spark, m) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Theme", style = T.bodyStrong, color = P.ink, modifier = Modifier.weight(1f))
                        Segmented(listOf("auto" to "Auto", "light" to "Light", "dark" to "Dark"), s.settings.theme) { v ->
                            Repo.update { it.copy(settings = it.settings.copy(theme = v)) }
                        }
                    }
                }

                Text("SmartCam ${com.hack.smartcam.BuildConfig.VERSION_NAME} · Powered by Presage", style = T.mono, color = P.ink3)
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            privacyOpen,
            enter = androidx.compose.animation.slideInHorizontally { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally { it } + androidx.compose.animation.fadeOut(),
        ) { PrivacyPolicyPanel { privacyOpen = false } }
    }
}

/** One settings category: a glass card with its own header. */
@Composable
private fun Group(title: String, icon: ImageVector, modifier: Modifier, logo: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier) {
        Row(Modifier.padding(start = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(P.accentSoft), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(15.dp), tint = P.ink)
            }
            Text(title.uppercase(), style = T.eyebrow.copy(fontSize = 12.sp), color = P.ink2, modifier = Modifier.weight(1f))
            logo?.invoke()
        }
        Column(
            Modifier.fillMaxWidth().glass().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(P.line))

@Composable
private fun AdminAccounts() {
    val scope = rememberCoroutineScope()
    val session by Accounts.session.collectAsState()
    var list by remember { mutableStateOf<List<Accounts.Listed>?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { list = Accounts.all() }
    val l = list
    when {
        l == null -> Text("Loading accounts…", style = T.small, color = P.ink2)
        l.isEmpty() -> Text("No one has signed up yet.", style = T.small, color = P.ink2)
        else -> l.forEachIndexed { i, a ->
            if (i > 0) Divider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("@${a.username}", style = T.bodyStrong, color = P.ink)
                    Text(
                        listOfNotNull(
                            if (a.created > 0) "joined ${Fmt.ago(a.created)}" else null,
                            if (a.inTiger) "synced" else "this phone only",
                        ).joinToString(" · "),
                        style = T.small, color = P.ink2,
                    )
                }
                if (a.username != session?.username) {
                    if (confirm == a.username) ScButton("Delete", {
                        scope.launch { Accounts.delete(a.username); Toasts.show("Deleted @${a.username}"); confirm = null; reload++ }
                    }, small = true, style = Btn.Danger)
                    else IconBtn(Ic.trash, "Delete @${a.username}", { confirm = a.username }, tint = P.ink2, size = 36.dp)
                }
            }
        }
    }
    Text("Deleting removes the account and all its data, everywhere it is stored.", style = T.small, color = P.ink3)
}

@Composable
private fun PermRow(title: String, sub: String, ok: Boolean, onAllow: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.bodyStrong, color = P.ink)
            Text(sub, style = T.small, color = P.ink2)
        }
        if (ok) StatusDot(true, "Allowed") else ScButton("Allow", onAllow, small = true, style = Btn.Ghost)
    }
}

@Composable
private fun StatusDot(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) P.good else P.ink3))
        Spacer(Modifier.size(6.dp))
        Text(text, style = T.mono.copy(fontSize = 12.sp), color = if (ok) P.good else P.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingToggle(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = T.bodyStrong, color = P.ink)
            Text(sub, style = T.small, color = P.ink2)
        }
        Toggle(on, onChange)
    }
}
