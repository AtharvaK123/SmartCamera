package com.hack.smartcam.data

import android.util.Log
import com.hack.smartcam.Keys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.sql.Types
import java.util.Properties

/** Tiger Data (TimescaleDB) over JDBC. Same tables the server and website use. */
object Tiger {
    sealed interface Status {
        data object Off : Status
        data object Connecting : Status
        data object On : Status
        data class Error(val message: String) : Status
    }

    val status = MutableStateFlow<Status>(Status.Off)
    private val lock = Mutex()
    private var conn: Connection? = null

    val SCHEMA = listOf(
        """CREATE TABLE IF NOT EXISTS sc_foods (
            id text PRIMARY KEY, user_id text NOT NULL, time timestamptz NOT NULL, name text NOT NULL,
            source text, barcode text, image_url text, basis text,
            kcal real, protein real, carbs real, sugar real, fat real, sat_fat real, fiber real, salt real,
            grade text, deleted boolean NOT NULL DEFAULT false, updated_at timestamptz NOT NULL DEFAULT now())""",
        """CREATE TABLE IF NOT EXISTS sc_vitals (
            user_id text NOT NULL, time timestamptz NOT NULL, heart_rate real, breathing real,
            stress real, hrv_rmssd real, mood text, source text)""",
        """CREATE TABLE IF NOT EXISTS sc_state (
            user_id text PRIMARY KEY, state jsonb NOT NULL, updated_at timestamptz NOT NULL DEFAULT now())""",
        """CREATE TABLE IF NOT EXISTS sc_users (
            username text PRIMARY KEY, role text NOT NULL DEFAULT 'user', pass_hash text NOT NULL, salt text NOT NULL,
            created_at timestamptz NOT NULL DEFAULT now())""",
    )

    private fun open(): Connection {
        val db = Keys.db ?: error(Keys.dbProblem ?: "Database not configured")
        Class.forName("org.postgresql.Driver")
        val props = Properties().apply {
            setProperty("user", db.user)
            setProperty("password", db.password)
            setProperty("sslmode", "require")
            setProperty("connectTimeout", "10")
            setProperty("socketTimeout", "20")
            setProperty("tcpKeepAlive", "true")
            setProperty("ApplicationName", "SmartCam Android")
        }
        return DriverManager.getConnection(db.jdbcUrl, props)
    }

