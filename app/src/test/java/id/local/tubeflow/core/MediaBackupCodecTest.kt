package id.local.tubeflow.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MediaBackupCodecTest {
    private val id = "a".repeat(64)
    private fun payload() = JSONObject().put("settings", JSONObject()).put("items", JSONArray().put(
        JSONObject().put("id", id).put("title", "Lamunan").put("artist", "Denny Caknan")
            .put("album", "").put("albumArtist", "").put("genre", "").put("track", "").put("year", "")))
        .put("playlists", JSONArray().put(JSONObject().put("id", "favorites").put("name", "Favorit").put("members", JSONArray().put(id))))
    @Test fun roundTripPreservesUnicodeLyricsAndOffset() {
        val source = payload()
        source.getJSONArray("items").getJSONObject(0).put("lyrics", JSONObject().put("original", "[00:02.00]Lirik ♪\n[00:10.00]Lanjut").put("offset", -2000))
        val restored = MediaBackupCodec.decode(MediaBackupCodec.encode(source))
        assertEquals(-2000L, restored.getJSONArray("items").getJSONObject(0).getJSONObject("lyrics").getLong("offset"))
        assertEquals("favorites", restored.getJSONArray("playlists").getJSONObject(0).getString("id"))
    }
    @Test fun damagedPayloadIsRejected() {
        val encoded = JSONObject(String(MediaBackupCodec.encode(payload())))
        encoded.put("payload", encoded.getString("payload").replace("Lamunan", "Wrong song"))
        assertThrows(IllegalArgumentException::class.java) { MediaBackupCodec.decode(encoded.toString().toByteArray()) }
    }
    @Test fun unknownSchemaIsRejected() {
        val encoded = JSONObject(String(MediaBackupCodec.encode(payload()))).put("schema", 2)
        assertThrows(IllegalArgumentException::class.java) { MediaBackupCodec.decode(encoded.toString().toByteArray()) }
    }
    @Test fun duplicateMediaAndDanglingPlaylistReferencesAreRejected() {
        val duplicate = payload(); duplicate.getJSONArray("items").put(duplicate.getJSONArray("items").getJSONObject(0))
        assertThrows(IllegalArgumentException::class.java) { MediaBackupCodec.decode(MediaBackupCodec.encode(duplicate)) }
        val dangling = payload(); dangling.getJSONArray("playlists").getJSONObject(0).put("members", JSONArray().put("b".repeat(64)))
        assertThrows(IllegalArgumentException::class.java) { MediaBackupCodec.decode(MediaBackupCodec.encode(dangling)) }
    }
    @Test fun dangerousOffsetAndOversizedMetadataAreRejected() {
        val source = payload(); source.getJSONArray("items").getJSONObject(0).put("lyrics", JSONObject().put("original", "[00:01.00]Text").put("offset", -30001))
        assertThrows(IllegalArgumentException::class.java) { MediaBackupCodec.decode(MediaBackupCodec.encode(source)) }
        val oversized = payload(); oversized.getJSONArray("items").getJSONObject(0).put("title", "x".repeat(1001))
        assertThrows(IllegalArgumentException::class.java) { MediaBackupCodec.decode(MediaBackupCodec.encode(oversized)) }
    }
    @Test fun emptyAndOversizedDocumentsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { MediaBackupCodec.decode(byteArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { MediaBackupCodec.decode(ByteArray(MediaBackupCodec.MAX_BYTES + 1)) }
    }
    @Test fun excessiveNestingAndInvalidUtf8AreRejected() {
        val source = payload(); source.put("extra", JSONArray("[".repeat(20) + "0" + "]".repeat(20)))
        assertThrows(IllegalArgumentException::class.java) { MediaBackupCodec.decode(MediaBackupCodec.encode(source)) }
        assertThrows(java.nio.charset.CharacterCodingException::class.java) { MediaBackupCodec.decode(byteArrayOf(0xc3.toByte(), 0x28)) }
    }
    @Test fun repeatedPlaylistEntriesAreRejected() {
        val source = payload(); source.getJSONArray("playlists").getJSONObject(0).getJSONArray("members").put(id)
        assertThrows(IllegalArgumentException::class.java) { MediaBackupCodec.decode(MediaBackupCodec.encode(source)) }
    }
}
