package id.local.tubeflow.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import id.local.tubeflow.core.AutoEq
import id.local.tubeflow.core.LrcLines
import id.local.tubeflow.core.MediaItem
import id.local.tubeflow.core.MediaLibraryViewModel
import id.local.tubeflow.core.MediaMode
import id.local.tubeflow.core.PlaybackQueue
import id.local.tubeflow.core.PlaybackQueueSearch
import id.local.tubeflow.core.PlayerEqualizer
import id.local.tubeflow.core.PlayerViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun MiniPlayer(state: PlayerViewModel.State, player: PlayerViewModel, expand: () -> Unit) {
    val track=state.track ?: return
    // Sengaja memakai bahasa visual kartu lagu di Koleksi (radius medium, latar datar bernuansa
    // aksen sampul) supaya mini player terbaca sebagai bagian dari daftar, bukan panel terpisah.
    val accent=rememberCoverAccent(track.cover)
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp)) {
        Card(colors=mediaCardColors(accent)) {
            Column {
                AdaptivePair(Modifier.padding(start=10.dp,end=2.dp,top=6.dp,bottom=6.dp), minInlineWidth=360.dp,
                    leading = { available -> Row(available.clickable(onClickLabel="Buka pemutar",onClick=expand).padding(end=6.dp), verticalAlignment=Alignment.CenterVertically) {
                        CoverThumb(track.cover,"",MediaMode.AUDIO,Modifier.size(48.dp))
                        Column(Modifier.weight(1f).padding(start=12.dp), verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            Text(track.title,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(track.artist.ifBlank { "Artis belum diketahui" },modifier=Modifier.weight(1f,fill=false),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                                if (state.speed != 1f) Text("  ·  ${speedLabel(state.speed)}×",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                            }
                        }
                    } }, trailing = { Row(verticalAlignment=Alignment.CenterVertically) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(22.dp)) else IconButton(onClick=player::toggle) {
                        Icon(if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if (state.playing) "Jeda audio" else "Putar audio",tint=accent)
                    }
                    IconButton(onClick=player::next,enabled=state.queueHasNext) { Icon(Icons.Outlined.SkipNext,"Lagu berikutnya") }
                    IconButton(onClick=player::stop) { Icon(Icons.Outlined.Close,"Tutup pemutar") }
                } })
                state.error?.let { Text(it,Modifier.padding(horizontal=14.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
                LinearProgressIndicator(progress={ if (state.durationMs > 0) (state.positionMs.toFloat()/state.durationMs).coerceIn(0f,1f) else 0f },
                    modifier=Modifier.fillMaxWidth().height(3.dp),color=accent,trackColor=accent.copy(alpha=0.18f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayer(state: PlayerViewModel.State, player: PlayerViewModel, library: MediaLibraryViewModel, karaoke: () -> Unit, dismiss: () -> Unit) {
    val track=state.track ?: return
    val timer by player.sleep.collectAsStateWithLifecycle()
    val accent=MaterialTheme.colorScheme.primary
    // Aksen diambil dari sampul lagu: latar pemutar dan lirik ikut bernuansa sampul, bukan hitam polos.
    val coverAccent=rememberCoverAccent(track.cover)
    val speedDescription=stringResource(id.local.tubeflow.R.string.player_speed_description, speedLabel(state.speed))
    val list=rememberLazyListState()
    val active=LrcLines.activeIndex(state.lines,state.positionMs)
    var userChoseView by remember(track.uri) { mutableStateOf(false) }
    var dragging by remember(track.uri) { mutableStateOf<Float?>(null) }
    var queueOpen by remember { mutableStateOf(false) }
    var equalizerOpen by remember { mutableStateOf(false) }
    var speedOpen by remember { mutableStateOf(false) }
    var sleepOpen by remember { mutableStateOf(false) }
    var timing by remember { mutableStateOf<MediaItem?>(null) }
    var lyricToolError by remember { mutableStateOf<String?>(null) }
    val pager=rememberPagerState(pageCount={ 2 })
    val scope=rememberCoroutineScope()
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    // Callback diingat agar halaman pager tidak dianggap berubah setiap tick posisi (lihat LyricsPage).
    val openSearch: () -> Unit = remember(track.uri,library) { { lyricToolError=null; library.openLyricsForTrack(track.uri) } }
    val openEditor: () -> Unit = remember(track.uri,library) {
        {
            lyricToolError=null
            scope.launch {
                val item=library.itemFor(track.uri)
                if (item?.lyricUri == null) lyricToolError="Lirik tersinkron belum tersedia untuk lagu ini."
                else timing=item
            }
        }
    }
    val seekLine: (Long) -> Unit = remember(player) { { player.seek(it) } }
    // Swipe pengguna menang atas pemilihan otomatis; lagu baru kembali memakai aturan otomatis.
    LaunchedEffect(dragged) { if (dragged) userChoseView=true }
    LaunchedEffect(track.uri) { pager.scrollToPage(if (state.lines.isNotEmpty()) 1 else 0) }
    LaunchedEffect(state.lines) { if (!userChoseView && state.lines.isNotEmpty() && pager.settledPage==0) pager.animateScrollToPage(1) }
    LaunchedEffect(active,pager.settledPage,track.uri) { if (pager.settledPage==1 && active>=0 && !list.isScrollInProgress) list.animateScrollToItem(active) }
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val view=LocalView.current
        val darkBars=MaterialTheme.colorScheme.background.luminance()<0.5f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window,view).apply {
                    isAppearanceLightStatusBars=!darkBars
                    isAppearanceLightNavigationBars=!darkBars
                }
            }
        }
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            // Layar pendek/landscape dan font besar butuh ruang lebih sedikit untuk sampul agar
            // kontrol bawah tetap terjangkau; angka tetap lama mendorong kontrol keluar layar.
            val compactHeight=maxHeight<600.dp || LocalDensity.current.fontScale>=1.3f
            val pageHeight=if(compactHeight) (maxHeight-280.dp).coerceIn(140.dp,400.dp)
                else (maxHeight-370.dp).coerceIn(220.dp,400.dp)
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(coverAccent.copy(alpha=0.18f),coverAccent.copy(alpha=0.06f),MaterialTheme.colorScheme.background))).verticalScroll(rememberScrollState()).padding(20.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=dismiss) { Icon(Icons.Outlined.ExpandMore,"Kembali ke mini player") }
                    Text(stringResource(id.local.tubeflow.R.string.player_now_playing),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Box {
                        TextButton(
                            onClick = { speedOpen = true },
                            modifier = Modifier.heightIn(min=48.dp).clearAndSetSemantics {
                                role = Role.Button
                                contentDescription = speedDescription
                                onClick(label = speedDescription) { speedOpen = true; true }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                        ) {
                            Icon(Icons.Outlined.Speed,null,Modifier.size(24.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${speedLabel(state.speed)}×", fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(speedOpen,onDismissRequest={ speedOpen=false }) {
                            listOf(0.5f,0.75f,1f,1.25f,1.5f,2f).forEach { value ->
                                DropdownMenuItem(
                                    text={ Text("${speedLabel(value)}×") },
                                    onClick={ speedOpen=false; player.setSpeed(value) },
                                    // Centang saja tidak terbaca TalkBack — item aktif diumumkan "dipilih".
                                    modifier = Modifier.semantics { if (state.speed == value) selected = true },
                                    trailingIcon = if (state.speed == value) {
                                        { Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }
                                    } else null,
                                )
                            }
                        }
                    }
                    IconButton(onClick={ sleepOpen=true }) { Icon(Icons.Outlined.Bedtime, stringResource(id.local.tubeflow.R.string.sleep_title), Modifier.size(24.dp)) }
                    IconButton(onClick={ equalizerOpen=true }) { Icon(Icons.Outlined.Tune,"Equalizer", Modifier.size(24.dp)) }
                }
                // Halaman utama dapat digeser: sampul ↔ lirik, tanpa harus mencari tombol.
                Box(Modifier.fillMaxWidth().height(pageHeight)) {
                HorizontalPager(state=pager,modifier=Modifier.fillMaxSize(),pageSpacing=12.dp) { page ->
                    if (page==1) {
                        LyricsPage(track,state.lines,active,coverAccent,state.lyricError,lyricToolError,list,seekLine,openSearch,openEditor)
                    } else {
                        PlayerCoverPage(track)
                    }
                }
                PlayerPageIndicator(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                    selectedPage = pager.currentPage,
                    coverDescription = stringResource(id.local.tubeflow.R.string.player_page_show_cover),
                    lyricsDescription = stringResource(id.local.tubeflow.R.string.player_page_show_lyrics),
                    accent = coverAccent,
                    onSelect = { page ->
                        userChoseView = true
                        scope.launch { pager.animateScrollToPage(page) }
                    },
                )
                }
                if (pager.settledPage==0) Text(stringResource(id.local.tubeflow.R.string.player_swipe_hint),
                    modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center,
                    style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text(track.title,style=MaterialTheme.typography.headlineSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(track.artist.ifBlank { "Artis belum diketahui" },style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.queueTracks.size>1) Text(
                    "Lagu ${state.queueIndex+1} dari ${state.queueTracks.size}" + (if (state.shuffle) " · acak" else "") + (if (state.repeat!=PlaybackQueue.Repeat.OFF) " · ulangi ${repeatLabel(state.repeat)}" else ""),
                    style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if (timer.remainingMs > 0 || timer.afterTrack) Text(
                    if (timer.afterTrack) stringResource(id.local.tubeflow.R.string.sleep_after_track) else stringResource(id.local.tubeflow.R.string.sleep_remaining, duration(timer.remainingMs)),
                    modifier = Modifier.clickable { sleepOpen = true }.padding(vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                // Bilah progres putih tipis di atas latar gelap — aksen hijau disimpan untuk tombol putar.
                val seekTint=MaterialTheme.colorScheme.onBackground
                val seekColors=SliderDefaults.colors(thumbColor=seekTint,activeTrackColor=seekTint,inactiveTrackColor=seekTint.copy(alpha=0.22f))
                Slider(value=dragging ?: state.positionMs.toFloat().coerceIn(0f,state.durationMs.coerceAtLeast(1).toFloat()),
                    onValueChange={ dragging=it }, onValueChangeFinished={ dragging?.let { player.seek(it.toLong()) }; dragging=null },
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                    valueRange=0f..state.durationMs.coerceAtLeast(1).toFloat(),enabled=!state.loading && state.durationMs>0,
                    colors=seekColors,
                    thumb={ Box(Modifier.size(12.dp).background(seekTint,CircleShape)) },
                    track={ sliderState -> SliderDefaults.Track(sliderState,modifier=Modifier.height(4.dp),
                        colors=seekColors,thumbTrackGapSize=0.dp,drawStopIndicator=null) })
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(duration((dragging?.toLong() ?: state.positionMs)),style=MaterialTheme.typography.labelMedium); Text(duration(state.durationMs),style=MaterialTheme.typography.labelMedium) }
                Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={ player.skip(-10000) },enabled=!state.loading) { Icon(Icons.Outlined.Replay10,"Mundur 10 detik") }
                    IconButton(onClick=player::previous,enabled=!state.loading) { Icon(Icons.Outlined.SkipPrevious,"Lagu sebelumnya") }
                    FilledIconButton(onClick=player::toggle,modifier=Modifier.size(72.dp),enabled=!state.loading,
                        colors=IconButtonDefaults.filledIconButtonColors(containerColor=accent,contentColor=AccentContrast.ink(accent))) {
                        Icon(if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if (state.playing) "Jeda audio" else "Putar audio",Modifier.size(34.dp))
                    }
                    IconButton(onClick=player::next,enabled=!state.loading && state.queueHasNext) { Icon(Icons.Outlined.SkipNext,"Lagu berikutnya") }
                    IconButton(onClick={ player.skip(10000) },enabled=!state.loading) { Icon(Icons.Outlined.Forward10,"Maju 10 detik") }
                }
                val largeLabels = LocalDensity.current.fontScale >= 1.3f
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top=8.dp)) {
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp),
                        verticalArrangement=Arrangement.spacedBy(4.dp),
                        maxItemsInEachRow=if (maxWidth < 320.dp || largeLabels) 2 else 4) {
                        PlayerAction(stringResource(id.local.tubeflow.R.string.player_shuffle), Icons.Outlined.Shuffle,
                            onClick=player::toggleShuffle, modifier=Modifier.weight(1f),
                            enabled=state.queueTracks.size>1, tint=if (state.shuffle) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            description="Acak: ${if (state.shuffle) "aktif" else "nonaktif"}")
                        PlayerAction(stringResource(id.local.tubeflow.R.string.player_repeat),
                            if (state.repeat==PlaybackQueue.Repeat.ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
                            onClick=player::cycleRepeat, modifier=Modifier.weight(1f),
                            tint=if (state.repeat==PlaybackQueue.Repeat.OFF) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                            description="Ulangi: ${repeatLabel(state.repeat)}")
                        PlayerAction(stringResource(id.local.tubeflow.R.string.player_karaoke), Icons.Outlined.MicOff,
                            onClick=karaoke, modifier=Modifier.weight(1f))
                        PlayerAction(stringResource(id.local.tubeflow.R.string.player_queue), Icons.AutoMirrored.Outlined.QueueMusic,
                            onClick={ queueOpen=true }, modifier=Modifier.weight(1f), count=state.queueTracks.size,
                            description="Antrean putar, ${state.queueTracks.size} lagu")
                    }
                }
            }
            if (sleepOpen) SleepTimerDialog(player) { sleepOpen=false }
            if (queueOpen) QueueDialog(state,player) { queueOpen=false }
            if (equalizerOpen) EqualizerDialog(player) { equalizerOpen=false }
            // Dialog lirik dihost DI DALAM jendela pemutar agar tampil di atas pemutar penuh.
            LyricsSearchDialog(library)
            timing?.let { item -> LyricTimingDialog(item,library) { timing=null } }
            }
        }
    }
}

/** Halaman sampul; hanya bergantung pada track agar tick posisi tidak menyusun ulang halaman. */
@Composable
private fun PlayerCoverPage(track: PlayerViewModel.Track) {
    Box(Modifier.fillMaxSize().padding(12.dp),contentAlignment=Alignment.Center) {
        CoverThumb(track.cover,"",MediaMode.AUDIO,
            Modifier.widthIn(max=380.dp).fillMaxWidth().aspectRatio(1f),shape=MaterialTheme.shapes.large)
    }
}

/**
 * Halaman lirik dipisah dari FullPlayer: parameter yang berubah tiap tick hanyalah [activeIndex],
 * sehingga daftar lirik dan tombol alat tidak ikut direkomposisi saat posisi audio bergerak.
 */
@Composable
private fun LyricsPage(
    track: PlayerViewModel.Track,
    lines: List<LrcLines.Line>,
    activeIndex: Int,
    accent: Color,
    lyricError: String?,
    toolError: String?,
    list: LazyListState,
    onSeek: (Long) -> Unit,
    onSearch: () -> Unit,
    onEdit: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha=0.28f),accent.copy(alpha=0.10f),Color.Transparent)))) {
        if (lines.isEmpty()) {
            // Lirik kosong: langsung tawarkan alat lirik yang sama dengan Koleksi.
            Column(Modifier.fillMaxSize().padding(start=24.dp,end=24.dp,top=24.dp,bottom=56.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Icon(Icons.Outlined.Lyrics,null,Modifier.size(44.dp),tint=accent)
                Spacer(Modifier.height(12.dp))
                Text(lyricError ?: "Lirik tersinkron belum tersedia.",style=MaterialTheme.typography.bodyLarge,textAlign=TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                ActionButton("Cari lirik", onSearch, icon = Icons.Outlined.Search, emphasis = ActionEmphasis.Primary)
                ActionButton("Editor lirik", onEdit, icon = Icons.Outlined.EditNote, enabled = track.lyricUri != null)
                if (track.lyricUri==null) Text("Editor waktu lirik tersedia setelah lirik dipilih.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                toolError?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                FlowRow(Modifier.fillMaxWidth().padding(horizontal=4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp,Alignment.End)) {
                    ActionButton("Atur waktu lirik", onEdit, icon = Icons.Outlined.EditNote, emphasis = ActionEmphasis.Quiet, enabled = track.lyricUri != null)
                    ActionButton("Cari lirik lain", onSearch, icon = Icons.Outlined.Search, emphasis = ActionEmphasis.Quiet)
                }
                toolError?.let { Text(it,Modifier.padding(horizontal=8.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
                LazyColumn(state=list,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(top=16.dp,bottom=64.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(lines) { index,line ->
                        Text(line.text.ifBlank { "♪" },Modifier.fillMaxWidth().clickable(onClickLabel="Putar dari baris lirik") { onSeek(line.atMs) }.padding(vertical=14.dp,horizontal=8.dp),
                            style=MaterialTheme.typography.titleLarge,fontWeight=if (index==activeIndex) FontWeight.ExtraBold else FontWeight.Medium,
                            color=if (index==activeIndex) accent else MaterialTheme.colorScheme.onSurfaceVariant, textAlign=TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerPageIndicator(
    modifier: Modifier = Modifier,
    selectedPage: Int,
    coverDescription: String,
    lyricsDescription: String,
    accent: Color,
    onSelect: (Int) -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        // Opak: indikator mengambang di atas lirik, jadi teks yang lewat di belakangnya tidak boleh tembus.
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier.selectableGroup().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerPageDot(selectedPage == 0, coverDescription, accent) { onSelect(0) }
            Spacer(Modifier.width(4.dp))
            PlayerPageDot(selectedPage == 1, lyricsDescription, accent) { onSelect(1) }
        }
    }
}

@Composable
private fun PlayerPageDot(
    selected: Boolean,
    description: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val width by animateDpAsState(if (selected) 22.dp else 8.dp, label = "player-page-dot-width")
    val color by animateColorAsState(
        if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        label = "player-page-dot-color",
    )
    Box(
        Modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                this.selected = selected
                role = Role.Tab
                contentDescription = description
                onClick(label = description) { onClick(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(width).height(8.dp).background(color, CircleShape))
    }
}

@Composable
private fun PlayerAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    count: Int? = null,
    description: String? = null,
) {
    TextButton(onClick=onClick, enabled=enabled,
        modifier=modifier.heightIn(min=48.dp).semantics {
            if (description != null) contentDescription=description
        },
        shape=MaterialTheme.shapes.small, contentPadding=PaddingValues(8.dp),
        colors=ButtonDefaults.textButtonColors(contentColor=tint)) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Icon(icon,null,Modifier.size(20.dp))
                count?.let { Text("$it",style=MaterialTheme.typography.labelMedium) }
            }
            Text(label,style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueDialog(state: PlayerViewModel.State, player: PlayerViewModel, dismiss: () -> Unit) {
    val accent=rememberCoverAccent(state.track?.cover.orEmpty())
    val list=rememberLazyListState()
    var confirmClear by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val positions=remember(query,state.queueTracks) {
        PlaybackQueueSearch.positions(state.queueTracks,query) { "${it.title} ${it.artist}" }
    }
    LaunchedEffect(query) { list.scrollToItem(if (query.isBlank()) (state.queueIndex-1).coerceAtLeast(0) else 0) }
    ModalBottomSheet(onDismissRequest=dismiss, sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).padding(bottom=16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.QueueMusic,null,tint=accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Antrean putar",style=MaterialTheme.typography.titleLarge)
                    Text("${state.queueTracks.size} lagu · ${if (state.shuffle) "acak" else "urut"} · ulangi ${repeatLabel(state.repeat)}",
                        style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick=dismiss) { Icon(Icons.Outlined.Close,stringResource(id.local.tubeflow.R.string.player_queue_close)) }
            }
            OutlinedTextField(value=query,onValueChange={ query=it },singleLine=true,
                label={ Text(stringResource(id.local.tubeflow.R.string.player_queue_search)) },
                leadingIcon={ Icon(Icons.Outlined.Search,null) },
                trailingIcon={ if (query.isNotEmpty()) IconButton(onClick={ query="" }) {
                    Icon(Icons.Outlined.Close,stringResource(id.local.tubeflow.R.string.player_queue_clear_search))
                } },modifier=Modifier.fillMaxWidth().padding(top=12.dp))
            if (query.isNotBlank()) Text(pluralStringResource(id.local.tubeflow.R.plurals.player_queue_results,positions.size,positions.size),
                Modifier.padding(top=8.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                FilterChip(selected=state.shuffle,onClick={ player.toggleShuffle() },enabled=state.queueTracks.size>1,
                    label={ Text("Acak") },leadingIcon={ Icon(Icons.Outlined.Shuffle,null,Modifier.size(18.dp)) })
                FilterChip(selected=state.repeat!=PlaybackQueue.Repeat.OFF,onClick=player::cycleRepeat,
                    label={ Text("Ulangi: ${repeatLabel(state.repeat)}") },leadingIcon={
                        Icon(if (state.repeat==PlaybackQueue.Repeat.ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,null,Modifier.size(18.dp)) })
                ActionChip(stringResource(id.local.tubeflow.R.string.action_clear_queue), { confirmClear = true }, icon = Icons.Outlined.DeleteSweep, destructive = true, enabled = state.queueTracks.size > 1)
            }
            if (positions.isEmpty()) Text(stringResource(id.local.tubeflow.R.string.player_queue_no_results),
                Modifier.padding(vertical=24.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(state=list,modifier=Modifier.weight(1f,fill=false).heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(positions,key={ position -> "${state.queueTracks[position].uri}#$position" }) { position ->
                    val queued=state.queueTracks[position]
                    val current=position==state.queueIndex
                    var menu by remember(queued.uri,position) { mutableStateOf(false) }
                    Surface(shape=MaterialTheme.shapes.medium,
                        color=if (current) accent.copy(alpha=0.12f).compositeOver(MaterialTheme.colorScheme.surface) else MaterialTheme.colorScheme.surfaceContainer,
                        border=if (current) BorderStroke(1.dp,accent.copy(alpha=0.30f)) else null) {
                        Row(Modifier.fillMaxWidth().padding(start=12.dp,end=4.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                            Row(Modifier.weight(1f).clickable(onClickLabel="Putar dari antrean") { player.jumpTo(position) }.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                                CoverThumb(queued.cover,"",MediaMode.AUDIO,Modifier.size(48.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(queued.title,style=MaterialTheme.typography.titleSmall,fontWeight=if (current) FontWeight.Bold else FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                                    Text(queued.artist.ifBlank { "Artis belum diketahui" },style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (current) Text("Sedang diputar",style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,color=accent)
                                }
                            }
                            Box {
                                IconButton(onClick={ menu=true }) { Icon(Icons.Outlined.MoreVert,"Menu ${queued.title}") }
                                DropdownMenu(menu,onDismissRequest={ menu=false }) {
                                    DropdownMenuItem(text={ Text("Putar sekarang") },leadingIcon={ Icon(Icons.Outlined.PlayArrow,null) },
                                        onClick={ menu=false; player.jumpTo(position) })
                                    DropdownMenuItem(text={ Text(stringResource(id.local.tubeflow.R.string.player_queue_next)) },leadingIcon={ Icon(Icons.AutoMirrored.Outlined.PlaylistPlay,null) },
                                        enabled=position!=state.queueIndex && position!=state.queueIndex+1,
                                        onClick={ menu=false; player.moveInQueue(position,if (position<state.queueIndex) state.queueIndex else state.queueIndex+1) })
                                    DropdownMenuItem(text={ Text("Pindah ke atas") },leadingIcon={ Icon(Icons.Outlined.ArrowUpward,null) },enabled=position>0,
                                        onClick={ menu=false; player.moveInQueue(position,position-1) })
                                    DropdownMenuItem(text={ Text("Pindah ke bawah") },leadingIcon={ Icon(Icons.Outlined.ArrowDownward,null) },enabled=position<state.queueTracks.size-1,
                                        onClick={ menu=false; player.moveInQueue(position,position+1) })
                                    DropdownMenuItem(text={ Text("Keluarkan dari antrean",color=MaterialTheme.colorScheme.error) },leadingIcon={ Icon(Icons.Outlined.DeleteOutline,null) },enabled=state.queueTracks.size>1,
                                        onClick={ menu=false; player.removeFromQueue(position) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Bersihkan antrean?",
            text = "Antrean disisakan lagu yang sedang diputar (${state.track?.title ?: "-"}). Pemutaran tidak berhenti dan tidak ada lagu yang dihapus dari penyimpanan.",
            confirmLabel = "Bersihkan",
            onConfirm = { confirmClear = false; player.keepCurrentOnly() },
            onDismiss = { confirmClear = false },
            destructive = true,
        )
    }
}

@Composable
private fun EqualizerDialog(player: PlayerViewModel, dismiss: () -> Unit) {
    var snapshot by remember { mutableStateOf(player.equalizer.snapshot()) }
    var presetOpen by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=dismiss,title={ Text("Equalizer") },text={
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if (!snapshot.available) {
                Text("Equalizer tidak tersedia pada perangkat ini. Pemutaran tetap berjalan tanpa efek.")
            } else {
                ToggleRow(
                    title = "Aktif",
                    checked = snapshot.enabled,
                    onCheckedChange = { player.equalizer.setEnabled(it); snapshot = player.equalizer.snapshot() },
                )
                Box {
                    ActionChip("Preset: ${snapshot.preset}", { presetOpen = true }, icon = Icons.Outlined.Equalizer)
                    DropdownMenu(presetOpen,onDismissRequest={ presetOpen=false },modifier=Modifier.heightIn(max=320.dp)) {
                        (listOf(AutoEq.AUTO_PRESET,PlayerEqualizer.DEFAULT_PRESET) + snapshot.presets).forEach { name ->
                            DropdownMenuItem(text={ Text(name) },onClick={ presetOpen=false; player.equalizer.usePreset(name); snapshot=player.equalizer.snapshot() })
                        }
                    }
                }
                if (snapshot.preset==AutoEq.AUTO_PRESET) Text(stringResource(id.local.tubeflow.R.string.eq_auto_description),
                    style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                snapshot.bands.forEach { band ->
                    Column {
                        Row(Modifier.fillMaxWidth()) { Text(band.label,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                            Text("%+.1f dB".format(java.util.Locale.ROOT,band.levelDb),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        Slider(value=band.levelDb,onValueChange={ player.equalizer.setBandLevel(band.index,it); snapshot=player.equalizer.snapshot() },
                            valueRange=band.minDb..band.maxDb,enabled=snapshot.enabled)
                    }
                }
                Text("Pengaturan tersimpan dan diterapkan ke semua lagu.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    },confirmButton={ TextButton(onClick=dismiss) { Text("Tutup") } })
}

private fun repeatLabel(repeat: PlaybackQueue.Repeat) = when (repeat) {
    PlaybackQueue.Repeat.OFF -> "nonaktif"
    PlaybackQueue.Repeat.ALL -> "semua lagu"
    PlaybackQueue.Repeat.ONE -> "satu lagu"
}

private fun speedLabel(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString()
    else "%.2f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')
