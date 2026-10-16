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
    @Test fun persistedReportKeepsActualFailureTimeAndLegacyTimeRemainsUnknown() {
        val before = report("HTTP Error 503")
        assertEquals(before,FailureDetails.fromJson(before.json().toString()))
        val legacy = FailureDetails.capture("offline","SAVING","legacy","unknown")
        assertNull(legacy.at)
        assertTrue(legacy.text().contains("Waktu: Tidak tercatat"))
        assertTrue(FailureDetails.safe("x".repeat(5000)).length <= 2000)
    }
}
