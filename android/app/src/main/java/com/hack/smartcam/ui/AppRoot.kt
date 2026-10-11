package com.hack.smartcam.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.smartcam.data.Accounts
import com.hack.smartcam.data.Repo
import com.hack.smartcam.data.Role as UserRole
import com.hack.smartcam.ui.screens.AuthScreen
import com.hack.smartcam.ui.screens.CameraScreen
import com.hack.smartcam.ui.screens.ChatPanel
import com.hack.smartcam.ui.screens.ChatSheet
import com.hack.smartcam.ui.screens.FoodScreen
import com.hack.smartcam.ui.screens.GuestChatSheet
import com.hack.smartcam.ui.screens.GuestOverview
import com.hack.smartcam.ui.screens.HomeScreen
import com.hack.smartcam.ui.screens.Onboarding
import com.hack.smartcam.ui.screens.PhoneScreen
import com.hack.smartcam.ui.screens.SettingsPanel
import com.hack.smartcam.ui.screens.ShopScreen
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

enum class Tab(val label: String, val title: String, val icon: ImageVector) {
    Home("Home", "Today", Ic.home),
    Camera("Camera", "Camera", Ic.camera),
    Food("Food", "Food", Ic.food),
    Phone("Phone", "Phone", Ic.phone),
    Shop("Shop", "Shop", Ic.bag),
}

/** True on landscape phones and tablets: sidebar layout, roomier grids. */
val LocalWide = compositionLocalOf { false }

private val SlowOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

@Composable
fun AppRoot() {
    val state by Repo.state.collectAsState()
    val session by Accounts.session.collectAsState()
    SmartCamTheme(state.settings.theme) {
        var editingProfile by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(P.bgBottom)) {
            Backdrop()
            val s = session
            when {
                s == null -> AuthScreen()
                s.role != UserRole.Guest && (state.profile == null || editingProfile) ->
                    Onboarding(editing = state.profile != null) { editingProfile = false }
                else -> MainApp(guest = s.role == UserRole.Guest, onEditProfile = { editingProfile = true })
            }
            // First sign-in: a short tour of the app (replay it from Settings).
            if (s != null && s.role != UserRole.Guest && state.profile != null && !editingProfile && !state.tutorialDone) {
                Tutorial(state.profile?.name?.substringBefore(' ')?.takeIf { it.isNotBlank() }) {
                    Repo.update { it.copy(tutorialDone = true) }
                }
            }
            ToastHost()
        }
    }
}

@Composable
private fun MainApp(guest: Boolean, onEditProfile: () -> Unit) {
    var start by rememberSaveable { mutableStateOf(Tab.Home) }
    // Tabs sit side by side: swipe left or right to move between them. At the first and last tab
    // the page stretches against your finger and springs back.
    val pager = rememberPagerState(initialPage = start.ordinal) { Tab.entries.size }
    val tab = Tab.entries[pager.targetPage]
    val scope = rememberCoroutineScope()
    val go: (Tab) -> Unit = { t -> scope.launch { pager.animateScrollToPage(t.ordinal, animationSpec = spring(dampingRatio = 0.86f, stiffness = 300f)) } }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { start = Tab.entries[it]; Haptics.tick() }
    }
    val position = { pager.currentPage + pager.currentPageOffsetFraction }
    var chatOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    val signIn = { settingsOpen = false; Accounts.signOut() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val chatDocked = maxWidth >= 1000.dp && !guest
        val compactRail = maxWidth < 840.dp
        val content: @Composable (Modifier) -> Unit = { m ->
            HorizontalPager(pager, m, key = { it }) { page ->
                val t = Tab.entries[page]
                Box(Modifier.fillMaxSize()) {
                    if (guest) GuestOverview(t, onSignIn = signIn)
                    else when (t) {
                        Tab.Home -> HomeScreen(onGo = go)
                        Tab.Camera -> CameraScreen()
                        Tab.Food -> FoodScreen()
                        Tab.Phone -> PhoneScreen()
                        Tab.Shop -> ShopScreen()
                    }
                }
            }
        }
        CompositionLocalProvider(LocalWide provides wide) {
            if (!wide) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Brand(Modifier.weight(1f))
                        if (guest) Chip("Guest", dashed = true)
                        IconBtn(Ic.dots, "Settings", { settingsOpen = true }, Modifier.tourTarget("settings"))
                    }
                    TabBar(tab, position, Modifier.tourTarget("tabs"), onSelect = go)
                    content(Modifier.weight(1f).fillMaxWidth())
                    if (tab != Tab.Shop) AskBar(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).tourTarget("ask")) { chatOpen = true }
                }
            } else {
                Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Rail(tab, compact = compactRail, onSelect = go, onSettings = { settingsOpen = true })
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Text(tab.title, style = T.h1, color = P.ink, modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 6.dp))
                        content(Modifier.weight(1f).fillMaxWidth())
                        if (!chatDocked && tab != Tab.Shop) AskBar(Modifier.padding(horizontal = 24.dp, vertical = 10.dp).widthIn(max = 720.dp)) { chatOpen = true }
                    }
                    if (chatDocked) {
                        Box(Modifier.padding(10.dp).width(380.dp).fillMaxHeight().glass().imePadding()) { ChatPanel(onClose = null) }
                    }
                }
            }
            if (chatOpen && !chatDocked) {
                if (guest) GuestChatSheet(onDismiss = { chatOpen = false }, onSignIn = signIn)
                else ChatSheet(onDismiss = { chatOpen = false })
            }

            // Settings rises slowly from the bottom, over a fading scrim.
            AnimatedVisibility(settingsOpen, enter = fadeIn(tween(600)), exit = fadeOut(tween(450))) {
                Box(Modifier.fillMaxSize().background(Brand.midnight.copy(alpha = 0.35f)).clickable(indication = null, interactionSource = null) { settingsOpen = false })
            }
            AnimatedVisibility(
                settingsOpen,
                enter = slideInVertically(tween(900, easing = SlowOut)) { it / 2 } + fadeIn(tween(700)) + scaleIn(tween(900, easing = SlowOut), initialScale = 0.94f),
                exit = slideOutVertically(tween(500)) { it / 2 } + fadeOut(tween(400)) + scaleOut(tween(500), targetScale = 0.96f),
            ) {
                SettingsPanel(
                    onDismiss = { settingsOpen = false },
                    onEditProfile = { settingsOpen = false; onEditProfile() },
                    onSignIn = signIn,
                )
            }
        }
    }
}

