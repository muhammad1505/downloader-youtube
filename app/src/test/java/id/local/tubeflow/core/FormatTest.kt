package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class FormatTest {
 @Test fun tidyFileNameCleansCollapsesAndShortens() {
  assertEquals("Clips Video", Format.tidyFileName("  Clips:/  Video  "))
  assertEquals("Lagu Tanpa Emoji", Format.tidyFileName("Lagu \uD83C\uDFB5 Tanpa Emoji"))
  assertEquals("TubeFlow", Format.tidyFileName("  :*?\"<>|  "))
  assertEquals(60, Format.tidyFileName("a".repeat(120)).length)
  assertTrue(Format.tidyFileName("judul. ").endsWith("judul"))
 }
    @Test fun durationFormatsMinutesAndHours() {
        assertEquals("0:05", Format.duration(5))
        assertEquals("1:35", Format.duration(95))
        assertEquals("1:02:05", Format.duration(3725))
        assertEquals("0:00", Format.duration(-3))
    }
    @Test fun etaRejectsUnknownAndFormatsHumanUnits() {
        assertEquals("", Format.eta(null))
        assertEquals("", Format.eta("-1"))
        assertEquals("", Format.eta(""))
        assertEquals("45 dtk", Format.eta("45s"))
        assertEquals("2 mnt 3 dtk", Format.eta("123s"))
        assertEquals("3 mnt", Format.eta("180s"))
        assertEquals("1 jam 2 mnt", Format.eta("3720"))
    }
    @Test fun speedExtractedFromProgressLine() {
        assertEquals("1.23MiB/s", Format.speed("[download]  12.5% of 10.00MiB at 1.23MiB/s ETA 00:45"))
        assertEquals("812.00KiB/s", Format.speed("[download]   4.0% of 20.00MiB at 812.00KiB/s ETA 00:12"))
        assertNull(Format.speed("[Merger] Merging formats into \"media.mp4\""))
        assertNull(Format.speed(null))
    }
    @Test fun neatTitleStripsNoiseAndAddsArtist() {
        assertEquals("Rick Astley - Never Gonna Give You Up", Format.neatTitle("Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)"))
        assertEquals("Rick Astley - Never Gonna Give You Up", Format.neatTitle("Never Gonna Give You Up (Official Audio)", "Rick Astley"))
        assertEquals("A - Judul - Lagu".substringBefore(" - Lagu"), Format.neatTitle("Judul - Official Video", "A"))
        assertEquals("A - Judul (feat. X)", Format.neatTitle("Judul (feat. X)", "A"))
        assertEquals("Judul", Format.neatTitle("Judul"))
    }
    @Test fun bytesUsesIndonesianDecimalSeparator() {
        assertEquals("512 B", Format.bytes(512))
        assertEquals("1,0 KB", Format.bytes(1024))
        assertEquals("1,5 MB", Format.bytes(1536L * 1024))
        assertEquals("2,0 GB", Format.bytes(2L * 1024 * 1024 * 1024))
    }
}
