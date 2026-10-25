package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InspectionCacheTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun metadata(url: String = "https://youtu.be/abc") = VideoMetadata(
        title = "Mutiara",
        uploader = "Ipank",
        durationSeconds = 247,
        thumbnail = "https://img.example/cover.jpg",
        url = url,
        subtitleLangs = listOf("id", "en"),
        artist = "Ipank",
        track = "Mutiara",
        creator = "Ipank",
        platformId = "youtube",
        sourceId = "abc",
        extractor = "Youtube",
        artistEvidence = "metadata_artist",
        resolutions = listOf(VideoResolution(1080, 1080), VideoResolution(720, 720), VideoResolution(480, 480)),
    )

    @Test
    fun freshMetadataRoundTripsAndKeepsEveryField() {
        val cache = InspectionCache(temporary.newFolder("cache"))

        cache.write("https://youtu.be/abc", metadata())

        assertEquals(metadata(), cache.read("https://youtu.be/abc"))
    }

    @Test
    fun wideResolutionKeepsTierAndHeightSeparateInCache() {
        val cache = InspectionCache(temporary.newFolder("wide"))
        val wide = metadata().copy(resolutions = listOf(VideoResolution(818, 1080), VideoResolution(540, 540)))
        cache.write(wide.url, wide)
        assertEquals(wide, cache.read(wide.url))
    }

    @Test
    fun cacheWithFormerRoundedTiersMustFetchFreshMetadata() {
        val folder = temporary.newFolder("old-tiers")
        val cache = InspectionCache(folder)
        cache.write(metadata().url, metadata())
        val file = folder.listFiles()!!.single { it.name.endsWith(".json") }
        val json = org.json.JSONObject(file.readText()).put("v", 2)
            .put("resolutions", org.json.JSONArray().put(org.json.JSONArray().put(540).put(720)))
        file.writeText(json.toString())
        assertNull(cache.read(metadata().url))
        assertEquals(0, folder.listFiles()!!.size)
    }

    @Test
    fun trimmedUrlUsesTheSameEntry() {
        val cache = InspectionCache(temporary.newFolder("cache"))
        cache.write("  https://youtu.be/abc  ", metadata())

        assertEquals(metadata(), cache.read("https://youtu.be/abc"))
    }

    @Test
    fun differentUrlsDoNotShareMetadata() {
        val cache = InspectionCache(temporary.newFolder("cache"))
        cache.write("https://youtu.be/abc", metadata())

        assertNull(cache.read("https://youtu.be/def"))
    }

    @Test
    fun expiredMetadataIsRemoved() {
        val folder = temporary.newFolder("cache")
        var now = System.currentTimeMillis()
        val cache = InspectionCache(folder) { now }
        cache.write("https://youtu.be/abc", metadata())
        now = System.currentTimeMillis() + InspectionCache.MAX_AGE_MS + 1

        assertNull(cache.read("https://youtu.be/abc"))
        assertEquals(0, folder.listFiles()?.size ?: 0)
    }

    @Test
    fun malformedCacheDoesNotBreakInspection() {
        val folder = temporary.newFolder("cache")
        val cache = InspectionCache(folder)
        cache.write("https://youtu.be/abc", metadata())
        folder.listFiles()!!.filter { it.name.endsWith(".json") }.forEach { it.writeText("{rusak") }

        assertNull(cache.read("https://youtu.be/abc"))
    }

    @Test
    fun cacheKeepsAtMostTwoHundredEntries() {
        val folder = temporary.newFolder("cache")
        val cache = InspectionCache(folder)
        repeat(203) { index -> cache.write("https://youtu.be/$index", metadata("https://youtu.be/$index")) }

        assertEquals(200, folder.listFiles()!!.count { it.name.endsWith(".json") })
    }
}
