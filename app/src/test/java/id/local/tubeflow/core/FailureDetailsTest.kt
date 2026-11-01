package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class FailureDetailsTest {
    private fun report(message: String) = FailureDetails.capture(message,"TRANSFERRING","test","pinned",1234)
    @Test fun categoriesUseEvidenceAndDoNotInferHttpFromVideoIds() {
        assertEquals("rate_limit",report("HTTP Error 429: retry later").category)
        assertEquals(403,report("HTTP Error 403: Forbidden").http)
        assertEquals("provider",report("status code: 503").category)
        assertEquals("network",report("SocketTimeoutException: timed out").category)
        assertEquals("storage",report("ENOSPC no space left on device").category)
        assertEquals("permission",report("SecurityException: no grant").category)
        assertNull(report("video id: 403").http)
    }
    @Test fun dnsResolutionFailureIsNotReportedAsConnectionLoss() {
        val dns = report("YoutubeDLException: [Errno 7] No address associated with hostname")
        assertEquals("dns", dns.category)
        assertTrue(dns.userMessage.contains("DNS"))
        assertEquals("dns", report("Name or service not known").category)
        assertEquals("network", report("connection reset by peer").category)
    }
    @Test fun tokensHeadersUrlsAndPrivatePathsAreRedacted() {
        val raw = "HTTP Error 403 https://user:PASS@example.com/file?secret_token=SECRET\n"+
            "Authorization: Bearer TOPSECRET\nCookie: sid=COOKIE\n"+
            "token=TOKEN signature='SIGN' api_key=API access_token=ACCESS client_secret=CLIENT po_token=POKEY refresh_token=REFRESH client_id=CLIENTID\n"+
            "content://media/private file:///storage/emulated/0/private /data/user/0/private /home/zaki/private"
        val safe = report(raw).text()
        for (secret in listOf("PASS","SECRET","TOPSECRET","COOKIE","TOKEN","SIGN","API","ACCESS","CLIENT","POKEY","REFRESH","CLIENTID","example.com","/data/user","/home/zaki","content://","file://")) {
            assertFalse(secret,safe.contains(secret))
        }
        assertTrue(safe.contains("HTTP: 403"))
    }
    @Test fun lyricCooldownIsSeparateFromDownloadFailureAndDoesNotInventHttp() {
        val details = FailureDetails.lyrics(LyricStatus.RATE_LIMIT)
        assertEquals("LYRICS",details.phase)
        assertEquals("rate_limit",details.category)
        assertEquals(LyricStatus.RATE_LIMIT,details.status)
        assertNull(details.http); assertNull(details.at)
        assertEquals(details,FailureDetails.fromJson(details.json().toString()))
        assertTrue(FailureDetails.isLyricFailure(LyricStatus.SAVE_FAILED))
        assertFalse(FailureDetails.isLyricFailure(LyricStatus.NO_MATCH))
        assertFalse(FailureDetails.isLyricFailure(LyricStatus.SAVED))
    }
    @Test fun persistedReportKeepsActualFailureTimeAndLegacyTimeRemainsUnknown() {
        val before = report("HTTP Error 503")
        assertEquals(before,FailureDetails.fromJson(before.json().toString()))
        val legacy = FailureDetails.capture("offline","SAVING","legacy","unknown")
        assertNull(legacy.at)
        assertTrue(legacy.text().contains("Waktu: Tidak tercatat"))
        assertTrue(FailureDetails.safe("x".repeat(5000)).length <= 2000)
    }
    @Test fun emptySeasonCatalogGetsItsOwnMessage() {
        val empty = report(BstationUrls.SEASON_EMPTY)
        assertEquals("empty", empty.category)
        assertTrue(empty.userMessage.contains("episode"))
    }
    @Test fun removedPlaylistVideoGetsUnavailableCategory() {
        val removed = report("YoutubeDLException: ERROR: [youtube] SIXBqhLD0js: Video unavailable. This video is not available")
        assertEquals("unavailable", removed.category)
        assertTrue(removed.userMessage.contains("tidak tersedia"))
        assertEquals("unavailable", report("ERROR: [youtube] x: Private video. Sign in if you have permission").category)
        assertEquals("unavailable", report("ERROR: [youtube] x: [Deleted video]").category)
    }
    @Test fun geoAndPremiumRestrictionsGetActionableMessages() {
        val geo = report("ERROR: [BiliIntl] This video is not available from your location due to geo restriction")
        assertEquals("geo", geo.category)
        assertTrue(geo.userMessage.contains("wilayah"))
        val premium = report("ERROR: [BiliIntl] This video is only available for registered users")
        assertEquals("premium", premium.category)
        assertTrue(premium.userMessage.contains("login") || premium.userMessage.contains("premium"))
    }
    @Test fun lyricHttpEvidenceSurvivesPersistenceAndSeparatesAccessFromServerErrors() {
        val denied=FailureDetails.lyrics(LyricStatus.SERVICE,httpCode=403)
        assertEquals("access",denied.category)
        assertEquals(403,denied.http)
        assertEquals(denied,FailureDetails.fromJson(denied.json().toString()))
        assertEquals("provider",FailureDetails.lyrics(LyricStatus.SERVICE,httpCode=503).category)
        assertNull(FailureDetails.lyrics(LyricStatus.RATE_LIMIT).http)
    }
}
