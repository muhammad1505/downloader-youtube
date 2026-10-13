package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class ManualLyricsInputTest {
    @Test fun signedDecimalSecondsPreserveMillisecondsAndRejectInvalidRanges() {
        assertEquals(-2000L,LrcTiming.parseSeconds("−2"))
        assertEquals(500L,LrcTiming.parseSeconds("0,5"))
        assertEquals(-1250L,LrcTiming.parseSeconds("-1.250"))
        assertEquals(30000L,LrcTiming.parseSeconds("+30"))
        listOf("", "NaN", "1e2", "30.001", "-31", "0.0001", "--2").forEach { assertNull(LrcTiming.parseSeconds(it)) }
        assertEquals("-1,25",LrcTiming.secondsLabel(-1250))
    }
    @Test fun renamedFilenameIsEditableQueryAndAmbiguousCreditStaysUnsplit() {
        assertEquals(ManualLyricQuery("Pingal","Denny Caknan"),ManualLyricQuery.fromFilename("Pingal - Denny Caknan.mp3"))
        assertEquals(ManualLyricQuery("Song - Artist - Label"),ManualLyricQuery.fromFilename("Song - Artist - Label.m4a"))
    }
    @Test fun editedSearchDoesNotMakeDifferentSongAnAutomaticRecordingMatch() {
        val item=MediaItem("content://media/1",kind="AUDIO",title="Song",displayName="Song - Artist.mp3",mime="audio/mpeg",folder="TubeFlow",artist="Artist",durationMs=200000)
        val state=MediaLibraryViewModel.LyricsState(item,"Completely Different","Another Band")
        val choice=Lyrics.Choice(1,"Completely Different","Another Band","",200,"[00:01.00]fixture")
        assertFalse(choice.automaticMatch(state.query))
    }

    @Test fun millisecondInputDoesNotLosePrecisionOnHundredthTimestamp() {
        val translated=LrcTiming.shift("[00:01.23]fixture",requireNotNull(LrcTiming.parseSeconds("0,005")),10000)
        assertEquals(1235L,LrcLines.parse(translated).single().atMs)
        assertEquals("[00:01.235]fixture",translated)
    }

}
