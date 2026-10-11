package com.hack.smartcam.data

import android.content.Context
import com.hack.smartcam.Keys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class Role { Admin, User, Guest }

data class Session(val username: String, val role: Role)

@Serializable
data class Account(val username: String, val role: String, val hash: String, val salt: String, val created: Long, val synced: Boolean = false)

/**
 * Sign-in for three kinds of people:
 *  - admin: one account set in server/.env (ADMIN_USERNAME / ADMIN_PASSWORD), full access plus account management
 *  - user: anyone who signs up; their data lives under their username, on the phone and in Tiger Data
 *  - guest: can look around, nothing is saved
 * Passwords are stored only as PBKDF2 hashes (same scheme the website server checks).
 */
object Accounts {
    val session = MutableStateFlow<Session?>(null)

    private lateinit var file: File
    private lateinit var prefs: android.content.SharedPreferences
    private var local: List<Account> = emptyList()
    private val accountsJson = ListSerializer(Account.serializer())

    fun init(context: Context) {
        file = File(context.filesDir, "accounts.json")
        prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        local = runCatching { Repo.json.decodeFromString(accountsJson, file.readText()) }.getOrDefault(emptyList())
        val name = prefs.getString("user", null)
        val role = prefs.getString("role", null)?.let { runCatching { Role.valueOf(it) }.getOrNull() }
        if (name != null && role != null) start(Session(name, role), remember = false)
    }

    private fun persist() = runCatching { file.writeText(Repo.json.encodeToString(accountsJson, local)) }

    private fun start(s: Session, remember: Boolean = true) {
        if (s.role == Role.Guest) Repo.close() else Repo.open(s.username)
        if (remember) prefs.edit().putString("user", s.username).putString("role", s.role.name).apply()
        session.value = s
    }

    fun clean(username: String) = username.trim().lowercase()
    fun validName(u: String) = Regex("^[a-z0-9._-]{3,20}$").matches(u)

    fun hash(password: String, saltHex: String): String {
        val spec = PBEKeySpec(password.toCharArray(), hex(saltHex), 20_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
    }

    private fun newSalt() = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun isAdminName(u: String) = Keys.adminPassword.isNotEmpty() && u == clean(Keys.adminUser)

    /** Returns an error message, or null when signed up and signed in. */
    suspend fun signUp(rawName: String, password: String): String? {
        val u = clean(rawName)
        if (!validName(u)) return "Usernames are 3–20 letters, numbers, dots, dashes or underscores."
        if (password.length < 6) return "Use at least 6 characters for the password."
        if (isAdminName(u) || local.any { it.username == u }) return "That username is taken."
        val salt = newSalt()
        val h = hash(password, salt)
        val remote = Tiger.createUser(u, "user", h, salt)
        if (remote == false) return "That username is taken."
        local = local + Account(u, "user", h, salt, System.currentTimeMillis(), synced = remote == true)
        persist()
        start(Session(u, Role.User))
        return null
    }

    /** Returns an error message, or null when signed in. */
    suspend fun signIn(rawName: String, password: String): String? {
        val u = clean(rawName)
        if (u.isEmpty() || password.isEmpty()) return "Enter your username and password."
        if (isAdminName(u)) {
            return if (password == Keys.adminPassword) { start(Session(u, Role.Admin)); null } else "Wrong password."
        }
        local.firstOrNull { it.username == u }?.let { a ->
            if (hash(password, a.salt) != a.hash) return "Wrong password."
            if (!a.synced) Tiger.createUser(a.username, a.role, a.hash, a.salt)?.let { markSynced(u) }
            start(Session(u, Role.User))
            return null
        }
        // Not on this phone yet: the account may have been made on another device.
        val r = Tiger.getUser(u) ?: return if (Tiger.status.value is Tiger.Status.Error) "No account called “$u” on this phone, and the account server couldn't be reached to check." else "No account called “$u”."
        if (hash(password, r.salt) != r.hash) return "Wrong password."
        local = local + Account(r.username, r.role, r.hash, r.salt, r.created, synced = true)
        persist()
        start(Session(u, Role.User))
        return null
    }

    private fun markSynced(u: String) {
        local = local.map { if (it.username == u) it.copy(synced = true) else it }
        persist()
    }

    fun guest() = start(Session("guest", Role.Guest))

    fun signOut() {
        prefs.edit().clear().apply()
        Repo.close()
        session.value = null
    }

    /* ---- admin ---- */

    data class Listed(val username: String, val role: String, val created: Long, val onPhone: Boolean, val inTiger: Boolean)

    suspend fun all(): List<Listed> {
        val remote = Tiger.listUsers().orEmpty()
        val names = (local.map { it.username } + remote.map { it.username }).distinct()
        return names.map { n ->
            val l = local.firstOrNull { it.username == n }
            val r = remote.firstOrNull { it.username == n }
            Listed(n, r?.role ?: l?.role ?: "user", r?.created ?: l?.created ?: 0, l != null, r != null)
        }.sortedBy { it.created }
    }

    /**
     * The signed-in person deletes their own account: signs out, then removes the account, its data
     * and its food photos from the phone and from Tiger Data. Runs outside the UI, which closes on sign-out.
     */
    fun deleteMe(onDone: () -> Unit) {
        val me = session.value?.takeIf { it.role == Role.User } ?: return
        val photos = Repo.state.value.foods.mapNotNull { it.image?.takeIf { p -> p.startsWith("/") } }
        signOut()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            photos.forEach { runCatching { File(it).delete() } }
            delete(me.username)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone() }
        }
    }

    suspend fun delete(username: String) {
        Tiger.deleteUser(username)
        local = local.filterNot { it.username == username }
        persist()
        Repo.deleteLocal(username)
    }
}
