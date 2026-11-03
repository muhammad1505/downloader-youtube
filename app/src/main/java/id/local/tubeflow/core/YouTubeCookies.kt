package id.local.tubeflow.core

/**
 * Cookie YouTube/Google tanpa ketergantungan Android.
 *
 * Video yang dibatasi usia, anggota kanal, atau privat membutuhkan cookie akun. Cookie disimpan
 * sebagai pasangan nama/nilai lalu ditulis ke file Netscape sementara untuk `yt-dlp --cookies`;
 * file itu hanya dibuat untuk host YouTube/Google dan dihapus setelah engine selesai.
 */
object YouTubeCookies {
    /** Cookie penanda sesi Google/YouTube yang dipakai extractor. */
    private val sessionNames = listOf("SID", "SAPISID", "__Secure-1PSID", "__Secure-3PSID")
    private val namePattern = Regex("[A-Za-z0-9!#$%&'*+.^_`|~-]{1,64}")
    // Nilai cookie sah berupa ASCII tercetak; menolak spasi/kontrol juga mencegah file cookie rusak.
    private val unsafeValue = Regex("[^\\x21-\\x7E]")
    private val domains = listOf(".youtube.com", ".google.com")

    fun parse(header: String?): Map<String, String> {
        if (header.isNullOrBlank()) return emptyMap()
        val cookies = linkedMapOf<String, String>()
        header.split(';').forEach { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) return@forEach
            val key = part.substring(0, separator).trim()
            val value = part.substring(separator + 1).trim()
            if (!validCookie(key, value)) return@forEach
            cookies[key] = value
        }
        return cookies
    }

    /** Gabungkan beberapa header CookieManager; nilai terakhir menang. */
    fun merge(vararg headers: String?): Map<String, String> {
        val cookies = linkedMapOf<String, String>()
        headers.forEach { cookies.putAll(parse(it)) }
        return cookies
    }

    fun isSignedIn(cookies: Map<String, String>): Boolean = sessionNames.any { name ->
        cookies.entries.any { it.key.equals(name, ignoreCase = true) && validCookie(it.key, it.value) }
    }

    fun header(cookies: Map<String, String>): String = cookies.entries
        .filter { (key, value) -> validCookie(key, value) }
        .joinToString("; ") { (key, value) -> "$key=$value" }

    /** Format Netscape untuk `--cookies`; hanya dibuat bila sesi benar-benar berisi cookie login. */
    fun netscape(cookies: Map<String, String>): String {
        if (!isSignedIn(cookies)) return ""
        return buildString {
            appendLine("# Netscape HTTP Cookie File")
            appendLine("# Dibuat oleh TubeFlow; file sementara dan dihapus setelah engine selesai.")
            domains.forEach { domain ->
                cookies.forEach { (key, value) ->
                    if (validCookie(key, value)) {
                        append(domain).append('\t')
                            .append("TRUE").append('\t')
                            .append('/').append('\t')
                            .append("TRUE").append('\t')
                            .append(0).append('\t')
                            .append(key).append('\t')
                            .append(value).append('\n')
                    }
                }
            }
        }
    }

    private fun validCookie(name: String, value: String): Boolean =
        namePattern.matches(name) && value.isNotEmpty() && !unsafeValue.containsMatchIn(value)
}
