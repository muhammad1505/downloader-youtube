package id.local.tubeflow.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.provider.OpenableColumns
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.ScreenLockLandscape
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import id.local.tubeflow.core.VideoAspect
import id.local.tubeflow.core.VideoAudioEffect
import id.local.tubeflow.core.VideoDecoderMode
import id.local.tubeflow.core.VideoEnhance
import id.local.tubeflow.core.VideoEqualizer
import id.local.tubeflow.core.VideoOrientation
import id.local.tubeflow.core.VideoPip
import id.local.tubeflow.core.VideoPlayback
import id.local.tubeflow.core.VideoPlayerViewModel
import id.local.tubeflow.core.VideoRepeat
import id.local.tubeflow.core.VideoScreenshot
import id.local.tubeflow.core.VideoSubtitle
import id.local.tubeflow.core.VideoSubtitleBackground
import id.local.tubeflow.core.VideoSubtitlePosition
import id.local.tubeflow.core.VideoSubtitleSize
import id.local.tubeflow.core.VideoSubtitleStyle
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay

private val PlayerBackground = Color.Black
private val PlayerContent = Color.White
private val PlayerMuted = Color.White.copy(alpha = 0.62f)
private val SheetBackground = Color(0xFF1B1B1F)
private val AccentBlue = Color(0xFF8AB4F8)

private enum class VideoSheet { SPEED, SUBTITLES, AUDIO, DISPLAY, EFFECTS, DECODER, QUEUE, MORE }

private sealed interface GestureFeedback {
    data class Seek(val deltaMs: Long, val targetMs: Long) : GestureFeedback
    data class Volume(val percent: Int) : GestureFeedback
    data class Brightness(val percent: Int) : GestureFeedback
}

