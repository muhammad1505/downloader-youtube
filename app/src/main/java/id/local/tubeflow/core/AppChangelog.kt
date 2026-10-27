package id.local.tubeflow.core

import android.content.Context
import androidx.core.content.edit

/** Sorotan satu rilis untuk dialog "Apa yang baru"; ditulis manual, tanpa jaringan. */
data class ReleaseNotes(val versionCode: Int, val versionName: String, val highlights: List<String>)

/**
 * Sorotan rilis bawaan aplikasi. Versi yang tidak punya entri tidak menampilkan dialog apa pun,
 * sehingga rilis kecil (mis. perbaikan internal) bisa dilewati tanpa mengubah kode pemanggil.
 */
object AppChangelog {
    /** Terbaru di atas. */
    val releases: List<ReleaseNotes> = listOf(
        ReleaseNotes(
            64, "1.1.49",
            listOf(
                "Berkas yang dibuka dari aplikasi lain diputar bersama media sejenis di folder yang sama, jadi tombol berikut/sebelumnya tetap berguna.",
                "Tahan ikon launcher untuk pintasan Unduh, Audio, Video, dan Riwayat.",
                "Kelola playlist lebih rapi dan menampilkan jumlah lagu tiap playlist.",
                "Layar Ringkasan diringkas jadi empat blok; angka rinci dan penjelasannya ada di tombol Lihat detail.",
            ),
        ),
        ReleaseNotes(
            63, "1.1.48",
            listOf(
                "Klip Status tidak lagi dibatasi 30 detik: geser awal dan akhir sendiri, berguna juga untuk dikirim ke chat.",
                "Batas waktu FFmpeg kini mengikuti panjang klip yang dipilih.",
            ),
        ),
        ReleaseNotes(
            62, "1.1.47",
            listOf(
                "Pemilihan klip Status memakai satu bilah rentang: geser tiap ujung atau geser bagian tengah untuk memindahkan seluruh klip.",
            ),
        ),
    )

    /** Catatan persis untuk versi ini; null bila rilisnya tidak punya sorotan. */
    fun forVersion(versionCode: Int): ReleaseNotes? =
        releases.firstOrNull { it.versionCode == versionCode }

    /** Entri terbaru yang tidak lebih baru dari versi ini; dipakai tombol "Apa yang baru" di Tentang. */
    fun latestAtOrBefore(versionCode: Int): ReleaseNotes? =
        releases.firstOrNull { it.versionCode <= versionCode }

    /**
     * Dialog hanya muncul bila versi berjalan lebih baru dari yang sudah ditutup pengguna **dan**
     * versi itu punya catatan sendiri; menampilkan catatan lama dengan judul versi baru menyesatkan.
     */
    fun shouldOffer(lastSeenVersionCode: Int, currentVersionCode: Int): Boolean =
        currentVersionCode > lastSeenVersionCode && forVersion(currentVersionCode) != null
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
