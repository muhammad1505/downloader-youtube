package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class PrivateStorageTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun cacheAndDownloadTempsAreSeparateAndModelsAndLyricsBackupsAreExcluded() {
        val cache = temp.newFolder("cache"); val data = temp.newFolder("private")
        fun write(root: File, name: String, size: Int) { File(root,name).apply { requireNotNull(parentFile).mkdirs(); writeBytes(ByteArray(size)) } }
        write(cache,"lyrics-responses/a",10); write(cache,"extractor-cache/b",20)
        write(data,"covers/c.jpg",30); write(data,"library-covers/d.jpg",40)
        write(data,"downloads/job/file.part",50); write(data,"models/uvr.onnx",500)
        write(data,"lyrics-baseline/backup.lrc",600)
        val size = PrivateStorage.summary(cache,data)
        assertEquals(100,size.cache.bytes); assertEquals(50,size.partial.bytes)
        assertEquals(10,size.lyrics.bytes); assertEquals(20,size.metadata.bytes)
        assertEquals(70,size.artwork.bytes)
        assertTrue(size.cache.complete); assertTrue(size.partial.complete)
    }
    @Test fun clearingLyricsUpdatesOnlyItsSizeAndPreservesOtherCachesAndMedia() {
        val cache = temp.newFolder("cache"); val data = temp.newFolder("private")
        val lyrics = File(cache,"lyrics-responses").apply { mkdirs() }
        File(lyrics,"response.json").writeText("cached lyrics")
        val metadata = File(cache,"extractor-cache/video.json").apply { requireNotNull(parentFile).mkdirs(); writeText("metadata") }
        val artwork = File(data,"covers/song.jpg").apply { requireNotNull(parentFile).mkdirs(); writeText("artwork") }
        val media = temp.newFile("song.lrc").apply { writeText("[00:01.00]Keep these lyrics") }
        val before = PrivateStorage.summary(cache,data)

        val cleared = LyricsResponseCache(lyrics).clear()
        val after = PrivateStorage.summary(cache,data)

        assertTrue(cleared.complete); assertEquals(1,cleared.removed)
        assertTrue(before.lyrics.bytes > 0)
        assertEquals(0,after.lyrics.bytes); assertTrue(after.lyrics.complete)
        assertEquals(before.metadata,after.metadata); assertEquals(before.artwork,after.artwork)
        assertEquals(before.cache.bytes - before.lyrics.bytes,after.cache.bytes)
        assertEquals("metadata",metadata.readText()); assertEquals("artwork",artwork.readText())
        assertEquals("[00:01.00]Keep these lyrics",media.readText())
    }
    @Test fun incompleteArtworkIsReportedWithoutMarkingLyricsOrMetadataIncomplete() {
        val cache = temp.newFolder("cache"); val data = temp.newFolder("private")
        val covers = File(data,"library-covers").apply { mkdirs() }
        val outside = temp.newFile("outside-cover.jpg").apply { writeText("outside") }
        Files.createSymbolicLink(File(covers,"link.jpg").toPath(),outside.toPath())
        val usage = PrivateStorage.summary(cache,data)
        assertFalse(usage.artwork.complete); assertFalse(usage.cache.complete)
        assertTrue(usage.lyrics.complete); assertTrue(usage.metadata.complete)
        assertEquals(0,usage.artwork.bytes); assertEquals("outside",outside.readText())
    }
    @Test fun refusesSymlinksOutsideRootAndIncompleteTraversal() {
        val root = temp.newFolder("downloads"); val job = File(root,"job").apply { mkdirs() }
        val outside = temp.newFile("valid-audio.mp3").apply { writeText("keep") }
        Files.createSymbolicLink(File(job,"link").toPath(),outside.toPath())
        assertFalse(PrivateStorage.measure(job).complete)
        assertFalse(PrivateStorage.deleteJob(root,job))
        assertFalse(PrivateStorage.deleteJob(root,outside))
        assertEquals("keep",outside.readText())
        assertFalse(PrivateStorage.measure(root,1).complete)
    }
    @Test fun completedTraversalDeletesOnlyTheOwnedJobFolder() {
        val root = temp.newFolder("downloads"); val job = File(root,"job").apply { mkdirs() }
        File(job,"file.part").writeText("partial")
        val media = temp.newFile("song.mp3").apply { writeText("media") }
        assertTrue(PrivateStorage.deleteJob(root,job))
        assertTrue(root.isDirectory); assertFalse(job.exists()); assertEquals("media",media.readText())
    }
    @Test fun workerLeaseRemainsBusyEvenIfWorkManagerAlreadyReportsFinished() {
        val mutex = DownloadFileAccess.lock("job")
        assertTrue(mutex.tryLock())
        try { assertFalse(DownloadFileAccess.lock("job").tryLock()) }
        finally { mutex.unlock() }
        assertTrue(mutex.tryLock()); mutex.unlock()
    }
}