/**
 * Pemutar video penuh di atas aplikasi, memakai Media3 ExoPlayer. Layar ini tidak menyentuh
 * pemutar audio: sebelum dibuka pemanggil mem-pause audio, dan setelah ditutup ExoPlayer dilepas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(video: VideoPlayerViewModel) {
    val state by video.state.collectAsStateWithLifecycle()
    if (!state.active) return
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val lifecycleOwner = LocalLifecycleOwner.current
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val supportsPip = remember(context) { VideoPip.supports(context) }
    val originalBrightness = remember { activity?.window?.attributes?.screenBrightness ?: -1f }
    val originalOrientation = remember { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<VideoSheet?>(null) }
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    var orientation by remember { mutableStateOf(VideoOrientation.AUTO) }
    var feedback by remember { mutableStateOf<GestureFeedback?>(null) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var showResume by remember { mutableStateOf(false) }
    var controlsLocked by remember { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    fun touch() {
        controlsVisible = true
        interaction++
    }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val picked = externalSubtitle(context, uri)
            if (picked == null) {
                Toast.makeText(
                    context,
                    "Format subtitle tidak dikenali. Gunakan SRT, VTT, ASS/SSA, atau TTML.",
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                video.addExternalSubtitle(picked)
                sheet = null
                touch()
                Toast.makeText(context, "Subtitle ${picked.label} ditambahkan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun takeScreenshot() {
        val view = playerView
        if (view == null) {
            Toast.makeText(context, "Frame video belum siap.", Toast.LENGTH_SHORT).show()
            return
        }
        VideoScreenshot.capture(context, view, state.entry?.displayTitle ?: "video") { result ->
            result.fold(
                onSuccess = {
                    Toast.makeText(context, "Screenshot disimpan ke Pictures/TubeFlow", Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                    Toast.makeText(context, it.message ?: "Screenshot gagal", Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    fun setBrightness(value: Float) {
        val window = activity?.window ?: return
        val attributes = window.attributes
        attributes.screenBrightness = value.coerceIn(0.01f, 1f)
        window.attributes = attributes
    }

    fun enterPip() {
        val host = activity ?: return
        VideoPip.enter(host, video.state.value)
    }

    BackHandler {
        if (controlsLocked) {
            controlsLocked = false
            touch()
        } else {
            video.close()
        }
    }

    // Kontrol disembunyikan saat diputar, tetap terlihat saat jeda; sentuhan apa pun memulai ulang timer.
    LaunchedEffect(controlsVisible, state.playing, interaction, sheet, inPip, controlsLocked) {
        if (controlsLocked) {
            controlsVisible = false
        } else if (controlsVisible && state.playing && sheet == null && !inPip) {
            delay(3_500)
            controlsVisible = false
        }
    }
    LaunchedEffect(state.playing) { if (!state.playing) controlsVisible = true }
    LaunchedEffect(state.error) { if (state.error != null) controlsLocked = false }
    LaunchedEffect(state.entry?.uri, state.resumeFromMs) {
        showResume = state.resumeFromMs > 0
        if (showResume) {
            delay(8_000)
            showResume = false
        }
    }
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(900)
            feedback = null
        }
    }

    // Sensor aktif selama pemutar terbuka: memutar perangkat langsung mengubah orientasi, tanpa
    // bergantung pada kunci rotasi sistem. Mode lanskap/potret mengunci arah secara eksplisit.
    LaunchedEffect(activity, orientation, inPip) {
        val host = activity ?: return@LaunchedEffect
        if (!inPip) host.requestedOrientation = orientation.activityValue()
    }

    // Beberapa perangkat menampilkan kembali system bar setelah rotasi; sembunyikan lagi agar
    // video tetap memakai seluruh layar di kedua orientasi.
    LaunchedEffect(activity, landscape, inPip, sheet, interaction) {
        if (inPip) return@LaunchedEffect
        val window = activity?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Rasio, aksi play/pause, dan izin auto-PiP (Android 12+) selalu diperbarui bersama status putar.
    LaunchedEffect(activity, state.playing, state.videoWidth, state.videoHeight, supportsPip, inPip) {
        val host = activity ?: return@LaunchedEffect
        if (!supportsPip) return@LaunchedEffect
        VideoPip.apply(host, video.state.value, autoEnter = state.playing && !inPip)
    }

    // Aksi play/pause PiP diteruskan receiver manifest ke ViewModel yang sedang aktif.
    DisposableEffect(video) {
        VideoPip.attachToggle { video.togglePlay() }
        onDispose { VideoPip.detachToggle() }
    }

    DisposableEffect(activity) {
        val host = activity
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
        }
        host?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { host?.removeOnPictureInPictureModeChangedListener(listener) }
    }

    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) {
                val attributes = window.attributes
                attributes.screenBrightness = if (originalBrightness >= 0f) originalBrightness else -1f
                window.attributes = attributes
            }
            activity?.requestedOrientation = originalOrientation
            VideoPip.disableAutoEnter(activity)
        }
    }

    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (activity?.isInPictureInPictureMode == true) video.close() else video.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val videoAspect = state.aspect
    val subtitleSize = state.subtitleSize
    val subtitleStyle = state.subtitleStyle
    val subtitlePosition = state.subtitlePosition
    val subtitleBackground = state.subtitleBackground
    Box(Modifier.fillMaxSize().background(PlayerBackground)) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    keepScreenOn = true
                }.also { playerView = it }
            },
            update = { view ->
                view.player = video.exoPlayer
                applyVideoAspect(view, videoAspect)
                applySubtitleSettings(view, subtitleSize, subtitleStyle, subtitlePosition, subtitleBackground)
            },
            onRelease = { view -> if (playerView === view) playerView = null },
            modifier = Modifier.fillMaxSize(),
        )

        if (!inPip && !controlsLocked) {
            // Permukaan gestur berada di atas video tetapi di bawah kontrol, sehingga tombol tetap menang.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(state.entry?.uri) {
                        var axis = 0 // 0 belum diputuskan, 1 seek, 2 volume, 3 kecerahan
                        var total = Offset.Zero
                        var startPosition = 0L
                        var startVolume = 0
                        var startBrightness = 0.5f
                        var startX = 0f
                        detectDragGestures(
                            onDragStart = { offset ->
                                axis = 0
                                total = Offset.Zero
                                startX = offset.x
                                startPosition = video.state.value.positionMs
                                startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                startBrightness = activity?.window?.attributes?.screenBrightness
                                    ?.takeIf { it >= 0f } ?: 0.5f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                total += amount
                                if (axis == 0 && (abs(total.x) > viewConfiguration.touchSlop || abs(total.y) > viewConfiguration.touchSlop)) {
                                    axis = if (abs(total.x) > abs(total.y)) 1
                                    else if (startX >= size.width / 2f) 2 else 3
                                }
                                when (axis) {
                                    1 -> {
                                        val duration = video.state.value.durationMs
                                        val delta = (total.x / size.width * 120_000f).toLong()
                                        val target = VideoPlayback.seekTarget(startPosition, delta, duration)
                                        feedback = GestureFeedback.Seek(target - startPosition, target)
                                    }
                                    2 -> {
                                        val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                        val delta = (-total.y / size.height * maximum).toInt()
                                        val value = (startVolume + delta).coerceIn(0, maximum)
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                                        feedback = GestureFeedback.Volume(value * 100 / maximum)
                                    }
                                    3 -> {
                                        val delta = -total.y / size.height
                                        val value = (startBrightness + delta).coerceIn(0.01f, 1f)
                                        setBrightness(value)
                                        feedback = GestureFeedback.Brightness((value * 100).toInt())
                                    }
                                }
                            },
                            onDragEnd = {
                                if (axis == 1) {
                                    (feedback as? GestureFeedback.Seek)?.let { video.seekTo(it.targetMs) }
                                }
                                axis = 0
                            },
                            onDragCancel = { axis = 0 },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (controlsVisible) controlsVisible = false
                                else {
                                    controlsVisible = true
                                    interaction++
                                }
                            },
                            onDoubleTap = { offset ->
                                val forward = offset.x >= size.width / 2f
                                val delta = if (forward) VideoPlayback.SEEK_STEP_MS else -VideoPlayback.SEEK_STEP_MS
                                video.seekBy(delta)
                                feedback = GestureFeedback.Seek(delta, video.state.value.positionMs)
                                touch()
                            },
                        )
                    },
            )

            if (state.buffering && state.error == null) {
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center).size(42.dp),
                    color = PlayerContent,
                    strokeWidth = 3.dp,
                )
            }

            AnimatedVisibility(
                visible = controlsVisible && state.error == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize()) {
                    PlayerTopBar(
                        state = state,
                        supportsPip = supportsPip,
                        onClose = video::close,
                        onEnterPip = ::enterPip,
                        orientation = orientation,
                        onRotate = { orientation = orientation.next() },
                        onMore = { sheet = VideoSheet.MORE },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                    if (!state.playing && !state.buffering && state.error == null) {
                        FilledIconButton(
                            onClick = {
                                video.togglePlay()
                                touch()
                            },
                            modifier = Modifier.align(Alignment.Center).size(72.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = PlayerContent,
                                contentColor = PlayerBackground,
                            ),
                        ) {
                            Icon(
                                if (state.ended) Icons.Outlined.Replay else Icons.Outlined.PlayArrow,
                                if (state.ended) "Putar ulang" else "Putar video",
                                Modifier.size(42.dp),
                            )
                        }
                    }
                    PlayerBottomBar(
                        state = state,
                        scrub = scrub,
                        onScrub = { scrub = it; touch() },
                        onScrubFinished = {
                            scrub?.let { video.seekTo(it.toLong()) }
                            scrub = null
                            touch()
                        },
                        onToggle = { video.togglePlay(); touch() },
                        onSeekBy = { video.seekBy(it); touch() },
                        onPrevious = { video.previous(); touch() },
                        onNext = { video.next(); touch() },
                        onSpeed = { video.cycleSpeed(); touch() },
                        onSubtitles = { video.cycleSubtitle(); touch() },
                        onDisplay = { video.cycleAspect(); touch() },
                        onRepeat = { video.cycleRepeat(); touch() },
                        onEqualizer = { video.cycleEqualizer(); touch() },
                        onAudioEffect = { video.cycleAudioEffect(); touch() },
                        onEnhance = { video.cycleEnhance(); touch() },
                        onDecoder = { video.cycleDecoderMode(); touch() },
                        onScreenshot = ::takeScreenshot,
                        onQueue = { sheet = VideoSheet.QUEUE },
                        onLock = { controlsLocked = true },
                        isLandscape = landscape,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            feedback?.let { value ->
                GestureOverlay(value, Modifier.align(Alignment.Center))
            }

            AnimatedVisibility(
                visible = showResume && !state.ended && state.error == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(bottom = if (landscape) 110.dp else 156.dp, start = 16.dp, end = 16.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White.copy(alpha = 0.16f),
                    contentColor = PlayerContent,
                ) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Dilanjutkan dari ${VideoPlayback.timeLabel(state.resumeFromMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = {
                            video.restart()
                            showResume = false
                            touch()
                        }) {
                            Text("Mulai dari awal", color = PlayerContent)
                        }
                    }
                }
            }

            state.error?.let { message ->
                PlayerErrorOverlay(
                    message = message,
                    hasNext = state.hasNext,
                    showSoftwareFallback = state.decoderMode == VideoDecoderMode.HARDWARE,
                    onRetry = video::retry,
                    onUseSoftware = video::useSoftwareDecoder,
                    onNext = video::next,
                    onClose = video::close,
                    onOpenExternal = { openExternalPlayer(context, state.entry?.uri) },
                )
            }
        }

        // Mode terkunci hanya menyisakan satu tombol buka kunci; tidak ada tap-to-show atau gestur
        // yang bisa mengubah video secara tidak sengaja.
        if (!inPip && controlsLocked && state.error == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                        .padding(12.dp)
                        .clickable {
                            controlsLocked = false
                            touch()
                        },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    contentColor = PlayerContent,
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.LockOpen, null, Modifier.size(20.dp))
                        Text("Buka kunci", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (!inPip) {
            sheet?.let { kind ->
                VideoOptionsSheet(
                    kind = kind,
                    state = state,
                    video = video,
                    orientation = orientation,
                    supportsPip = supportsPip,
                    onScreenshot = {
                        takeScreenshot()
                        sheet = null
                    },
                    onPickSubtitle = {
                        subtitlePicker.launch(
                            arrayOf(
                                "text/*",
                                "application/x-subrip",
                                "application/x-srt",
                                "text/vtt",
                                "text/x-ssa",
                                "text/x-ass",
                                "application/ttml+xml",
                                "application/octet-stream",
                            ),
                        )
                    },
                    onOpen = { sheet = it },
                    onOrientation = {
                        orientation = it
                        touch()
                    },
                    onEnterPip = { sheet = null; enterPip() },
                    onLock = {
                        sheet = null
                        controlsLocked = true
                        controlsVisible = false
                    },
                    onDismiss = { sheet = null },
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun applyVideoAspect(view: PlayerView, aspect: VideoAspect) {
    view.resizeMode = when (aspect) {
        VideoAspect.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        VideoAspect.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        VideoAspect.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun applySubtitleSettings(
    view: PlayerView,
    size: VideoSubtitleSize,
    style: VideoSubtitleStyle,
    position: VideoSubtitlePosition,
    background: VideoSubtitleBackground,
) {
    val subtitleView = view.subtitleView ?: return
    subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * size.scale)
    subtitleView.setBottomPaddingFraction(position.bottomPaddingFraction)
    // Gaya dari video (ASS/SSA) sering mengunci warna dan ukuran; pengaturan pengguna menang.
    subtitleView.setApplyEmbeddedStyles(false)
    subtitleView.setApplyEmbeddedFontSizes(false)
    subtitleView.setStyle(style.captionStyle(background))
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun VideoSubtitleStyle.captionStyle(background: VideoSubtitleBackground): CaptionStyleCompat {
    val backgroundColor = android.graphics.Color.argb(background.alphaPercent * 255 / 100, 0, 0, 0)
    val (foreground, typeface) = when (this) {
        VideoSubtitleStyle.OUTLINE -> android.graphics.Color.WHITE to Typeface.DEFAULT
        VideoSubtitleStyle.BOLD -> android.graphics.Color.WHITE to Typeface.DEFAULT_BOLD
        VideoSubtitleStyle.YELLOW -> android.graphics.Color.YELLOW to Typeface.DEFAULT
    }
    return CaptionStyleCompat(
        foreground,
        backgroundColor,
        android.graphics.Color.TRANSPARENT,
        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
        android.graphics.Color.BLACK,
        typeface,
    )
}

private val VideoOrientation.icon: ImageVector
    get() = when (this) {
        VideoOrientation.AUTO -> Icons.Outlined.ScreenRotation
        VideoOrientation.LANDSCAPE -> Icons.Outlined.ScreenLockLandscape
        VideoOrientation.PORTRAIT -> Icons.Outlined.ScreenLockPortrait
    }

private fun VideoOrientation.activityValue(): Int = when (this) {
    VideoOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
    VideoOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    VideoOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
}

private fun openExternalPlayer(context: Context, rawUri: String?) {
    if (rawUri.isNullOrBlank()) return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(rawUri.toUri(), "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Putar video dengan")) }
        .onFailure {
            Toast.makeText(
                context,
                "Tidak ada aplikasi pemutar lain yang bisa membuka video ini.",
                Toast.LENGTH_LONG,
            ).show()
        }
}

/** Membaca nama/tipe berkas subtitle dari penyedia dokumen dan meminta izin baca persisten. */
private fun externalSubtitle(context: Context, uri: Uri): VideoSubtitle? {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val resolver = context.contentResolver
    val name = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "subtitle"
    val mime = VideoPlayback.subtitleMime(name)
        ?: VideoPlayback.subtitleMimeFromType(runCatching { resolver.getType(uri) }.getOrNull())
        ?: return null
    val language = VideoPlayback.languageFrom(name)
    // Nama berkas dipakai sebagai label agar bisa dibedakan dari track subtitle tertanam yang
    // kebetulan memakai bahasa yang sama.
    return VideoSubtitle(uri.toString(), mime, language, name)
}

