package com.hack.smartcam.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.smartcam.ui.AppLogo
import com.hack.smartcam.ui.Backdrop
import com.hack.smartcam.ui.ElevenLabsLogo
import com.hack.smartcam.ui.GeminiLogo
import com.hack.smartcam.ui.Haptics
import com.hack.smartcam.ui.Ic
import com.hack.smartcam.ui.IconBtn
import com.hack.smartcam.ui.P
import com.hack.smartcam.ui.SolanaLogo
import com.hack.smartcam.ui.T
import com.hack.smartcam.ui.glass

/* SmartCam's privacy policy. Keep it in step with PRIVACY.md and web/privacy.html. */

const val PRIVACY_UPDATED = "October 11, 2026"
const val PRIVACY_CONTACT = "https://github.com/AtharvaK123/SmartCam/issues"

private class Glance(val icon: ImageVector, val title: String, val text: String)

private val GLANCE = listOf(
    Glance(Ic.camera, "No face video is kept", "The camera is used only while you scan. SmartCam saves the numbers, not the video."),
    Glance(Ic.shield, "Never sold, no ads", "Your data is not sold, rented or used for advertising."),
    Glance(Ic.user, "Your account, your data", "Everything is saved under your username, and you can delete it all from Settings."),
    Glance(Ic.lock, "Guests leave no trace", "Browsing as a guest saves nothing on the phone or in the cloud."),
)

private class Partner(val name: String, val gets: String, val why: String, val url: String, val logo: (@Composable () -> Unit)? = null, val icon: ImageVector? = null)

private val PARTNERS = listOf(
    Partner("Presage Technologies", "Camera frames during a face scan", "Turns the video into pulse, breathing, heart-rate variability and expression", "https://www.presagetech.com", icon = Ic.wave),
    Partner("Google Gemini", "Your questions, food photos, profile answers and the day's numbers you ask about", "Chat answers, food estimates, tomorrow's plan, song picks and shop suggestions", "https://policies.google.com/privacy", logo = { GeminiLogo(18.dp) }),
    Partner("ElevenLabs", "The text being read aloud", "Speaks answers and fun facts in the voice you chose", "https://elevenlabs.io/privacy-policy", logo = { ElevenLabsLogo(14.dp) }),
    Partner("Tiger Data", "Your account and everything you save in SmartCam", "Stores your data so you can sign in on another phone or the website", "https://www.tigerdata.com/legal/privacy", icon = Ic.refresh),
    Partner("Open Food Facts", "The barcode number you scan", "Looks up the product's nutrition", "https://world.openfoodfacts.org/privacy", icon = Ic.barcode),
    Partner("Solana network", "Your wallet address, the amount and an order memo", "Pays for a shop order. Blockchain payments are public and permanent", "https://solana.com/privacy-policy", logo = { SolanaLogo(16.dp) }),
    Partner("Google speech recognition", "What you say after tapping the microphone", "Turns your voice into text for Gemini. Handled by your phone's speech service", "https://policies.google.com/privacy", icon = Ic.mic),
)

private class Section(val icon: ImageVector, val title: String, val blocks: List<String>)
// A block that starts with "• " is a bullet; anything else is a paragraph.

