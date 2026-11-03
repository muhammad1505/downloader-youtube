package id.local.tubeflow.core

import java.util.Locale
import kotlin.math.ln

/**
 * Kurva equalizer "Otomatis (bass bulat)": bass penuh dengan bahu landai (bukan lonjakan tajam),
 * mid tetap netral, dan treble hanya cukup untuk kilau. Murni tanpa Android karena jumlah/lebar
 * band berbeda per perangkat; level dihitung dari frekuensi tengah band, bukan indeks band,
 * sehingga dapat diuji di host.
 */
object AutoEq {
    const val AUTO_PRESET = "Otomatis (bass bulat)"

    /**
     * Titik kurva: frekuensi (mHz) → level dB sebelum dibulatkan dan dijepit ke rentang band.
     * Bass dibuat lebar: 30–60 Hz penuh, turun landai sampai 500 Hz sehingga tidak terasa
     * seperti lonjakan sempit; mid 500 Hz–2 kHz netral agar vokal tidak tertutup.
     */
    private val CURVE = listOf(
        30_000 to 6.0f,
        60_000 to 6.0f,
        120_000 to 4.5f,
        250_000 to 2.0f,
        500_000 to 0.5f,
        1_000_000 to 0.0f,
        2_000_000 to 0.0f,
        4_000_000 to 0.5f,
        8_000_000 to 1.5f,
        16_000_000 to 2.5f,
    )

    /** Kurva otomatis yang dipakai bila genre lagu tidak dikenali. */
    fun autoCurve(): List<Pair<Int, Float>> = CURVE

    /** Profil genre untuk pemutar audio; null bila genre kosong/tidak dikenali. */
    data class GenreProfile(val label: String, val curve: List<Pair<Int, Float>>)

    private fun curve(vararg points: Pair<Int, Float>): List<Pair<Int, Float>> = points.toList()

    // Urutan penting: kata kunci yang lebih spesifik diperiksa lebih dulu.
    private val GENRES: List<Pair<List<String>, GenreProfile>> = listOf(
        listOf("hip hop", "hip-hop", "rap", "r&b", "rnb", "soul", "funk") to GenreProfile(
            "Bass", curve(30_000 to 6.0f, 60_000 to 6.0f, 120_000 to 4.5f, 250_000 to 2.0f, 500_000 to 0.0f,
                1_000_000 to -0.5f, 2_000_000 to 0.0f, 4_000_000 to 0.5f, 8_000_000 to 1.0f, 16_000_000 to 1.5f),
        ),
        listOf("electronic", "edm", "dance", "house", "techno", "trance", "dubstep") to GenreProfile(
            "Elektronik", curve(30_000 to 5.0f, 60_000 to 5.0f, 120_000 to 4.0f, 250_000 to 1.5f, 500_000 to 0.0f,
                1_000_000 to -1.0f, 2_000_000 to 0.0f, 4_000_000 to 1.5f, 8_000_000 to 2.5f, 16_000_000 to 3.0f),
        ),
        listOf("rock", "metal", "punk", "grunge") to GenreProfile(
            "Rock", curve(30_000 to 3.5f, 60_000 to 3.5f, 120_000 to 2.5f, 250_000 to 1.0f, 500_000 to 0.0f,
                1_000_000 to -0.5f, 2_000_000 to 0.0f, 4_000_000 to 1.0f, 8_000_000 to 1.5f, 16_000_000 to 1.5f),
        ),
        listOf("pop", "disco") to GenreProfile(
            "Pop", curve(30_000 to 2.5f, 60_000 to 2.5f, 120_000 to 2.0f, 250_000 to 1.0f, 500_000 to 0.5f,
                1_000_000 to 0.5f, 2_000_000 to 1.0f, 4_000_000 to 1.0f, 8_000_000 to 1.5f, 16_000_000 to 1.5f),
        ),
        listOf("jazz", "blues", "swing") to GenreProfile(
            "Jazz", curve(30_000 to 2.0f, 60_000 to 2.0f, 120_000 to 1.5f, 250_000 to 1.0f, 500_000 to 0.0f,
                1_000_000 to 0.0f, 2_000_000 to 0.5f, 4_000_000 to 1.0f, 8_000_000 to 1.0f, 16_000_000 to 1.0f),
        ),
        listOf("classical", "klasik", "orchestra", "symphony") to GenreProfile(
            "Klasik", curve(30_000 to 1.0f, 60_000 to 1.0f, 120_000 to 1.0f, 250_000 to 0.5f, 500_000 to 0.0f,
                1_000_000 to 0.0f, 2_000_000 to 0.0f, 4_000_000 to 0.5f, 8_000_000 to 1.0f, 16_000_000 to 1.5f),
        ),
        listOf("acoustic", "akustik", "folk", "country") to GenreProfile(
            "Akustik", curve(30_000 to 1.0f, 60_000 to 1.0f, 120_000 to 1.0f, 250_000 to 0.5f, 500_000 to 0.0f,
                1_000_000 to 0.5f, 2_000_000 to 1.0f, 4_000_000 to 1.0f, 8_000_000 to 1.0f, 16_000_000 to 1.0f),
        ),
        listOf("vocal", "vokal", "ballad", "opera", "choir") to GenreProfile(
            "Vokal", curve(30_000 to 0.5f, 60_000 to 0.5f, 120_000 to 0.5f, 250_000 to 0.5f, 500_000 to 1.0f,
                1_000_000 to 1.5f, 2_000_000 to 2.0f, 4_000_000 to 1.0f, 8_000_000 to 0.5f, 16_000_000 to 0.5f),
        ),
    )