    private suspend fun <T> withConn(block: (Connection) -> T): T? = lock.withLock {
        if (Keys.db == null) {
            status.value = Status.Error(Keys.dbProblem ?: "Not configured")
            return@withLock null
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val c = conn?.takeIf { !it.isClosed && it.isValid(3) } ?: open().also { conn = it }
                block(c).also { status.value = Status.On }
            }.onFailure {
                Log.w("Tiger", "query failed", it)
                status.value = Status.Error(it.message ?: it.javaClass.simpleName)
                runCatching { conn?.close() }
                conn = null
            }.getOrNull()
        }
    }

    suspend fun connect() {
        if (Keys.db == null) {
            status.value = Status.Error(Keys.dbProblem ?: "Not configured")
            return
        }
        status.value = Status.Connecting
        withConn { c ->
            c.createStatement().use { st -> SCHEMA.forEach { st.execute(it) } }
            // Hypertable needs the timescaledb extension; harmless if it's already one.
            runCatching { c.createStatement().use { it.execute("SELECT create_hypertable('sc_vitals', 'time', if_not_exists => TRUE)") } }
        }
    }

    private fun java.sql.PreparedStatement.setDoubleOrNull(i: Int, v: Double?) =
        if (v == null) setNull(i, Types.REAL) else setDouble(i, v)

    suspend fun upsertFood(user: String, f: Food) = withConn { c ->
        c.prepareStatement(
            """INSERT INTO sc_foods (id, user_id, time, name, source, barcode, image_url, basis,
                 kcal, protein, carbs, sugar, fat, sat_fat, fiber, salt, grade, deleted, updated_at)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,false,now())
               ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, kcal = EXCLUDED.kcal, protein = EXCLUDED.protein,
                 carbs = EXCLUDED.carbs, sugar = EXCLUDED.sugar, fat = EXCLUDED.fat, sat_fat = EXCLUDED.sat_fat,
                 fiber = EXCLUDED.fiber, salt = EXCLUDED.salt, deleted = false, updated_at = now()""",
        ).use { ps ->
            ps.setString(1, f.id); ps.setString(2, user); ps.setTimestamp(3, Timestamp(f.t))
            ps.setString(4, f.name); ps.setString(5, f.source); ps.setString(6, f.barcode)
            ps.setString(7, f.image?.takeIf { it.startsWith("http") }); ps.setString(8, f.basis)
            ps.setDoubleOrNull(9, f.kcal); ps.setDoubleOrNull(10, f.protein); ps.setDoubleOrNull(11, f.carbs)
            ps.setDoubleOrNull(12, f.sugar); ps.setDoubleOrNull(13, f.fat); ps.setDoubleOrNull(14, f.satFat)
            ps.setDoubleOrNull(15, f.fiber); ps.setDoubleOrNull(16, f.salt); ps.setString(17, f.grade)
            ps.executeUpdate()
        }
    }

    suspend fun deleteFood(user: String, id: String) = withConn { c ->
        c.prepareStatement("UPDATE sc_foods SET deleted = true, updated_at = now() WHERE id = ? AND user_id = ?").use {
            it.setString(1, id); it.setString(2, user); it.executeUpdate()
        }
    }

    suspend fun fetchFoods(user: String): List<Food>? = withConn { c ->
        c.prepareStatement(
            """SELECT id, time, name, source, barcode, image_url, basis, kcal, protein, carbs, sugar, fat, sat_fat, fiber, salt, grade
               FROM sc_foods WHERE user_id = ? AND NOT deleted ORDER BY time DESC LIMIT 300""",
        ).use { ps ->
            ps.setString(1, user)
            ps.executeQuery().use { rs ->
                fun d(col: String) = rs.getDouble(col).takeUnless { rs.wasNull() }
                buildList {
                    while (rs.next()) add(
                        Food(
                            id = rs.getString("id"), t = rs.getTimestamp("time").time, name = rs.getString("name"),
                            source = rs.getString("source") ?: "manual", barcode = rs.getString("barcode"),
                            image = rs.getString("image_url"), basis = rs.getString("basis") ?: "per serving",
                            kcal = d("kcal"), protein = d("protein"), carbs = d("carbs"), sugar = d("sugar"),
                            fat = d("fat"), satFat = d("sat_fat"), fiber = d("fiber"), salt = d("salt"),
                            grade = rs.getString("grade"),
                        ),
                    )
                }
            }
        }
    }

    suspend fun insertVitals(user: String, v: Vitals) = withConn { c ->
        c.prepareStatement(
            "INSERT INTO sc_vitals (user_id, time, heart_rate, breathing, stress, hrv_rmssd, mood, source) VALUES (?,?,?,?,?,?,?,?)",
        ).use { ps ->
            ps.setString(1, user); ps.setTimestamp(2, Timestamp(v.t)); ps.setDouble(3, v.hr.toDouble())
            ps.setDouble(4, v.br.toDouble()); ps.setDouble(5, v.stress.toDouble()); ps.setDoubleOrNull(6, v.hrv)
            ps.setString(7, v.mood); ps.setString(8, v.source)
            ps.executeUpdate()
        }
    }

    suspend fun pushState(user: String, s: AppState) {
        if (Keys.db == null || user.isEmpty()) return
        // Foods have their own table; photos stay on the phone.
        val body = Repo.json.encodeToString(AppState.serializer(), s.copy(foods = emptyList()))
        withConn { c ->
            c.prepareStatement(
                """INSERT INTO sc_state (user_id, state, updated_at) VALUES (?, ?::jsonb, now())
                   ON CONFLICT (user_id) DO UPDATE SET state = EXCLUDED.state, updated_at = now()""",
            ).use { it.setString(1, user); it.setString(2, body); it.executeUpdate() }
        }
    }

    suspend fun fetchState(user: String): AppState? = withConn { c ->
        c.prepareStatement("SELECT state::text FROM sc_state WHERE user_id = ?").use { ps ->
            ps.setString(1, user)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }?.let { runCatching { Repo.json.decodeFromString(AppState.serializer(), it) }.getOrNull() }

    /* ---- accounts ---- */

    data class RemoteUser(val username: String, val role: String, val hash: String, val salt: String, val created: Long)

    suspend fun getUser(username: String): RemoteUser? = withConn { c ->
        c.prepareStatement("SELECT username, role, pass_hash, salt, created_at FROM sc_users WHERE username = ?").use { ps ->
            ps.setString(1, username)
            ps.executeQuery().use { rs ->
                if (rs.next()) RemoteUser(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5).time) else null
            }
        }
    }

    /** True if created, false if the name is taken, null if the database couldn't be reached. */
    suspend fun createUser(username: String, role: String, hash: String, salt: String): Boolean? = withConn { c ->
        c.prepareStatement("INSERT INTO sc_users (username, role, pass_hash, salt) VALUES (?,?,?,?) ON CONFLICT (username) DO NOTHING").use { ps ->
            ps.setString(1, username); ps.setString(2, role); ps.setString(3, hash); ps.setString(4, salt)
            ps.executeUpdate() == 1
        }
    }

    suspend fun listUsers(): List<RemoteUser>? = withConn { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT username, role, pass_hash, salt, created_at FROM sc_users ORDER BY created_at").use { rs ->
                buildList { while (rs.next()) add(RemoteUser(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5).time)) }
            }
        }
    }

    /** Removes the account and everything stored under it. */
    suspend fun deleteUser(username: String) = withConn { c ->
        listOf("DELETE FROM sc_foods WHERE user_id = ?", "DELETE FROM sc_vitals WHERE user_id = ?", "DELETE FROM sc_state WHERE user_id = ?", "DELETE FROM sc_users WHERE username = ?")
            .forEach { q -> c.prepareStatement(q).use { it.setString(1, username); it.executeUpdate() } }
    }
}
