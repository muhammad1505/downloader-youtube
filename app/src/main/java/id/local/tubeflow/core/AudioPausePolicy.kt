package id.local.tubeflow.core

/**
 * Validitas perintah [android.media.MediaPlayer] yang bergantung pada state pemutar.
 *
 * `pause()` hanya sah saat pemutar benar-benar berjalan. Memanggilnya ketika pemutar masih
 * PREPARED atau sudah PAUSED dilaporkan platform lewat `onErrorListener` sebagai error (-38, 0),
 * sehingga sesi yang sehat terlihat gagal padahal hanya sedang dijeda. Kasus ini nyata terjadi
 * saat pemutar video membuka decoder-nya sendiri sementara pemutar lagu berhenti dalam keadaan
 * siap-putar.
 */
object AudioPausePolicy {
    /**
     * @param prepared true bila engine sudah selesai menyiapkan track.
     * @param isPlaying hasil [android.media.MediaPlayer.isPlaying]; null saat pemutar tidak dapat
     *   ditanya (mis. sudah dilepas).
     */
    fun shouldPause(prepared: Boolean, isPlaying: Boolean?): Boolean = prepared && isPlaying == true
}
