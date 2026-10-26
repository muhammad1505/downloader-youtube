package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BstationCookiesTest {
    @Test fun parsesHeaderWithoutLosingEqualsOrPercentEncoding() {
        val cookies = BstationCookies.parse(" SESSDATA=abc%2Cdef ; bili_jct=x=y; DedeUserID=42 ")
        assertEquals("abc%2Cdef", cookies["SESSDATA"])
        assertEquals("x=y", cookies["bili_jct"])
        assertEquals("42", cookies["DedeUserID"])
    }

    @Test fun dropsMalformedNamesEmptyValuesAndHeaderInjection() {
        val cookies = BstationCookies.parse("bad name=v; =x; empty=; injected=x\nInjected: yes; SESSDATA=ok; DedeUserID=007")
        assertEquals(setOf("SESSDATA", "DedeUserID"), cookies.keys)
        assertEquals("ok", cookies["SESSDATA"])
        assertEquals("007", cookies["DedeUserID"])
    }

    @Test fun laterHeadersWinWhenWebViewReturnsDuplicates() {
        val cookies = BstationCookies.merge("SESSDATA=old; buvid3=a", "SESSDATA=new")
        assertEquals("new", cookies["SESSDATA"])
        assertEquals("a", cookies["buvid3"])
    }

    @Test fun signedInRequiresNonBlankSessionCookie() {
        assertFalse(BstationCookies.isSignedIn(emptyMap()))
        assertFalse(BstationCookies.isSignedIn(mapOf("SESSDATA" to "")))
        assertTrue(BstationCookies.isSignedIn(mapOf("SESSDATA" to "abc")))
        // Deploy Bstation lama/varian dapat memakai huruf kecil; header asli tetap dipertahankan.
        assertTrue(BstationCookies.isSignedIn(mapOf("sessdata" to "abc")))
        assertEquals("123", BstationCookies.accountId(mapOf("dedeuserid" to "123")))
        assertEquals("", BstationCookies.netscape(mapOf("bili_jct" to "x")))
    }

    @Test fun netscapeExportScopesCookiesToBstationOnly() {
        val export = BstationCookies.netscape(mapOf("SESSDATA" to "abc", "DedeUserID" to "42"))
        assertTrue(export.startsWith("# Netscape HTTP Cookie File"))
        assertTrue(export.contains(".bilibili.tv\tTRUE\t/\tTRUE\t0\tSESSDATA\tabc"))
        assertTrue(export.contains(".biliintl.com\tTRUE\t/\tTRUE\t0\tSESSDATA\tabc"))
        assertFalse(export.contains("youtube"))
        assertFalse(export.contains("google"))
    }

    @Test fun headerRoundTripsAndAccountIdNeedsDigits() {
        val cookies = BstationCookies.parse("SESSDATA=a=b; DedeUserID=12345")
        assertEquals("a=b", BstationCookies.parse(BstationCookies.header(cookies))["SESSDATA"])
        assertEquals("12345", BstationCookies.accountId(cookies))
        assertEquals("", BstationCookies.accountId(mapOf("DedeUserID" to "12x")))
    }
}
