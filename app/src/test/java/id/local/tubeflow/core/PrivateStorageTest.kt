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
        fun write(root: File, name: String, size: Int) { File(root,name).apply { parentFile.mkdirs(); writeBytes(ByteArray(size)) } }
        write(cache,"lyrics-responses/a",10); write(cache,"extractor-cache/b",20)
        write(data,"covers/c.jpg",30); write(data,"library-covers/d.jpg",40)
        write(data,"downloads/job/file.part",50); write(data,"models/uvr.onnx",500)
        write(data,"lyrics-baseline/backup.lrc",600)
        val size = PrivateStorage.summary(cache,data)
        assertEquals(100,size.cache.bytes); assertEquals(50,size.partial.bytes)
        assertTrue(size.cache.complete); assertTrue(size.partial.complete)
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
