package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class RenameBatchTest {
    private val entries = listOf(RenameBatch.Entry("lyric", "Old.lrc", "New.lrc"), RenameBatch.Entry("audio", "Old.mp3", "New.mp3"))
    @Test fun secondFailureRollsBackBothMembersIncludingPartialProviderChange() {
        val names = mutableMapOf("audio" to "Old.mp3", "lyric" to "Old.lrc")
        try {
            RenameBatch.apply(entries, { names.getValue(it) }, { uri, name -> names[uri] = name; if (uri == "audio" && name == "New.mp3") error("provider close failed") }); fail()
        } catch (_: IllegalStateException) { }
        assertEquals("Old.lrc", names["lyric"]); assertEquals("Old.mp3", names["audio"])
    }
    @Test fun providerCollisionSuffixIsRejectedAndRestored() {
        val names = mutableMapOf("audio" to "Old.mp3", "lyric" to "Old.lrc")
        try { RenameBatch.apply(entries, { names.getValue(it) }, { uri, name -> names[uri] = if (name == "New.lrc") "New (1).lrc" else name }); fail() } catch (_: IllegalStateException) { }
        assertEquals("Old.lrc", names["lyric"]); assertEquals("Old.mp3", names["audio"])
    }
    @Test fun staleSelectionMakesNoWritesAndSuccessChangesNamesOnly() {
        val names = mutableMapOf("audio" to "Externally changed.mp3", "lyric" to "Old.lrc")
        try { RenameBatch.apply(entries, { names.getValue(it) }, { _, _ -> fail("must not write") }); fail() } catch (_: IllegalArgumentException) { }
        names["audio"] = "Old.mp3"
        RenameBatch.apply(entries, { names.getValue(it) }, { uri, name -> names[uri] = name })
        assertEquals("New.lrc", names["lyric"]); assertEquals("New.mp3", names["audio"])
    }
}
