package id.local.tubeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.local.tubeflow.core.LrcLines
import id.local.tubeflow.core.MediaMode
import id.local.tubeflow.core.PlayerViewModel

@Composable
fun MiniPlayer(state: PlayerViewModel.State, player: PlayerViewModel, expand: () -> Unit) {
    val track=state.track ?: return
    Surface(tonalElevation=6.dp) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp), verticalAlignment=Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable(onClickLabel="Buka pemutar",onClick=expand).padding(vertical=8.dp), verticalAlignment=Alignment.CenterVertically) {
                    CoverThumb(track.cover,"",MediaMode.AUDIO,Modifier.size(48.dp))
                    Column(Modifier.weight(1f).padding(start=12.dp)) {
                        Text(track.title,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(track.artist.ifBlank { "Artis belum diketahui" },style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
                if (state.loading) CircularProgressIndicator(Modifier.size(24.dp)) else IconButton(onClick=player::toggle) {
                    Icon(if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if (state.playing) "Jeda audio" else "Putar audio")
                }
                IconButton(onClick=player::stop) { Icon(Icons.Outlined.Close,"Tutup pemutar") }
            }
            state.error?.let { Text(it,Modifier.padding(horizontal=16.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
            LinearProgressIndicator(progress={ if (state.durationMs > 0) (state.positionMs.toFloat()/state.durationMs).coerceIn(0f,1f) else 0f },modifier=Modifier.fillMaxWidth().height(3.dp))
        }
    }
}

@Composable
fun FullPlayer(state: PlayerViewModel.State, player: PlayerViewModel, dismiss: () -> Unit) {
    val track=state.track ?: return
    val accent=MaterialTheme.colorScheme.primary
    val list=rememberLazyListState()
    val active=LrcLines.activeIndex(state.lines,state.positionMs)
    var lyrics by remember { mutableStateOf(false) }
    var dragging by remember(track.uri) { mutableStateOf<Float?>(null) }
    LaunchedEffect(active,lyrics,track.uri) { if (lyrics && active >= 0 && !list.isScrollInProgress) list.animateScrollToItem(active) }
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha=0.15f),MaterialTheme.colorScheme.background))).safeDrawingPadding().padding(20.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=dismiss) { Icon(Icons.Outlined.ExpandMore,"Kembali ke mini player") }
                    Text("Sedang diputar",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    IconButton(onClick={ lyrics=!lyrics }) { Icon(Icons.Outlined.Lyrics,if (lyrics) "Tampilkan sampul" else "Tampilkan lirik") }
                }
                Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                    if (lyrics) {
                        if (state.lines.isEmpty()) Text(state.lyricError ?: "Lirik tersinkron belum tersedia.",style=MaterialTheme.typography.bodyLarge)
                        else LazyColumn(state=list,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=24.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(state.lines) { index,line ->
                                Text(line.text.ifBlank { "♪" },Modifier.fillMaxWidth().clickable(onClickLabel="Putar dari baris lirik") { player.seek(line.atMs) }.padding(vertical=14.dp,horizontal=8.dp),
                                    style=MaterialTheme.typography.titleLarge,fontWeight=if (index==active) FontWeight.ExtraBold else FontWeight.Medium,
                                    color=if (index==active) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else CoverThumb(track.cover,"",MediaMode.AUDIO,Modifier.fillMaxWidth().widthIn(max=400.dp).aspectRatio(1f).shadow(24.dp,MaterialTheme.shapes.large,ambientColor=accent,spotColor=accent))
                }
                Spacer(Modifier.height(16.dp))
                Text(track.title,style=MaterialTheme.typography.headlineSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(track.artist.ifBlank { "Artis belum diketahui" },style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                Slider(value=dragging ?: state.positionMs.toFloat().coerceIn(0f,state.durationMs.coerceAtLeast(1).toFloat()),
                    onValueChange={ dragging=it }, onValueChangeFinished={ dragging?.let { player.seek(it.toLong()) }; dragging=null },
                    valueRange=0f..state.durationMs.coerceAtLeast(1).toFloat(),enabled=!state.loading && state.durationMs>0)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(duration((dragging?.toLong() ?: state.positionMs)),style=MaterialTheme.typography.labelMedium); Text(duration(state.durationMs),style=MaterialTheme.typography.labelMedium) }
                Row(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={ player.skip(-10000) },enabled=!state.loading) { Icon(Icons.Outlined.Replay10,"Mundur 10 detik") }
                    FilledIconButton(onClick=player::toggle,modifier=Modifier.size(64.dp),enabled=!state.loading) {
                        Icon(if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if (state.playing) "Jeda audio" else "Putar audio",Modifier.size(32.dp))
                    }
                    IconButton(onClick={ player.skip(10000) },enabled=!state.loading) { Icon(Icons.Outlined.Forward10,"Maju 10 detik") }
                }
            }
        }
    }
}
