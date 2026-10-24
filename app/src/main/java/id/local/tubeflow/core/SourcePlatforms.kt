package id.local.tubeflow.core

import java.net.URI

data class SourcePlatform(val id: String, val label: String, val domains: Set<String>, val icon: String = "globe")

/** One registry for platform identity, URL allowlist and presentation; metadata never supplies a channel here. */
object SourcePlatforms {
    val known = listOf(
        SourcePlatform("youtube_music", "YouTube Music", setOf("music.youtube.com"), "music"),
        SourcePlatform("youtube", "YouTube", setOf("youtube.com", "youtu.be"), "play"),
        SourcePlatform("tiktok", "TikTok", setOf("tiktok.com"), "music"),
        SourcePlatform("soundcloud", "SoundCloud", setOf("soundcloud.com"), "wave"),
        SourcePlatform("facebook", "Facebook", setOf("facebook.com", "fb.watch"), "facebook"),
        SourcePlatform("instagram", "Instagram", setOf("instagram.com"), "camera"),
        SourcePlatform("x", "X", setOf("x.com", "twitter.com"), "x"),
        SourcePlatform("vimeo", "Vimeo", setOf("vimeo.com"), "video"),
        SourcePlatform("dailymotion", "Dailymotion", setOf("dailymotion.com", "dai.ly"), "video"),
        SourcePlatform("twitch", "Twitch", setOf("twitch.tv"), "game"),
        SourcePlatform("reddit", "Reddit", setOf("reddit.com", "redd.it")),
        SourcePlatform("pinterest", "Pinterest", setOf("pinterest.com", "pin.it")),
        SourcePlatform("threads", "Threads", setOf("threads.net")),
        SourcePlatform("bandcamp", "Bandcamp", setOf("bandcamp.com"), "wave"),
        SourcePlatform("bilibili", "Bilibili", setOf("bilibili.com", "b23.tv"), "video"),
        SourcePlatform("likee", "Likee", setOf("likee.video")),
        SourcePlatform("snapchat", "Snapchat", setOf("snapchat.com")),
        // Spotify hanya sumber metadata: audio tetap diambil dari video YouTube hasil pencocokan.
        // Domains sengaja kosong supaya tautannya tidak pernah lolos allowlist engine (yt-dlp tanpa extractor Spotify).
        SourcePlatform(Spotify.PLATFORM_ID, "Spotify", emptySet(), "music"),
    )
    private val unknown = SourcePlatform("unknown", "Sumber tidak diketahui", emptySet())
    fun fromId(id: String) = known.firstOrNull { it.id == id } ?: unknown
    fun fromUrl(url: String): SourcePlatform {
        val host = runCatching { URI(url.trim()).host?.lowercase() }.getOrNull() ?: return unknown
        return known.firstOrNull { platform -> platform.domains.any { host == it || host.endsWith(".$it") } } ?: unknown
    }
}