@Composable
private fun PlayerTopBar(
    state: VideoPlayerViewModel.State,
    supportsPip: Boolean,
    orientation: VideoOrientation,
    onClose: () -> Unit,
    onEnterPip: () -> Unit,
    onRotate: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent)))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = PlayerContent),
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Tutup pemutar")
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                state.entry?.displayTitle ?: "Video",
                color = PlayerContent,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.entries.size > 1) {
                Text(
                    "${state.index + 1} dari ${state.entries.size}",
                    color = PlayerMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (supportsPip) {
            IconButton(
                onClick = onEnterPip,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = PlayerContent),
            ) {
                Icon(Icons.Outlined.PictureInPictureAlt, "Putar di jendela kecil")
            }
        }
        IconButton(
            onClick = onRotate,
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = PlayerContent),
        ) {
            Icon(
                orientation.icon,
                "Orientasi ${orientation.label}. Ketuk untuk ${orientation.next().label.lowercase()}",
            )
        }
        IconButton(
            onClick = onMore,
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = PlayerContent),
        ) {
            Icon(Icons.Outlined.MoreVert, "Opsi pemutar")
        }
    }
}@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerBottomBar(
    state: VideoPlayerViewModel.State,
    scrub: Float?,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onToggle: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeed: () -> Unit,
    onSubtitles: () -> Unit,
    onDisplay: () -> Unit,
    onRepeat: () -> Unit,
    onEqualizer: () -> Unit,
    onAudioEffect: () -> Unit,
    onEnhance: () -> Unit,
    onDecoder: () -> Unit,
    onScreenshot: () -> Unit,
    onQueue: () -> Unit,
    onLock: () -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val duration = state.durationMs.coerceAtLeast(1)
    val shownPosition = scrub?.toLong() ?: state.positionMs
    val subtitlesActive = state.textTracks.isNotEmpty() && !state.subtitlesDisabled
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.94f))))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = if (isLandscape) 18.dp else 12.dp, vertical = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                VideoPlayback.timeLabel(shownPosition),
                color = PlayerContent,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(if (isLandscape) 54.dp else 46.dp),
            )
            Slider(
                value = (scrub ?: state.positionMs.toFloat()).coerceIn(0f, duration.toFloat()),
                onValueChange = onScrub,
                onValueChangeFinished = onScrubFinished,
                valueRange = 0f..duration.toFloat(),
                enabled = state.durationMs > 0,
                modifier = Modifier.weight(1f).height(28.dp),
                thumb = {
                    Box(Modifier.size(10.dp).background(PlayerContent, CircleShape))
                },
                track = { sliderState ->
                    val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                    val fraction = if (range > 0f) {
                        ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
                    } else 0f
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(PlayerContent.copy(alpha = 0.28f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(PlayerContent),
                        )
                    }
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    disabledThumbColor = Color.Transparent,
                    disabledActiveTrackColor = Color.Transparent,
                    disabledInactiveTrackColor = Color.Transparent,
                ),
            )
            Text(
                if (state.durationMs > 0) VideoPlayback.timeLabel(state.durationMs) else "--:--",
                color = PlayerMuted,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End,
                modifier = Modifier.width(if (isLandscape) 54.dp else 46.dp),
            )
        }
        TransportControls(
            state = state,
            onToggle = onToggle,
            onSeekBy = onSeekBy,
            onPrevious = onPrevious,
            onNext = onNext,
            compact = isLandscape,
            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        )
        // Semua tombol di bawah adalah siklus satu ketuk, seperti tombol Ulangi. Pilihan detail
        // tetap tersedia lewat lembar "Opsi pemutar" di kanan atas.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            QuickChip(
                icon = Icons.Outlined.Speed,
                text = VideoPlayback.speedLabel(state.speed),
                description = "Kecepatan ${VideoPlayback.speedLabel(state.speed)}. Ketuk untuk berikutnya",
                active = abs(state.speed - 1f) > 0.001f,
                onClick = onSpeed,
            )
            QuickChip(
                icon = Icons.Outlined.ClosedCaption,
                text = subtitleChipLabel(state),
                description = "Subtitle ${subtitleChipDescription(state)}. Ketuk untuk berikutnya",
                active = subtitlesActive,
                onClick = onSubtitles,
            )
            QuickChip(
                icon = Icons.Outlined.GraphicEq,
                text = "EQ ${state.equalizer.shortLabel}",
                description = "Equalizer ${state.equalizer.label}. Ketuk untuk berikutnya",
                active = state.equalizer != VideoEqualizer.OFF,
                onClick = onEqualizer,
            )
            QuickChip(
                icon = Icons.Outlined.SurroundSound,
                text = "FX ${state.audioEffect.shortLabel}",
                description = "Efek audio ${state.audioEffect.label}. Ketuk untuk berikutnya",
                active = state.audioEffect != VideoAudioEffect.OFF,
                onClick = onAudioEffect,
            )
            QuickChip(
                icon = Icons.Outlined.AutoAwesome,
                text = state.enhance.shortLabel,
                description = "Smart Enhance ${state.enhance.label}. Ketuk untuk berikutnya",
                active = state.enhance != VideoEnhance.OFF,
                onClick = onEnhance,
            )
            QuickChip(
                icon = Icons.Outlined.AspectRatio,
                text = state.aspect.label,
                description = "Rasio ${state.aspect.label}. Ketuk untuk berikutnya",
                active = state.aspect != VideoAspect.FIT,
                onClick = onDisplay,
            )
            QuickChip(
                icon = if (state.repeat == VideoRepeat.ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
                text = repeatSummary(state.repeat),
                description = "Ulangi ${state.repeat.label}. Ketuk untuk berikutnya",
                active = state.repeat != VideoRepeat.OFF,
                onClick = onRepeat,
            )
            QuickChip(
                icon = Icons.Outlined.Memory,
                text = "Dek ${state.decoderMode.shortLabel}",
                description = "Dekoder ${state.decoderMode.label}. Ketuk untuk berikutnya",
                active = state.decoderMode != VideoDecoderMode.AUTO,
                onClick = onDecoder,
            )
            QuickChip(
                icon = Icons.Outlined.PhotoCamera,
                text = "Foto",
                description = "Ambil screenshot video",
                active = false,
                onClick = onScreenshot,
            )
            QuickChip(
                icon = Icons.AutoMirrored.Outlined.PlaylistPlay,
                text = "${state.index + 1}/${state.entries.size}",
                description = "Buka antrean video",
                active = false,
                onClick = onQueue,
            )
            QuickChip(
                icon = Icons.Outlined.Lock,
                text = "Kunci",
                description = "Kunci kontrol pemutar",
                active = false,
                onClick = onLock,
            )
        }
    }
}

