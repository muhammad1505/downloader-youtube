package id.local.tubeflow.core

/**
 * Cookie Bstation tanpa ketergantungan Android.
 *
 * Cookie disimpan sebagai pasangan nama/nilai, bukan header utuh, agar tidak ada header injection
 * saat ditulis ke file Netscape untuk engine. Cookie hanya dipakai untuk domain Bstation.
 */
object BstationCookies {
    const val SESSION_COOKIE = "SESSDATA"
    private const val ACCOUNT_COOKIE = "DedeUserID"
    private val namePattern = Regex("[A-Za-z0-9!#$%&'*+.^_`|~-]{1,64}")
    // Cookie Bstation yang sah berupa ASCII tercetak (SESSDATA memakai percent-encoding).
    // Menolak spasi/kontrol/non-ASCII juga mencegah header atau file cookie rusak.
    private val unsafeValue = Regex("[^\\x21-\\x7E]")
    private val domains = listOf(".bilibili.tv", ".biliintl.com")

    /** Parse header `a=1; b=2`; nama tidak valid dan nilai kosong diabaikan. */
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

    fun isSignedIn(cookies: Map<String, String>): Boolean =
        cookies.entries.any { it.key.equals(SESSION_COOKIE, ignoreCase = true) && validCookie(it.key, it.value) }

    fun accountId(cookies: Map<String, String>): String =
        cookies.entries.firstOrNull { it.key.equals(ACCOUNT_COOKIE, ignoreCase = true) }
            ?.value?.trim()?.takeIf { it.matches(Regex("\\d{1,20}")) }.orEmpty()

    /** Header `Cookie` untuk API katalog; kosong bila belum masuk. */
    fun header(cookies: Map<String, String>): String = cookies.entries
        .filter { (key, value) -> validCookie(key, value) }
        .joinToString("; ") { (key, value) -> "$key=$value" }

    /**
     * Format Netscape untuk `--cookies` yt-dlp. Dua domain Bstation ditulis karena tautan
     * `biliintl.com` lama tetap boleh dipakai, sementara katalog/kanonis memakai `bilibili.tv`.
     */
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