@Composable
private fun Brand(modifier: Modifier = Modifier) = Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    AppLogo(30.dp)
    Spacer(Modifier.width(10.dp))
    Text(
        buildAnnotatedString {
            append("Smart")
            withStyle(SpanStyle(color = P.pop)) { append("Cam") }
        },
        style = T.h1.copy(fontSize = 24.sp),
        color = P.ink,
    )
}

/**
 * Liquid-glass tab bar. The selected tab sits in a glass droplet that slides to the next tab:
 * its leading edge moves first and the trailing edge catches up, so it stretches like liquid.
 */
@Composable
private fun TabBar(tab: Tab, position: () -> Float, modifier: Modifier = Modifier, onSelect: (Tab) -> Unit) {
    val p = P
    val count = Tab.entries.size
    val left = remember { Animatable(tab.ordinal.toFloat()) }
    val right = remember { Animatable(tab.ordinal + 1f) }
    // The droplet follows the pages (and your finger while swiping); its edges chase at
    // different speeds so it stretches like liquid.
    LaunchedEffect(Unit) {
        val fast = spring<Float>(dampingRatio = 0.72f, stiffness = 900f)
        val slow = spring<Float>(dampingRatio = 0.8f, stiffness = 260f)
        snapshotFlow { position().coerceIn(0f, count - 1f) }.collect { target ->
            val goingRight = target > left.value
            launch { left.animateTo(target, if (goingRight) slow else fast) }
            launch { right.animateTo(target + 1, if (goingRight) fast else slow) }
        }
    }
    BoxWithConstraints(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).then(modifier).fillMaxWidth().height(54.dp).glass(CircleShape).padding(5.dp)) {
        val w = maxWidth / count
        Box(
            Modifier.offset(x = w * left.value).width(w * (right.value - left.value)).fillMaxHeight()
                .glass(CircleShape, fill = p.accent.copy(alpha = if (p.dark) 0.9f else 0.92f), elevated = false),
        )
        Row(Modifier.fillMaxSize()) {
            Tab.entries.forEach { t ->
                val on = t == tab
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Tab, indication = null, interactionSource = null) { onSelect(t) },
                    contentAlignment = Alignment.Center,
                ) { Icon(t.icon, t.label, Modifier.size(22.dp), tint = if (on) p.accentInk else p.ink2) }
            }
        }
    }
}

@Composable
private fun Rail(tab: Tab, compact: Boolean, onSelect: (Tab) -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.padding(10.dp).fillMaxHeight().width(if (compact) 72.dp else 210.dp).glass().padding(vertical = 14.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (!compact) Brand(Modifier.padding(start = 10.dp, bottom = 16.dp))
        Tab.entries.forEach { t -> RailItem(t.icon, t.label, t == tab, compact) { onSelect(t) } }
        Spacer(Modifier.weight(1f))
        RailItem(Ic.dots, "Settings", false, compact, onSettings)
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, on: Boolean, compact: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.then(if (compact) Modifier.size(52.dp) else Modifier.fillMaxWidth().height(46.dp))
            .then(if (on) Modifier.glass(RoundedCornerShape(16.dp), fill = P.accent, elevated = false) else Modifier)
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = if (compact) 0.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, label, Modifier.size(22.dp), tint = if (on) P.accentInk else P.ink2)
        if (!compact) Text(label, style = T.bodyStrong, color = if (on) P.accentInk else P.ink2)
    }
}

@Composable
private fun AskBar(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().height(54.dp).glass(CircleShape)
            .clickable(role = Role.Button, onClick = onClick).padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GeminiLogo(20.dp)
        Text("Ask Gemini", style = T.bodyStrong, color = P.ink2, modifier = Modifier.padding(start = 10.dp).weight(1f))
        Box(Modifier.size(38.dp).clip(CircleShape).background(P.accent), contentAlignment = Alignment.Center) {
            Icon(Ic.mic, null, Modifier.size(18.dp), tint = P.accentInk)
        }
    }
}
