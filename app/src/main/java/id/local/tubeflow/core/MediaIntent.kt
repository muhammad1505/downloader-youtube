package id.local.tubeflow.core

import java.util.Locale

/** Media milik aplikasi lain yang dibuka lewat "Buka dengan" pada chooser sistem. */
data class IncomingMedia(val uri: String, val title: String, val kind: Kind) {
    enum class Kind { AUDIO, VIDEO }
}

/**
 * Klasifikasi berkas media yang masuk dari aplikasi lain. Bagian ini sengaja tidak menyentuh
 * Android Intent agar dapat diuji di host: MainActivity hanya memasok URI, MIME, dan nama tampilan.
 */
object MediaIntent {
    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "m4b", "m4p", "mp2", "aac", "flac", "wav", "ogg", "oga", "opus",
        "wma", "amr", "mid", "midi", "mka", "ac3", "eac3", "dts", "ape", "aif", "aiff",
    )
    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "f4v", "mkv", "webm", "mov", "avi", "3gp", "3g2", "ts", "m2ts", "mts",
        "flv", "wmv", "mpg", "mpeg", "ogv", "rmvb", "divx", "vob", "asf",
    )

    /** Hanya berkas lokal; tautan http(s) tetap masuk jalur unduhan yang sudah ada. */
    fun isLocalMediaUri(uri: String): Boolean {
        val value = uri.trim().lowercase(Locale.ROOT)
        return value.startsWith("content://") || value.startsWith("file://")
    }

    /**
     * MIME dari pengirim diprioritaskan. Bila pengirim memakai MIME umum seperti
     * application/octet-stream, ekstensi nama berkas menjadi penentu.
     */
    fun kind(mimeType: String?, nameOrUri: String): IncomingMedia.Kind? {
        val mime = mimeType?.trim()?.lowercase(Locale.ROOT).orEmpty()
        when {
            mime.startsWith("audio/") -> return IncomingMedia.Kind.AUDIO
            mime.startsWith("video/") -> return IncomingMedia.Kind.VIDEO
            mime == "application/ogg" || mime == "application/x-ogg" -> return IncomingMedia.Kind.AUDIO
        }
        return when (extension(nameOrUri)) {
            in AUDIO_EXTENSIONS -> IncomingMedia.Kind.AUDIO
            in VIDEO_EXTENSIONS -> IncomingMedia.Kind.VIDEO
            else -> null
        }
    }

    /** Nama berkas tanpa ekstensi; nama kosong tetap aman dipakai sebagai judul pemutar. */
    fun title(displayName: String): String {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return ""
        val base = trimmed.substringBeforeLast('.', trimmed)
        return base.ifBlank { trimmed }
    }

    /** Null berarti berkas ini bukan audio/video lokal yang bisa dibuka pemutar TubeFlow. */
    fun from(uri: String, mimeType: String?, displayName: String?): IncomingMedia? {
        val raw = uri.trim()
        if (!isLocalMediaUri(raw)) return null
        val name = displayName?.takeIf { it.isNotBlank() }
        val kind = kind(mimeType, name.orEmpty()) ?: kind(mimeType, raw) ?: return null
        val encoded = raw.substringBefore('?').substringBefore('#').substringAfterLast('/')
        val fallback = runCatching { java.net.URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8") }.getOrDefault(encoded)
        return IncomingMedia(raw, title(name.orEmpty()).ifBlank { title(fallback) }, kind)
    }

    private fun extension(nameOrUri: String): String =
        nameOrUri.substringBefore('?').substringBefore('#')
            .substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)
}