private fun subtitleChipLabel(state: VideoPlayerViewModel.State): String = when {
    state.subtitlesDisabled -> "CC Mati"
    state.textTracks.isEmpty() -> "CC —"
    else -> {
        val selected = state.textTracks.firstOrNull { it.selected }
        val name = selected?.language?.takeIf { it.isNotBlank() }
            ?.let(VideoPlayback::languageName)
            ?.takeIf { it.isNotBlank() }
            ?: selected?.label
            ?: "Otomatis"
        "CC ${name.take(10)}"
    }
}

private fun subtitleChipDescription(state: VideoPlayerViewModel.State): String = when {
    state.subtitlesDisabled -> "nonaktif"
    state.textTracks.isEmpty() -> "belum tersedia"
    else -> state.textTracks.firstOrNull { it.selected }?.label ?: "otomatis"
}

private fun repeatSummary(repeat: VideoRepeat): String = when (repeat) {
    VideoRepeat.OFF -> "Mati"
    VideoRepeat.ALL -> "Semua"
    VideoRepeat.ONE -> "Satu"
}

@Composable
private fun TransportControls(
    state: VideoPlayerViewModel.State,
    onToggle: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val buttonSize = if (compact) 40.dp else 44.dp
    val playSize = if (compact) 50.dp else 56.dp
    Row(
        modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = state.hasPrevious || state.positionMs > 5_000,
            modifier = Modifier.size(buttonSize),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = PlayerContent,
                disabledContentColor = PlayerContent.copy(alpha = 0.3f),
            ),
        ) {
            Icon(Icons.Outlined.SkipPrevious, "Video sebelumnya")
        }
        IconButton(
            onClick = { onSeekBy(-VideoPlayback.SEEK_STEP_MS) },
            modifier = Modifier.size(buttonSize),
            colors = IconButtonDefaults.iconButtonColors(contentColor = PlayerContent),
        ) {
            Icon(Icons.Outlined.Replay10, "Mundur 10 detik")
        }
        FilledIconButton(
            onClick = onToggle,
            modifier = Modifier.size(playSize),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = PlayerContent,
                contentColor = PlayerBackground,
            ),
        ) {
            if (state.buffering) {
                CircularProgressIndicator(Modifier.size(if (compact) 22.dp else 24.dp), color = PlayerBackground, strokeWidth = 2.dp)
            } else {
                Icon(
                    when {
                        state.ended -> Icons.Outlined.Replay
                        state.playing -> Icons.Outlined.Pause
                        else -> Icons.Outlined.PlayArrow
                    },
                    when {
                        state.ended -> "Putar ulang"
                        state.playing -> "Jeda video"
                        else -> "Putar video"
                    },
                    Modifier.size(if (compact) 28.dp else 32.dp),
                )
            }
        }
        IconButton(
            onClick = { onSeekBy(VideoPlayback.SEEK_STEP_MS) },
            modifier = Modifier.size(buttonSize),
            colors = IconButtonDefaults.iconButtonColors(contentColor = PlayerContent),
        ) {
            Icon(Icons.Outlined.Forward10, "Maju 10 detik")
        }
        IconButton(
            onClick = onNext,
            enabled = state.hasNext,
            modifier = Modifier.size(buttonSize),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = PlayerContent,
                disabledContentColor = PlayerContent.copy(alpha = 0.3f),
            ),
        ) {
            Icon(Icons.Outlined.SkipNext, "Video berikutnya")
        }
    }
}

