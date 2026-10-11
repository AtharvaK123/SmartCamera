package com.hack.smartcam.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.smartcam.data.Accounts
import com.hack.smartcam.ui.Btn
import com.hack.smartcam.ui.Ic
import com.hack.smartcam.ui.Level
import com.hack.smartcam.ui.Notice
import com.hack.smartcam.ui.P
import com.hack.smartcam.ui.ScButton
import com.hack.smartcam.ui.ScField
import com.hack.smartcam.ui.Segmented
import com.hack.smartcam.ui.T
import com.hack.smartcam.ui.glass
import kotlinx.coroutines.launch

/** Sign in, create an account, or look around as a guest. */
@Composable
fun AuthScreen() {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("signin") }
    var name by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return
        if (mode == "signup" && pass != pass2) { error = "The two passwords don't match."; return }
        busy = true; error = null
        scope.launch {
            error = if (mode == "signup") Accounts.signUp(name, pass) else Accounts.signIn(name, pass)
            busy = false
        }
    }

    var privacyOpen by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(24.dp))
        com.hack.smartcam.ui.AppLogo(88.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            buildAnnotatedString { append("Smart"); withStyle(SpanStyle(color = P.pop)) { append("Cam") } },
            style = T.big.copy(fontSize = 44.sp), color = P.ink,
        )
        Text("Your camera, your calm.", style = T.body, color = P.ink2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().glass().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Segmented(listOf("signin" to "Sign in", "signup" to "Create account"), mode) { mode = it; error = null }
            ScField(name, { name = it.take(20) }, label = "Username")
            ScField(pass, { pass = it.take(64) }, label = "Password", password = true)
            if (mode == "signup") ScField(pass2, { pass2 = it.take(64) }, label = "Password again", password = true)
            error?.let { Notice(it, Level.Bad, Ic.alert) }
            ScButton(
                if (busy) "One moment…" else if (mode == "signup") "Create account" else "Sign in",
                { submit() }, Modifier.fillMaxWidth(), icon = if (mode == "signup") Ic.plus else Ic.user, enabled = !busy,
            )
            if (mode == "signup") Text("Your data is saved under your username and backed up, so you can sign in on another phone.", style = T.small, color = P.ink2)
            PrivacyLink(if (mode == "signup") "By creating an account you agree to the " else "How we handle your data: ", { privacyOpen = true })
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScButton("Just looking? Continue as guest", { Accounts.guest() }, style = Btn.Ghost, small = true)
        }
        Spacer(Modifier.height(24.dp))
    }
    androidx.compose.animation.AnimatedVisibility(
        privacyOpen,
        enter = androidx.compose.animation.slideInVertically { it / 2 } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically { it / 2 } + androidx.compose.animation.fadeOut(),
    ) { PrivacyPolicyPanel { privacyOpen = false } }
    }
}
