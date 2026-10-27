package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class MediaFoldersTest {

    private fun media(name: String) = FolderMedia("content://media/audio/$name", name)

    @Test
    fun folderOrderFollowsTitlesAndPointsAtTheOpenedFile() {
        val siblings = listOf(media("Cinta"), media("Anugrah"), media("Beta"))
        val planned = MediaFolderQueue.plan(siblings, media("Beta"))

        assertNotNull(planned)
        val (ordered, index) = planned!!
        assertEquals(listOf("Anugrah", "Beta", "Cinta"), ordered.map { it.title })
        assertEquals("Beta", ordered[index].title)
    }

    @Test
    fun openedFileJoinsTheQueueEvenWhenTheScanMissedIt() {
        val planned = MediaFolderQueue.plan(listOf(media("Lagu Lama")), media("Baru Diunduh"))

        assertEquals(listOf("Baru Diunduh", "Lagu Lama"), planned!!.first.map { it.title })
        assertEquals(0, planned.second)
    }

    @Test
    fun duplicateRowsCollapseIntoOneEntry() {
        val satu = FolderMedia("content://media/audio/satu", "Satu")
        val dua = FolderMedia("content://media/audio/dua", "Dua")
        // Baris kedua datang dari berkas yang dibuka; baris pertama menang agar judul tidak berubah.
        val planned = MediaFolderQueue.plan(listOf(satu, satu, dua), FolderMedia(satu.uri, "Judul Lain"))

        assertEquals(2, planned!!.first.size)
        assertEquals("Satu", planned.first[planned.second].title)
        assertEquals(listOf("Dua", "Satu"), planned.first.map { it.title })
    }

    @Test
    fun singleFileOrUnknownFolderKeepsTheOldSingleTrackQueue() {
        assertNull(MediaFolderQueue.plan(listOf(media("Sendiri")), media("Sendiri")))
        assertNull(MediaFolderQueue.plan(emptyList(), media("Sendiri")))
        assertNull(MediaFolderQueue.plan(listOf(media("Satu"), media("Dua")), FolderMedia("", "Tanpa Uri")))
    }

    @Test
    fun blankUrisFromMediaStoreAreIgnored() {
        val planned = MediaFolderQueue.plan(
            listOf(FolderMedia("", "Kosong"), media("Dua"), media("Satu")),
            media("Satu"),
        )

        assertEquals(listOf("Dua", "Satu"), planned!!.first.map { it.title })
        assertEquals(1, planned.second)
    }
}
