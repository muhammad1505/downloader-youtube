package id.local.tubeflow.core

/**
 * Kebijakan percobaan ulang untuk kegagalan [android.media.MediaPlayer] yang biasanya
 * bersifat sementara. Nilai `what` disalin dari konstanta platform agar kelas ini tetap
 * murni dan dapat diuji di host tanpa Android SDK.
 *
 * Satu track hanya boleh dicoba ulang sekali; kegagalan codec/format tidak diulang karena
 * pemuatan ulang tidak akan memperbaikinya.
 */
object AudioErrorPolicy {
    // android.media.MediaPlayer.MEDIA_ERROR_*
    private const val ERROR_UNKNOWN = 1
    private const val ERROR_SERVER_DIED = 100
    private const val ERROR_IO = -1004
    private const val ERROR_TIMED_OUT = -110

    /**
     * Kode platform untuk perintah yang tidak sah pada state saat itu. Bukan kode publik
     * MediaPlayer, tetapi dilaporkan apa adanya lewat `onErrorListener`.
     */
    const val ERROR_INVALID_OPERATION = -38

    /**
     * @param alreadyRetried true bila track ini sudah pernah dimuat ulang setelah error.
     */
    fun shouldRetry(what: Int, alreadyRetried: Boolean): Boolean {
        if (alreadyRetried) return false
        return what == ERROR_UNKNOWN ||
            what == ERROR_SERVER_DIED ||
            what == ERROR_IO ||
            what == ERROR_TIMED_OUT
    }

    /**
     * Error yang tidak boleh menghentikan sesi. Perintah tidak sah saat pemutar berhenti hanya
     * berarti perintah itu diabaikan platform; file tetap siap diputar kembali.
     */
    fun benignWhilePaused(what: Int): Boolean = what == ERROR_INVALID_OPERATION

    /** Label diagnostik aman: tidak memuat URI, judul, atau data akun. */
    fun label(what: Int, extra: Int): String = "what=$what extra=$extra"
}
