package id.local.tubeflow.core
import org.junit.Assert.*
import org.junit.Test
class SelectionTest {
 @Test fun qualityFallsBackToTheClosestReportedHeight() {
  org.junit.Assert.assertEquals(1080, Selection.closestQuality(1080, listOf(1080, 720, 480)))
  org.junit.Assert.assertEquals(720, Selection.closestQuality(1080, listOf(720, 480)))
  org.junit.Assert.assertEquals(0, Selection.closestQuality(360, listOf(720)))
  org.junit.Assert.assertEquals(1080, Selection.closestQuality(1080, emptyList()))
  org.junit.Assert.assertEquals(0, Selection.closestQuality(0, listOf(1080)))
 }
 @Test fun soundcloudTrackingAliasesMatchExistingOutput() {
  val clean = "https://soundcloud.com/bondanfade2black/ya-sudahlah"
  val shared = "$clean?utm_source=clipboard&utm_medium=text&utm_campaign=social_sharing"
  val job = DownloadJob("old", shared, "Ya Sudahlah", MediaMode.AUDIO, "mp3", 0, 128,
   state = JobState.COMPLETED, subtitles = "asli", embedThumb = true)
  val options = DownloadOptions(clean, MediaMode.AUDIO, "mp3", bitrate = 128, subtitles = "asli", embedThumb = true)
  listOf(JobState.QUEUED, JobState.RUNNING, JobState.PAUSED, JobState.COMPLETED).forEach {
   assertTrue(job.copy(state = it).matchesOutput(clean, options))
  }
  assertEquals(clean, Selection.identity("https://www.soundcloud.com/bondanfade2black/ya-sudahlah?utm_source=share#t=12"))
  assertFalse(job.matchesOutput(clean, options.copy(bitrate = 320)))
  assertFalse(job.matchesOutput("https://soundcloud.com/other/ya-sudahlah", options))
  assertEquals(shared, job.url)
 }
 @Test fun soundcloudIdentityPreservesAccessParametersAndOtherPlatforms() {
  val privateTrack = "https://soundcloud.com/artist/track?secret_token=s-a%2Fb"
  assertEquals(privateTrack, Selection.identity("$privateTrack&utm_source=share"))
  assertNotEquals(Selection.identity(privateTrack), Selection.identity("https://soundcloud.com/artist/track"))
  val other = "https://www.tiktok.com/@user/video/7123456789?utm_source=share"
  assertEquals(other, Selection.identity(other))
  assertNull(Selection.identity("https://soundcloud.com.evil.org/artist/track?utm_source=share"))
 }
 @Test fun videoIdentityIgnoresHostPathAndContext() {
  listOf(
   "https://www.youtube.com/watch?v=aqz-KE-bpKQ&list=RDaqz-KE-bpKQ&start_radio=1",
   "https://youtu.be/aqz-KE-bpKQ?si=shared&t=25",
   "https://music.youtube.com/watch?list=PLabc123&v=aqz-KE-bpKQ",
   "https://m.youtube.com/shorts/aqz-KE-bpKQ",
   "https://youtube.com/live/aqz-KE-bpKQ?feature=share",
   " https://www.youtube.com/watch?v=%61qz-KE-bpKQ ",
  ).forEach { assertEquals(it, "aqz-KE-bpKQ", Selection.videoId(it)) }
 }
 @Test fun videoIdentityRejectsInvalidSources() {
  listOf("http://youtu.be/abc", "https://evil.org/watch?v=abc", "https://user@youtube.com/watch?v=abc",
   "https://youtu.be:444/abc", "https://youtube.com/playlist?list=PLabc123",
   "https://youtube.com/shorts/", "https://youtu.be/abc/other", "https://youtube.com/watch?v=%2Fabc")
   .forEach { assertNull(it, Selection.videoId(it)) }
 }
 @Test fun legacyJobMatchesAliasWithoutChangingHistory() {
  val url = "https://www.youtube.com/watch?v=aqz-KE-bpKQ"
  val job = DownloadJob("old", url, "Old file", MediaMode.AUDIO, "mp3", 0, 128, state = JobState.COMPLETED, embedThumb = true)
  val alias = "https://youtu.be/aqz-KE-bpKQ?si=shared"
  val options = DownloadOptions(alias, MediaMode.AUDIO, "mp3", bitrate = 128, embedThumb = true)
  listOf(JobState.QUEUED, JobState.RUNNING, JobState.PAUSED, JobState.COMPLETED).forEach {
   assertTrue(job.copy(state = it).matchesOutput(alias, options))
  }
  assertFalse(job.matchesOutput(alias, options.copy(bitrate = 320)))
  assertFalse(job.matchesOutput("https://youtu.be/Aqz-KE-bpKQ", options))
  assertEquals(url, job.url)
 }
 @Test fun invalidIdentitiesNeverMatchEachOther() {
  val url = "https://evil.org/watch?v=abc"
  val job = DownloadJob("old", url, "Invalid", MediaMode.VIDEO, "auto", 0, 0)
  assertFalse(job.matchesOutput(url, DownloadOptions(url)))
 }
 @Test fun mixInspectionPreservesSeedAcrossLinkForms() {
  val expected = "https://www.youtube.com/watch?v=3J1Opi7MVHw&list=RD3J1Opi7MVHw&start_radio=1"
  listOf(expected, "https://youtu.be/3J1Opi7MVHw?list=RD3J1Opi7MVHw",
   "https://youtube.com/shorts/3J1Opi7MVHw?list=RD3J1Opi7MVHw").forEach {
   assertTrue(Selection.isMix(it))
   assertEquals(expected, Selection.playlistInspectionUrl(it))
  }
  val seedless = "https://www.youtube.com/playlist?list=RDMMabc123"
  assertEquals(seedless, Selection.playlistInspectionUrl(seedless))
 }
 @Test fun standaloneMixLinkRecoversSeedFromPlaylistId() {
  // Tautan Bagikan dari autoplay: playlist?list=RD… tanpa seed — YouTube menolak playlist polos
  // ("This playlist type is unviewable", terbukti di HP & host); seed dipulihkan dari ID Mix (BUG-027).
  assertEquals("https://www.youtube.com/watch?v=rEjSkBl47OY&list=RDrEjSkBl47OY&start_radio=1",
   Selection.playlistInspectionUrl("https://youtube.com/playlist?list=RDrEjSkBl47OY&playnext=1&si=mvfrZkxYG_CqldGI"))
  // Bentuk RDMM (mobile) dengan videoId 11 karakter ikut dipulihkan.
  assertEquals("https://www.youtube.com/watch?v=abc12345678&list=RDMMabc12345678&start_radio=1",
   Selection.playlistInspectionUrl("https://youtube.com/playlist?list=RDMMabc12345678"))
 }
 @Test fun staticInspectionRemainsCanonicalAndRejectsUnsafeLinks() {
  val regular = "https://youtu.be/abc?list=PLabc123"
  assertFalse(Selection.isMix(regular))
  assertEquals("https://www.youtube.com/playlist?list=PLabc123", Selection.playlistInspectionUrl(regular))
  listOf("http://youtube.com/watch?v=abc&list=RDabc123", "https://evil.org/watch?v=abc&list=RDabc123",
   "https://youtube.com/watch?v=abc&list=RDabc%26bad").forEach { assertNull(Selection.playlistInspectionUrl(it)) }
 }
 @Test fun acceptsSupportedVideoLinks() { listOf("https://www.youtube.com/watch?v=abc123","https://youtu.be/abc123","https://youtube.com/shorts/abc123","https://music.youtube.com/watch?v=abc123").forEach { assertTrue(it,Selection.validUrl(it)) } }
 @Test fun rejectsUnsafeAndUnsupportedUrls() { listOf("file:///etc/passwd","http://youtube.com/watch?v=x","https://youtube.com.evil.org/watch?v=x","https://evil.org/watch?v=x","https://user@youtube.com/watch?v=x","https://youtube.com/playlist?list=x","https://youtube.com/watch","https://youtu.be/").forEach { assertFalse(it,Selection.validUrl(it)) } }
 @Test fun acceptsOtherSupportedPlatforms() {
  listOf(
   "https://www.tiktok.com/@user/video/7123456789",
   "https://vm.tiktok.com/ZMabcdef/",
   "https://www.instagram.com/reel/ABC123/",
   "https://soundcloud.com/artist/track",
   "https://x.com/user/status/12345",
   "https://vimeo.com/123456",
   "https://dai.ly/abc123",
   "https://clips.twitch.tv/ClipName",
  ).forEach { assertTrue(it, Selection.validUrl(it)) }
 }
 @Test fun rejectsUnsafeOrUnknownForeignUrls() {
  listOf(
   "http://www.tiktok.com/@user/video/1", // wajib https
   "https://user@www.tiktok.com/@user/video/1", // tanpa userInfo
   "https://www.tiktok.com:8443/@user/video/1", // tanpa port asing
   "https://www.tiktok.com.evil.org/video/1", // subdomain palsu
   "https://evil-tiktok.com/video/1", // host mirip
   "https://www.tiktok.com/", // tanpa path
   "https://unknown-site.example/video/1", // di luar allowlist
  ).forEach { assertFalse(it, Selection.validUrl(it)) }
 }
 @Test fun nonYoutubeIdentityUsesFullUrlWithoutFragment() {
  val url = "https://www.tiktok.com/@user/video/7123456789"
  // ID video hanya untuk YouTube; platform lain memakai URL utuh (tanpa #fragment) sebagai identitas.
  assertNull(Selection.videoId(url))
  assertEquals(url, Selection.identity(url))
  assertEquals(url, Selection.identity("$url#caption"))
  assertNull(Selection.identity("https://evil.org/video/1"))
  val job = DownloadJob("old", url, "TikTok", MediaMode.VIDEO, "mp4", 720, 0, state = JobState.COMPLETED)
  assertTrue(job.matchesOutput(url, DownloadOptions(url, MediaMode.VIDEO, "mp4", 720)))
  assertFalse(job.matchesOutput("https://www.tiktok.com/@user/video/9999999999", DownloadOptions(url, MediaMode.VIDEO, "mp4", 720)))
 }
 @Test fun explicitQualityConstrainsAllFallbacks() { val selector=Selection.selector(DownloadOptions("https://youtu.be/x",quality=720)); assertEquals("bestvideo[height<=?720]+bestaudio/best[height<=?720]/best",selector) }
 @Test fun unknownHeightsStayEligibleAndBestIsTheLastResort() {
  // Instagram tidak selalu melaporkan height; `<=?` harus tetap mencocokkan format tersebut.
  val selector = Selection.selector(DownloadOptions("https://www.instagram.com/reel/ABC/", format="mp4", quality=1080))
  org.junit.Assert.assertTrue(selector.contains("height<=?1080"))
  org.junit.Assert.assertTrue(selector.endsWith("/best"))
 }
 @Test fun mp4UsesCompatibleAudio() { assertTrue(Selection.selector(DownloadOptions("https://youtu.be/x",format="mp4")).contains("bestaudio[ext=m4a]")) }
 @Test(expected=IllegalArgumentException::class) fun rejectsFormatForWrongMode() { Selection.validate(DownloadOptions("https://youtu.be/x",mode=MediaMode.AUDIO,format="mkv")) }
 @Test fun acceptsPlaylistPageButRejectsItAsSingleVideo() {
  val playlist="https://www.youtube.com/playlist?list=PLabc123"
  assertTrue(Selection.validPlaylistUrl(playlist))
  assertFalse(Selection.validUrl(playlist))
 }
 @Test fun detectsPlaylistParamOnWatchLinks() {
  assertTrue(Selection.validUrl("https://www.youtube.com/watch?v=abc123&list=PLabc"))
  assertTrue(Selection.hasListParam("https://www.youtube.com/watch?v=abc123&list=PLabc"))
  assertFalse(Selection.hasListParam("https://www.youtube.com/watch?v=abc123"))
 }
 @Test fun rejectsUnsafePlaylistUrls() {
  listOf("http://www.youtube.com/playlist?list=x","https://youtube.com.evil.org/playlist?list=x","https://evil.org/playlist?list=x","https://user@youtube.com/playlist?list=x","https://www.youtube.com/playlist").forEach { assertFalse(it,Selection.validPlaylistUrl(it)) }
 }
 @Test fun acceptsExtendedFormats() {
  Selection.validate(DownloadOptions("https://youtu.be/x",mode=MediaMode.VIDEO,format="mov"))
  Selection.validate(DownloadOptions("https://youtu.be/x",mode=MediaMode.VIDEO,format="avi"))
  Selection.validate(DownloadOptions("https://youtu.be/x",mode=MediaMode.AUDIO,format="aac"))
  Selection.validate(DownloadOptions("https://youtu.be/x",mode=MediaMode.AUDIO,format="vorbis"))
  Selection.validate(DownloadOptions("https://youtu.be/x",quality=4320))
 }
 @Test fun subtitleChoicesAreWhitelisted() {
  Selection.validate(DownloadOptions("https://youtu.be/x",subtitles="asli"))
  Selection.validate(DownloadOptions("https://youtu.be/x",subtitles="id"))
  Selection.validate(DownloadOptions("https://youtu.be/x",subtitles="en"))
  Selection.validate(DownloadOptions("https://youtu.be/x",subtitles=".*"))
  assertThrows(IllegalArgumentException::class.java) { Selection.validate(DownloadOptions("https://youtu.be/x",subtitles="id; rm -rf /")) }
 }
 @Test fun embedSubsOnlyValidForVideo() {
  Selection.validate(DownloadOptions("https://youtu.be/x",mode=MediaMode.VIDEO,subtitles="en",embedSubs=true))
  assertThrows(IllegalArgumentException::class.java) { Selection.validate(DownloadOptions("https://youtu.be/x",mode=MediaMode.AUDIO,subtitles="en",embedSubs=true)) }
 }
 @Test fun thumbnailOptionsAllowedForBothModes() {
  Selection.validate(DownloadOptions("https://youtu.be/x",mode=MediaMode.VIDEO,thumbnail=true,embedThumb=true))
  Selection.validate(DownloadOptions("https://youtu.be/x",mode=MediaMode.AUDIO,thumbnail=true,embedThumb=true))
 }
 @Test fun listParamExtractsPlaylistId() {
  assertEquals("PLabc123",Selection.listParam("https://www.youtube.com/playlist?list=PLabc123"))
  assertEquals("RDMMxyz9",Selection.listParam("https://www.youtube.com/watch?v=abc&list=RDMMxyz9"))
  assertNull(Selection.listParam("https://youtu.be/abc"))
  assertNull(Selection.listParam("https://www.youtube.com/watch?v=abc"))
 }
 @Test fun coverEmbedLimitedToFormatsSupportedWithoutMutagen() {
  assertTrue(Selection.coverSupported(MediaMode.AUDIO,"auto"))
  assertTrue(Selection.coverSupported(MediaMode.AUDIO,"mp3"))
  assertTrue(Selection.coverSupported(MediaMode.AUDIO,"m4a"))
  listOf("flac","opus","vorbis","aac","wav").forEach { assertFalse(it,Selection.coverSupported(MediaMode.AUDIO,it)) }
  assertFalse(Selection.coverSupported(MediaMode.VIDEO,"mp4"))
 }
 @Test fun autoFolderRequiresArtistEvidenceInEitherTitleOrder() {
  listOf("Mix - DENNY CAKNAN - SATRU 2", "Mix - SATRU 2 - DENNY CAKNAN").forEach { title ->
   assertNull(Selection.playlistFolder(title, "Label Channel"))
   assertEquals("mix-denny-caknan", Selection.playlistFolder(title, provenArtist="Denny Caknan"))
  }
 }
 @Test fun remixCreatorAndPlaylistOwnerNeverBecomeArtistFolders() {
  assertNull(Selection.playlistFolder("Mix - DJ JAUH KO PERGI", "Maman Fvndy Rmx"))
  assertNull(Selection.playlistFolder("Playlist Favorit", "Random uploader"))
  assertNull(Selection.playlistFolder("Mix - SATRU 2", "Denny Caknan - Topic"))
 }
 @Test fun playlistFolderPrefersProvenArtistOverChannel() {
  assertEquals("mix-denny-caknan", Selection.playlistFolder("Mix - SATRU", "Label", "Denny Caknan"))
  assertEquals("denny-caknan", Selection.artistFolder("Denny Caknan"))
  assertNull(Selection.artistFolder(""))
 }
 @Test fun platformNamesRecognizeKnownHosts() {
  assertEquals("YouTube", Selection.platform("https://youtu.be/abc123"))
  assertEquals("YouTube", Selection.platform("https://www.youtube.com/watch?v=abc123"))
  assertEquals("TikTok", Selection.platform("https://www.tiktok.com/@user/video/1"))
  assertEquals("SoundCloud", Selection.platform("https://soundcloud.com/artist/track"))
  assertEquals("Instagram", Selection.platform("https://www.instagram.com/reel/ABC/"))
  assertEquals("Facebook", Selection.platform("https://www.facebook.com/watch/?v=1"))
  assertEquals("Facebook", Selection.platform("https://fb.watch/abc"))
  assertEquals("X", Selection.platform("https://twitter.com/user/status/1"))
  // Host asing tetap bernama hostnya (tanpa www.) — bukan null, supaya chip Riwayat tetap muncul.
  assertNull(Selection.platform("https://www.example.com/video/1"))
  assertNull(Selection.platform("bukan url"))
 }
 @Test fun playlistFolderSlugsAreAlwaysSafeSubfolders() {
  listOf("Denny Caknan", "Björk", "歌手", "Artis ".repeat(30)).forEach { artist ->
   val folder=Selection.artistFolder(artist)!!
   assertEquals(folder, Selection.safeFolder(folder))
  }
  assertNull(Selection.playlistFolder("???", "Uploader"))
 }

