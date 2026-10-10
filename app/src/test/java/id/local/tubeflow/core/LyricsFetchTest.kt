package id.local.tubeflow.core

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Before
    fun start() {
        requests.clear()
        respond = { 404 to "" }
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.toString()
            synchronized(requests) { requests += path }
            val (code, body) = respond(path)
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
        Lyrics.baseUrl = "https://lrclib.net"
        server.stop(0)
    }

    private fun studioList() =
        """[{"trackName":"Song","artistName":"Artist","duration":200,"instrumental":false,"syncedLyrics":"[00:01.00]line"}]"""

    private fun studioRecord() =
        """{"trackName":"Song","artistName":"Artist","duration":200,"instrumental":false,"syncedLyrics":"[00:01.00]line"}"""

    @Test
    fun tidyFallbackNeverBuildsSecondPairForVersionedTitle() {
        // Varian "(Live)" tidak boleh dipotong menjadi "Song" — dulu membangun pair kedua dan menerima
        // rekaman studio dengan skor penuh (probe R3: LIVE_REQUEST_STUDIO_RESULT=tersimpan).
        respond = { path -> if (path.startsWith("/api/get")) 404 to "" else 200 to studioList() }
        val outcome = Lyrics.fetch("Artist - Song (Live)", 200)
        assertEquals("fallback tidy tak boleh menambah pasangan query", 1, requests.count { it.startsWith("/api/get") })
        assertEquals(LyricStatus.NOT_FOUND, outcome.status)
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
        assertEquals(LyricStatus.NETWORK, Lyrics.fetch("Artist - Song", 200).status)
    }

    @Test
    fun completeEmptySearchReportsNotFound() {
        // Semua jalur menjawab sukses tanpa kandidat = memang tidak ada lirik (bukan gangguan jaringan).
        respond = { path -> if (path.startsWith("/api/get")) 404 to "" else 200 to "[]" }
        assertEquals(LyricStatus.NOT_FOUND, Lyrics.fetch("Artist - Unknown Song", 200).status)
    }

    @Test
    fun allFailuresReportNetwork() {
        respond = { 503 to "" }
        assertEquals(LyricStatus.NETWORK, Lyrics.fetch("Artist - Song", 200).status)
    }

    @Test
    fun exactRequestCarriesDurationForProviderGate() {
        // LRCLIB mencocokkan durasi ±2 detik pada get — durasi valid harus ikut dikirim (BUG-013/R3).
        respond = { 404 to "" }
        Lyrics.fetch("Artist - Song", 222)
        val get = requests.first { it.startsWith("/api/get") }
        assertTrue(get, get.contains("duration=222"))
    }

    @Test
    fun exactRequestOmitsDurationWhenUnknown() {
        respond = { 404 to "" }
        Lyrics.fetch("Artist - Song", 0)
        val get = requests.first { it.startsWith("/api/get") }
        assertTrue(get, !get.contains("duration="))
    }

    @Test
    fun acceptedCandidateIsSaved() {
        respond = { path -> if (path.startsWith("/api/get")) 200 to studioRecord() else 404 to "" }
        val outcome = Lyrics.fetch("Artist - Song", 200)
        assertEquals(LyricStatus.SAVED, outcome.status)
        assertTrue(outcome.text.orEmpty().startsWith("[00:01.00]"))
    }
}
