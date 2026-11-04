package dev.uint.qrserv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Small persistence wrapper backed by a plain SharedPreferences file, loaded once and read/written synchronously. */
object Preferences {
    private const val PREFS_NAME = "config"

    const val PREF_SERVER_PORT = "server_port"
    const val PREF_SERVER_ALL_INTERFACES = "server_all_interfaces"
    const val PREF_HOTSPOT_BAND = "hotspot_band"
    const val PREF_SERVER_IDLE_MINUTES = "server_idle_minutes"
    const val PREF_CLIENT_DAM = "client_dam"
    const val PREF_CLIENT_FIU = "client_fiu"
    const val PREF_ZIP_COMPRESSION = "zip_compression"
    const val PREF_THEME_MODE = "theme_mode"
    const val PREF_NOTIFICATIONS_ASKED = "notifications_asked_install"
    const val PREF_NEARBY_ASKED = "nearby_asked_install"
    const val PREF_LOCAL_NETWORK_ASKED = "local_network_asked_install"

    private val LegacyAskedKeys = mapOf(
        "notifications_asked" to PREF_NOTIFICATIONS_ASKED,
        "nearby_asked" to PREF_NEARBY_ASKED,
    )

    private const val PREF_SESSION_ACTIVE = "session_active_install"
    private const val LEGACY_PREF_SESSION_ACTIVE = "session_active"
    const val PREF_SESSION_HOTSPOT = "session_hotspot"

    private val RuntimeKeys = setOf(
        PREF_NOTIFICATIONS_ASKED,
        PREF_NEARBY_ASKED,
        PREF_LOCAL_NETWORK_ASKED,
        PREF_SESSION_ACTIVE,
        PREF_SESSION_HOTSPOT,
    )

    private lateinit var prefs: SharedPreferences

    private var installTime = 0L

    fun init(context: Context) {
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        installTime = app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime
        migrateAskedFlags()
        if (prefs.contains(LEGACY_PREF_SESSION_ACTIVE)) prefs.edit { remove(LEGACY_PREF_SESSION_ACTIVE) }
    }

    private fun migrateAskedFlags() {
        val legacy = LegacyAskedKeys.keys.filter(prefs::contains)
        if (legacy.isEmpty()) return
        prefs.edit {
            legacy.forEach { old ->
                if (prefs.getBoolean(old, false)) putLong(LegacyAskedKeys.getValue(old), installTime)
                remove(old)
            }
        }
    }

    fun wasAskedOnThisInstall(key: String): Boolean =
        prefs.getLong(key, 0L).let { it != 0L && it == installTime }

    fun markAskedOnThisInstall(key: String) {
        prefs.edit { putLong(key, installTime) }
    }

    // Tied to this install, as a backup taken mid-share restores it too.
    var sessionActive: Boolean
        get() = wasAskedOnThisInstall(PREF_SESSION_ACTIVE)
        set(active) {
            if (active) markAskedOnThisInstall(PREF_SESSION_ACTIVE) else prefs.edit { remove(PREF_SESSION_ACTIVE) }
        }

    fun readInt(key: String): Int? =
        if (prefs.contains(key)) prefs.getInt(key, 0) else null

    fun readBool(key: String, default: Boolean = false): Boolean =
        prefs.getBoolean(key, default)

    fun writeInt(key: String, value: Int?) {
        prefs.edit {
            if (value == null) remove(key) else putInt(key, value)
        }
    }

    fun writeBool(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }

    fun readString(key: String): String? =
        if (prefs.contains(key)) prefs.getString(key, null) else null

    fun writeString(key: String, value: String?) {
        prefs.edit {
            if (value == null) remove(key) else putString(key, value)
        }
    }

    fun clearSettings() {
        prefs.edit { prefs.all.keys.filterNot(RuntimeKeys::contains).forEach(::remove) }
    }
}
