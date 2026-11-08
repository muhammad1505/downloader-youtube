package id.local.tubeflow.core

import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener

/**
 * Diagnostik ringan pemutar video.
 *
 * Tujuannya menjawab satu pertanyaan di perangkat: apakah jeda berkala berasal dari dekoder yang
 * diinisialisasi ulang, pipeline efek GL yang dibangun ulang, atau frame yang benar-benar dibuang
 * renderer. Setiap baris mewakili satu kejadian, bukan satu tick progres — ticker berjalan
 * beberapa kali per detik dan akan menenggelamkan sinyal yang justru dicari.
 *
 * Tidak ada judul, URI, atau nama berkas yang dicatat. Di perangkat, saring dengan
 * `adb logcat -s TubeFlow-video`, lalu cocokkan jeda yang terlihat dengan waktu tiap baris:
 *
 * - `dropped=…` berulang tanpa `dekoder=` baru berarti beban renderer/GPU.
 * - `dekoder=… init=…` tepat sebelum jeda berarti MediaCodec diinisialisasi ulang.
 * - `efek=dipasang` tepat sebelum jeda berarti pipeline GL dibangun ulang.
 * - `viewport=…` atau `pip=…` tepat sebelum jeda berarti perubahan permukaan.
 */
@androidx.annotation.OptIn(UnstableApi::class)
object VideoDiagnostics {
    const val TAG = "TubeFlow-video"

    fun session(reason: String, decoder: String, positionMs: Long, playerVersion: Int) {
        Log.d(TAG, "sesi=$reason dekoder=$decoder pos=${VideoPlayback.timeLabel(positionMs)} v=$playerVersion")
    }

    fun release(playerVersion: Int) {
        Log.d(TAG, "lepas v=$playerVersion")
    }

    fun viewport(width: Int, height: Int) {
        Log.d(TAG, "viewport=${width}x$height")
    }

    /** Setiap pemasangan membangun ulang pipeline GL, jadi kejadian ini wajib terlihat. */
    fun effects(key: String, applied: Boolean) {
        Log.d(TAG, "efek=${if (applied) "dipasang" else "ditolak"} $key")
    }

    fun subtitle(size: String, style: String) {
        Log.d(TAG, "subtitle=$size/$style")
    }

    fun failure(code: Int, codec: Boolean) {
        Log.d(TAG, "galat kode=$code codec=$codec")
    }

    fun pip(entering: Boolean) {
        Log.d(TAG, "pip=${if (entering) "masuk" else "keluar"}")
    }

    /**
     * Geometri permukaan: satu baris ini menjelaskan kenapa gambar terpotong atau berletterbox.
     * `permukaan` lebih besar dari `wadah` pada mode Pas berarti bug ukuran, bukan crop yang wajar.
     */
    fun geometry(
        aspect: String,
        containerWidth: Float,
        containerHeight: Float,
        videoWidth: Int,
        videoHeight: Int,
        pixelRatio: Float,
        surfaceWidth: Float,
        surfaceHeight: Float,
        presenting: Boolean,
        anchoredBottom: Boolean = false,
    ) {
        Log.d(
            TAG,
            "geometri aspek=$aspect wadah=${containerWidth.toInt()}x${containerHeight.toInt()} " +
                "video=${videoWidth}x$videoHeight par=$pixelRatio " +
                "permukaan=${surfaceWidth.toInt()}x${surfaceHeight.toInt()} presentasi=$presenting" +
                if (anchoredBottom) " anchor=bawah" else "",
        )
    }

    /** Satu-satunya sinyal langsung bahwa gambar benar-benar patah; senyap bila tidak ada. */
    fun droppedFrames(dropped: Int, elapsedMs: Long) {
        if (dropped <= 0) return
        Log.d(TAG, "dropped=$dropped dalam ${elapsedMs}ms")
    }

    /** Dekoder diinisialisasi ulang; jeda di sekitar baris ini menunjuk ke MediaCodec. */
    fun decoderInit(name: String, durationMs: Long) {
        Log.d(TAG, "dekoder=$name init=${durationMs}ms")
    }

    internal fun analytics(): AnalyticsListener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) = decoderInit(decoderName, initializationDurationMs)

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) = droppedFrames(droppedFrames, elapsedMs)
    }
}