    /** Genre dari tag lagu; pencocokan kata kunci agar "Alternative Rock" tetap terbaca Rock. */
    fun profileFor(genre: String?): GenreProfile? {
        val value = genre?.lowercase(Locale.ROOT)?.trim().orEmpty()
        if (value.isBlank()) return null
        return GENRES.firstOrNull { (keywords, _) -> keywords.any { value.contains(it) } }?.second
    }

    /** Kurva untuk sebuah genre, atau kurva otomatis bila genre tidak dikenali. */
    fun curveFor(genre: String?): List<Pair<Int, Float>> = profileFor(genre)?.curve ?: CURVE

    /** Level dB untuk band berfrekuensi tengah [frequencyMilliHz], dijepit ke [minDb]..[maxDb]. */
    fun levelDb(
        frequencyMilliHz: Int,
        minDb: Float,
        maxDb: Float,
        activeCurve: List<Pair<Int, Float>> = CURVE,
    ): Float {
        val f = frequencyMilliHz.coerceAtLeast(1)
        val points = activeCurve.ifEmpty { CURVE }
        val raw = when {
            f <= points.first().first -> points.first().second
            f >= points.last().first -> points.last().second
            else -> {
                val upper = points.indexOfFirst { it.first >= f }
                val (f0, l0) = points[upper - 1]
                val (f1, l1) = points[upper]
                // Interpolasi linear pada skala logaritmik frekuensi.
                val t = (ln(f.toDouble() / f0) / ln(f1.toDouble() / f0)).toFloat()
                l0 + (l1 - l0) * t
            }
        }
        return ((raw * 10f).toInt() / 10f).coerceIn(minDb, maxDb)
    }

    /**
     * Preset bawaan: [AUTO_PRESET] kecuali pengguna sudah memilih preset perangkat atau level
     * kustom sendiri. "Kustom" tanpa level tersimpan berarti datar (belum ada pilihan nyata),
     * sehingga aman diganti kurva otomatis.
     */
    fun defaultPreset(storedPreset: String?, hasCustomLevels: Boolean): String = when {
        storedPreset == null -> AUTO_PRESET
        storedPreset == PlayerEqualizer.DEFAULT_PRESET && !hasCustomLevels -> AUTO_PRESET
        else -> storedPreset
    }
}
