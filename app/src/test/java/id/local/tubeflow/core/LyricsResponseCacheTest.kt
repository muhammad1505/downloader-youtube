package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class LyricsResponseCacheTest {
    @Test fun clearDoesNotWaitForNetworkAndOldResponseCannotRepopulateCache() {
        val dir = Files.createTempDirectory("lyrics-clear-race").toFile()
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        try {
            val cache = LyricsResponseCache(dir)
            val pending = executor.submit<LyricsResponseCache.Response> {
                cache.request("old") {
                    entered.countDown()
                    check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    LyricsResponseCache.Response(200, "[]")
                }
            }
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            val cleared = executor.submit<LyricsResponseCache.ClearResult> { cache.clear() }
            assertTrue(cleared.get(1, java.util.concurrent.TimeUnit.SECONDS).complete)
            release.countDown()
            assertEquals(200, pending.get(2, java.util.concurrent.TimeUnit.SECONDS).code)
            assertTrue(dir.listFiles()!!.isEmpty())
            var calls = 0
            cache.request("old") { calls++; LyricsResponseCache.Response(200, "[]") }
            assertEquals(1, calls)
        } finally { release.countDown(); executor.shutdownNow(); dir.deleteRecursively() }
    }

    @Test fun clearBeforeEngineInitializationRemovesDiskCacheAndPreservesProviderCooldown() {
        val root = Files.createTempDirectory("lyrics-clear-startup").toFile()
        val previous = Lyrics.responseCache
        try {
            val dir = java.io.File(root, "lyrics-responses").apply { mkdirs() }
            java.io.File(dir, "old.json").writeText("cached")
            val cooldown = java.io.File(root, "lyrics-cooldown.json").apply { writeText("keep") }
            Lyrics.responseCache = null
            val result = Lyrics.clearCache(dir)
            assertTrue(result.complete)
            assertEquals(1, result.removed)
            assertTrue(dir.listFiles()!!.isEmpty())
            assertEquals("keep", cooldown.readText())
            assertEquals(0, Lyrics.clearCache(dir).removed)
        } finally { Lyrics.responseCache = previous; root.deleteRecursively() }
    }

    @Test fun unremovableOrUnexpectedCacheEntriesAreNotReportedAsClean() {
        val dir = Files.createTempDirectory("lyrics-clear-partial").toFile()
        try {
            java.io.File(dir, "unexpected").mkdirs()
            java.io.File(dir, "old.json").writeText("cached")
            val result = LyricsResponseCache(dir).clear()
            assertFalse(result.complete)
            assertEquals(1, result.removed)
            assertTrue(java.io.File(dir, "unexpected").isDirectory)
        } finally { dir.deleteRecursively() }
    }

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
