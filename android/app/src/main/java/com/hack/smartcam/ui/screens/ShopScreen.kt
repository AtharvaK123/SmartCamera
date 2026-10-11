package com.hack.smartcam.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hack.smartcam.Keys
import com.hack.smartcam.data.Order
import com.hack.smartcam.data.Product
import com.hack.smartcam.data.Repo
import com.hack.smartcam.net.Gemini
import com.hack.smartcam.net.Solana
import com.hack.smartcam.net.num
import com.hack.smartcam.net.str
import com.hack.smartcam.ui.BigNumber
import com.hack.smartcam.ui.Btn
import com.hack.smartcam.ui.Chip
import com.hack.smartcam.ui.Fmt
import com.hack.smartcam.ui.GeminiMark
import com.hack.smartcam.ui.Ic
import com.hack.smartcam.ui.IconBtn
import com.hack.smartcam.ui.Level
import com.hack.smartcam.ui.LocalWide
import com.hack.smartcam.ui.Notice
import com.hack.smartcam.ui.P
import com.hack.smartcam.ui.ScButton
import com.hack.smartcam.ui.ScCard
import com.hack.smartcam.ui.ScSheet
import com.hack.smartcam.ui.SectionTitle
import com.hack.smartcam.ui.SolanaLogo
import com.hack.smartcam.ui.glass
import com.hack.smartcam.ui.T
import com.hack.smartcam.ui.Toasts
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.Locale

@Composable
fun ShopScreen() {
    val s by Repo.state.collectAsState()
    val scope = rememberCoroutineScope()
    val wide = LocalWide.current
    var query by remember { mutableStateOf(s.shopQuery) }
    var busy by remember { mutableStateOf(false) }
    var buying by remember { mutableStateOf<Product?>(null) }
    val p = P

    fun search() {
        val q = query.trim()
        if (q.isEmpty() || busy) return
        if (!Gemini.available) { Toasts.show("Gemini isn't available right now", true); return }
        busy = true
        scope.launch {
            runCatching {
                val r = Gemini.generateJson(
                    "Shopping request: \"$q\". Suggest 3–5 specific, widely available products. " +
                        "Return JSON {\"items\":[{\"name\": string, \"price\": number (typical USD), \"why\": \"one short sentence\"}]}. " +
                        "If the request is not something a person can buy, return {\"items\": []}.",
                    system = "You are the shopping helper inside the SmartCam app. Only suggest products to buy; never answer other questions. Prices are estimates.",
                ).jsonObject
                val list = r["items"]?.jsonArray.orEmpty()
                if (list.isEmpty()) error("The shop only finds things to buy. Describe a product you need.")
                list.mapNotNull { e ->
                    val o = e.jsonObject
                    val name = o.str("name") ?: return@mapNotNull null
                    Product(name, o.num("price") ?: 0.0, o.str("why").orEmpty())
                }.take(5)
            }.onSuccess { items -> Repo.update { it.copy(shopQuery = q, products = items) } }
                .onFailure { Toasts.show(it.message ?: "Search failed", true) }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 24.dp else 16.dp).padding(bottom = 16.dp)) {
        Row(
            Modifier.padding(top = 8.dp, bottom = 16.dp).fillMaxWidth()
                .glass(CircleShape).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                query, { query = it }, Modifier.weight(1f), singleLine = true,
                textStyle = T.body.copy(color = p.ink), cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                decorationBox = { inner -> if (query.isEmpty()) Text("Shop with Gemini", style = T.body, color = p.ink3); inner() },
            )
            IconBtn(Ic.send, "Search", { search() }, size = 40.dp, tint = p.ink)
            GeminiMark()
        }

        when {
            busy -> Text("Gemini is finding options…", style = T.body, color = P.ink2, modifier = Modifier.padding(vertical = 30.dp))
            s.products.isEmpty() -> CenteredEmpty(Ic.bag, "Nothing to show right now", "Describe what you need and Gemini will suggest a few options.")
            else -> s.products.chunked(if (wide) 2 else 1).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { item ->
                        ScCard(Modifier.weight(1f)) {
                            Text(item.name, style = T.h2, color = P.ink)
                            Spacer(Modifier.height(6.dp))
                            BigNumber("$" + String.format(Locale.US, "%.2f", item.price), "est.", 22)
                            Text(item.why, style = T.body, color = P.ink2, modifier = Modifier.padding(vertical = 6.dp))
                            ScButton("Buy with Solana", { buying = item }, style = Btn.Primary, small = true, leading = { SolanaLogo(18.dp) })
                        }
                    }
                }
            }
        }

        if (s.orders.isNotEmpty()) {
            SectionTitle("Recent orders")
            ScCard(padding = 0.dp) {
                s.orders.take(8).forEachIndexed { i, o ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(o.name, style = T.bodyStrong, color = P.ink, maxLines = 1)
                            Text(Fmt.ago(o.t), style = T.small, color = P.ink2)
                        }
                        Text("$" + String.format(Locale.US, "%.2f", o.usd), style = T.mono, color = P.ink)
                        Spacer(Modifier.padding(4.dp))
                        Chip(o.status, if (o.status == "paid") Level.Good else null)
                    }
                    if (i < minOf(7, s.orders.lastIndex)) androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(1.dp).background(P.line))
                }
            }
        }
    }

    buying?.let { CheckoutSheet(it) { buying = null } }
}

@Composable
private fun CheckoutSheet(item: Product, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var rate by remember { mutableStateOf<Double?>(null) }
    var loading by remember { mutableStateOf(true) }
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { rate = Solana.usdPerSol(); loading = false }
    val sol = rate?.let { item.price / it }

    ScSheet("Checkout", onClose) {
        ScCard(shadow = false) {
            Text(item.name, style = T.bodyStrong, color = P.ink)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BigNumber("$" + String.format(Locale.US, "%.2f", item.price), size = 26)
                Spacer(Modifier.weight(1f))
                Text(
                    when { loading -> "Getting SOL price…"; sol != null -> "≈ ${String.format(Locale.US, "%.4f", sol)} SOL"; else -> "SOL price unavailable" },
                    style = T.mono, color = P.ink,
                )
            }
        }
        Text("Paid with Solana Pay on ${Keys.solanaNetwork}. Your wallet app shows the exact amount before you approve.", style = T.small, color = P.ink2)
        if (!Solana.ready) Notice("Payments aren't available right now.", Level.Warn)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SolanaLogo(22.dp)
            Text("Solana Pay", style = T.h3, color = P.ink)
        }
        ScButton(
            "Pay with Solana wallet",
            {
                val url = Solana.payUrl(sol!!, "SmartCam Shop", item.name, "sc-${System.currentTimeMillis()}")
                if (Solana.openWallet(ctx, url)) {
                    opened = true
                    Repo.update { it.copy(orders = listOf(Order(Repo.newId("o"), System.currentTimeMillis(), item.name, item.price, sol, "pending")) + it.orders) }
                } else Toasts.show("Install a Solana wallet such as Phantom or Solflare", true)
            },
            Modifier.fillMaxWidth(), style = Btn.Primary, enabled = Solana.ready && sol != null, leading = { SolanaLogo(18.dp) },
        )
        if (opened) ScButton("I've approved it in my wallet", {
            Repo.update { st -> st.copy(orders = st.orders.mapIndexed { i, o -> if (i == 0) o.copy(status = "paid") else o }) }
            Toasts.show("Order saved")
            onClose()
        }, Modifier.fillMaxWidth(), icon = Ic.check, style = Btn.Ghost)
    }
}
