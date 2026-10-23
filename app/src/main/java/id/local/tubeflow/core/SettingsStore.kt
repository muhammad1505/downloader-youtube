package id.local.tubeflow.core

import android.content.Context
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Persisted appearance choices, observed directly by Compose. */
object SettingsStore {
    private const val FILE = "settings"
    const val DEFAULT_FOLDER = "TubeFlow"
    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"
    private val supported = setOf(MODE_SYSTEM, MODE_LIGHT, MODE_DARK)

    var themeMode by mutableStateOf(MODE_SYSTEM)
        private set

    var dynamicColor by mutableStateOf(true)
        private set

    var artworkAccent by mutableStateOf(true); private set

    fun setArtworkAccent(context: Context, enabled: Boolean) {
        artworkAccent=enabled
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean("artwork_accent", enabled) }
    }

    var resumeAfterInterruption by mutableStateOf(true); private set

    fun setResumeAfterInterruption(context: Context, enabled: Boolean) {
        resumeAfterInterruption = enabled
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean("resume_interruption", enabled) }
        PlayerViewModel.active?.setInterruptionResume(enabled)
    }

    var storageFolder by mutableStateOf(DEFAULT_FOLDER)
        private set

    /** SAF tree URI picked by the user; null means the default MediaStore Downloads target. */
    var storageTree: String? by mutableStateOf(null)
        private set

    /** How many downloads may run at the same time (1..4). */
    var maxConcurrent by mutableIntStateOf(1)
        private set

    /** Hapus catatan riwayat selesai yang lebih lama dari N hari; 0 = nonaktif. File media tidak disentuh. */
    var historyAutoDays by mutableIntStateOf(0)
        private set

    fun setHistoryAutoDays(context: Context, days: Int) {
        val value = days.coerceIn(0, 365)
        historyAutoDays = value
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { putInt("history_auto_days", value) }
    }

    var autoRename by mutableStateOf(true); private set
    var autoTag by mutableStateOf(true); private set
    var autoArtwork by mutableStateOf(true); private set
    var autoLyrics by mutableStateOf(false); private set
    var autoLibrary by mutableStateOf(true); private set
    var analyticsSince: Long = 0; private set

    fun setSmart(context: Context, key: String, enabled: Boolean) {
        when (key) {
            "rename" -> autoRename = enabled
            "tag" -> autoTag = enabled
            "artwork" -> autoArtwork = enabled
            "lyrics" -> autoLyrics = enabled
            "library" -> autoLibrary = enabled
            else -> return
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean("smart_$key", enabled) }
    }

    fun load(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        resumeAfterInterruption = prefs.getBoolean("resume_interruption", true)
        autoRename = prefs.getBoolean("smart_rename", true)
        autoTag = prefs.getBoolean("smart_tag", true)
        autoArtwork = prefs.getBoolean("smart_artwork", true)
        autoLyrics = prefs.getBoolean("smart_lyrics", false)
        autoLibrary = prefs.getBoolean("smart_library", true)
        analyticsSince = prefs.getLong("analytics_since", 0).takeIf { it > 0 } ?: System.currentTimeMillis().also {
            prefs.edit { putLong("analytics_since", it) }
        }
        themeMode = prefs.getString("theme", MODE_SYSTEM)?.takeIf { it in supported } ?: MODE_SYSTEM
        dynamicColor = prefs.getBoolean("dynamic", true)
        artworkAccent = prefs.getBoolean("artwork_accent", true)
        storageFolder = prefs.getString("folder", DEFAULT_FOLDER)?.let(Selection::safeFolder) ?: DEFAULT_FOLDER
        storageTree = prefs.getString("tree", "")?.takeIf { it.isNotBlank() }
        maxConcurrent = prefs.getInt("concurrent", 1).coerceIn(1, 4)
        historyAutoDays = prefs.getInt("history_auto_days", 0).coerceIn(0, 365)
    }

    /** Picks (or clears with null) the storage directory via a persisted SAF tree URI. */
    fun setStorageTree(context: Context, uri: String?) {
        storageTree = uri?.takeIf { it.isNotBlank() }
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { putString("tree", storageTree ?: "") }
    }

    fun setMaxConcurrent(context: Context, value: Int) {
        val limit = value.coerceIn(1, 4)
        maxConcurrent = limit
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { putInt("concurrent", limit) }
    }

    /** Returns true when [raw] is a valid folder name and was persisted. */
    fun setStorageFolder(context: Context, raw: String): Boolean {
        val name = Selection.safeFolder(raw) ?: return false
        if (name == storageFolder) return true
        storageFolder = name
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { putString("folder", name) }
        return true
    }

    fun setThemeMode(context: Context, mode: String) {
        if (mode !in supported) return
        themeMode = mode
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { putString("theme", mode) }
    }

    fun setDynamicColor(context: Context, enabled: Boolean) {
        dynamicColor = enabled
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { putBoolean("dynamic", enabled) }
    }

    fun isDark(systemDark: Boolean): Boolean = when (themeMode) {
        MODE_LIGHT -> false
        MODE_DARK -> true
        else -> systemDark
    }
}
