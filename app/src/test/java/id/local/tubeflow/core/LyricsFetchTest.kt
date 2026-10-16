package id.local.tubeflow.core

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/**
 * Fixture HTTP lokal untuk [Lyrics.fetch] (BUG-013/R3 dan BUG-014/R4).
 * Audit R3/R4 menuntut tes yang memanggil pencarian dengan respons berfixture — bukan hanya label status —
 * sehingga klasifikasi dan bypass fallback diuji pada jalur kode sesungguhnya.
 */
class LyricsFetchTest {
    private lateinit var server: HttpServer
    private val requests = mutableListOf<String>()

    /** Kode HTTP + badan per jalur; diatur ulang oleh tiap tes. */
    private var respond: (path: String) -> Pair<Int, String> = { 404 to "" }
    private var retryAfter: String? = null

    @Before
    fun start() {
        Lyrics.requestGate = LyricsRequestGate(minimumGapMs=0)
        Lyrics.responseCache = null
        requests.clear()
        respond = { 404 to "" }
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.toString()
            synchronized(requests) { requests += path }
            val (code, body) = respond(path)
            retryAfter?.let { exchange.responseHeaders.set("Retry-After",it) }
            val bytes = body.toByteArray(Charsets.UTF_8)
            if (bytes.isEmpty()) exchange.sendResponseHeaders(code, -1)
            else {
                exchange.sendResponseHeaders(code, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
        Lyrics.baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun stop() {
        Lyrics.responseCache = null
        Lyrics.baseUrl = "https://lrclib.net"
        server.stop(0)
    }

    private fun fetchKnown(display: String, duration: Long) = Lyrics.fetch(
        TitleSplit.identify(display, metadataArtist=display.substringAfter(" - ")), duration)

    private fun studioList() =
        """[{"trackName":"Song","artistName":"Artist","duration":200,"instrumental":false,"syncedLyrics":"[00:01.00]line"}]"""

    private fun studioRecord() =
        """{"trackName":"Song","artistName":"Artist","duration":200,"instrumental":false,"syncedLyrics":"[00:01.00]line"}"""

    @Test fun automaticSearchFindsLyricsWithoutCallingRateLimitedExactLookup() {
        respond = { path -> if (path == "/api/search?track_name=Song&artist_name=Artist")
            200 to studioList().replace("{", "{\"id\":99,") else 429 to "" }
        val automatic = fetchKnown("Song - Artist", 200)
        assertEquals(LyricStatus.SAVED, automatic.status)
        assertEquals(1, requests.size)
        assertTrue(requests.single().startsWith("/api/search"))
        val manual = Lyrics.search("Song", "Artist")
        assertEquals(automatic.text.orEmpty().trim(), manual.choices.single().text.orEmpty().trim())
    }

    @Test fun automaticLookupCanUseManualSearchCacheWhileAnotherTrackIsLimited() {
        val directory = java.nio.file.Files.createTempDirectory("lyrics-auto-cache").toFile()
        try {
            Lyrics.responseCache = LyricsResponseCache(directory)
            respond = { 200 to studioList().replace("{", "{\"id\":99,") }
            assertEquals(1,Lyrics.search("Song","Artist").choices.size)
            respond = { 429 to "" }
            assertEquals(LyricStatus.RATE_LIMIT,Lyrics.search("Other","Artist").failureStatus)
            assertEquals(LyricStatus.SAVED,fetchKnown("Song - Artist",200).status)
            assertEquals(2,requests.size) // Cached data is usable; no request bypasses the gate.
        } finally { Lyrics.responseCache = null; directory.deleteRecursively() }
    }

    @Test fun emptySearchStillAllowsExactFallbackWithDurationAndUnknownAlbum() {
        respond = { path -> if (path.startsWith("/api/get?")) 200 to studioRecord() else 200 to "[]" }
        assertEquals(LyricStatus.SAVED, fetchKnown("Song - Artist",200).status)
        assertTrue(requests.first().startsWith("/api/search"))
        val exact = requests.first { it.startsWith("/api/get?") }
        assertTrue(exact, exact.contains("duration=200") && exact.contains("album_name="))
    }

    @Test fun displayVersionAndCollaborationDoNotForceAnUnnecessaryRawSearchBeforeUsefulResults() {
        respond={ path -> if(path=="/api/search?track_name=Song&artist_name=Denny+Caknan") 200 to
            studioList().replace("{","{\"id\":99,").replace("Artist","Denny Caknan") else 429 to "" }
        val result=Lyrics.search("Song | Live","Denny Caknan FT Happy Asmara")
        assertEquals(1,result.choices.size)
        assertEquals(null,result.failureStatus)
        assertEquals(1,requests.size)
        assertTrue(!result.choices.single().automaticMatch(LyricMatch.Query("Song | Live","Denny Caknan FT Happy Asmara",200)))
    }

    @Test fun usefulManualResultsAvoidUnneededVariantsButCanExpandExplicitly() {
        respond={ 200 to studioList().replace("{","{\"id\":99,") }
        assertTrue(Lyrics.search("Song","Artist").canExpand)
        assertEquals(1,requests.size)
        requests.clear()
        assertTrue(!Lyrics.search("Song","Artist",expand=true).canExpand)
        assertTrue(requests.size > 1)
    }

    @Test fun expiredProviderCooldownActuallyRetriesAndCanFindLyrics() {
        var now=1000L
        Lyrics.requestGate=LyricsRequestGate(minimumGapMs=0,clock={ now })
        retryAfter="600"
        respond={ 429 to "" }
        assertEquals(LyricStatus.RATE_LIMIT,Lyrics.search("Song","Artist").failureStatus)
        assertEquals(600L,Lyrics.retryAfterSeconds())
        Lyrics.clearCache()
        assertEquals(LyricStatus.RATE_LIMIT,Lyrics.search("Song","Artist").failureStatus)
        assertEquals(1,requests.size)
        now += 600_000
        respond={ 200 to studioList().replace("{","{\"id\":99,") }
        assertEquals(1,Lyrics.search("Song","Artist").choices.size)
        assertEquals(2,requests.size)
        assertEquals(0L,Lyrics.retryAfterSeconds())
    }

    @Test fun cachedLyricsRemainUsableWhileAnotherTrackIsLimited() {
        val dir=java.nio.file.Files.createTempDirectory("lyrics-cached-limit").toFile()
        try {
            Lyrics.responseCache=LyricsResponseCache(dir)
            respond={ 200 to studioList().replace("{","{\"id\":99,") }
            assertEquals(1,Lyrics.search("Song","Artist").choices.size)
            respond={ 429 to "" }
            assertEquals(LyricStatus.RATE_LIMIT,Lyrics.search("Other","Artist").failureStatus)
            assertEquals(1,Lyrics.search("Song","Artist").choices.size)
            assertEquals(2,requests.size)
        } finally { Lyrics.responseCache=null; dir.deleteRecursively() }
    }

    @Test
    fun tidyFallbackNeverBuildsSecondPairForVersionedTitle() {
        // Varian "(Live)" tidak boleh dipotong menjadi "Song" — dulu membangun pair kedua dan menerima
        // rekaman studio dengan skor penuh (probe R3: LIVE_REQUEST_STUDIO_RESULT=tersimpan).
        respond = { path -> if (path.startsWith("/api/get")) 404 to "" else 200 to studioList() }
        val outcome = fetchKnown("Song (Live) - Artist", 200)
        assertTrue("lookup harus mencoba variasi tanpa melonggarkan versi", requests.any { it.startsWith("/api/search?track_name=Song&") })
        assertEquals(LyricStatus.NO_MATCH, outcome.status)
    }

    @Test fun collaborationLookupUsesLeadArtistButKeepsFullValidationGate() {
        respond = { path ->
            if (path.contains("artist_name=Denny+Caknan") && !path.contains("Happy")) {
                val record="""{"trackName":"Song","artistName":"Denny Caknan","duration":200,"syncedLyrics":"[00:01.00]line"}"""
                200 to if (path.startsWith("/api/get")) record else "[$record]"
            } else if (path.startsWith("/api/get")) 404 to "" else 200 to "[]"
        }
        assertEquals(LyricStatus.SAVED,Lyrics.fetch(SongIdentity("Song","Denny Caknan x Happy Asmara"),200).status)
        assertTrue(requests.any { it.contains("artist_name=Denny+Caknan") && !it.contains("Happy") })
        Lyrics.clearCache()
        assertEquals(LyricStatus.NO_MATCH,Lyrics.fetch(SongIdentity("Song","Denny Caknan x Happy Asmara"),220).status)
    }

    @Test
    fun nameParsesAsTitleFirstArtistSecond() {
        // Nama konsisten "Judul - Artis" (judul dulu): track = segmen pertama, artis = sisanya.
        respond = { path -> if (path.startsWith("/api/get")) 404 to "" else 200 to "[]" }
        fetchKnown("LAMUNAN - Sasya Arkhisna", 200)
        val get = requests.first { it.startsWith("/api/get") }
        assertTrue(get, get.contains("track_name=LAMUNAN"))
        assertTrue(get, get.contains("artist_name=Sasya+Arkhisna"))
    }

    @Test
    fun partialSearchFailureReportsNetworkNotNotFound() {
        // Probe R4: get 404 + search by-field 200 kosong + free search 503 — dulu dilaporkan "tidak
        // ditemukan" karena satu respons 200 menyalakan penanda respons. Sekarang = jaringan (bisa dicoba lagi).
        respond = { path ->
            when {
                path.startsWith("/api/get") -> 404 to ""
                path.contains("track_name=") -> 200 to "[]"
                else -> 503 to ""
            }
        }
        assertEquals(LyricStatus.SERVICE, fetchKnown("Song - Artist", 200).status)
    }

    @Test
    fun completeEmptySearchReportsNotFound() {
        // Semua jalur menjawab sukses tanpa kandidat = memang tidak ada lirik (bukan gangguan jaringan).
        respond = { path -> if (path.startsWith("/api/get")) 404 to "" else 200 to "[]" }
        assertEquals(LyricStatus.NOT_FOUND, fetchKnown("Unknown Song - Artist", 200).status)
    }

    @Test
    fun allFailuresReportNetwork() {
        respond = { 503 to "" }
        assertEquals(LyricStatus.SERVICE, fetchKnown("Song - Artist", 200).status)
    }

    @Test
    fun providerRejectionIsNotFoundNotNetwork() {
        // 400 = jawaban tegas provider atas query itu sendiri — bukan gangguan jaringan.
        respond = { 400 to "" }
        assertEquals(LyricStatus.NOT_FOUND, fetchKnown("Song - Artist", 200).status)
    }

    @Test
    fun rateLimitReportsProviderLimitAndStopsFurtherRequests() {
        // 429 = kelebihan permintaan — bisa dicoba lagi nanti, jangan dibilang "tidak ada".
        respond = { 429 to "" }
        assertEquals(LyricStatus.RATE_LIMIT, fetchKnown("Song - Artist", 200).status)
        assertEquals(1, requests.size)
    }

    @Test
    fun exactRequestCarriesDurationForProviderGate() {
        // LRCLIB mencocokkan durasi ±2 detik pada get — durasi valid harus ikut dikirim (BUG-013/R3).
        respond = { 404 to "" }
        fetchKnown("Song - Artist", 222)
        val get = requests.first { it.startsWith("/api/get") }
        assertTrue(get, get.contains("duration=222"))
    }

    @Test
    fun exactRequestOmitsDurationWhenUnknown() {
        respond = { 404 to "" }
        fetchKnown("Song - Artist", 0)
        assertTrue(requests.none { it.startsWith("/api/get") })
        assertTrue(requests.any { it.startsWith("/api/search") })
    }

    @Test
    fun acceptedCandidateIsSaved() {
        respond = { path -> if (path.startsWith("/api/get")) 200 to studioRecord() else 404 to "" }
        val outcome = fetchKnown("Song - Artist", 200)
        assertEquals(LyricStatus.SAVED, outcome.status)
        assertTrue(outcome.text.orEmpty().startsWith("[00:01.00]"))
    }

    @Test
    fun equalIdentityScoresPreferClosestDurationRegardlessOfProviderOrder() {
        val farther = """{"trackName":"Song","artistName":"Artist","duration":202,"syncedLyrics":"[00:01.00]farther"}"""
        val closer = """{"trackName":"Song","artistName":"Artist","duration":200,"syncedLyrics":"[00:01.00]closer"}"""
        for (records in listOf("[$farther,$closer]", "[$closer,$farther]")) {
            respond = { path -> if (path.startsWith("/api/get")) 404 to "" else 200 to records }
            val outcome = fetchKnown("Song - Artist", 200)
            assertEquals(LyricStatus.SAVED, outcome.status)
            assertEquals("[00:01.00]closer\n", outcome.text)
        }
    }

    @Test
    fun closestDurationNeverOverridesStrongerIdentityOrVersionGate() {
        respond = { path ->
            if (path.startsWith("/api/get")) 404 to "" else 200 to """[
                {"trackName":"Song Extra","artistName":"Artist","duration":200,"syncedLyrics":"[00:01.00]weak"},
                {"trackName":"Song (Live)","artistName":"Artist","duration":200,"syncedLyrics":"[00:01.00]live"},
                {"trackName":"Song","artistName":"Artist","duration":202,"syncedLyrics":"[00:01.00]strong"}
            ]"""
        }
        assertEquals("[00:01.00]strong\n", fetchKnown("Song - Artist", 200).text)
    }

    @Test
    fun exactAndSearchRejectTenSecondOffset() {
        for (exact in listOf(true, false)) {
            respond = { path ->
                if (path.startsWith("/api/get")) (if (exact) 200 to studioRecord() else 404 to "")
                else 200 to studioList()
            }
            assertEquals(LyricStatus.NO_MATCH, fetchKnown("Song - Artist", 210).status)
        }
    }

    @Test
    fun unknownRecordingOrProviderDurationNeverSavesTimedLyrics() {
        respond = { path -> if (path.startsWith("/api/get")) 200 to studioRecord() else 200 to studioList() }
        assertEquals(LyricStatus.NO_MATCH, fetchKnown("Song - Artist", 0).status)
        respond = { path ->
            if (path.startsWith("/api/get")) 200 to studioRecord().replace("200", "0")
            else 200 to studioList().replace("200", "0")
        }
        assertEquals(LyricStatus.NO_MATCH, fetchKnown("Song - Artist", 200).status)
    }

    @Test
    fun timestampsBeyondRecordingAndMalformedSecondsAreRejected() {
        for (line in listOf("[03:30.00]too late", "[00:60.00]invalid", "[ar:Artist]")) {
            val record = org.json.JSONObject(studioRecord()).put("syncedLyrics", line).toString()
            respond = { path -> if (path.startsWith("/api/get")) 200 to record else 200 to "[$record]" }
            assertEquals(line, LyricStatus.NO_MATCH, fetchKnown("Song - Artist", 200).status)
        }
    }

    @Test
    fun offsetAndMultipleTimestampsArePreserved() {
        val lyrics = "[ar:Artist]\n[offset:250]\n[00:01.00][00:02.000]line\n[00:03.00]"
        val record = org.json.JSONObject(studioRecord()).put("syncedLyrics", lyrics).toString()
        respond = { 200 to record }
        val outcome = fetchKnown("Song - Artist", 200)
        assertEquals(LyricStatus.SAVED, outcome.status)
        assertEquals("[ar:Artist]\n[offset:250]\n[00:01.00][00:02.000]line\n[00:03.00]\n", outcome.text)
    }
    @Test fun titleOnlyFallbackStillValidatesKnownArtistAndVersion() {
        respond = { path ->
            if (path.startsWith("/api/search?track_name=Song&")) 404 to ""
            else if (path == "/api/search?track_name=Song") 200 to studioList()
            else 404 to ""
        }
        val result = Lyrics.fetch(SongIdentity("Song", "Artist", evidence="metadata_artist"), 200)
        assertEquals(LyricStatus.SAVED, result.status)
        assertTrue(requests.any { it.contains("track_name=Song") })
    }
    @Test fun creatorIsNeverSentAsArtistAndUnknownDashStaysIntact() {
        respond = { 404 to "" }
        Lyrics.fetch(SongIdentity("Song", null, creator="Uploader"), 200)
        assertTrue(requests.none { it.contains("Uploader") })
        requests.clear()
        Lyrics.fetch("Left - Right", 200)
        assertTrue(requests.none { it.contains("artist_name=") })
        assertTrue(requests.any { it.contains("track_name=Left+-+Right") })
    }

    @Test fun providerResolvesEitherOrderOnlyWithUniqueTitleArtistAndDurationMatch() {
        respond = { path -> if (path.contains("track_name=Song&artist_name=Artist")) 200 to studioList() else 200 to "[]" }
        for (raw in listOf("Artist - Song", "Song - Artist")) {
            val identity=Lyrics.resolveIdentity(TitleSplit.identify(raw, "Record Label"),200)
            assertEquals("Song - Artist",TitleSplit.display(identity))
            assertEquals("provider_identity",identity.evidence)
        }
        assertEquals(null,Lyrics.resolveIdentity(TitleSplit.identify("Artist - Song"),250).artist)
    }
    @Test fun providerAmbiguityAndStudioAgainstRemixNeverSupplyArtist() {
        respond = { 200 to """[{"trackName":"Song","artistName":"A","duration":200},{"trackName":"Song","artistName":"B","duration":200}]""" }
        assertEquals(null,Lyrics.resolveIdentity(SongIdentity("Song",null),200).artist)
        respond = { 200 to studioList() }
        assertEquals(null,Lyrics.resolveIdentity(SongIdentity("Song",null,"Remix"),200).artist)
    }

    @Test fun manualSearchExposesDifferentRecordingWithoutWeakeningAutomaticGate() {
        respond={ path -> if (path.startsWith("/api/search")) 200 to
            """[{"id":12,"trackName":"Song","artistName":"Other Artist","albumName":"Studio","duration":180,"syncedLyrics":"[00:01.00]fixture"}]""" else 404 to "" }
        val results=Lyrics.search("Song (Live)","Artist")
        assertEquals(1,results.choices.size)
        val choice=results.choices.single()
        assertTrue(!choice.automaticMatch(LyricMatch.Query("Song (Live)","Artist",200)))
        assertEquals("[re:TubeFlow / LRCLIB #12]\n[00:01.00]fixture",Lyrics.selectedText(choice,200000))
        assertEquals(LyricStatus.NO_MATCH,Lyrics.fetch(SongIdentity("Song", "Artist", "Live"),200).status)
    }
    @Test fun manualSearchReportsPartialFailureAndDisablesMalformedTimedText() {
        respond={ path -> if (path.contains("track_name=")) 200 to
            """[{"id":13,"trackName":"Song","artistName":"Artist","duration":200,"syncedLyrics":"[00:90.00]bad"},{"id":14,"trackName":"Song","artistName":"Artist","duration":200,"syncedLyrics":"[00:01.00]fixture"}]""" else 503 to "" }
        val results=Lyrics.search("Song","Artist",expand=true)
        assertTrue(results.incomplete)
        assertTrue(!results.choices.first().hasSyncedLyrics)
        assertTrue(results.choices.last().hasSyncedLyrics)
    }
    @Test(expected=IllegalArgumentException::class) fun selectedLyricsCannotRunPastAudioEnd() {
        Lyrics.selectedText(Lyrics.Choice(1,"Song","Artist","",300,"[04:00.00]fixture"),200000)
    }

    @Test fun manualFallbackCanExposeStudioChoiceForLiveCollaborationWithoutAutoAccepting() {
        respond={ path -> if (path == "/api/search?track_name=Song&artist_name=Denny+Caknan") 200 to
            """[{"id":25,"trackName":"Song","artistName":"Denny Caknan","duration":200,"syncedLyrics":"[00:01.00]fixture"}]""" else 200 to "[]" }
        val result=Lyrics.search("Song | Live","Denny Caknan FT Happy Asmara")
        assertEquals(1,result.choices.size)
        assertTrue(!result.choices.single().automaticMatch(LyricMatch.Query("Song | Live","Denny Caknan FT Happy Asmara",200)))
        assertTrue(requests.size <= 4)
    }

    @Test fun nullableProviderMetadataDoesNotBecomeFictionalArtistOrAlbum() {
        respond={ 200 to """[{"id":26,"trackName":"Song","artistName":null,"albumName":"null","duration":200,"syncedLyrics":"[00:01.00]fixture"}]""" }
        val result=Lyrics.search("Song","").choices.single()
        assertEquals("",result.artist)
        assertEquals("",result.album)
        assertTrue(result.hasSyncedLyrics)
    }


    @Test fun automaticAndManualDiscoveryUseTheSameSimplifiedFallback() {
        respond={ path -> if (path == "/api/search?track_name=Song") 200 to studioList().replace("{", "{\"id\":99,")
            else if(path.startsWith("/api/get")) 404 to "" else 200 to "[]" }
        assertEquals(LyricStatus.SAVED,Lyrics.fetch(SongIdentity("Song (Official Audio)","Artist"),200).status)
        assertEquals(1,Lyrics.search("Song (Official Audio)","Artist").choices.size)
        assertTrue(requests.count { it.startsWith("/api/get") } <= 1)
    }
    @Test fun providerLimitIsSharedWithManualSearchAndNeverBlamesConnectivity() {
        respond={ 429 to "" }
        val automatic=fetchKnown("Song - Artist",200)
        assertEquals(LyricStatus.RATE_LIMIT,automatic.status)
        assertEquals(LyricStatus.RATE_LIMIT,Lyrics.search("Song","Artist").failureStatus)
        assertEquals(1,requests.size)
    }
    @Test fun malformedJsonIsNotReportedAsMissingLyricsOrNetwork() {
        respond={ 200 to "<html>upstream proxy error</html>" }
        assertEquals(LyricStatus.INVALID_RESPONSE,fetchKnown("Song - Artist",200).status)
        assertEquals(LyricStatus.INVALID_RESPONSE,Lyrics.search("Song","Artist").failureStatus)
    }
    @Test fun lookupIsBoundedAndNoArtistOrDurationNeverUsesExactEndpoint() {
        respond={ path -> if(path.startsWith("/api/get")) 404 to "" else 200 to "[]" }
        Lyrics.fetch(SongIdentity("Song","A x B","Live"),200)
        assertTrue(requests.size <= 6)
        requests.clear()
        Lyrics.fetch(SongIdentity("Song",null),0)
        assertTrue(requests.none { it.startsWith("/api/get") })
        assertTrue(requests.size <= 4)
    }
    @Test fun emptyTimedLinesArePreservedToClearPreviousLyricsDuringSilence() {
        val record=org.json.JSONObject(studioRecord()).put("syncedLyrics","[00:01.00]line\n[00:02.00]\n[00:04.00]next").toString()
        respond={ 200 to record }
        val result=fetchKnown("Song - Artist",200)
        assertEquals("",LrcLines.parse(result.text!!)[1].text)
        assertEquals(1,LrcLines.activeIndex(LrcLines.parse(result.text),3000))
    }
    @Test fun positiveNativeOffsetBeyondTheRecordingIsRejectedInBothPaths() {
        val text="[offset:5000]\n[03:19.00]line"
        val record=org.json.JSONObject(studioRecord()).put("syncedLyrics",text).toString()
        respond={ path -> 200 to if(path.startsWith("/api/get")) record else "[$record]" }
        assertEquals(LyricStatus.NO_MATCH,fetchKnown("Song - Artist",200).status)
        assertThrows(IllegalArgumentException::class.java) {
            Lyrics.selectedText(Lyrics.Choice(1,"Song","Artist","",200,text),200000)
        }
    }

    @Test fun actualConnectionFailureRetainsConnectivityStatus() {
        Lyrics.baseUrl="http://127.0.0.1:1"
        assertEquals(LyricStatus.NETWORK,fetchKnown("Song - Artist",200).status)
    }

    @Test fun unknownArtistCannotAutoChooseBetweenDifferentAcceptedPerformers() {
        val a=org.json.JSONObject(studioRecord()).put("artistName","A").toString()
        val b=org.json.JSONObject(studioRecord()).put("artistName","B").toString()
        respond={ 200 to "[$a,$b]" }
        assertEquals(LyricStatus.NO_MATCH,Lyrics.fetch(SongIdentity("Song",null),200).status)
    }
}
