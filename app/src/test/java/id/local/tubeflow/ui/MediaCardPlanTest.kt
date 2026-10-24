package id.local.tubeflow.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCardPlanTest {

    private fun List<MediaActionSpec>.inline() = filter { it.slot == MediaActionSlot.Inline }.map { it.kind }
    private fun List<MediaActionSpec>.menu() = filter { it.slot == MediaActionSlot.Menu }.map { it.kind }

    @Test
    fun libraryAudioShowsTheSixDailyActionsAndHidesTheRest() {
        val plan = MediaCardPlan.library(audio = true, favorite = false, inPlaylist = false, hasLyrics = false)

        assertEquals(
            listOf(
                MediaActionKind.PLAY,
                MediaActionKind.FAVORITE_ADD,
                MediaActionKind.SHARE,
                MediaActionKind.RENAME,
                MediaActionKind.FIND_LYRICS,
                MediaActionKind.DELETE,
            ),
            plan.inline(),
        )
        assertTrue("Ikon langsung melebihi batas kartu", plan.inline().size <= MediaCardPlan.MAX_INLINE)
        assertTrue(plan.menu().contains(MediaActionKind.ENQUEUE))
        assertTrue(plan.menu().contains(MediaActionKind.DETAIL))
        assertTrue(plan.menu().contains(MediaActionKind.OPEN))
        assertFalse("Audio tidak punya pemotong Status video", plan.menu().contains(MediaActionKind.CLIP_STATUS))
    }

    @Test
    fun libraryVideoPlaysInAppAndKeepsExternalOpenInMenu() {
        val plan = MediaCardPlan.library(audio = false, favorite = false, inPlaylist = false, hasLyrics = false)

        assertEquals(
            listOf(MediaActionKind.PLAY, MediaActionKind.SHARE, MediaActionKind.RENAME, MediaActionKind.DELETE),
            plan.inline(),
        )
        assertFalse("Video tidak punya aksi lirik", plan.inline().contains(MediaActionKind.FIND_LYRICS))
        assertFalse("Video tidak punya aksi lirik", plan.menu().contains(MediaActionKind.FIND_LYRICS))
        assertEquals(MediaCardPlan.GROUP_FILE, plan.first { it.kind == MediaActionKind.CLIP_STATUS }.group)
        assertFalse("Video tidak punya antrean audio", plan.menu().contains(MediaActionKind.ENQUEUE))
        assertTrue("Video punya pemotong Status", plan.menu().contains(MediaActionKind.CLIP_STATUS))
        assertTrue("Buka di aplikasi lain tetap tersedia", plan.menu().contains(MediaActionKind.OPEN))
        assertEquals(MediaCardPlan.GROUP_FILE, plan.first { it.kind == MediaActionKind.OPEN }.group)
    }

    @Test
    fun onlyTheSixChosenKindsEverSitOutsideTheMenu() {
        val visible = setOf(
            MediaActionKind.PLAY,
            MediaActionKind.OPEN,
            MediaActionKind.FAVORITE_ADD,
            MediaActionKind.FAVORITE_REMOVE,
            MediaActionKind.SHARE,
            MediaActionKind.RENAME,
            MediaActionKind.FIND_LYRICS,
            MediaActionKind.DELETE,
        )
        val plans = listOf(
            MediaCardPlan.library(audio = true, favorite = true, inPlaylist = true, hasLyrics = true),
            MediaCardPlan.library(audio = false, favorite = false, inPlaylist = false, hasLyrics = false),
            MediaCardPlan.history(audio = true, hasThumbnail = true, hasSubtitle = false, hasLyrics = true),
            MediaCardPlan.history(audio = false, hasThumbnail = true, hasSubtitle = true),
        )

        plans.forEach { plan ->
            assertTrue(
                "Aksi ${plan.inline() - visible} seharusnya di menu",
                visible.containsAll(plan.inline()),
            )
        }
    }

    @Test
    fun favoriteToggleReflectsCurrentState() {
        val favorite = MediaCardPlan.library(audio = true, favorite = true, inPlaylist = false, hasLyrics = false)
        val notFavorite = MediaCardPlan.library(audio = true, favorite = false, inPlaylist = false, hasLyrics = false)

        assertTrue(favorite.inline().contains(MediaActionKind.FAVORITE_REMOVE))
        assertFalse(favorite.inline().contains(MediaActionKind.FAVORITE_ADD))
        assertTrue(notFavorite.inline().contains(MediaActionKind.FAVORITE_ADD))
    }

    @Test
    fun playlistRemovalAndLyricTimingAppearOnlyWhenRelevant() {
        val plain = MediaCardPlan.library(audio = true, favorite = false, inPlaylist = false, hasLyrics = false)
        val insidePlaylist = MediaCardPlan.library(audio = true, favorite = false, inPlaylist = true, hasLyrics = true)

        assertFalse(plain.menu().contains(MediaActionKind.REMOVE_PLAYLIST))
        assertFalse(plain.menu().contains(MediaActionKind.EDIT_LYRIC_TIMING))
        assertTrue(insidePlaylist.menu().contains(MediaActionKind.REMOVE_PLAYLIST))
        assertTrue(insidePlaylist.menu().contains(MediaActionKind.EDIT_LYRIC_TIMING))
    }

    @Test
    fun replacingActionStaysInTheMenuWhileDeleteIsAlwaysVisible() {
        val plans = listOf(
            MediaCardPlan.library(audio = true, favorite = false, inPlaylist = true, hasLyrics = true),
            MediaCardPlan.library(audio = false, favorite = false, inPlaylist = false, hasLyrics = false),
            MediaCardPlan.history(audio = true, hasThumbnail = false, hasSubtitle = false),
            MediaCardPlan.history(audio = false, hasThumbnail = true, hasSubtitle = true),
        )

        plans.forEach { plan ->
            assertTrue("Hapus file harus terlihat di kartu", plan.inline().contains(MediaActionKind.DELETE))
            assertFalse("Unduh lagi bukan aksi utama", plan.inline().contains(MediaActionKind.REDOWNLOAD))
            assertTrue(plan.filter { it.kind == MediaActionKind.DELETE }.all { it.slot == MediaActionSlot.Inline })
            assertTrue(plan.filter { it.kind == MediaActionKind.REDOWNLOAD }.all { it.slot == MediaActionSlot.Menu })
        }
    }

    @Test
    fun historyAudioPlaysInAppAndKeepsFileActionsInMenu() {
        val plan = MediaCardPlan.history(audio = true, hasThumbnail = false, hasSubtitle = false)

        assertEquals(
            listOf(MediaActionKind.PLAY, MediaActionKind.SHARE, MediaActionKind.FIND_LYRICS, MediaActionKind.DELETE),
            plan.inline(),
        )
        assertFalse("Cari lirik tampil di kartu, bukan di menu", plan.menu().contains(MediaActionKind.FIND_LYRICS))
        assertTrue(plan.menu().contains(MediaActionKind.OPEN))
        assertTrue(plan.menu().contains(MediaActionKind.REDOWNLOAD))
    }

    @Test
    fun historyVideoListsOnlySavedAttachments() {
        val without = MediaCardPlan.history(audio = false, hasThumbnail = false, hasSubtitle = false)
        val with = MediaCardPlan.history(audio = false, hasThumbnail = true, hasSubtitle = true)

        assertFalse(without.menu().contains(MediaActionKind.OPEN_THUMBNAIL))
        assertFalse(without.menu().contains(MediaActionKind.OPEN_SUBTITLE))
        assertTrue(with.menu().contains(MediaActionKind.OPEN_THUMBNAIL))
        assertTrue(with.menu().contains(MediaActionKind.OPEN_SUBTITLE))
        assertTrue("Video diputar di TubeFlow, buka eksternal ada di menu", with.menu().contains(MediaActionKind.OPEN))
        assertTrue(with.inline().contains(MediaActionKind.PLAY))
    }

    @Test
    fun everyKindHasOneNonBlankLabel() {
        MediaActionKind.entries.forEach { kind ->
            assertTrue("Label kosong untuk $kind", MediaCardPlan.label(kind, audio = true).isNotBlank())
            assertTrue("Label kosong untuk $kind", MediaCardPlan.label(kind, audio = false).isNotBlank())
        }
    }

    @Test
    fun lyricsAreVisibleAndRedownloadEndsTheMenuInBothLists() {
        val library = MediaCardPlan.library(
            audio = true, favorite = false, inPlaylist = false, hasLyrics = true, canRedownload = true,
        )
        val history = MediaCardPlan.history(
            audio = true, hasThumbnail = false, hasSubtitle = false, hasLyrics = true,
        )

        listOf(library, history).forEach { plan ->
            val lyrics = plan.first { it.kind == MediaActionKind.FIND_LYRICS }
            val download = plan.first { it.kind == MediaActionKind.REDOWNLOAD }
            assertEquals(MediaActionSlot.Inline, lyrics.slot)
            assertEquals(MediaCardPlan.GROUP_FILE, download.group)
            assertEquals(MediaActionSlot.Menu, download.slot)
            assertEquals(
                "Unduh lagi adalah aksi berkas terakhir di menu",
                MediaActionKind.REDOWNLOAD,
                plan.menu().last(),
            )
        }
    }

    @Test
    fun libraryHidesRedownloadWhenNoLinkedDownloadExists() {
        val imported = MediaCardPlan.library(
            audio = true, favorite = false, inPlaylist = false, hasLyrics = false, canRedownload = false,
        )

        assertFalse("Item tanpa sumber tidak boleh menawarkan Unduh lagi", imported.menu().contains(MediaActionKind.REDOWNLOAD))
    }

    @Test
    fun historyAudioCanQueueAndEditSavedLyricTiming() {
        val plan = MediaCardPlan.history(audio = true, hasThumbnail = false, hasSubtitle = false, hasLyrics = true)

        assertTrue(plan.menu().contains(MediaActionKind.ENQUEUE))
        assertTrue(plan.menu().contains(MediaActionKind.PLAY_NEXT))
        assertEquals(MediaCardPlan.GROUP_QUEUE, plan.first { it.kind == MediaActionKind.ENQUEUE }.group)
        assertEquals(MediaCardPlan.GROUP_LYRICS, plan.first { it.kind == MediaActionKind.EDIT_LYRIC_TIMING }.group)
    }

    @Test
    fun audioLyricSidecarIsNeverOfferedAsSubtitle() {
        val sidecars = "sub|content://lrc/1\nthumb|content://thumb/1"

        assertFalse("LRC audio bukan subtitle video", hasSubtitleAttachment(audio = true, sidecars))
        assertTrue(hasSubtitleAttachment(audio = false, sidecars))
        assertFalse(hasSubtitleAttachment(audio = false, "thumb|content://thumb/1"))
    }

    @Test
    fun attachmentUrisParsesSidecarLines() {
        val parsed = attachmentUris("thumb|content://thumb/1\ncc|content://cc/2\nthumb|")

        assertEquals("content://thumb/1", parsed[ATTACHMENT_THUMBNAIL])
        assertEquals("content://cc/2", parsed["cc"])
        assertTrue(attachmentUris(null).isEmpty())
    }
}
