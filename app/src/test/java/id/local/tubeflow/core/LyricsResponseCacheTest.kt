package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class LyricsResponseCacheTest {
    @Test fun explicitRetryRefreshesOnlyMissingResponsesAndKeepsPositiveCache() {
        val dir=Files.createTempDirectory("lyrics-refresh").toFile()
        try {
            val cache=LyricsResponseCache(dir)
            for ((key,code,text) in listOf(Triple("missing",404,""),Triple("empty",200,"[]"),Triple("positive",200,"[{\"id\":1}]"))) {
                cache.request(key) { LyricsResponseCache.Response(code,text) }
                var calls=0
                cache.request(key,refreshMissing=true) { calls++; LyricsResponseCache.Response(200,"[{\"id\":2}]") }
                assertEquals(if(key=="positive") 0 else 1,calls)
            }
        } finally { dir.deleteRecursively() }
    }
    @Test fun positiveCacheWorksOfflineThenExpiresAndCanBeCleared() {
        val dir = Files.createTempDirectory("lyrics-cache").toFile()
        try {
            var now = 1000L; var calls = 0
            val cache = LyricsResponseCache(dir) { now }
            fun fetch() = cache.request("track") { calls++; LyricsResponseCache.Response(200, "{\"trackName\":\"Song\"}") }
            fetch(); assertEquals(200, cache.request("track") { error("offline") }.code); assertEquals(1, calls)
            now += 86_400_001; fetch(); assertEquals(2, calls)
            cache.clear(); fetch(); assertEquals(3, calls)
        } finally { dir.deleteRecursively() }
    }
    @Test fun notFoundExpiresSoonAndTransientFailuresOrInvalidBodiesAreNeverCached() {
        val dir = Files.createTempDirectory("lyrics-cache").toFile()
        try {
            var now = 1000L; var calls = 0; val cache = LyricsResponseCache(dir) { now }
            fun missing() = cache.request("missing") { calls++; LyricsResponseCache.Response(404, "") }
            missing(); missing(); assertEquals(1, calls); now += 3_600_001; missing(); assertEquals(2, calls)
            for (code in listOf(429, 503, 200)) {
                var n = 0
                repeat(2) { cache.request("bad$code") { n++; LyricsResponseCache.Response(code, "invalid response") } }
                assertEquals(2, n)
            }
        } finally { dir.deleteRecursively() }
    }
}