@Composable
private fun QuickChip(
    icon: ImageVector,
    text: String,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.height(36.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = if (active) 0.20f else 0.10f),
        contentColor = if (active) PlayerContent else PlayerContent.copy(alpha = 0.82f),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp).height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, description, Modifier.size(16.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun GestureOverlay(feedback: GestureFeedback, modifier: Modifier = Modifier) {
    val icon = when (feedback) {
        is GestureFeedback.Seek -> if (feedback.deltaMs >= 0) Icons.Outlined.FastForward else Icons.Outlined.FastRewind
        is GestureFeedback.Volume -> Icons.AutoMirrored.Outlined.VolumeUp
        is GestureFeedback.Brightness -> Icons.Outlined.Brightness6
    }
    val text = when (feedback) {
        is GestureFeedback.Seek -> {
            val sign = if (feedback.deltaMs >= 0) "+" else "−"
            "$sign${abs(feedback.deltaMs) / 1000} dtk · ${VideoPlayback.timeLabel(feedback.targetMs)}"
        }
        is GestureFeedback.Volume -> "Volume ${feedback.percent}%"
        is GestureFeedback.Brightness -> "Kecerahan ${feedback.percent}%"
    }
    Surface(
        modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.72f),
        contentColor = PlayerContent,
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, null, Modifier.size(24.dp))
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun PlayerErrorOverlay(
    message: String,
    hasNext: Boolean,
    showSoftwareFallback: Boolean,
    onRetry: () -> Unit,
    onUseSoftware: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier.padding(24.dp).widthIn(max = 460.dp),
            shape = RoundedCornerShape(24.dp),
            color = SheetBackground,
            contentColor = PlayerContent,
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(44.dp), tint = Color(0xFFFFB4AB))
                Text(message, color = PlayerContent, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Jika tetap gagal, buka video ini dengan aplikasi pemutar lain di perangkat.",
                    color = PlayerMuted,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onRetry) { Text("Coba lagi") }
                    OutlinedButton(
                        onClick = onClose,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PlayerContent),
                    ) { Text("Tutup") }
                }
                if (showSoftwareFallback) {
                    TextButton(onClick = onUseSoftware) {
                        Text("Coba dekoder software", color = PlayerContent)
                    }
                }
                TextButton(onClick = onOpenExternal) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Buka di pemutar lain", color = PlayerContent)
                }
                if (hasNext) {
                    TextButton(onClick = onNext) { Text("Video berikutnya", color = PlayerContent) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoOptionsSheet(
    kind: VideoSheet,
    state: VideoPlayerViewModel.State,
    video: VideoPlayerViewModel,
    orientation: VideoOrientation,
    supportsPip: Boolean,
    onPickSubtitle: () -> Unit,
    onScreenshot: () -> Unit,
    onOpen: (VideoSheet) -> Unit,
    onOrientation: (VideoOrientation) -> Unit,
    onEnterPip: () -> Unit,
    onLock: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetBackground,
        contentColor = PlayerContent,
        dragHandle = { BottomSheetDefaults.DragHandle(color = PlayerContent.copy(alpha = 0.4f)) },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (kind != VideoSheet.MORE) {
                    IconButton(onClick = { onOpen(VideoSheet.MORE) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali", tint = PlayerContent)
                    }
                }
                Text(
                    when (kind) {
                        VideoSheet.SPEED -> "Kecepatan putar"
                        VideoSheet.SUBTITLES -> "Subtitle"
                        VideoSheet.AUDIO -> "Track audio"
                        VideoSheet.DISPLAY -> "Tampilan"
                        VideoSheet.EFFECTS -> "Suara & gambar"
                        VideoSheet.DECODER -> "Dekoder video"
                        VideoSheet.QUEUE -> "Antrean video"
                        VideoSheet.MORE -> "Opsi pemutar"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = if (kind == VideoSheet.MORE) 20.dp else 4.dp),
                )
            }
            when (kind) {
                VideoSheet.SPEED -> {
                    SheetSection("Kecepatan")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VideoPlayback.speeds.forEach { speed ->
                            ChoicePill(
                                label = VideoPlayback.speedLabel(speed),
                                selected = abs(state.speed - speed) < 0.001f,
                                onClick = { video.setSpeed(speed) },
                            )
                        }
                    }
                }
                VideoSheet.SUBTITLES -> {
                    SheetChoice(
                        label = "Nonaktif",
                        selected = state.subtitlesDisabled,
                        onClick = { video.disableSubtitles(); onDismiss() },
                    )
                    SheetChoice(
                        label = "Otomatis",
                        selected = !state.subtitlesDisabled && state.textTracks.none { it.selected },
                        onClick = { video.automaticTracks(androidx.media3.common.C.TRACK_TYPE_TEXT); onDismiss() },
                    )
                    SheetSection("Subtitle tambahan")
                    OutlinedButton(
                        onClick = onPickSubtitle,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PlayerContent),
                    ) {
                        Text("Pilih berkas SRT / VTT / ASS / TTML")
                    }
                    if (state.textTracks.isEmpty()) {
                        SheetHint("Subtitle belum terbaca. Tunggu video siap, lalu buka lagi.")
                    } else {
                        SheetSection("Tersedia")
                        state.textTracks.forEach { track ->
                            SheetChoice(
                                label = track.label,
                                selected = !state.subtitlesDisabled && track.selected,
                                onClick = { video.selectSubtitle(track); onDismiss() },
                            )
                        }
                    }
                    SheetSection("Ukuran teks")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VideoSubtitleSize.entries.forEach { size ->
                            ChoicePill(size.label, state.subtitleSize == size) { video.setSubtitleSize(size) }
                        }
                    }
                    SheetSection("Gaya teks")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VideoSubtitleStyle.entries.forEach { style ->
                            ChoicePill(style.label, state.subtitleStyle == style) { video.setSubtitleStyle(style) }
                        }
                    }
                    SheetSection("Posisi vertikal")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VideoSubtitlePosition.entries.forEach { position ->
                            ChoicePill(position.label, state.subtitlePosition == position) {
                                video.setSubtitlePosition(position)
                            }
                        }
                    }
                    SheetSection("Latar teks")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VideoSubtitleBackground.entries.forEach { background ->
                            ChoicePill(background.label, state.subtitleBackground == background) {
                                video.setSubtitleBackground(background)
                            }
                        }
                    }
                    SheetHint("Transparan menyisakan garis tepi saja, sehingga kotak latar tidak menutupi bagian video.")
                    SheetSection("Delay subtitle")
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { video.adjustSubtitleDelay(-VideoPlayback.SUBTITLE_DELAY_STEP_MS) },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PlayerContent),
                        ) { Text("−0,25 dtk") }
                        Text(
                            VideoPlayback.subtitleDelayLabel(state.subtitleDelayMs),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            color = PlayerContent,
                        )
                        OutlinedButton(
                            onClick = { video.adjustSubtitleDelay(VideoPlayback.SUBTITLE_DELAY_STEP_MS) },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PlayerContent),
                        ) { Text("+0,25 dtk") }
                    }
                    if (state.subtitleDelayMs != 0) {
                        TextButton(
                            onClick = video::resetSubtitleDelay,
                            modifier = Modifier.padding(horizontal = 10.dp),
                        ) { Text("Kembalikan ke 0", color = PlayerContent) }
                    }
                    SheetHint("Positif membuat subtitle lebih lambat, negatif lebih cepat. Berlaku untuk subtitle sidecar maupun track di dalam video.")
                }
                VideoSheet.AUDIO -> {
                    SheetChoice(
                        label = "Otomatis",
                        selected = !state.audioTracks.any { it.selected },
                        onClick = { video.automaticTracks(androidx.media3.common.C.TRACK_TYPE_AUDIO); onDismiss() },
                    )
                    state.audioTracks.forEach { track ->
                        SheetChoice(
                            label = track.label,
                            selected = track.selected,
                            onClick = { video.selectAudio(track); onDismiss() },
                        )
                    }
                    if (state.audioTracks.isEmpty()) SheetHint("Track audio belum terbaca.")
                }
                VideoSheet.DISPLAY -> {
                    SheetSection("Rasio layar")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VideoAspect.entries.forEach { aspect ->
                            ChoicePill(aspect.label, state.aspect == aspect) { video.setAspect(aspect) }
                        }
                    }
                    SheetSection("Rotasi layar")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VideoOrientation.entries.forEach { option ->
                            ChoicePill(option.label, orientation == option) { onOrientation(option) }
                        }
                    }
                    SheetHint("Otomatis mengikuti sensor dan mengabaikan kunci rotasi sistem saat pemutar terbuka.")
                    SheetSection("Ulangi")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VideoRepeat.entries.forEach { repeat ->
                            ChoicePill(repeat.label, state.repeat == repeat) { video.setRepeat(repeat) }
                        }
                    }
                }
                VideoSheet.EFFECTS -> {
                    SheetSection("Equalizer")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VideoEqualizer.entries.forEach { preset ->
                            ChoicePill(preset.label, state.equalizer == preset) { video.setEqualizer(preset) }
                        }
                    }
                    SheetSection("Efek audio")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VideoAudioEffect.entries.forEach { effect ->
                            ChoicePill(effect.label, state.audioEffect == effect) { video.setAudioEffect(effect) }
                        }
                    }
                    SheetHint("Bass Boost dan Ruang bergantung pada dukungan audiofx perangkat. Equalizer video terpisah dari equalizer lagu.")
                    SheetSection("Smart Enhance")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VideoEnhance.entries.forEach { preset ->
                            ChoicePill(preset.label, state.enhance == preset) { video.setEnhance(preset) }
                        }
                    }
                    SheetHint("Warna dan kontras diproses di GPU. Jika perangkat tidak mendukung efek video, video tetap diputar tanpa efek.")
                }
                VideoSheet.DECODER -> {
                    VideoDecoderMode.entries.forEach { mode ->
                        SheetChoice(
                            label = when (mode) {
                                VideoDecoderMode.AUTO -> "Otomatis · perangkat lalu software"
                                VideoDecoderMode.HARDWARE -> "Perangkat saja"
                                VideoDecoderMode.SOFTWARE -> "Software (FFmpeg/dav1d)"
                            },
                            selected = state.decoderMode == mode,
                            onClick = { video.setDecoderMode(mode) },
                        )
                    }
                    SheetHint("Mengganti dekoder membuka ulang video pada posisi yang sama. Mode Perangkat tidak jatuh ke software otomatis; dari layar error tersedia tombol Coba dekoder software.")
                }
                VideoSheet.QUEUE -> {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        itemsIndexed(state.entries) { index, entry ->
                            SheetChoice(
                                label = "${index + 1}. ${entry.displayTitle}",
                                selected = index == state.index,
                                onClick = {
                                    video.jumpTo(index)
                                    onDismiss()
                                },
                            )
                        }
                    }
                    if (state.entries.size <= 1) {
                        SheetHint("Belum ada video lain di antrean. Antrean diisi dari Koleksi atau Riwayat.")
                    }
                }
                VideoSheet.MORE -> {
                    val audioLabel = state.audioTracks.firstOrNull { it.selected }?.label ?: "Otomatis"
                    val subtitleLabel = when {
                        state.subtitlesDisabled -> "Nonaktif"
                        else -> state.textTracks.firstOrNull { it.selected }?.label ?: "Otomatis"
                    }
                    SheetChoice("Kecepatan · ${VideoPlayback.speedLabel(state.speed)}", false) { onOpen(VideoSheet.SPEED) }
                    SheetChoice("Subtitle · $subtitleLabel", false) { onOpen(VideoSheet.SUBTITLES) }
                    SheetChoice("Track audio · $audioLabel", false) { onOpen(VideoSheet.AUDIO) }
                    SheetChoice("Tampilan · ${state.aspect.label} · ${orientation.label}", false) { onOpen(VideoSheet.DISPLAY) }
                    SheetChoice("Equalizer & efek audio · ${state.equalizer.label} · ${state.audioEffect.label}", false) {
                        onOpen(VideoSheet.EFFECTS)
                    }
                    SheetChoice("Smart Enhance · ${state.enhance.label}", false) { onOpen(VideoSheet.EFFECTS) }
                    SheetChoice("Dekoder · ${state.decoderMode.label}", false) { onOpen(VideoSheet.DECODER) }
                    SheetChoice("Antrean · ${state.index + 1} dari ${state.entries.size}", false) { onOpen(VideoSheet.QUEUE) }
                    SheetChoice("Ambil screenshot", false, onScreenshot)
                    SheetChoice("Kunci kontrol", false, onLock)
                    if (supportsPip) {
                        SheetChoice("Putar di jendela kecil", false, onEnterPip)
                    }
                    SheetHint("Tombol cepat di bawah pemutar berputar satu ketuk. Audio tetap memakai pemutar lagu terpisah; menutup video tidak mengubah antrean audio.")
                }
            }
        }
    }
}

@Composable
private fun SheetSection(text: String) {
    Text(
        text,
        Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = PlayerMuted,
    )
}

@Composable
private fun SheetHint(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        style = MaterialTheme.typography.bodySmall,
        color = PlayerMuted,
    )
}

@Composable
private fun SheetChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = PlayerContent,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) Icon(Icons.Outlined.Check, null, tint = AccentBlue)
    }
}

@Composable
private fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (selected) PlayerContent else Color.Transparent,
        contentColor = if (selected) PlayerBackground else PlayerContent,
        border = if (selected) null else BorderStroke(1.dp, PlayerContent.copy(alpha = 0.35f)),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
