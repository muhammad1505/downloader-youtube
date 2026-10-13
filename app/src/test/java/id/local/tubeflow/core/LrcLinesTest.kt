package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class LrcLinesTest {
    @Test fun realTimestampsMultipleTagsOffsetAndSeekUseSameTimeline() {
        val lines=LrcLines.parse("[ar:Artist]\n[offset:-2000]\n[00:05.25][00:10.250]Refrain\n[00:02.5]Start\n[00:12.00]")
        assertEquals(listOf(500L,3250L,8250L,10000L),lines.map { it.atMs })
        assertEquals(-1,LrcLines.activeIndex(lines,499)); assertEquals(0,LrcLines.activeIndex(lines,500))
        assertEquals(2,LrcLines.activeIndex(lines,8250)); assertEquals("",lines.last().text)
        assertEquals(0L,LrcLines.parse("\uFEFF[offset:-2000]\n[00:01.00]Intro").single().atMs)
        assertEquals(1000L,LrcLines.parse("[offset:1000]\n[00:00.00]Intro").single().atMs)
    }
    @Test fun invalidAndOversizedInputsDoNotCreateGuessedTimelines() {
        assertTrue(LrcLines.parse("Plain lyrics without timestamps").isEmpty())
        for (raw in listOf("[00:61.00]wrong", "[offset:bad]\n[00:01.00]wrong", "[offset:1]\n[offset:2]\n[00:01.00]wrong", "x".repeat(1024*1024+1))) {
            try { LrcLines.parse(raw); fail("Invalid LRC accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
