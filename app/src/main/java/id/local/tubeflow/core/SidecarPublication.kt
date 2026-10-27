package id.local.tubeflow.core

/**
 * Penamaan berkas pendamping mengikuti nama media yang benar-benar diterbitkan.
 *
 * Contoh: media `media.mp4` diterbitkan sebagai `E01 - Judul.mp4`, sidecar
 * `media.id.srt` harus menjadi `E01 - Judul.id.srt` — suffix bahasa dipertahankan dan
 * tetap berada di folder yang sama karena pemanggil memakai parent/media yang sama.
 */
object SidecarPublication {
    private val LANGUAGE_SUFFIX = Regex("""\.[A-Za-z0-9-]+""")

    /**
     * Mengambil suffix setelah nama dasar media, mis. `media.id.srt` → `.id`.
     * Nama yang tidak diawali stem media tidak diberi suffix agar tidak menghasilkan
     * nama seperti `E01subtitle.srt`.
     */
    fun suffix(mediaStem: String, sidecarName: String): String {
        val sidecarStem = sidecarName.substringBeforeLast('.', sidecarName)
        val raw = sidecarStem.removePrefix(mediaStem)
        return raw.takeIf { LANGUAGE_SUFFIX.matches(it) }.orEmpty()
    }

    /** Nama akhir sidecar di folder tujuan, memakai suffix dari nama sumber dan stem terbit. */
    fun requestedName(publishedStem: String, suffix: String, extension: String): String =
        "$publishedStem$suffix.$extension"
}
