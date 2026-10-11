package com.hack.smartcam

import java.net.URI
import java.net.URLDecoder

/** Keys compiled in from server/.env (see app/build.gradle.kts). */
object Keys {
    val gemini: String = BuildConfig.GEMINI_API_KEY
    val geminiModel: String = BuildConfig.GEMINI_MODEL.ifBlank { "gemini-3.8-flash" }
    val elevenLabs: String = BuildConfig.ELEVENLABS_API_KEY
    val voiceId: String = BuildConfig.ELEVENLABS_VOICE_ID
    val presage: String = BuildConfig.PRESAGE_API_KEY
    val solanaMerchant: String = BuildConfig.SOLANA_MERCHANT
    val solanaNetwork: String = BuildConfig.SOLANA_NETWORK
    val adminUser: String = BuildConfig.ADMIN_USERNAME.ifBlank { "admin" }
    val adminPassword: String = BuildConfig.ADMIN_PASSWORD

    /** postgres://user:pass@host:port/db?params → JDBC pieces, or null when not usable yet. */
    data class Db(val jdbcUrl: String, val user: String, val password: String)

    val db: Db? by lazy {
        val raw = BuildConfig.DATABASE_URL
        if (raw.isBlank() || "CHANGE_ME" in raw) return@lazy null
        runCatching {
            val uri = URI(raw.replaceFirst(Regex("^postgres(ql)?://"), "http://"))
            val info = uri.rawUserInfo ?: return@lazy null
            val user = URLDecoder.decode(info.substringBefore(':'), "UTF-8")
            val pass = if (':' in info) URLDecoder.decode(info.substringAfter(':'), "UTF-8") else ""
            if (pass.isEmpty()) return@lazy null
            val query = uri.rawQuery?.let { "?$it" } ?: "?sslmode=require"
            Db("jdbc:postgresql://${uri.host}:${if (uri.port > 0) uri.port else 5432}${uri.rawPath}$query", user, pass)
        }.getOrNull()
    }

    /** Why the database isn't connected, for the settings screen. */
    val dbProblem: String?
        get() = when {
            BuildConfig.DATABASE_URL.isBlank() -> "DATABASE_URL is empty in server/.env"
            "CHANGE_ME" in BuildConfig.DATABASE_URL -> "DATABASE_URL still has placeholders"
            db == null -> "DATABASE_URL has no password (tsdbadmin:PASSWORD@host)"
            else -> null
        }
}
