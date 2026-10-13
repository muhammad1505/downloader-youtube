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
 @Test fun tidyFileNameKeepsArtistSuffixWhenTruncating() {
  // Nama "Judul - Artis" yang kepanjangan: judul dipotong, artis tetap terbaca di nama file.
  val name = Format.tidyFileName("Judul Yang Sangat Panjang Sekali Sampai Melebihi Batas Enam Puluh Karakter - Denny Caknan")
  assertTrue(name, name.endsWith("- Denny Caknan"))
  assertTrue(name, name.length <= 60)
  // Tanpa sufiks " - " perambu (mis. "a - b" yang bagian belakang > 24 karakter), potong polos seperti biasa.
  assertEquals(60, Format.tidyFileName(("x".repeat(50) + " - " + "y".repeat(40))).length)
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
    @Test fun neatTitleStripsNoiseAndUsesEvidenceForArtist() {
        // Nama file rapi "Judul - Artis" (judul dulu). Artis HANYA diisi bila ada bukti (nama channel
        // yang muncul utuh di judul) — bukan sekadar uploader yang ditempel membabi buta (P1).
        // Tanpa channel ada, "Rick Astley" tidak bisa dibuktikan sebagai artis → judul utuh dipertahankan
        // (termasuk sisa " - "), hanya embel-embel promosi yang dibuang.
        assertEquals("Rick Astley - Never Gonna Give You Up", Format.neatTitle("Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)"))
        // Channel "Rick Astley" muncul utuh di judul → terbukti, dipisah jadi "Judul - Artis".
        assertEquals("Never Gonna Give You Up - Rick Astley", Format.neatTitle("Rick Astley - Never Gonna Give You Up (Official Audio)", "Rick Astley"))
        // Noise promosi dibuang; tanpa bukti artis, hanya judul yang dipakai.
        assertEquals("Judul", Format.neatTitle("Judul - Official Video", "A"))
        // "(feat. X)" = penanda versi, dipertahankan di judul.
        assertEquals("Judul | feat. X", Format.neatTitle("Judul (feat. X)", "A"))
        assertEquals("Judul", Format.neatTitle("Judul"))
        // Nama channel yang sudah menempel di judul tidak diduplikasi menjadi suffix " - ".
        assertEquals("SATRU 2 Denny Caknan", Format.neatTitle("SATRU 2 Denny Caknan", "Denny Caknan"))
    }
    @Test fun bytesUsesIndonesianDecimalSeparator() {
        assertEquals("512 B", Format.bytes(512))
        assertEquals("1,0 KB", Format.bytes(1024))
        assertEquals("1,5 MB", Format.bytes(1536L * 1024))
        assertEquals("2,0 GB", Format.bytes(2L * 1024 * 1024 * 1024))
    }
 @Test fun musicFileNamePreservesVersionCreditAndUtf8Budget() {
  val name = Format.musicFileName(SongIdentity("歌".repeat(100),"Artis","Live Remix"))
  assertTrue(name.endsWith(" | Live Remix - Artis"))
  assertTrue(name.toByteArray(Charsets.UTF_8).size < 220)
 }

}
