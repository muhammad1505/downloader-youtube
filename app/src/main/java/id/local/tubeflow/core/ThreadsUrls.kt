package id.local.tubeflow.core

import java.net.URI

/** Restrict the optional Threads extractor to canonical public post/share URL shapes. */
internal object ThreadsUrls {
    private val hosts = setOf("threads.com", "www.threads.com", "threads.net", "www.threads.net")
    private val postPath = Regex("""/(?:@[A-Za-z0-9._-]+/post/[A-Za-z0-9_-]+|t/[A-Za-z0-9_-]+|share/[A-Za-z0-9_-]+)/?""")

    fun isHost(host: String): Boolean = host.lowercase() in hosts

    fun hasSupportedPostPath(path: String): Boolean = postPath.matches(path)

    fun isSupportedUrl(raw: String): Boolean = runCatching {
        val uri = URI(raw.trim())
        uri.scheme.equals("https", ignoreCase = true) && uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            uri.host?.let(::isHost) == true && hasSupportedPostPath(uri.rawPath.orEmpty())
    }.getOrDefault(false)
}