 @Test fun safeFolderAcceptsPlainNamesOnly() {
  assertEquals("TubeFlow",Selection.safeFolder("TubeFlow"))
  assertEquals("Musik Kue",Selection.safeFolder("  Musik Kue  "))
  assertEquals("Lagu (2024)-baru_v2",Selection.safeFolder("Lagu (2024)-baru_v2"))
  listOf("","   ".trim(),".","../etc","my/folder","my%folder","a\\b","folder.","x".repeat(41)).forEach { assertNull(it,Selection.safeFolder(it)) }
 }
 @Test fun acceptsShortAndEmbeddedPlaylistLinks() {
  listOf(
   "https://youtu.be/abc123?list=PLabc123",
   "https://www.youtube.com/watch?v=abc123&list=PLabc123",
   "https://www.youtube.com/shorts/abc123?list=PLabc123",
   "https://www.youtube.com/live/abc123?list=PLabc123",
  ).forEach { assertTrue(it, Selection.validPlaylistUrl(it)) }
  // Tanpa param list tetap bukan playlist; host asing yang membawa list tetap ditolak (BUG-007).
  assertFalse(Selection.validPlaylistUrl("https://www.youtube.com/watch?v=abc123"))
  assertFalse(Selection.validPlaylistUrl("https://www.youtube.com/shorts/abc123"))
  assertFalse(Selection.validPlaylistUrl("https://evil.org/watch?v=abc123&list=PLabc123"))
 }
 @Test fun canonicalizesPlaylistLinksFromAnyYoutubeHost() {
  listOf(
   "https://www.youtube.com/watch?v=abc&list=PLabc123",
   "https://m.youtube.com/watch?v=abc&list=PLabc123",
   "https://www.youtube.com/shorts/abc?list=PLabc123",
   "https://youtu.be/abc?list=PLabc123",
   "https://www.youtube.com/playlist?list=PLabc123",
  ).forEach { assertEquals(it, "https://www.youtube.com/playlist?list=PLabc123", Selection.canonicalPlaylistUrl(it)) }
  assertNull(Selection.canonicalPlaylistUrl("https://youtu.be/abc"))
  assertNull(Selection.canonicalPlaylistUrl("https://evil.org/watch?v=abc&list=PLabc123"))
 }
 @Test fun matchesOutputComparesEveryOutputOption() {
  val url="https://www.youtube.com/watch?v=abc123"
  fun job() = DownloadJob("id-1",url,"Judul",MediaMode.VIDEO,"mp4",720,0)
  val base = DownloadOptions(url, MediaMode.VIDEO, "mp4", 720, 0, "", false, false, false)
  assertTrue(job().matchesOutput(url, base))
  // Setiap opsi keluaran membedakan hasil — MP3 128 bukan duplikat MP3 320 (BUG-005).
  assertFalse(job().matchesOutput("https://www.youtube.com/watch?v=other", base))
  assertFalse(job().matchesOutput(url, base.copy(mode = MediaMode.AUDIO)))
  assertFalse(job().matchesOutput(url, base.copy(format = "mkv")))
  assertFalse(job().matchesOutput(url, base.copy(quality = 1080)))
  assertFalse(job().matchesOutput(url, base.copy(bitrate = 320)))
  assertFalse(job().matchesOutput(url, base.copy(subtitles = "id")))
  assertFalse(job().matchesOutput(url, base.copy(embedSubs = true)))
  assertFalse(job().matchesOutput(url, base.copy(thumbnail = true)))
  assertFalse(job().matchesOutput(url, base.copy(embedThumb = true)))
 }
 @Test fun extendedVideoSelectorPrefersCompatibleCodec() {
  assertTrue(Selection.selector(DownloadOptions("https://youtu.be/x",format="mov")).contains("[vcodec^=avc1]"))
  assertEquals("bestvideo[height<=?720][ext=mp4][vcodec^=avc1]+bestaudio[ext=m4a]/best[height<=?720][ext=mp4][vcodec^=avc1]/bestvideo[height<=?720][ext=mp4]+bestaudio[ext=m4a]/best[height<=?720][ext=mp4]/best",Selection.selector(DownloadOptions("https://youtu.be/x",format="avi",quality=720)))
  assertEquals("bestvideo[height<=?720]+bestaudio/best[height<=?720]/best",Selection.selector(DownloadOptions("https://youtu.be/x",format="auto",quality=720)))
 }
 @Test fun audioManualBitrateKeepsCrossContainerWithFallback() {
  // Wadah sumber sengaja berbeda: yt-dlp me-remux (copy) bila codec cocok dan --audio-quality diabaikan —
  // lintas wadah memaksa re-encode sesuai bitrate pilihan. Fallback mencegah gagal total (BUG-024).
  assertEquals("bestaudio[ext=webm]/bestaudio/best",Selection.selector(DownloadOptions("https://youtu.be/x",mode=MediaMode.AUDIO,format="m4a",bitrate=192)))
  assertEquals("bestaudio[ext=m4a]/bestaudio/best",Selection.selector(DownloadOptions("https://youtu.be/x",mode=MediaMode.AUDIO,format="opus",bitrate=192)))
  assertEquals("bestaudio/best",Selection.selector(DownloadOptions("https://youtu.be/x",mode=MediaMode.AUDIO,format="mp3",bitrate=192)))
  assertEquals("bestaudio/best",Selection.selector(DownloadOptions("https://youtu.be/x",mode=MediaMode.AUDIO,format="m4a")))
 }
}
