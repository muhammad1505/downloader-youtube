package id.local.tubeflow.core

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.audiofx.Equalizer
import android.net.Uri
import androidx.core.content.edit

/**
 * Equalizer bawaan Android pada audio session MediaPlayer. Ketersediaan bergantung perangkat/ROM;
 * seluruh operasi dibungkus runCatching sehingga kegagalan audiofx tidak menjatuhkan pemutaran.
 * Pengaturan (preset atau level kustom) dipersisten dan diterapkan ulang pada setiap track baru.
 */
class PlayerEqualizer(context: Context) {
    data class Band(val index: Int, val label: String, val minDb: Float, val maxDb: Float, val levelDb: Float)
    data class Snapshot(
        val available: Boolean,
        val enabled: Boolean,
        val preset: String,
        val presets: List<String>,
        val bands: List<Band>,
        /** Profil genre yang sedang dipakai kurva otomatis; kosong bila genre tak dikenali. */
        val genreLabel: String = "",
    )

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("player-fx", 0)
    private var genre: String? = null
    private var effect: Equalizer? = null
    private var bandLabels: List<String> = emptyList()
    private var presets: List<String> = emptyList()
    private var bandRange: ClosedFloatingPointRange<Float> = 0f..0f

    @Synchronized
    fun attach(sessionId: Int) {
        release()
        runCatching {
            val created = Equalizer(0, sessionId)
            effect = created
            bandRange = created.bandLevelRange.let { it[0] / 100f..it[1] / 100f }
            bandLabels = (0 until created.numberOfBands).map { formatFrequency(created.getCenterFreq(it.toShort())) }
            presets = (0 until created.numberOfPresets).map { created.getPresetName(it.toShort()) }
            applyStored(created)
        }.onFailure { release() }
    }

    @Synchronized
    fun release() {
        runCatching { effect?.release() }
        effect = null
    }

    /**
     * Baca tag genre lagu di latar belakang, lalu sesuaikan kurva otomatis. Hanya pemutar audio
     * yang memanggil ini; pemutar video punya equalizer sendiri.
     */
    @Synchronized
    fun followGenre(rawUri: String?) {
        genre = null
        val uri = runCatching { Uri.parse(rawUri.orEmpty()) }.getOrNull() ?: return
        Thread {
            val value = runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(appContext, uri)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                }
            }.getOrNull()
            applyGenre(value)
        }.apply { isDaemon = true }.start()
    }

    @Synchronized
    private fun applyGenre(value: String?) {
        genre = value?.takeIf { AutoEq.profileFor(it) != null }
        val current = effect ?: return
        val preset = AutoEq.defaultPreset(prefs.getString("eq_preset", null), prefs.contains("eq_levels"))
        if (preset == AutoEq.AUTO_PRESET) applyAuto(current)
    }

    @Synchronized
    fun snapshot(): Snapshot {
        val current = effect ?: return Snapshot(available = false, enabled = false, preset = "", presets = emptyList(), bands = emptyList())
        val bands = bandLabels.mapIndexed { index, label ->
            Band(index, label, bandRange.start, bandRange.endInclusive, runCatching { current.getBandLevel(index.toShort()) }.getOrDefault(0) / 100f)
        }
        return Snapshot(available = true, enabled = runCatching { current.enabled }.getOrDefault(false),
            preset = AutoEq.defaultPreset(prefs.getString("eq_preset", null), prefs.contains("eq_levels")), presets = presets, bands = bands,
            genreLabel = AutoEq.profileFor(genre)?.label.orEmpty())
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("eq_enabled", enabled) }
        runCatching { effect?.enabled = enabled }
    }    /** Pakai preset bernama; [AutoEq.AUTO_PRESET] memakai kurva bass bulat, [DEFAULT_PRESET] mempertahankan level kustom yang tersimpan. */
    @Synchronized
    fun usePreset(name: String) {
        val current = effect ?: return
        prefs.edit { putString("eq_preset", name) }
        when (name) {
            AutoEq.AUTO_PRESET -> applyAuto(current)
            DEFAULT_PRESET -> storedLevels().forEachIndexed { index, level -> runCatching { current.setBandLevel(index.toShort(), (level * 100).toInt().toShort()) } }
            else -> { val index = presets.indexOf(name); if (index >= 0) runCatching { current.usePreset(index.toShort()) } }
        }
        runCatching { current.enabled = prefs.getBoolean("eq_enabled", true) }
    }

    @Synchronized
    fun setBandLevel(index: Int, db: Float) {
        val current = effect ?: return
        val levels = storedLevels().toMutableList()
        while (levels.size < bandLabels.size) levels.add(0f)
        if (index in levels.indices) {
            levels[index] = db.coerceIn(bandRange.start, bandRange.endInclusive)
            prefs.edit { putString("eq_levels", levels.joinToString(",") { ((it * 100).toInt()).toString() }); putString("eq_preset", DEFAULT_PRESET) }
            runCatching { current.setBandLevel(index.toShort(), (levels[index] * 100).toInt().toShort()) }
        }
    }

    private fun applyStored(created: Equalizer) {
        when (val preset = AutoEq.defaultPreset(prefs.getString("eq_preset", null), prefs.contains("eq_levels"))) {
            AutoEq.AUTO_PRESET -> applyAuto(created)
            DEFAULT_PRESET -> storedLevels().forEachIndexed { index, level -> runCatching { created.setBandLevel(index.toShort(), (level * 100).toInt().toShort()) } }
            else -> { val index = presets.indexOf(preset); if (index >= 0) runCatching { created.usePreset(index.toShort()) } }
        }
        runCatching { created.enabled = prefs.getBoolean("eq_enabled", true) }
    }

    /** Kurva otomatis per band dari frekuensi tengahnya; diterapkan pada setiap lagu. */
    private fun applyAuto(created: Equalizer) {
        val curve = AutoEq.curveFor(genre)
        for (index in 0 until created.numberOfBands) runCatching {
            val db = AutoEq.levelDb(created.getCenterFreq(index.toShort()), bandRange.start, bandRange.endInclusive, curve)
            created.setBandLevel(index.toShort(), (db * 100).toInt().toShort()) }
    }

    private fun storedLevels(): List<Float> =
        prefs.getString("eq_levels", null)?.split(',')?.mapNotNull { value -> value.toIntOrNull()?.div(100f) }.orEmpty()

    private fun formatFrequency(milliHertz: Int): String {
        val hz = milliHertz / 1000f
        return if (hz >= 1000) "%s kHz".format(java.util.Locale.ROOT, (hz / 1000).let { if (it % 1f == 0f) it.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, it) })
        else "${hz.toInt()} Hz"
    }

    companion object { const val DEFAULT_PRESET = "Kustom" }
}
