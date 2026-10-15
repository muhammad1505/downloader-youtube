package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LyricsRequestGateTest {
    @Test fun distinctRequestsAreSpacedWithoutDelayingCacheHits() {
        var now = 1000L
        val started = mutableListOf<Long>()
        val gate = LyricsRequestGate(clock={ now }, sleep={ now += it })
        repeat(3) { gate.request { started += now; "ok" } }
        assertEquals(listOf(1000L, 2000L, 3000L), started)
    }

    @Test fun repeatedLimitsBackOffAndSuccessAllowsNormalRequestsAgain() {
        var now = 1000L
        val gate = LyricsRequestGate(minimumGapMs=0,clock={ now })
        gate.request { gate.limited("1"); "limited" }
        assertEquals(5L,gate.remainingSeconds())
        assertNull(gate.request { error("must not contact server") })
        now += 5000
        gate.request { gate.limited("1"); "limited" }
        assertEquals(10L,gate.remainingSeconds())
        now += 10000
        gate.request { gate.accepted(); "ok" }
        assertEquals(0L,gate.remainingSeconds())
        gate.request { gate.limited(null); "limited" }
        assertEquals(5L,gate.remainingSeconds())
    }

    @Test fun longRetryAfterAndHttpDateAreNotShortened() {
        assertEquals(900_000L,LyricsRequestGate.retryMillis("900",null,0))
        assertEquals(900_000L,LyricsRequestGate.retryMillis("Fri, 16 Oct 2026 00:15:00 GMT","Fri, 16 Oct 2026 00:00:00 GMT",0))
        assertEquals(0L,LyricsRequestGate.retryMillis("invalid",null,0))
        assertEquals(0L,LyricsRequestGate.retryMillis("-1",null,0))
        assertEquals(Long.MAX_VALUE,LyricsRequestGate.retryMillis(Long.MAX_VALUE.toString(),null,0))
        val gate=LyricsRequestGate(clock={ 1000 })
        gate.limited("900")
        assertEquals(900L,gate.remainingSeconds())
    }

    @Test fun cooldownSurvivesNewInstanceAndExpiresWithoutExtendingOnBlockedRequests() {
        val dir=Files.createTempDirectory("lyrics-limit").toFile()
        try {
            var now=1000L
            val file=java.io.File(dir,"limit.json")
            LyricsRequestGate(clock={ now }).apply { attach(file); limited("60") }
            val restored=LyricsRequestGate(clock={ now }).apply { attach(file) }
            now += 1001
            assertEquals(59L,restored.remainingSeconds())
            assertNull(restored.request { error("blocked") })
            now=61_000
            assertEquals("ok",restored.request { restored.accepted(); "ok" })
            assertEquals(0L,LyricsRequestGate(clock={ now }).apply { attach(file) }.remainingSeconds())
        } finally { dir.deleteRecursively() }
    }

    @Test fun concurrentCallersCannotSlipPastFirst429() {
        val gate=LyricsRequestGate(minimumGapMs=0)
        val entered=CountDownLatch(1)
        val release=CountDownLatch(1)
        val pool=Executors.newFixedThreadPool(2)
        try {
            val first=pool.submit<String?> { gate.request {
                entered.countDown(); check(release.await(3,TimeUnit.SECONDS))
                gate.limited("60"); "limited"
            } }
            assertTrue(entered.await(3,TimeUnit.SECONDS))
            val second=pool.submit<String?> { gate.request { error("second caller escaped cooldown") } }
            release.countDown()
            assertEquals("limited",first.get(3,TimeUnit.SECONDS))
            assertNull(second.get(3,TimeUnit.SECONDS))
        } finally { release.countDown(); pool.shutdownNow() }
    }

    @Test fun interruptedWaitNeverCallsProvider() {
        var now=0L
        val gate=LyricsRequestGate(clock={ now },sleep={ throw InterruptedException() })
        gate.request { "ok" }
        assertThrows(InterruptedException::class.java) { gate.request { error("cancelled") } }
    }
}