private val SECTIONS = listOf(
    Section(Ic.info, "Who we are", listOf(
        "SmartCam is a wellness app for Android with a companion website, made by the SmartCam team. This policy explains what SmartCam collects, why, who it is shared with, how long it is kept and the choices you have.",
        "By creating an account or using SmartCam you agree to this policy. If you do not agree, please do not use the app. You can still look around as a guest, which saves nothing.",
    )),
    Section(Ic.user, "What we collect", listOf(
        "• Account: your username and a scrambled (hashed) form of your password. We never store or see the password itself.",
        "• Your answers from setup: first name, age, sex, height, weight, activity level, goals, anything you tell us about how you eat, favourite music genres, chosen voice, sleep times and step goal.",
        "• Face scan results: heart rate, breathing rate, heart-rate variability, a stress score, an expression-based mood, and the time of each reading.",
        "• Food: what you log, its nutrition, portion size, and the barcode or photo you used to add it.",
        "• Activity: daily step counts from your phone's step sensor.",
        "• Phone habits: screen time per app and per day, and sleep estimated from when your screen was off. These are read on your phone each time you open the Phone tab.",
        "• Things you set up: alarms, app limits, the bedtime lock, plans, reminders, songs you add and your settings.",
        "• Chats with Gemini, and shop searches and orders.",
        "SmartCam does not collect your location, contacts, messages, call history, advertising ID or photos other than the ones you choose to scan.",
    )),
    Section(Ic.camera, "Camera and microphone", listOf(
        "The camera is on only while the Camera tab is open or while you scan a barcode or take a food photo. During a face scan, the video is processed by the Presage SmartSpectra software to measure your vitals. Depending on how that software works, frames or signals taken from them may be sent to Presage to do the calculation. SmartCam itself never saves face video or face photos.",
        "Food photos are sent to Google Gemini to estimate nutrition. SmartCam keeps only a small thumbnail of the photo, on your phone, next to that food in your log. Barcode photos are read on your phone and not kept.",
        "The microphone is used only after you tap it, to turn what you say into a message for Gemini.",
    )),
    Section(Ic.spark, "How we use your data", listOf(
        "• To show your day: widgets, charts, scores and history.",
        "• To personalise: calorie and nutrient limits, tomorrow's plan, the game suggested for your stress level, and the music picked for your mood and taste.",
        "• To answer you: Gemini sees the numbers it needs (steps, latest scan, today's food, screen time, sleep and alarms) so its answers are about your day.",
        "• To warn you: high heart rate, foods that take you over your limits, and apps over their daily limit.",
        "• To keep your account working on every device you sign in on.",
        "We do not sell your data, share it with advertisers, or use it to build profiles for anyone else.",
    )),
    Section(Ic.heart, "Health information", listOf(
        "Readings from the camera are wellness estimates, not medical measurements. SmartCam is not a medical device and does not diagnose, treat or prevent any condition. Do not use it for emergencies or instead of advice from a doctor.",
        "Because vitals, food and sleep are sensitive, we only use them to run SmartCam features for you.",
    )),
    Section(Ic.refresh, "Where your data is stored", listOf(
        "Your data is saved on your phone, inside SmartCam's private storage, under your username. When you are signed in, a copy is kept in our Tiger Data database, a cloud PostgreSQL service, so you can use another phone or the SmartCam website. Connections to it are encrypted.",
        "Screen-time and sleep details stay on your phone. Guest sessions are not saved anywhere.",
    )),
    Section(Ic.calendar, "How long we keep it", listOf(
        "We keep your data for as long as your account exists. Heart-rate readings older than 48 hours are cleared automatically, and only your 40 most recent chat messages are kept.",
        "When you delete your account, your account and all its data are removed from your phone and from our database straight away. Copies held by the services listed above follow their own policies.",
    )),
    Section(Ic.check, "Your choices and rights", listOf(
        "• See and change: your answers are in Settings → Edit my answers, and everything else is visible in the app.",
        "• Delete: Settings → Privacy → Delete my account removes your account and data everywhere we store them.",
        "• Control access: camera, microphone, physical activity, notifications and usage access can be turned off at any time in Settings → Phone permissions or in Android settings. Features that need them will stop working.",
        "• Voice: turn off reading answers or fun facts aloud in Settings → Voice.",
        "• Ask us: you can ask what data we hold about you or for a copy of it through the contact link below.",
        "Depending on where you live (for example under the GDPR in Europe or the CCPA in California), you may have more rights, such as objecting to processing or complaining to a data protection authority.",
    )),
    Section(Ic.lock, "Security", listOf(
        "Passwords are stored only as salted PBKDF2 hashes. Data on the phone sits in app-private storage that other apps cannot read, and connections to our database and partners use encryption (TLS).",
        "No system is perfectly secure. Use a password you do not use anywhere else and keep your phone locked.",
    )),
    Section(Ic.shield, "Screen lock and permissions", listOf(
        "If you turn on the bedtime screen lock, SmartCam asks Android for device-admin access. It uses that access only to lock the screen at bedtime and when an app goes over its daily limit. It cannot read your screen, erase your phone or change your passwords. Turning the lock off in Settings stops it, and you can remove the access completely in Android settings → Device admin apps.",
    )),
    Section(Ic.wallet, "Payments", listOf(
        "Shop payments use Solana Pay and happen in your own wallet app. SmartCam never sees your wallet's secret key or recovery phrase. Blockchain transactions, including your wallet address and the amount, are public and cannot be changed or deleted.",
    )),
    Section(Ic.alert, "Children", listOf(
        "SmartCam is not meant for children under 13, and we do not knowingly collect their data. If you think a child has created an account, contact us and we will delete it.",
    )),
    Section(Ic.pencil, "Changes to this policy", listOf(
        "If we change this policy, we will update the date at the top and, for important changes, tell you in the app before they apply.",
    )),
)

