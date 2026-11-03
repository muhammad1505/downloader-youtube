package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeCookiesTest {
    @Test
    fun parsesHeaderAndDetectsSignedInSession() {
        val cookies = YouTubeCookies.parse("SID=abc; HSID=def; PREF=f6=400")
        assertTrue(YouTubeCookies.isSignedIn(cookies))
        assertEquals("SID=abc; HSID=def; PREF=f6=400", YouTubeCookies.header(cookies))
    }

    @Test
    fun unrelatedCookiesAreNotASession() {
        val cookies = YouTubeCookies.parse("PREF=f6=400; VISITOR_INFO1_LIVE=xyz")
        assertFalse(YouTubeCookies.isSignedIn(cookies))
        assertEquals("", YouTubeCookies.netscape(cookies))
    }

    @Test
    fun netscapeFileCoversYouTubeAndGoogleDomains() {
        val content = YouTubeCookies.netscape(YouTubeCookies.parse("SID=abc; __Secure-3PSID=def"))
        assertTrue(content.contains(".youtube.com"))
        assertTrue(content.contains(".google.com"))
        assertTrue(content.contains("\tSID\tabc"))
    }

    @Test
    fun unsafeCookieValuesAreDropped() {
        val cookies = YouTubeCookies.parse("SID=abc; BAD=contains space")
        assertFalse(cookies.containsKey("BAD"))
        assertTrue(YouTubeCookies.isSignedIn(cookies))
    }
}
