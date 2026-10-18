package id.local.tubeflow.core

import kotlin.math.ln

/**
 * Kurva equalizer "Otomatis (bass bulat)": bass dan treble naik seimbang, mid tetap netral.
 * Murni tanpa Android karena jumlah/lebar band berbeda per perangkat;
 * level dihitung dari frekuensi tengah band, bukan indeks band, sehingga dapat diuji di host.
 */
object AutoEq {
    const val AUTO_PRESET = "Otomatis (bass bulat)"

    /** Titik kurva: frekuensi (mHz) → level dB sebelum dibulatkan dan dijepit ke rentang band. */
    private val CURVE = listOf(
        60_000 to 3.0f,
        120_000 to 2.5f,
        250_000 to 1.0f,
        500_000 to 0.0f,
        1_000_000 to 0.0f,
        2_000_000 to 0.0f,
        4_000_000 to 0.5f,
        8_000_000 to 2.0f,
        16_000_000 to 3.0f,
    )

    /** Level dB untuk band berfrekuensi tengah [frequencyMilliHz], dijepit ke [minDb]..[maxDb]. */
    fun levelDb(frequencyMilliHz: Int, minDb: Float, maxDb: Float): Float {
        val f = frequencyMilliHz.coerceAtLeast(1)
        val raw = when {
            f <= CURVE.first().first -> CURVE.first().second
            f >= CURVE.last().first -> CURVE.last().second
            else -> {
                val upper = CURVE.indexOfFirst { it.first >= f }
                val (f0, l0) = CURVE[upper - 1]
                val (f1, l1) = CURVE[upper]
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