/** Full-screen privacy policy, opened from Settings and from the sign-in screen. */
@Composable
fun PrivacyPolicyPanel(onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val uri = LocalUriHandler.current
    Box(Modifier.fillMaxSize()) {
        Backdrop()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Privacy Policy", style = T.h1, color = P.ink, modifier = Modifier.weight(1f))
                IconBtn(Ic.close, "Close privacy policy", onDismiss, bg = P.card2)
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 28.dp).navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val m = Modifier.widthIn(max = 640.dp).fillMaxWidth()

                // Hero
                Column(m.glass().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppLogo(48.dp)
                        Column {
                            Text("Your data, explained", style = T.h2, color = P.ink)
                            Text("Last updated $PRIVACY_UPDATED", style = T.mono.copy(fontSize = 12.sp), color = P.ink2)
                        }
                    }
                    Text(
                        "SmartCam reads sensitive things: your face, your heartbeat, what you eat and how you sleep. Here is exactly what we collect, why, and who else sees it, in plain words.",
                        style = T.body, color = P.ink,
                    )
                }

                // At a glance
                Label("At a glance", m)
                Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GLANCE.chunked(2).forEach { row ->
                        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { g ->
                                Column(Modifier.weight(1f).fillMaxHeight().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(34.dp).clip(CircleShape).background(P.accentSoft), contentAlignment = Alignment.Center) {
                                        Icon(g.icon, null, Modifier.size(18.dp), tint = P.ink)
                                    }
                                    Text(g.title, style = T.bodyStrong, color = P.ink)
                                    Text(g.text, style = T.small, color = P.ink2)
                                }
                            }
                        }
                    }
                }

                // Partners
                Label("Who else sees what", m)
                Column(m.glass().padding(vertical = 6.dp)) {
                    PARTNERS.forEachIndexed { i, p ->
                        if (i > 0) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(P.line))
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { runCatching { uri.openUri(p.url) } }.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(36.dp).glass(CircleShape, elevated = false), contentAlignment = Alignment.Center) {
                                p.logo?.invoke() ?: p.icon?.let { Icon(it, null, Modifier.size(18.dp), tint = P.ink) }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(p.name, style = T.bodyStrong, color = P.ink)
                                Text("Gets: ${p.gets}", style = T.small, color = P.ink)
                                Text("Why: ${p.why}", style = T.small, color = P.ink2)
                            }
                            Icon(Ic.chevronRight, "Open ${p.name}'s privacy policy", Modifier.size(16.dp).align(Alignment.CenterVertically), tint = P.ink3)
                        }
                    }
                }
                Text("Tap a service to read its own privacy policy.", style = T.small, color = P.ink3, modifier = m.padding(start = 6.dp))

                // Full policy
                Label("The full policy", m)
                var open by remember { mutableStateOf<Int?>(0) }
                Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SECTIONS.forEachIndexed { i, s ->
                        PolicySection(i + 1, s, open == i) { Haptics.tick(); open = if (open == i) null else i }
                    }
                }

                // Contact
                Column(m.glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Questions or requests", style = T.h2, color = P.ink)
                    Text("Ask about your data, request a copy, or report a problem on the SmartCam project page. We aim to reply within 30 days.", style = T.body, color = P.ink2)
                    Row(
                        Modifier.clip(CircleShape).background(P.accent).clickable(role = Role.Button) { runCatching { uri.openUri(PRIVACY_CONTACT) } }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Ic.send, null, Modifier.size(16.dp), tint = P.accentInk)
                        Text("Contact the SmartCam team", style = T.bodyStrong, color = P.accentInk)
                    }
                }
                Text("SmartCam · effective $PRIVACY_UPDATED", style = T.mono, color = P.ink3)
            }
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier) =
    Text(text.uppercase(), style = T.eyebrow.copy(fontSize = 12.sp), color = P.ink2, modifier = modifier.padding(start = 6.dp))

@Composable
private fun PolicySection(n: Int, s: Section, open: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (open) 90f else 0f, spring(dampingRatio = 0.8f, stiffness = 400f), label = "chev")
    Column(Modifier.fillMaxWidth().glass().animateContentSize(spring(dampingRatio = 0.9f, stiffness = 380f))) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle).padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(if (open) P.accent else P.accentSoft), contentAlignment = Alignment.Center) {
                Icon(s.icon, null, Modifier.size(16.dp), tint = if (open) P.accentInk else P.ink)
            }
            Text("%02d".format(n), style = T.mono.copy(fontSize = 12.sp), color = P.ink3)
            Text(s.title, style = T.bodyStrong, color = P.ink, modifier = Modifier.weight(1f))
            Icon(Ic.chevronRight, if (open) "Collapse" else "Expand", Modifier.size(16.dp).rotate(turn), tint = P.ink2)
        }
        AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.blocks.forEach { b ->
                    if (b.startsWith("• ")) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(P.pop))
                        Text(b.removePrefix("• "), style = T.body, color = P.ink, modifier = Modifier.weight(1f))
                    } else Text(b, style = T.body, color = P.ink2)
                }
            }
        }
    }
}

/** "By creating an account you agree to the Privacy Policy" line with a tappable link. */
@Composable
fun PrivacyLink(prefix: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Ic.shield, null, Modifier.size(14.dp), tint = P.ink2)
        Spacer(Modifier.width(6.dp))
        Text(prefix, style = T.small, color = P.ink2)
        Text(
            "Privacy Policy", style = T.small.copy(fontWeight = FontWeight.SemiBold), color = P.ink,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 3.dp, vertical = 2.dp),
        )
    }
}
