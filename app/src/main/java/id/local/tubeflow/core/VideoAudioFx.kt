package id.local.tubeflow.core

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer

/**
 * Efek audio untuk sesi ExoPlayer. Berbeda dari [PlayerEqualizer] yang menempel pada MediaPlayer
 * audio, kelas ini punya sesi sendiri dan preset video sendiri supaya memutar video tidak mengubah
 * equalizer lagu. Semua operasi audiofx dibungkus runCatching: banyak ROM hanya menyediakan sebagian
 * efek, dan pemutaran video tidak boleh gagal karena efek opsional tidak tersedia.
 */
@Suppress("DEPRECATION") // Virtualizer masih satu-satunya efek spasial yang bekerja pada Android 10.
class VideoAudioFx {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var bandMinMb = -1_500
    private var bandMaxMb = 1_500

    @Synchronized
    fun attach(sessionId: Int, equalizerPreset: VideoEqualizer, effect: VideoAudioEffect) {
        release()
        if (sessionId <= 0) return
        runCatching {
            equalizer = Equalizer(0, sessionId).also { created ->
                bandMinMb = created.bandLevelRange.getOrNull(0)?.toInt() ?: bandMinMb
                bandMaxMb = created.bandLevelRange.getOrNull(1)?.toInt() ?: bandMaxMb
            }
        }.onFailure { equalizer = null }
        runCatching { bassBoost = BassBoost(0, sessionId) }.onFailure { bassBoost = null }
        runCatching { virtualizer = Virtualizer(0, sessionId) }.onFailure { virtualizer = null }
        apply(equalizerPreset, effect)
    }

    @Synchronized
    fun apply(equalizerPreset: VideoEqualizer, effect: VideoAudioEffect) {
        equalizer?.let { current ->
            runCatching {
                current.enabled = equalizerPreset != VideoEqualizer.OFF
                if (equalizerPreset != VideoEqualizer.OFF) {
                    for (band in 0 until current.numberOfBands) {
                        val hz = current.getCenterFreq(band.toShort()) / 1_000
                        val levelMb = (equalizerPreset.levelDb(hz) * 100).toInt()
                            .coerceIn(bandMinMb, bandMaxMb)
                        current.setBandLevel(band.toShort(), levelMb.toShort())
                    }
                }
            }
        }
        val bass = when (effect) {
            VideoAudioEffect.BASS_BOOST -> 700
            VideoAudioEffect.STUDIO -> 450
            else -> 0
        }
        runCatching {
            bassBoost?.enabled = bass > 0
            if (bass > 0) bassBoost?.setStrength(bass.toShort())
        }
        val spatial = when (effect) {
            VideoAudioEffect.SPATIAL -> 650
            VideoAudioEffect.STUDIO -> 400
            else -> 0
        }
        runCatching {
            virtualizer?.enabled = spatial > 0
            if (spatial > 0) virtualizer?.setStrength(spatial.toShort())
        }
    }

    @Synchronized
    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
    }
}
