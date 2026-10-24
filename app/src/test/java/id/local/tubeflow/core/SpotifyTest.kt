package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyTest {
    private val trackUrl = "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"
    private val cover = "https://image-cdn-fa.spotifycdn.com/image/ab67616d0000b273baf89eb11ec7c657805d2da0"

    private fun page(
        title: String = "Never Gonna Give You Up",
        artists: List<String> = listOf("Rick Astley"),
        durationMillis: Long = 213573,
        type: String = "track",
    ): String {
        val artistJson = artists.joinToString(",") { """{"name":"$it","uri":"spotify:artist:x"}""" }
        val json = """
            {"props":{"pageProps":{"state":{"data":{"entity":{
              "type":"$type","name":"$title","title":"$title",
              "id":"4cOdK2wGLETKBW3PvgPWqT","duration":$durationMillis,
              "artists":[$artistJson],
              "visualIdentity":{"image":[
                {"url":"$cover","maxHeight":640,"maxWidth":640},
                {"url":"https://small.example/64.jpg","maxHeight":64,"maxWidth":64}
              ]}
            }}}}}}
        """.trimIndent()
        return "<!DOCTYPE html><html><body><script id=\"__NEXT_DATA__\" type=\"application/json\">$json</script></body></html>"
    }

    private fun spotifyTrack(durationSeconds: Long = 214) = SpotifyTrack(
        id = "4cOdK2wGLETKBW3PvgPWqT",
        title = "Never Gonna Give You Up",
        artists = "Rick Astley",
        durationSeconds = durationSeconds,
        coverUrl = cover,
        url = trackUrl,
    )

    private fun video(
        title: String,
        duration: Long = 213,
        uploader: String = "Rick Astley",
        url: String = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    ) = VideoMetadata(title, uploader, duration, "", url)

    @Test
    fun acceptsTrackLinksAndRejectsOtherSpotifyPages() {
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", Spotify.trackId(trackUrl))
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", Spotify.trackId("$trackUrl?si=abc123&nd=1"))
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", Spotify.trackId("https://open.spotify.com/intl-id/track/4cOdK2wGLETKBW3PvgPWqT"))
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", Spotify.trackId("spotify:track:4cOdK2wGLETKBW3PvgPWqT"))
        assertTrue(Spotify.isTrackLink(" $trackUrl "))

        listOf(
            "https://open.spotify.com/album/4cOdK2wGLETKBW3PvgPWqT",
            "https://open.spotify.com/playlist/4cOdK2wGLETKBW3PvgPWqT",
            "https://open.spotify.com/artist/4cOdK2wGLETKBW3PvgPWqT",
            "https://open.spotify.com/track/short",
            "http://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
            "https://open.spotify.com.evil.org/track/4cOdK2wGLETKBW3PvgPWqT",
            "https://user@open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
            "https://open.spotify.com:8443/track/4cOdK2wGLETKBW3PvgPWqT",
            "https://example.com/track/4cOdK2wGLETKBW3PvgPWqT",
        ).forEach { assertNull(it, Spotify.trackId(it)) }
    }

    /** Tautan Spotify tidak boleh lolos allowlist engine — yt-dlp tidak punya extractor Spotify. */
    @Test
    fun spotifyLinksStayOutsideEngineAllowlist() {
        assertFalse(Selection.validUrl(trackUrl))
        assertNull(Selection.identity(trackUrl))
        assertEquals("Spotify", SourcePlatforms.fromId(Spotify.PLATFORM_ID).label)
    }

    @Test
    fun parsesPublicEmbedMetadata() {
        val track = Spotify.parse(page(), trackUrl)
        assertEquals("Never Gonna Give You Up", track?.title)
        assertEquals("Rick Astley", track?.artists)
        assertEquals(214L, track?.durationSeconds)
        // Gambar terbesar yang dipakai, bukan thumbnail 64 px.
        assertEquals(cover, track?.coverUrl)
        assertEquals(trackUrl, track?.url)
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", track?.id)
    }

    /** Fixture diambil dari halaman embed publik Spotify yang sebenarnya (Never Gonna Give You Up). */
    @Test
    fun parsesRealSpotifyEmbedMarkup() {
        val html = javaClass.getResource("/spotify-track-embed.html")!!.readText()
        val track = Spotify.parse(html, trackUrl)
        assertEquals("Never Gonna Give You Up", track?.title)
        assertEquals("Rick Astley", track?.artists)
        assertEquals(214L, track?.durationSeconds)
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", track?.id)
        assertTrue(track?.coverUrl.orEmpty().startsWith("https://image-cdn-fa.spotifycdn.com/image/"))
    }

    @Test
    fun joinsMultipleArtistsAndIgnoresNonTrackEntities() {
        assertEquals("Rick Astley & Foo & Bar", Spotify.parse(page(artists = listOf("Rick Astley", "Foo", "Bar")), trackUrl)?.artists)
        assertNull(Spotify.parse(page(type = "album"), trackUrl))
        assertNull(Spotify.parse("<html><body>tanpa data</body></html>", trackUrl))
        assertNull(Spotify.parse(page(title = "   "), trackUrl))
    }

    @Test
    fun metadataUsesSpotifyIdentityWithYoutubeSource() {
        val candidate = video("Rick Astley - Never Gonna Give You Up (Official Video)", duration = 213)
        val meta = Spotify.toMetadata(spotifyTrack(), candidate)

        assertEquals("Never Gonna Give You Up", meta.title)
        assertEquals("Rick Astley", meta.artist)
        assertEquals(cover, meta.thumbnail)
        assertEquals(candidate.url, meta.url)
        assertEquals("spotify", meta.platformId)
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", meta.sourceId)
        assertEquals(213L, meta.durationSeconds)

        // Job hasil antrean memakai identitas Spotify walau URL-nya YouTube.
        val job = DownloadJob("id", candidate.url, "Video", MediaMode.AUDIO, "mp3", 0, 0).withMetadata(meta)
        assertEquals("Never Gonna Give You Up", job.trackTitle)
        assertEquals("Rick Astley", job.artist)
        assertEquals("spotify", job.platformId)
        assertEquals("Spotify", job.sourcePlatform.label)

        // Durasi kandidat hilang → pakai durasi Spotify.
        val unknown = Spotify.toMetadata(spotifyTrack(), candidate.copy(durationSeconds = 0))
        assertEquals(214L, unknown.durationSeconds)
    }

    @Test
    fun searchQueryPutsArtistFirst() {
        assertEquals("Rick Astley Never Gonna Give You Up", Spotify.searchQuery(spotifyTrack()))
        assertEquals("Never Gonna Give You Up", Spotify.searchQuery(spotifyTrack().copy(artists = "")))
    }

    @Test
    fun ranksMatchingRecordingFirst() {
        val track = spotifyTrack()
        val correct = video("Rick Astley - Never Gonna Give You Up (Official Video)", duration = 213, url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        val karaoke = video("Never Gonna Give You Up (Karaoke Version)", duration = 213, uploader = "Karaoke Channel", url = "https://www.youtube.com/watch?v=kkkkkkkkkkk")
        val live = video("Rick Astley - Never Gonna Give You Up (Live in London)", duration = 260, url = "https://www.youtube.com/watch?v=lllllllllll")
        val unrelated = video("Rick Astley - Together Forever", duration = 200, url = "https://www.youtube.com/watch?v=uuuuuuuuuuu")

        val ranked = SpotifyMatch.rank(track, listOf(karaoke, unrelated, live, correct))

        assertEquals(correct.url, ranked.first().video.url)
        assertTrue(ranked.first().confident)
        assertEquals(100, ranked.first().score)
        assertEquals(100, ranked.first().titleCoverage)
        assertTrue(ranked.first().artistMatch)
        assertTrue(ranked.single { it.video.url == karaoke.url }.score < 70)
        assertFalse(ranked.single { it.video.url == karaoke.url }.confident)
        assertTrue(ranked.indexOfFirst { it.video.url == live.url } < ranked.indexOfFirst { it.video.url == unrelated.url })
    }

    @Test
    fun rankDeduplicatesAliasesAndSkipsUnknownDurations() {
        val track = spotifyTrack()
        val canonical = video("Rick Astley - Never Gonna Give You Up", duration = 213, url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        val alias = canonical.copy(url = "https://youtu.be/dQw4w9WgXcQ?si=share")
        val unknownDuration = video("Rick Astley - Never Gonna Give You Up (Audio)", duration = 0, url = "https://www.youtube.com/watch?v=aaaaaaaaaaa")

        val ranked = SpotifyMatch.rank(track, listOf(canonical, alias, unknownDuration))

        assertEquals(2, ranked.size)
        assertEquals(canonical.url, ranked.first().video.url)
        assertEquals(-1L, ranked.single { it.video.url == unknownDuration.url }.durationDeltaSeconds)
        // Durasi tidak diketahui tidak dihukum, tetapi juga tidak mendapat poin durasi.
        assertEquals(85, ranked.single { it.video.url == unknownDuration.url }.score)
    }

    @Test
    fun titleWordsIgnorePromotionalDecorations() {
        assertEquals(setOf("never", "gonna", "give", "you", "up"), SpotifyMatch.titleWords("Never Gonna Give You Up (Official Video) [4K]"))
        assertEquals("Rick Astley", SpotifyMatch.mainArtist("Rick Astley & Foo, Bar"))
        // Nama yang dimulai dengan huruf X tidak boleh terpotong.
        assertEquals("X Ambassadors", SpotifyMatch.mainArtist("X Ambassadors"))
    }

    @Test
    fun acceptsAlbumAndPlaylistLinksOnlyForCollections() {
        val album = "https://open.spotify.com/album/3T4tUhGYeRNVUGevb0wThu"
        val playlist = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"
        assertEquals(SpotifyKind.ALBUM, Spotify.ref(album)?.kind)
        assertEquals("3T4tUhGYeRNVUGevb0wThu", Spotify.ref(album)?.id)
        assertEquals(SpotifyKind.PLAYLIST, Spotify.ref("$playlist?si=share")?.kind)
        assertEquals(SpotifyKind.PLAYLIST, Spotify.ref("https://open.spotify.com/intl-id/playlist/37i9dQZF1DXcBWIGoYBM5M")?.kind)
        assertEquals(SpotifyKind.ALBUM, Spotify.ref("spotify:album:3T4tUhGYeRNVUGevb0wThu")?.kind)
        assertTrue(Spotify.isCollectionLink(album))
        assertTrue(Spotify.isCollectionLink(playlist))
        assertFalse(Spotify.isCollectionLink(trackUrl))
        assertNull(Spotify.trackId(album))
        assertNull(Spotify.trackId(playlist))
        // Album/playlist tidak boleh lolos allowlist engine — hanya track yang punya alur yt-dlp.
        assertFalse(Selection.validUrl(album))
        assertFalse(Selection.validUrl(playlist))

        listOf(
            "https://open.spotify.com/artist/3T4tUhGYeRNVUGevb0wThu",
            "https://open.spotify.com/episode/3T4tUhGYeRNVUGevb0wThu",
            "https://open.spotify.com/album/short",
            "http://open.spotify.com/album/3T4tUhGYeRNVUGevb0wThu",
            "https://evil.example/album/3T4tUhGYeRNVUGevb0wThu",
        ).forEach {
            assertNull(it, Spotify.ref(it))
            assertFalse(it, Spotify.isCollectionLink(it))
        }
    }

    /** Fixture dari halaman embed album Spotify sebenarnya (÷ Deluxe, 3 lagu pertama). */
    @Test
    fun parsesRealAlbumEmbed() {
        val url = "https://open.spotify.com/album/3T4tUhGYeRNVUGevb0wThu"
        val html = javaClass.getResource("/spotify-album-embed.html")!!.readText()
        val collection = Spotify.parseCollection(html, url)

        assertEquals(SpotifyKind.ALBUM, collection?.kind)
        assertEquals("÷ (Deluxe)", collection?.title)
        assertEquals("Ed Sheeran", collection?.owner)
        assertEquals(3, collection?.tracks?.size)
        assertEquals(3, collection?.playableCount)
        assertEquals("Eraser", collection?.tracks?.first()?.title)
        assertEquals("Ed Sheeran", collection?.tracks?.first()?.artists)
        assertEquals(227L, collection?.tracks?.first()?.durationSeconds)
        assertEquals("7oolFzHipTMg2nL7shhdz2", collection?.tracks?.first()?.id)
        assertTrue(collection?.tracks?.first()?.playable == true)
        assertTrue(collection?.coverUrl.orEmpty().startsWith("https://image-cdn"))
        assertEquals(227L + 261L + 238L, collection?.totalSeconds)
    }

    /** Fixture dari halaman embed playlist Spotify sebenarnya (Today’s Top Hits, 3 lagu pertama). */
    @Test
    fun parsesRealPlaylistEmbedWithPlaylistCover() {
        val url = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"
        val html = javaClass.getResource("/spotify-playlist-embed.html")!!.readText()
        val collection = Spotify.parseCollection(html, url)

        assertEquals(SpotifyKind.PLAYLIST, collection?.kind)
        assertEquals("Today’s Top Hits", collection?.title)
        assertEquals("Spotify", collection?.owner)
        assertEquals(3, collection?.tracks?.size)
        assertEquals("Housewife", collection?.tracks?.first()?.title)
        assertEquals("Tate McRae", collection?.tracks?.first()?.artists)
        assertEquals(209L, collection?.tracks?.first()?.durationSeconds)
        // Playlist memakai coverArt.sources, bukan visualIdentity.
        assertTrue(collection?.coverUrl.orEmpty().startsWith("https://i.scdn.co/image/"))
    }

    @Test
    fun collectionSkipsEpisodesButKeepsUnplayableTracks() {
        val json = """{"props":{"pageProps":{"state":{"data":{"entity":{
          "type":"album","id":"3T4tUhGYeRNVUGevb0wThu","title":"Album","subtitle":"Artis",
          "trackList":[
            {"uri":"spotify:track:4cOdK2wGLETKBW3PvgPWqT","title":"Lagu A","subtitle":"Artis","duration":200000,"entityType":"track"},
            {"uri":"spotify:episode:4cOdK2wGLETKBW3PvgPWqT","title":"Episode","subtitle":"Podcast","duration":100000,"entityType":"episode"},
            {"uri":"spotify:track:5cOdK2wGLETKBW3PvgPWqT","title":"Lagu B","subtitle":"Artis","duration":300000,"entityType":"track","isPlayable":false}
          ],"visualIdentity":{"image":[{"url":"https://cover.example/a.jpg","maxWidth":640}]}
        }}}}}}"""
        val collection = Spotify.parseCollection("<script id=\"__NEXT_DATA__\" type=\"application/json\">$json</script>", "https://open.spotify.com/album/3T4tUhGYeRNVUGevb0wThu")
        assertEquals(2, collection?.tracks?.size)
        assertEquals("Lagu A", collection?.tracks?.first()?.title)
        assertEquals("Lagu B", collection?.tracks?.last()?.title)
        assertFalse(collection?.tracks?.last()?.playable == true)
        assertEquals(1, collection?.playableCount)
    }

    @Test
    fun collectionTrackUsesCollectionCoverAndSpotifyTags() {
        val url = "https://open.spotify.com/album/3T4tUhGYeRNVUGevb0wThu"
        val collection = Spotify.parseCollection(javaClass.getResource("/spotify-album-embed.html")!!.readText(), url)!!
        val track = collection.tracks.first()
        val candidate = video("Ed Sheeran - Eraser (Official Audio)", duration = 227, uploader = "Ed Sheeran", url = "https://www.youtube.com/watch?v=eeeeeeeeeee")
        val meta = Spotify.toMetadata(track, candidate)

        assertEquals("Eraser", meta.title)
        assertEquals("Ed Sheeran", meta.artist)
        assertEquals(collection.coverUrl, meta.thumbnail)
        assertEquals("spotify", meta.platformId)
        assertEquals("7oolFzHipTMg2nL7shhdz2", meta.sourceId)
        assertEquals(candidate.url, meta.url)
        assertEquals("Ed Sheeran Eraser", Spotify.searchQuery(track))
    }

    @Test
    fun artworkAndLimitsAreSafeForUI() {
        assertEquals("", Spotify.toMetadata(spotifyTrack().copy(coverUrl = ""), video("Song", duration = 214, url = "https://www.youtube.com/watch?v=bbbbbbbbbbb")).thumbnail)
        val many = (0 until 10).map { video("Song $it", duration = 213, url = "https://www.youtube.com/watch?v=${"cccccccccc" + it}") }
        assertEquals(5, SpotifyMatch.rank(spotifyTrack(), many).size)
    }
}
