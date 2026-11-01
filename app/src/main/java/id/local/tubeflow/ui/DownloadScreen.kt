package id.local.tubeflow.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FeaturedPlayList
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import id.local.tubeflow.core.BstationSeason
import id.local.tubeflow.core.BstationUrls
import id.local.tubeflow.core.DownloadOptions
import id.local.tubeflow.core.Format
import id.local.tubeflow.core.InspectionPhase
import id.local.tubeflow.core.MediaMode
import id.local.tubeflow.core.PlaylistEntry
import id.local.tubeflow.core.PlaylistMetadata
import id.local.tubeflow.core.Selection
import id.local.tubeflow.core.SettingsStore
import id.local.tubeflow.core.SourcePlatform
import id.local.tubeflow.core.SourcePlatforms
import id.local.tubeflow.core.Spotify
import id.local.tubeflow.core.SpotifyCandidate
import id.local.tubeflow.core.SpotifyCollection
import id.local.tubeflow.core.SpotifyCollectionPreview
import id.local.tubeflow.core.SpotifyKind
import id.local.tubeflow.core.SpotifyPreview
import id.local.tubeflow.core.SpotifyTrack
import id.local.tubeflow.core.SubtitlePicker
import id.local.tubeflow.core.VideoMetadata
import kotlinx.coroutines.delay
import java.util.Locale

private data class Preset(val label: String, val mode: MediaMode, val format: String, val quality: Int, val bitrate: Int)

