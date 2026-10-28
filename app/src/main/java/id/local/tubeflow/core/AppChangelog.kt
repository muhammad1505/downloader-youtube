package id.local.tubeflow.core

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray

/** Sorotan satu rilis untuk dialog "Apa yang baru"; dibaca dari assets/changelog.json. */
data class ReleaseNotes(val versionCode: Int, val versionName: String, val highlights: List<String>)

/**
 * Sumber data changelog bawaan aplikasi. Daftar tidak lagi ditulis di Kotlin: tambahkan atau ubah
 * entri di `assets/changelog.json`, lalu naikkan `versionCode` agar dialog muncul untuk pembaruan.
 *
 * Versi yang tidak punya entri tidak menampilkan dialog apa pun, sehingga rilis kecil (mis. perbaikan
 * internal) bisa dilewati tanpa mengubah kode pemanggil.
 */
object AppChangelog {
    private const val ASSET = "changelog.json"

    @Volatile
    private var cached: List<ReleaseNotes>? = null

    /** Baca changelog sekali per proses; kegagalan membaca aset tidak boleh menjatuhkan aplikasi. */
    fun load(context: Context): List<ReleaseNotes> {
        cached?.let { return it }
        val notes = runCatching {
            context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }
        }.getOrDefault(emptyList())
        cached = notes
        return notes
    }

    /** Parser murni agar isi aset bisa diuji tanpa Context/Android. */
    internal fun parse(raw: String): List<ReleaseNotes> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                val versionCode = entry.optInt("versionCode", 0)
                val versionName = entry.optString("versionName").trim()
                val highlights = entry.optJSONArray("highlights")?.let { values ->
                    buildList {
                        for (line in 0 until values.length()) {
                            values.optString(line).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                        }
                    }
                }.orEmpty()
                if (versionCode <= 0 || versionName.isEmpty() || highlights.isEmpty()) continue
                add(ReleaseNotes(versionCode, versionName, highlights))
            }
        }.sortedByDescending { it.versionCode }
    }

    /** Catatan persis untuk versi ini; null bila rilisnya tidak punya sorotan. */
    fun forVersion(notes: List<ReleaseNotes>, versionCode: Int): ReleaseNotes? =
        notes.firstOrNull { it.versionCode == versionCode }

    /** Entri terbaru yang tidak lebih baru dari versi ini; dipakai tombol "Apa yang baru" di Tentang. */
    fun latestAtOrBefore(notes: List<ReleaseNotes>, versionCode: Int): ReleaseNotes? =
        notes.firstOrNull { it.versionCode <= versionCode }

    /**
     * Dialog hanya muncul bila versi berjalan lebih baru dari yang sudah ditutup pengguna **dan**
     * versi itu punya catatan sendiri; menampilkan catatan lama dengan judul versi baru menyesatkan.
     */
    fun shouldOffer(
        lastSeenVersionCode: Int,
        currentVersionCode: Int,
        notes: List<ReleaseNotes>,
    ): Boolean = currentVersionCode > lastSeenVersionCode && forVersion(notes, currentVersionCode) != null
}

/** Versi terakhir yang sorotannya sudah ditutup pengguna; 0 berarti belum pernah melihat. */
object ChangelogStore {
    private const val FILE = "changelog"
    private const val KEY = "last_seen_version"

    fun lastSeen(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY, 0)

    fun markSeen(context: Context, versionCode: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putInt(KEY, versionCode) }
    }
}
