package com.hack.smartcam.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.hack.smartcam.Keys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import java.util.Locale

/** Checkout with Solana Pay: builds a transfer request that any Solana wallet app opens. */
object Solana {
    val ready get() = Keys.solanaMerchant.isNotBlank()

    suspend fun usdPerSol(): Double? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("https://api.coingecko.com/api/v3/simple/price?ids=solana&vs_currencies=usd").build()
            Http.client.newCall(req).execute().use { res ->
                Http.json.parseToJsonElement(res.body!!.string()).jsonObject["solana"]!!.jsonObject.num("usd")
            }
        }.getOrNull()
    }

    fun payUrl(amountSol: Double, label: String, message: String, memo: String): String {
        val q = listOf(
            "amount" to String.format(Locale.US, "%.6f", amountSol),
            "label" to label,
            "message" to message.take(60),
            "memo" to memo,
        ).joinToString("&") { (k, v) -> "$k=${Uri.encode(v)}" }
        return "solana:${Keys.solanaMerchant}?$q"
    }

    /** Opens the wallet app. Returns false if no Solana wallet is installed. */
    fun openWallet(context: Context, url: String): Boolean = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