private val videoPresets = listOf(
    Preset("Otomatis", MediaMode.VIDEO, "auto", 0, 0),
    Preset("1080p MP4", MediaMode.VIDEO, "mp4", 1080, 0),
    Preset("720p MP4", MediaMode.VIDEO, "mp4", 720, 0),
    Preset("4K MP4", MediaMode.VIDEO, "mp4", 2160, 0),
)
private val audioPresets = listOf(
    Preset("Otomatis", MediaMode.AUDIO, "auto", 0, 0),
    Preset("MP3 320", MediaMode.AUDIO, "mp3", 0, 320),
    Preset("MP3 128", MediaMode.AUDIO, "mp3", 0, 128),
    Preset("M4A terbaik", MediaMode.AUDIO, "m4a", 0, 0),
    Preset("FLAC lossless", MediaMode.AUDIO, "flac", 0, 0),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadComposer(
    url: String,
    onUrl: (String) -> Unit,
    metadata: VideoMetadata?,
    playlist: PlaylistMetadata?,
    busy: Boolean,
    inspectPhase: String?,
    inspectStartedAt: Long,
    inspectError: String?,
    inspectDetails: id.local.tubeflow.core.FailureDetails? = null,
    bstationSignedIn: Boolean = false,
    onBstationLogin: () -> Unit = {},
    inspect: () -> Unit,
    inspectPlaylist: () -> Unit,
    retryInspect: () -> Unit,
    clearPlaylist: () -> Unit,
    cancelInspect: () -> Unit,
    download: (DownloadOptions) -> Unit,
    downloadPlaylist: (List<PlaylistEntry>, DownloadOptions) -> Unit,
    spotify: SpotifyPreview = SpotifyPreview(),
    inspectSpotify: () -> Unit = {},
    clearSpotify: () -> Unit = {},
    downloadSpotify: (DownloadOptions, SpotifyCandidate) -> Unit = { _, _ -> },
    spotifyCollection: SpotifyCollectionPreview = SpotifyCollectionPreview(),
    inspectSpotifyCollection: () -> Unit = {},
    clearSpotifyCollection: () -> Unit = {},
    matchSpotifyTracks: (List<SpotifyTrack>) -> Unit = {},
    cancelSpotifyMatch: () -> Unit = {},
    downloadSpotifyCollection: (DownloadOptions, List<SpotifyTrack>) -> Unit = { _, _ -> },
    focusComposer: Boolean = false,
    onFocusHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val composerFocus = remember { FocusRequester() }
    var linkFocused by remember { mutableStateOf(false) }
    LaunchedEffect(focusComposer) {
        if (focusComposer) {
            runCatching { composerFocus.requestFocus() }
            onFocusHandled()
        }
    }
    var mode by rememberSaveable { mutableStateOf(MediaMode.VIDEO) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var videoFormat by rememberSaveable { mutableStateOf("mp4") }
    var audioFormat by rememberSaveable { mutableStateOf("mp3") }
    // A separate saveable group discards the former pixel-height UI state on upgrade.
    var qualityTier by key("video-quality-tier") { rememberSaveable { mutableIntStateOf(0) } }
    var bitrate by rememberSaveable { mutableIntStateOf(0) }
    var preset by key("tier-aware-preset") { rememberSaveable { mutableStateOf("") } }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var playlistQuery by rememberSaveable { mutableStateOf("") }
    var showFailureDetails by remember(url.trim()) { mutableStateOf(false) }
    if (showFailureDetails && inspectDetails != null) FailureDetailsDialog(inspectDetails) { showFailureDetails = false }
    var inspectedUrl by rememberSaveable { mutableStateOf("") }
    var spotifyCandidateUrl by rememberSaveable { mutableStateOf("") }
    var selectedSpotifyIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var spotifyCollectionQuery by rememberSaveable { mutableStateOf("") }

    var subtitleLang by rememberSaveable { mutableStateOf("") }
    // Lirik audio hanya on/off dari penyedia lirik — pilihan bahasa berlaku untuk subtitle video (BUG-009).
    var lyrics by rememberSaveable { mutableStateOf(SettingsStore.autoLyrics) }
    var embedSubs by rememberSaveable { mutableStateOf(false) }
    var embedThumb by rememberSaveable { mutableStateOf(SettingsStore.autoArtwork) }
    LaunchedEffect(SettingsStore.autoLyrics) { lyrics = SettingsStore.autoLyrics }
    LaunchedEffect(SettingsStore.autoArtwork) { embedThumb = SettingsStore.autoArtwork }

    val trimmed = url.trim()
    val isVideo = Selection.validUrl(trimmed)
    val isPlaylistLink = Selection.validPlaylistUrl(trimmed)
    val hasList = Selection.hasListParam(trimmed)
    val shownPlaylist = playlist?.takeIf { it.url == trimmed }
    val shownVideo = metadata?.takeIf { it.url == trimmed && (shownPlaylist == null) }
    val isSpotifyTrackLink = Spotify.isTrackLink(trimmed)
    val isSpotifyCollectionLink = Spotify.isCollectionLink(trimmed)
    val isSpotifyLink = isSpotifyTrackLink || isSpotifyCollectionLink
    val isBstationSeason = BstationUrls.isSeasonOnly(trimmed)
    val isCollectionLink = isPlaylistLink || isBstationSeason
    val shownSeason = shownPlaylist?.let { BstationUrls.isSeasonOnly(it.url) } == true
    val spotifyTrack = spotify.track?.takeIf { spotify.url == trimmed }
    val spotifyCollectionData = spotifyCollection.collection?.takeIf { spotifyCollection.url == trimmed }
    val spotifyBusy = if (isSpotifyCollectionLink) spotifyCollection.busy else spotify.busy
    val selectedSpotify = spotify.candidates.firstOrNull { it.video.url == spotifyCandidateUrl } ?: spotify.candidates.firstOrNull()
    val spotifyTracks = spotifyCollectionData?.tracks.orEmpty()
    val visibleSpotifyTracks = if (spotifyCollectionQuery.isBlank()) spotifyTracks
        else spotifyTracks.filter {
            it.title.contains(spotifyCollectionQuery.trim(), ignoreCase = true) ||
                it.artists.contains(spotifyCollectionQuery.trim(), ignoreCase = true)
        }
    val spotifyVisibleSelectable = visibleSpotifyTracks.filter { it.playable }
    val spotifyAllVisibleSelected = spotifyVisibleSelectable.isNotEmpty() && spotifyVisibleSelectable.all { it.id in selectedSpotifyIds }
    val spotifySelectedTracks = spotifyTracks.filter { it.id in selectedSpotifyIds }
    val spotifyUnmatchedTracks = spotifySelectedTracks.filter { !spotifyCollection.matches.containsKey(it.id) }
    val spotifyMatchedSelected = spotifySelectedTracks.count { spotifyCollection.matches.containsKey(it.id) }
    // Track Spotify selalu audio — tidak ada video yang bisa diunduh dari tautannya.
    LaunchedEffect(isSpotifyLink) { if (isSpotifyLink) { mode = MediaMode.AUDIO; preset = "" } }
    LaunchedEffect(spotify.url, spotify.candidates) {
        if (spotify.candidates.none { it.video.url == spotifyCandidateUrl }) {
            spotifyCandidateUrl = spotify.candidates.firstOrNull()?.video?.url.orEmpty()
        }
    }
    // Pilihan awal koleksi: semua track yang tersedia di Spotify.
    LaunchedEffect(spotifyCollectionData?.url) {
        selectedSpotifyIds = spotifyCollectionData?.tracks?.filter { it.playable }?.map { it.id }?.toSet().orEmpty()
        spotifyCollectionQuery = ""
    }
    var inspectElapsed by remember(busy, inspectStartedAt) { mutableLongStateOf(0L) }
    LaunchedEffect(busy, inspectStartedAt) {
        if (!busy) { inspectElapsed = 0L; return@LaunchedEffect }
        while (true) {
            inspectElapsed = inspectStartedAt.takeIf { it > 0 }
                ?.let { ((System.currentTimeMillis() - it) / 1000).coerceAtLeast(0) } ?: 0L
            delay(1000)
        }
    }
    val inspectProgressText = (inspectPhase?.let(InspectionPhase::label) ?: "Membaca detail link…") +
        if (inspectElapsed >= 3) " · ${inspectElapsed} dtk" else ""

    // Auto-baca detail: jeda singkat lalu inspect tanpa tombol "Cek link".
    LaunchedEffect(trimmed) {
        if (isSpotifyLink) {
            inspectedUrl = ""
            // Pratinjau jenis lain (track ↔ album/playlist) tidak boleh tertinggal di memori.
            if (!isSpotifyTrackLink && spotify.url.isNotEmpty()) clearSpotify()
            if (!isSpotifyCollectionLink && spotifyCollection.url.isNotEmpty()) clearSpotifyCollection()
            if (isSpotifyTrackLink) {
                if (spotify.url != trimmed) { delay(400); inspectSpotify() }
            } else if (spotifyCollection.url != trimmed) {
                delay(400); inspectSpotifyCollection()
            }
            return@LaunchedEffect
        }
        if (spotify.url.isNotEmpty()) clearSpotify()
        if (spotifyCollection.url.isNotEmpty()) clearSpotifyCollection()
        if (!isVideo && !isCollectionLink) { inspectedUrl = ""; return@LaunchedEffect }
        delay(700)
        if (shownVideo?.url == trimmed || shownPlaylist?.url == trimmed) { inspectedUrl = trimmed; return@LaunchedEffect }
        if (inspectedUrl == trimmed) return@LaunchedEffect
        inspectedUrl = trimmed
        // Tautan yang juga video (watch/shorts/youtu.be + list) tetap membuka video; buka playlist lewat tombol (BUG-007).
        if (isCollectionLink && !isVideo) inspectPlaylist() else inspect()
    }

    fun availableTier(requestedTier: Int): Int =
        Selection.resolutionForTier(requestedTier, shownVideo?.resolutions.orEmpty()).tier

    fun applyPreset(p: Preset) {
        mode = p.mode; preset = p.label
        if (p.mode == MediaMode.VIDEO) {
            videoFormat = p.format
            val target = availableTier(p.quality)
            qualityTier = target
            if (target != p.quality) preset = ""
        }
        else { audioFormat = p.format; bitrate = p.bitrate }
    }

    LaunchedEffect(shownVideo?.url, shownVideo?.resolutions) {
        val target = availableTier(qualityTier)
        if (target != qualityTier) { qualityTier = target; preset = "" }
    }

    val resolutionOptions = run {
        val heights = shownVideo?.resolutions.orEmpty()
        if (heights.isEmpty()) listOf(0 to "Auto · terbaik")
        else listOf(0 to "Auto · terbaik") + heights.map { it.tier to "${it.tier}p" }
    }

    val presets = if (mode == MediaMode.VIDEO) {
        val resolutions = shownVideo?.resolutions.orEmpty()
        videoPresets.filter { p -> p.quality == 0 || resolutions.isEmpty() || resolutions.any { it.tier == p.quality } }
    } else audioPresets
    val activePreset = presets.find { it.label == preset }

    fun buildOptions(target: String) = DownloadOptions(
        url = target,
        mode = mode,
        format = if (mode == MediaMode.VIDEO) videoFormat else audioFormat,
        quality = if (mode == MediaMode.VIDEO) Selection.heightForTier(qualityTier, shownVideo?.resolutions.orEmpty()) else 0,
        bitrate = if (mode == MediaMode.AUDIO && audioFormat !in listOf("flac", "wav", "vorbis")) bitrate else 0,
        subtitles = if (mode == MediaMode.VIDEO) subtitleLang else if (lyrics) "asli" else "",
        embedSubs = embedSubs && subtitleLang.isNotEmpty() && mode == MediaMode.VIDEO,
        thumbnail = false,
        // Spotify: sampul album hanya dipakai di aplikasi; sematan file akan memakai thumbnail video YouTube.
        embedThumb = embedThumb && !isSpotifyLink && mode == MediaMode.AUDIO && Selection.coverSupported(mode, audioFormat),
    )

    val entries = shownPlaylist?.entries.orEmpty()
    val visibleEntries = if (playlistQuery.isBlank()) entries else entries.filter { it.title.contains(playlistQuery.trim(), ignoreCase = true) }
    val selectableVisible = if (shownSeason) visibleEntries.filterNot { BstationSeason.premiumLocked(it, bstationSignedIn) } else visibleEntries
    val allSelected = selectableVisible.isNotEmpty() && selectableVisible.all { it.id in selectedIds }
    val selectionCount = entries.count { it.id in selectedIds }
    val seasonEpisodeCount = entries.count { it.episodeNumber != null }
    val seasonSpecialCount = entries.count { it.episodeNumber == null }
    val seasonPremiumCount = entries.count { it.premium == true }

    // Status login ikut menjadi kunci: keluar akun membuang pilihan Premium yang sudah dicentang.
    LaunchedEffect(shownPlaylist?.url, bstationSignedIn) {
        val current = shownPlaylist
        selectedIds = when {
            current == null -> emptySet()
            BstationUrls.isSeasonOnly(current.url) -> BstationSeason.initialSelection(current.entries)
            else -> current.entries.map { it.id }.toSet()
        }
        playlistQuery = ""
    }

    Column(
        // Ketuk di luar kolom isian melepas fokus kursor, termasuk saat keyboard masih terbuka.
        modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
        verticalArrangement = Arrangement.spacedBy(UiSpacing.item),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = { onUrl(it); if (shownPlaylist != null) clearPlaylist() },
                modifier = Modifier.fillMaxWidth().focusRequester(composerFocus).onFocusChanged { linkFocused = it.isFocused },
                label = { Text("Link video, season, playlist, atau lagu Spotify") },
                placeholder = { Text("https://youtu.be/… , bilibili.tv/play/… , playlist?list=… , atau open.spotify.com/track/…") },
                singleLine = true,
                isError = url.isNotBlank() && !isVideo && !isCollectionLink && !isSpotifyLink,
                supportingText = {
                    when {
                        busy -> Text(inspectProgressText)
                        spotifyBusy -> Text(
                            (if (isSpotifyCollectionLink) spotifyCollection.phase else spotify.phase)
                                ?.let(InspectionPhase::label) ?: "Membaca link Spotify…"
                        )
                        isBstationSeason -> Text(if (shownPlaylist != null) "Season Bstation · pilih episode di bawah." else BstationUrls.SEASON_LOADING)
                        url.isNotBlank() && !isVideo && !isCollectionLink && !isSpotifyLink -> Text("Link belum didukung — gunakan https dari platform yang tersedia.")
                        isPlaylistLink -> Text("Terdeteksi link playlist.")
                        isSpotifyLink -> Text("Terdeteksi link Spotify — audio diambil dari video YouTube yang cocok.")
                        hasList -> Text("Link ini bagian dari sebuah playlist.")
                        else -> Text("Tempel tautan video, playlist, season, atau lagu. Ketuk × untuk membatalkan isian.")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (busy || spotifyBusy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else if (url.isEmpty()) {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val text = runCatching { clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
                                val link = text?.let(::extractLink).orEmpty()
                                onUrl(link)
                                clearPlaylist()
                            }) { Icon(Icons.Outlined.ContentPaste, "Tempel link dari papan klip") }
                        }
                        // Selama fokus, × tetap tersedia walau kolom kosong supaya input bisa dibatalkan.
                        if (url.isNotEmpty() || linkFocused) {
                            IconButton(onClick = {
                                onUrl("")
                                clearPlaylist()
                                linkFocused = false
                                focusManager.clearFocus()
                            }) { Icon(Icons.Outlined.Close, if (url.isEmpty()) "Batalkan input link" else "Hapus link") }
                        }
                    }
                },
            )
            SupportedPlatforms()
            if (busy || spotifyBusy) {
                TextButton(onClick = {
                    when {
                        isSpotifyCollectionLink -> clearSpotifyCollection()
                        spotify.busy -> clearSpotify()
                        else -> cancelInspect()
                    }
                }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Icon(Icons.Outlined.Close, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Batalkan pemeriksaan")
                }
            }
            // Dibatalkan atau gagal tanpa pesan: tetap sediakan jalan membaca ulang tautan yang sama.
            if (isSpotifyTrackLink && !spotify.busy && spotifyTrack == null && spotify.error == null) {
                ActionButton("Baca ulang link Spotify", inspectSpotify, icon = Icons.Outlined.Refresh, emphasis = ActionEmphasis.Quiet)
            }
            if (isSpotifyCollectionLink && !spotifyCollection.busy && spotifyCollectionData == null && spotifyCollection.error == null) {
                ActionButton("Baca ulang link Spotify", inspectSpotifyCollection, icon = Icons.Outlined.Refresh, emphasis = ActionEmphasis.Quiet)
            }
            if (spotifyCollection.error != null && !spotifyCollection.busy && isSpotifyCollectionLink) {
                AdaptivePair(leading = { available -> Row(available, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(spotifyCollection.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                } }, trailing = {
                    ActionButton("Coba lagi", { inspectSpotifyCollection() }, icon = Icons.Outlined.Refresh, emphasis = ActionEmphasis.Quiet)
                })
            }
            if (spotify.error != null && !spotify.busy && isSpotifyTrackLink) {
                AdaptivePair(leading = { available -> Row(available, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(spotify.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                } }, trailing = {
                    ActionButton("Coba lagi", { inspectSpotify() }, icon = Icons.Outlined.Refresh, emphasis = ActionEmphasis.Quiet)
                })
            }
            if (hasList && isVideo) {
                ActionButton(
                    "Link ini punya playlist — buka sebagai playlist",
                    inspectPlaylist,
                    icon = Icons.AutoMirrored.Outlined.FeaturedPlayList,
                    emphasis = ActionEmphasis.Quiet,
                    enabled = !busy,
                    fillWidth = true,
                )
            }
            // Inspeksi gagal untuk URL ini: sediakan coba lagi tanpa perlu mengganti tautan (BUG-006).
            if (inspectDetails != null && !busy && trimmed.isNotBlank()) {
                ActionButton(
                    androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.failure_details),
                    { showFailureDetails = true },
                    icon = Icons.Outlined.Info,
                    emphasis = ActionEmphasis.Quiet,
                )
            }
            if (inspectError != null && !busy && trimmed.isNotBlank() && (isVideo || isCollectionLink)) {
                AdaptivePair(leading = { available -> Row(available, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        inspectError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                } }, trailing = {
                    ActionButton(
                        "Coba lagi",
                        {
                            inspectedUrl = ""
                            // Retry mengulang jenis inspeksi terakhir — playlist yang gagal tidak diulang sebagai video (BUG-006/R6).
                            retryInspect()
                        },
                        icon = Icons.Outlined.Refresh,
                        emphasis = ActionEmphasis.Quiet,
                    )
                })
            }
        }

        if (shownVideo != null) {
            AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                VideoPreviewCard(shownVideo, if (mode == MediaMode.VIDEO) subtitleLang else "")
            }
        }

        if (spotifyTrack != null) {
            AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                SpotifyPreviewCard(spotifyTrack, spotify.candidates, selectedSpotify?.video?.url.orEmpty()) { spotifyCandidateUrl = it }
            }
        }

        if (spotifyCollectionData != null) {
            AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                SpotifyCollectionCard(
                    collection = spotifyCollectionData,
                    state = spotifyCollection,
                    query = spotifyCollectionQuery,
                    onQuery = { spotifyCollectionQuery = it },
                    selected = selectedSpotifyIds,
                    visible = visibleSpotifyTracks,
                    allVisibleSelected = spotifyAllVisibleSelected,
                    unmatchedCount = spotifyUnmatchedTracks.size,
                    onToggle = { id ->
                        selectedSpotifyIds = if (id in selectedSpotifyIds) selectedSpotifyIds - id else selectedSpotifyIds + id
                    },
                    onToggleAll = {
                        selectedSpotifyIds = if (spotifyAllVisibleSelected) selectedSpotifyIds - spotifyVisibleSelectable.map { it.id }.toSet()
                        else selectedSpotifyIds + spotifyVisibleSelectable.map { it.id }
                    },
                    onMatch = { matchSpotifyTracks(spotifyUnmatchedTracks) },
                    onCancelMatch = cancelSpotifyMatch,
                )
            }
        }

        if (shownPlaylist != null) {
            SectionCard {
                    Column(Modifier.padding(UiSpacing.panel), verticalArrangement = Arrangement.spacedBy(UiSpacing.row)) {
                        AdaptivePair(leading = { available -> Row(available, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Outlined.FeaturedPlayList, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(shownPlaylist.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                Text(
                                    when {
                                        shownSeason -> buildList {
                                            if (seasonEpisodeCount > 0) add("$seasonEpisodeCount episode")
                                            if (seasonSpecialCount > 0) add("$seasonSpecialCount spesial")
                                            if (seasonPremiumCount > 0) add("$seasonPremiumCount Premium")
                                        }.ifEmpty { listOf("${entries.size} episode") }.joinToString(" · ")
                                        Selection.isMix(shownPlaylist.url) -> "${entries.size} video dalam antrean awal"
                                        else -> "${entries.size} video · mengikuti isi link (maks 500)"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } }, trailing = {
                            ActionButton(
                                if (allSelected) "Kosongkan" else "Pilih semua",
                                { selectedIds = if (allSelected) emptySet() else selectedIds + selectableVisible.map { it.id } },
                                emphasis = ActionEmphasis.Quiet,
                            )
                        })
                        if (shownSeason) {
                            Text(
                                "Daftar mengikuti episode yang tersedia di wilayah ini." +
                                    if (seasonPremiumCount > 0) " Episode Premium hanya bisa diunduh dengan akun yang berlangganan." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (seasonPremiumCount > 0) {
                                Text(
                                    if (bstationSignedIn) "Kamu sudah masuk. Pilih episode Premium yang ingin diantrekan — aksesnya tetap mengikuti langganan akun."
                                    else "$seasonPremiumCount episode Premium terkunci. Masuk ke Bstation untuk membuka pilihannya.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (bstationSignedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                                if (!bstationSignedIn) ActionButton(
                                    "Masuk Bstation untuk membuka Premium",
                                    onBstationLogin,
                                    icon = Icons.AutoMirrored.Outlined.Login,
                                    fillWidth = true,
                                    enabled = !busy,
                                )
                            }
                        } else if (Selection.isMix(shownPlaylist.url)) {
                            Text(
                                "Antrean awal Mix dari YouTube (± video terkait yang sedang diputar) — tanpa memuat halaman lanjutan. Daftar dapat berubah dan berbeda dari akun YouTube Anda.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SearchField(playlistQuery, { playlistQuery = it }, label = if (shownSeason) "Cari episode atau judul" else "Cari video dalam playlist")
                        if (entries.isEmpty()) {
                            Text(if (shownSeason) BstationUrls.SEASON_EMPTY else "Playlist kosong atau tidak bisa dibaca.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(if (shownSeason) "Pilih episode yang mau diantrekan:" else "Pilih video yang mau diantrekan:", style = MaterialTheme.typography.labelLarge)
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                visibleEntries.forEach { entry ->
                                    val premiumLocked = shownSeason && BstationSeason.premiumLocked(entry, bstationSignedIn)
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = entry.id in selectedIds,
                                            enabled = !premiumLocked,
                                            onCheckedChange = { checked ->
                                                selectedIds = if (checked) selectedIds + entry.id else selectedIds - entry.id
                                            },
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(entry.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                            if (shownSeason) {
                                                val note = when {
                                                    entry.premium == true && bstationSignedIn -> "Premium · hanya untuk akun berlangganan"
                                                    entry.premium == true -> "Premium · masuk untuk memilih"
                                                    entry.episodeNumber == null -> "Spesial"
                                                    else -> null
                                                }
                                                if (note != null) Text(
                                                    note,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (entry.premium == true && !bstationSignedIn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            } else if (entry.durationSeconds > 0) {
                                                Text(Format.duration(entry.durationSeconds), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                            if (visibleEntries.isEmpty()) {
                                Text("Tidak ada ${if (shownSeason) "episode" else "video"} yang cocok dengan \"$playlistQuery\".", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                if (playlistQuery.isBlank()) {
                                    if (shownSeason && seasonPremiumCount > 0) "$selectionCount / ${entries.size} terpilih · $seasonPremiumCount Premium " + if (bstationSignedIn) "dapat dipilih" else "terkunci"
                                    else "$selectionCount / ${entries.size} terpilih"
                                } else {
                                    "Menampilkan ${visibleEntries.size} dari ${entries.size} · $selectionCount terpilih"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
        }

        if (shownVideo != null || shownPlaylist != null || spotifyTrack != null || spotifyCollectionData != null) {
            Panel(
                title = if (shownPlaylist != null) {
                    if (shownSeason) "Opsi untuk semua episode terpilih" else "Opsi untuk semua video terpilih"
                } else "Pilih download",
                description = when {
                    shownPlaylist != null && shownSeason -> "Semua episode masuk satu folder: ${shownPlaylist.folderName}. Nama file memakai judul episode. " +
                        if (bstationSignedIn) "Episode Premium yang dipilih ikut diantrekan; keberhasilannya tergantung langganan akun." else "Episode Premium terkunci sampai kamu masuk ke Bstation."
                    shownPlaylist != null -> "Semua pilihan masuk satu folder: ${shownPlaylist.folderName}. Nama file tetap memakai judul dan artis/remixer masing-masing lagu."
                    spotifyCollectionData != null -> "Setiap lagu menjadi job terpisah dengan tag Spotify; audionya dari video YouTube hasil pencocokan. Semua file masuk satu folder koleksi."
                    spotifyTrack != null -> "Audio diunduh dari video YouTube yang dipilih; judul, artis, dan sampul di aplikasi memakai metadata Spotify."
                    else -> null
                },
            ) {
                    if (!isSpotifyLink) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = mode == MediaMode.VIDEO,
                            onClick = { mode = MediaMode.VIDEO; preset = "" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            label = { Text("Video") },
                            icon = { Icon(Icons.Outlined.Videocam, null, Modifier.size(18.dp)) },
                        )
                        SegmentedButton(
                            selected = mode == MediaMode.AUDIO,
                            onClick = { mode = MediaMode.AUDIO; preset = "" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            label = { Text("Audio saja") },
                            icon = { Icon(Icons.Outlined.Headphones, null, Modifier.size(18.dp)) },
                        )
                    }
                    if (isSpotifyLink) Text("Link Spotify selalu diunduh sebagai audio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Text("Pilihan cepat", style = MaterialTheme.typography.labelLarge)
                    ScrollRow {
                        presets.forEach { p ->
                            FilterChip(
                                selected = activePreset?.label == p.label,
                                onClick = { applyPreset(p) },
                                label = { Text(p.label) },
                                leadingIcon = if (activePreset?.label == p.label) {
                                    { Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) }
                                } else null,
                            )
                        }
                    }

                    AnimatedVisibility(advanced, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (mode == MediaMode.VIDEO) {
                                OptionMenu("Resolusi", resolutionOptions, qualityTier, onSelect = { qualityTier = it; preset = "" })
                                Text(
                                    if (shownVideo?.resolutions.isNullOrEmpty())
                                        "Sumber ini tidak melaporkan daftar resolusi; unduhan memakai format terbaik yang tersedia."
                                    else
                                        "Hanya resolusi yang benar-benar disediakan video ini yang muncul di daftar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OptionMenu("Format video", listOf("auto" to "Auto · format asli", "mp4" to "MP4 · kompatibel", "mkv" to "MKV", "webm" to "WebM", "mov" to "MOV · Apple", "avi" to "AVI · lama"), videoFormat, onSelect = { videoFormat = it; preset = "" })
                            } else {
                                if (audioFormat !in listOf("flac", "wav", "vorbis")) OptionMenu("Bitrate audio", listOf(0 to "Auto · terbaik", 320 to "320 kbps", 256 to "256 kbps", 192 to "192 kbps", 128 to "128 kbps", 64 to "64 kbps"), bitrate, onSelect = { bitrate = it; preset = "" })
                                OptionMenu("Format audio", listOf("auto" to "Auto · format asli", "mp3" to "MP3", "m4a" to "M4A", "aac" to "AAC", "opus" to "Opus", "vorbis" to "OGG Vorbis", "flac" to "FLAC", "wav" to "WAV"), audioFormat, onSelect = { audioFormat = it; preset = ""; if (it !in Selection.coverFormats) embedThumb = false })
                                if (audioFormat == "opus" && bitrate > 0) Text("Bitrate Opus adalah target nominal; hasil dapat bervariasi sesuai konten.", style = MaterialTheme.typography.bodySmall)
                                if (audioFormat == "auto" && bitrate > 0) Text("Bitrate manual dengan format Auto dikonversi ke MP3.", style = MaterialTheme.typography.bodySmall)
                                if (audioFormat in listOf("flac", "wav")) Text("FLAC/WAV tidak meningkatkan kualitas sumber dan menghasilkan file lebih besar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            HorizontalDivider()

                            ToggleRow(
                                title = if (mode == MediaMode.VIDEO) "Download subtitle" else "Download lirik",
                                description = if (mode == MediaMode.VIDEO) "Simpan teks subtitle (.srt) di samping file video."
                                else "Simpan lirik tersinkron (.lrc) dari penyedia lirik (LRCLIB).",
                                checked = if (mode == MediaMode.VIDEO) subtitleLang.isNotEmpty() else lyrics,
                                onCheckedChange = { enabled ->
                                    if (mode == MediaMode.VIDEO) {
                                        // Bstation & sumber non-YouTube tidak punya konsep "bahasa asli";
                                        // mulai dari bahasa yang benar-benar tersedia (atau bahasa perangkat).
                                        subtitleLang = if (enabled)
                                            SubtitlePicker.defaultSelection(shownVideo, Locale.getDefault().language)
                                        else ""
                                        if (!enabled) embedSubs = false
                                    } else {
                                        lyrics = enabled
                                    }
                                },
                            )
                            AnimatedVisibility(if (mode == MediaMode.VIDEO) subtitleLang.isNotEmpty() else lyrics, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Bahasa subtitle hanya untuk video — lirik audio selalu dari LRCLIB, tanpa pilihan bahasa (BUG-009).
                                    if (mode == MediaMode.VIDEO) {
                                        ScrollRow {
                                            SubtitlePicker.options(shownVideo).forEach { (value, label) ->
                                                FilterChip(selected = subtitleLang == value, onClick = { subtitleLang = value }, label = { Text(label) })
                                            }
                                        }
                                    }
                                    if (mode == MediaMode.VIDEO) {
                                        ToggleRow(
                                            title = "Sematkan ke file video",
                                            description = "Subtitle jadi soft-sub di pemutar (MP4/MKV).",
                                            checked = embedSubs,
                                            onCheckedChange = { embedSubs = it },
                                        )
                                    } else {
                                        Text("File lirik (.lrc) disimpan di folder yang sama dengan file audio — nama file sama, langsung terbaca di AIMP/Poweramp. Kalau lagu tidak ada di penyedia lirik, file liriknya tidak ditulis.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            if (mode == MediaMode.AUDIO) {
                                HorizontalDivider()
                                if (Selection.coverSupported(mode, audioFormat) && isSpotifyLink) {
                                    Text("Sampul Spotify dipakai di kartu, Riwayat, dan Koleksi. Sematan gambar ke file tidak diaktifkan untuk tautan Spotify karena yang tersedia hanya thumbnail video YouTube.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else if (Selection.coverSupported(mode, audioFormat)) {
                                    ToggleRow(
                                        title = "Sampul album (cover)",
                                        description = "Sampul disematkan ke file audio — tidak ada file gambar terpisah.",
                                        checked = embedThumb,
                                        onCheckedChange = { embedThumb = it },
                                    )
                                    if (embedThumb && audioFormat == "auto") Text("Auto mengikuti format asli jika bitrate tidak dipilih. Sampul disematkan bila format dan engine mendukung; preview tetap tersedia.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("Format ${audioFormat.uppercase()} belum mendukung sampul album — pilih Auto, MP3, atau M4A.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    ActionButton(
                        if (advanced) "Tutup pengaturan lanjutan" else "Pengaturan lanjutan",
                        { advanced = !advanced },
                        icon = if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        emphasis = ActionEmphasis.Quiet,
                    )
            }
        }

        if (shownVideo != null || (shownPlaylist != null && entries.isNotEmpty()) || spotifyTrack != null || spotifyCollectionData != null) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val summary = buildSummary(mode, videoFormat, audioFormat, qualityTier, bitrate, if (mode == MediaMode.VIDEO) subtitleLang else if (lyrics) "on" else "", embedThumb && mode == MediaMode.AUDIO && Selection.coverSupported(mode, audioFormat))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Tune, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(summary, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        if (shownPlaylist != null) Text(if (shownSeason) "$selectionCount episode" else "$selectionCount video", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        else if (spotifyCollectionData != null) Text("$spotifyMatchedSelected/${spotifySelectedTracks.size} cocok", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        else if (spotifyTrack != null) Text("Spotify · YouTube", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                ActionButton(
                    text = when {
                        shownPlaylist != null -> if (shownSeason) "Antrekan $selectionCount episode" else "Antrekan $selectionCount video"
                        spotifyCollectionData != null -> "Antrekan $spotifyMatchedSelected lagu"
                        spotifyTrack != null -> "Download audio"
                        mode == MediaMode.VIDEO -> "Download video"
                        else -> "Download audio"
                    },
                    onClick = {
                        when {
                            shownPlaylist != null -> downloadPlaylist(entries.filter { it.id in selectedIds }, buildOptions(shownPlaylist.url))
                            spotifyCollectionData != null -> downloadSpotifyCollection(buildOptions(""), spotifySelectedTracks.filter { spotifyCollection.matches.containsKey(it.id) })
                            spotifyTrack != null -> selectedSpotify?.let { downloadSpotify(buildOptions(it.video.url), it) }
                            else -> download(buildOptions(trimmed))
                        }
                    },
                    icon = if (shownPlaylist != null) Icons.Outlined.Queue else Icons.Outlined.Download,
                    emphasis = ActionEmphasis.Primary,
                    enabled = !busy && !spotifyBusy && !spotifyCollection.matching && when {
                        shownPlaylist != null -> selectionCount > 0 && entries.isNotEmpty()
                        spotifyCollectionData != null -> spotifyMatchedSelected > 0
                        spotifyTrack != null -> selectedSpotify != null
                        else -> isVideo
                    },
                    fillWidth = true,
                )
            }
        }

        Text(
            "Tautan Spotify memakai metadata resmi Spotify; audionya tetap dari video YouTube yang kamu pilih. Hasil tersimpan di Downloads/${SettingsStore.storageFolder}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** Domain yang didukung: satu baris ikon tanpa caption; sisanya tersembunyi agar formulir ringkas. */
@Composable
private fun SupportedPlatforms() {
    val platforms = remember { SourcePlatforms.known.filter { it.domains.isNotEmpty() } }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        items(platforms, key = { it.id }) { platform ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                Box(Modifier.padding(7.dp)) {
                    PlatformBrandLogo(platform, Modifier.size(18.dp), platform.label)
                }
            }
        }
    }
}

@Composable
private fun VideoPreviewCard(meta: VideoMetadata, subtitleLang: String) {
    SectionCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)) {
                AsyncImage(
                    model = meta.thumbnail,
                    contentDescription = "Thumbnail video",
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent,androidx.compose.ui.graphics.Color.Black.copy(alpha=0.6f)))))
                // Durasi hanya ditampilkan bila sumber melaporkannya; "0:00" menyesatkan.
                if (meta.durationSeconds > 0) Surface(
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.68f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                ) {
                    Text(
                        Format.duration(meta.durationSeconds),
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(meta.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            if (meta.uploader.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Person, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(meta.uploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(6.dp))
                val ready = if (meta.durationSeconds > 0) "Siap diunduh · ${Format.duration(meta.durationSeconds)}" else "Siap diunduh"
                Text(ready, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Hd, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (meta.resolutions.isEmpty()) "Resolusi: belum dilaporkan sumber"
                    else "Resolusi tersedia: ${meta.resolutions.joinToString(", ") { "${it.tier}p" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (meta.subtitleLangs.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.ClosedCaption, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Subtitle tersedia: ${meta.subtitleLangs.take(6).joinToString(", ") { SubtitlePicker.label(it) }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (subtitleLang.isNotEmpty()) {
                    Text(
                        when {
                            subtitleLang == SubtitlePicker.ALL -> "Semua bahasa diambil, termasuk hasil terjemahan mesin bila ada."
                            subtitleLang == SubtitlePicker.ORIGINAL -> "Hanya bahasa asli video yang diambil — tanpa terjemahan mesin."
                            else -> "Subtitle ${SubtitlePicker.label(subtitleLang)} diambil bila disediakan sumber."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Text(
                    "Video ini tidak punya subtitle/CC tercatat.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun spotifyDeltaLabel(delta: Long): String = when {
    delta < 0 -> "durasi tidak diketahui"
    delta == 0L -> "durasi sama"
    else -> "selisih $delta dtk"
}

/** Metadata Spotify + pilihan video YouTube; audio tidak pernah diambil dari Spotify. */
@Composable
private fun SpotifyPreviewCard(track: SpotifyTrack, candidates: List<SpotifyCandidate>, selected: String, onSelect: (String) -> Unit) {
    SectionCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = track.coverUrl,
                    contentDescription = "Sampul album Spotify",
                    modifier = Modifier.size(88.dp).clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Text(
                        track.artists.ifBlank { "Artis tidak tercantum" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.MusicNote, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("Spotify · ${Format.duration(track.durationSeconds)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            HorizontalDivider()
            Text("Sumber audio: YouTube", style = MaterialTheme.typography.labelLarge)
            Text(
                "Spotify hanya dipakai untuk judul, artis, dan sampul. Pilih video yang cocok; file hasil unduhan memakai tag Spotify.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (candidates.isEmpty()) {
                Text("Tidak ada video YouTube yang bisa dipakai. Coba lagi nanti.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                candidates.forEach { candidate ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onSelect(candidate.video.url) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = candidate.video.url == selected, onClick = { onSelect(candidate.video.url) })
                        Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(candidate.video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            Text(
                                listOf(candidate.video.uploader.ifBlank { "Channel tidak diketahui" }, Format.duration(candidate.video.durationSeconds), spotifyDeltaLabel(candidate.durationDeltaSeconds)).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                val best = candidates.first()
                if (!best.confident) {
                    Text(
                        "Kecocokan terbaik belum kuat (skor ${best.score}/100) — periksa judul dan durasi sebelum mengunduh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** Album/playlist Spotify: metadata di atas, daftar lagu dengan status pencocokan YouTube. */
@Composable
private fun SpotifyCollectionCard(
    collection: SpotifyCollection,
    state: SpotifyCollectionPreview,
    query: String,
    onQuery: (String) -> Unit,
    selected: Set<String>,
    visible: List<SpotifyTrack>,
    allVisibleSelected: Boolean,
    unmatchedCount: Int,
    onToggle: (String) -> Unit,
    onToggleAll: () -> Unit,
    onMatch: () -> Unit,
    onCancelMatch: () -> Unit,
) {
    val kindLabel = if (collection.kind == SpotifyKind.ALBUM) "Album" else "Playlist"
    SectionCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = collection.coverUrl,
                    contentDescription = "Sampul $kindLabel Spotify",
                    modifier = Modifier.size(88.dp).clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(collection.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Text(
                        collection.owner.ifBlank { "$kindLabel Spotify" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.MusicNote, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "$kindLabel · ${collection.tracks.size} lagu · ${Format.duration(collection.totalSeconds)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (collection.tracks.size >= 100) {
                        Text("Halaman Spotify hanya memuat 100 lagu pertama.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider()
            Text("Sumber audio: YouTube", style = MaterialTheme.typography.labelLarge)
            Text(
                "Setiap lagu dicocokkan ke video YouTube lalu diantrekan sebagai job terpisah dengan tag Spotify. Pilih lagu yang ingin diunduh; yang belum cocok akan dilewati.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (collection.tracks.isEmpty()) {
                Text("Tidak ada lagu yang bisa dibaca dari halaman ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                SearchField(query, onQuery, label = "Cari lagu di ${kindLabel.lowercase()}")
                AdaptivePair(leading = { available -> Row(available, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${selected.size} / ${collection.playableCount} dipilih",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                } }, trailing = {
                    ActionButton(if (allVisibleSelected) "Kosongkan" else "Pilih semua", onToggleAll, emphasis = ActionEmphasis.Quiet)
                })
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    visible.forEach { track ->
                        val match = state.matches[track.id]
                        val failure = state.failures[track.id]
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                            Checkbox(
                                checked = track.id in selected,
                                onCheckedChange = { onToggle(track.id) },
                                enabled = track.playable,
                            )
                            Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    "${track.artists.ifBlank { "Artis tidak tercantum" }} · ${Format.duration(track.durationSeconds)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                when {
                                    !track.playable -> Text("Tidak tersedia di Spotify — tidak bisa diunduh.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    match != null -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.secondary)
                                            Spacer(Modifier.width(4.dp))
                                            Text("YouTube: ${match.video.title}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                        }
                                        Text(
                                            "${match.video.uploader.ifBlank { "Channel tidak diketahui" }} · ${spotifyDeltaLabel(match.durationDeltaSeconds)}" +
                                                if (match.confident) "" else " · kecocokan lemah, periksa dulu",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (match.confident) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                            maxLines = 1,
                                        )
                                    }
                                    failure != null -> Text("Gagal dicocokkan: $failure", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    state.matching -> Text("Menunggu pencocokan…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (visible.isEmpty()) {
                    Text("Tidak ada lagu yang cocok dengan \"$query\".", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.matching) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Mencocokkan ${state.done}/${state.total} lagu…", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = onCancelMatch) { Text("Batalkan") }
                    }
                } else if (unmatchedCount > 0) {
                    ActionButton(
                        "Cocokkan $unmatchedCount lagu",
                        onMatch,
                        icon = Icons.Outlined.Search,
                        emphasis = ActionEmphasis.Quiet,
                        fillWidth = true,
                    )
                }
                if (state.failures.isNotEmpty() && !state.matching) {
                    Text("Sebagian lagu gagal dicocokkan dan akan dilewati saat antre.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun buildSummary(mode: MediaMode, videoFormat: String, audioFormat: String, quality: Int, bitrate: Int, subtitleLang: String, cover: Boolean): String {
    val base = if (mode == MediaMode.VIDEO)
        "${if (quality == 0) "Kualitas terbaik" else "${quality}p"} · ${videoFormat.uppercase()}"
    else
        "${if (bitrate == 0 || audioFormat in listOf("flac", "wav", "vorbis")) "Kualitas sumber" else "$bitrate kbps"} · ${audioFormat.uppercase()}"
    val extras = buildList {
        if (subtitleLang.isNotEmpty()) {
            if (mode == MediaMode.AUDIO) add("Lirik") // lirik selalu dari penyedia lirik — tanpa pilihan bahasa (BUG-009)
            else {
                val all = subtitleLang == SubtitlePicker.ALL
                add(if (all) "Semua CC" else "CC ${SubtitlePicker.describe(subtitleLang)}")
            }
        }
        if (cover) add("Sampul")
    }
    return if (extras.isEmpty()) base else "$base · ${extras.joinToString(" · ")}"
}
