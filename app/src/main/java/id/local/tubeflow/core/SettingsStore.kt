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

    var storageFolder by mutableStateOf(DEFAULT_FOLDER)
        private set

    /** SAF tree URI picked by the user; null means the default MediaStore Downloads target. */
    var storageTree: String? by mutableStateOf(null)
        private set

    /** How many downloads may run at the same time (1..4). */
    var maxConcurrent by mutableIntStateOf(1)
        private set

    fun load(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        themeMode = prefs.getString("theme", MODE_SYSTEM)?.takeIf { it in supported } ?: MODE_SYSTEM
        dynamicColor = prefs.getBoolean("dynamic", true)
        storageFolder = prefs.getString("folder", DEFAULT_FOLDER)?.let(Selection::safeFolder) ?: DEFAULT_FOLDER
        storageTree = prefs.getString("tree", "")?.takeIf { it.isNotBlank() }
        maxConcurrent = prefs.getInt("concurrent", 1).coerceIn(1, 4)
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
