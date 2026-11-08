package id.local.tubeflow.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CutCornerShape
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
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.ScreenLockLandscape
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import id.local.tubeflow.core.VideoBrightnessMode
import id.local.tubeflow.core.MediaMode
import id.local.tubeflow.core.VideoBrightnessPolicy
import id.local.tubeflow.core.VideoVolumePolicy
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
import id.local.tubeflow.ui.theme.LocalTransformersTheme
import kotlin.math.abs
import kotlinx.coroutines.delay

private val PlayerBackground = Color.Black
private val PlayerContent = Color.White
private val PlayerMuted = Color.White.copy(alpha = 0.62f)
private val SheetBackground = Color(0xFF1B1B1F)
private val AccentBlue = Color(0xFF8AB4F8)

/**
 * Aksen kontrol pemutar. Tema Transformers memakai biru elektrik paletnya sendiri supaya status
 * aktif di pengaturan cepat, lencana, dan tanda pilih tidak tertinggal di biru generik.
 */
@Composable
private fun PlayerAccent(): Color =
    if (LocalTransformersTheme.current) MaterialTheme.colorScheme.primary else AccentBlue

/**
 * Latar lembar/overlay pemutar. Pemutar tetap gelap di semua tema, tetapi tema Transformers memakai
 * panel baja navy paletnya agar lembar opsi dan galat tidak tampil sebagai hitam generik.
 */
@Composable
private fun PlayerSheet(): Color =
    if (LocalTransformersTheme.current) MaterialTheme.colorScheme.surfaceContainer else SheetBackground

private enum class VideoSheet { SPEED, SUBTITLES, AUDIO, DISPLAY, EFFECTS, DECODER, QUEUE, MORE }

private fun currentVideoBrightness(activity: Activity?): Float = VideoBrightnessPolicy.initial(
    activity?.window?.attributes?.screenBrightness ?: -1f,
    activity?.let { Settings.System.getInt(it.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 127) } ?: 127,
)

private sealed interface GestureFeedback {
    data class Seek(val deltaMs: Long, val targetMs: Long) : GestureFeedback
    data class Volume(val percent: Int) : GestureFeedback
    data class Brightness(val percent: Int) : GestureFeedback
}

/** Konfirmasi singkat setelah tombol pengaturan cepat mengubah sesuatu. */
private data class PlayerNotice(
    val icon: ImageVector,
    val title: String,
    val value: String,
)

/**
 * Pemutar video penuh di atas aplikasi, memakai Media3 ExoPlayer. Layar ini tidak menyentuh
 * pemutar audio: sebelum dibuka pemanggil mem-pause audio, dan setelah ditutup ExoPlayer dilepas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(video: VideoPlayerViewModel) {
    val state by video.state.collectAsStateWithLifecycle()
    if (!state.active) return
    val context = LocalContext.current
    val activity = LocalActivity.current as? ComponentActivity
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val lifecycleOwner = LocalLifecycleOwner.current
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val supportsPip = remember(context) { VideoPip.supports(context) }
    val originalOrientation = remember { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<VideoSheet?>(null) }
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    var orientation by remember { mutableStateOf(VideoOrientation.AUTO) }
    var feedback by remember { mutableStateOf<GestureFeedback?>(null) }
    var notice by remember { mutableStateOf<PlayerNotice?>(null) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var showResume by remember { mutableStateOf(false) }
    var controlsLocked by remember { mutableStateOf(false) }
    var quickSettingsVisible by rememberSaveable { mutableStateOf(true) }
    var pickingSubtitle by remember { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    // Buffer sekejap saat refill/seek tidak boleh menampilkan spinner atau mengganti tombol putar.
    var bufferingVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.buffering, state.error) {
        if (state.buffering && state.error == null) {
            delay(VideoPlayback.BUFFERING_INDICATOR_DELAY_MS)
            val latest = video.state.value
            bufferingVisible = VideoPlayback.shouldRevealBuffering(
                buffering = latest.buffering,
                hasError = latest.error != null,
                waitedMs = VideoPlayback.BUFFERING_INDICATOR_DELAY_MS,
            )
        } else {
            bufferingVisible = false
        }
    }

    fun touch() {
        controlsVisible = true
        interaction++
    }

    fun notify(noticeValue: PlayerNotice) {
        notice = noticeValue
        touch()
    }

    fun finishSubtitlePick() {
        pickingSubtitle = false
        VideoPip.resumeAutoEnter()
    }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        finishSubtitlePick()
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
    // Jeda sesaat saat buffering tidak boleh memunculkan kontrol tanpa sentuhan pengguna.
    LaunchedEffect(state.playing, state.buffering) {
        if (!state.playing && !state.buffering) {
            delay(400)
            val latest = video.state.value
            if (!latest.playing && !latest.buffering) controlsVisible = true
        }
    }
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
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(1_600)
            notice = null
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
    LaunchedEffect(activity, state.playing, state.videoWidth, state.videoHeight, supportsPip, inPip, pickingSubtitle) {
        val host = activity ?: return@LaunchedEffect
        if (!supportsPip) return@LaunchedEffect
        VideoPip.apply(host, video.state.value, autoEnter = state.playing && !inPip && !pickingSubtitle)
    }

    // Aksi play/pause PiP diteruskan receiver manifest ke ViewModel yang sedang aktif.
    DisposableEffect(video) {
        VideoPip.attachToggle { video.togglePlay() }
        onDispose {
            VideoPip.detachToggle()
            VideoPip.resumeAutoEnter()
        }
    }

    DisposableEffect(activity) {
        val host = activity
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
            id.local.tubeflow.core.VideoDiagnostics.pip(info.isInPictureInPictureMode)
        }
        host?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { host?.removeOnPictureInPictureModeChangedListener(listener) }
    }

    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = originalOrientation
            VideoPip.disableAutoEnter(activity)
        }
    }

    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            // Saat masuk PiP, sebagian perangkat tetap mengirim ON_STOP. Melepas pemutar di sini
            // mematikan permukaan yang masih dipakai jendela PiP, sehingga pemutaran berhenti dan
            // tampak sebagai galat dekoder. Cukup jeda ketika aplikasi benar-benar ke latar.
            if (event == Lifecycle.Event.ON_STOP && activity?.isInPictureInPictureMode != true) video.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.playing, inPip, activity) {
        activity?.window?.let { window ->
            if (state.playing && !inPip) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    val saving = id.local.tubeflow.core.PlaybackPower.saving
    val brightnessOverride = rememberVideoBrightness(activity, inPip, saving, state.brightnessMode, state.manualBrightness)
    LaunchedEffect(saving) { video.refreshPowerPolicy() }

    val videoAspect = state.aspect
    val subtitleSize = state.subtitleSize
    val subtitleStyle = state.subtitleStyle
    val subtitlePosition = state.subtitlePosition
    val subtitleBackground = state.subtitleBackground
    Box(Modifier.fillMaxSize().background(PlayerBackground)) {
        // Setiap pergantian dekoder membuat ExoPlayer baru. Permukaan PlayerView ikut dibuat
        // ulang agar video tidak tertinggal di surface milik player lama (audio tetap jalan).
        key(state.playerVersion) {
            BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().onSizeChanged { video.setViewport(it.width, it.height) }, contentAlignment = Alignment.Center) {
                val (surfaceWidth, surfaceHeight) = if (state.effectPresentation) maxWidth.value to maxHeight.value else id.local.tubeflow.core.VideoViewport.size(
                    maxWidth.value, maxHeight.value, state.videoWidth, state.videoHeight, videoAspect, state.pixelRatio,
                )
                // Jalur software tidak memakai efek GL yang mengunci crop ke bawah; permukaan yang
                // lebih tinggi dari wadah ditempel ke tepi bawah agar hardsub tetap terlihat.
                val anchoredBottom = !state.effectPresentation && id.local.tubeflow.core.VideoViewport.anchorsBottom(
                    maxWidth.value, maxHeight.value, state.videoWidth, state.videoHeight, videoAspect, state.pixelRatio,
                )
                LaunchedEffect(surfaceWidth, surfaceHeight, anchoredBottom, videoAspect, state.videoWidth, state.videoHeight, state.pixelRatio, state.effectPresentation) {
                    id.local.tubeflow.core.VideoDiagnostics.geometry(
                        aspect = videoAspect.name,
                        containerWidth = maxWidth.value,
                        containerHeight = maxHeight.value,
                        videoWidth = state.videoWidth,
                        videoHeight = state.videoHeight,
                        pixelRatio = state.pixelRatio,
                        surfaceWidth = surfaceWidth,
                        surfaceHeight = surfaceHeight,
                        presenting = state.effectPresentation,
                        anchoredBottom = anchoredBottom,
                    )
                }
            // `update` berjalan setiap tick progres. setStyle/setTextSize memicu layout ulang
            // SubtitleView, jadi pengaturan subtitle hanya diterapkan saat nilainya berubah.
            val appliedSubtitle = remember { mutableStateOf<String?>(null) }
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        useController = false
                        keepScreenOn = false
                    }.also { playerView = it }
                },
                update = { view ->
                    view.player = video.exoPlayer
                    // Permukaan sudah berukuran tepat, jadi FIT tidak menambah letterbox. Bila
                    // pipeline GL ternyata tidak mengurus rasio, FIT berletterbox benar alih-alih
                    // memotong tepi seperti FILL.
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    view.keepScreenOn = state.playing && !inPip
                    val subtitleKey = "${subtitleSize.name}|${subtitleStyle.name}|${subtitlePosition.name}|${subtitleBackground.name}"
                    if (appliedSubtitle.value != subtitleKey) {
                        applySubtitleSettings(view, subtitleSize, subtitleStyle, subtitlePosition, subtitleBackground)
                        id.local.tubeflow.core.VideoDiagnostics.subtitle(subtitleSize.name, subtitleStyle.name)
                        appliedSubtitle.value = subtitleKey
                    }
                },
                onRelease = { view ->
                    view.player = null
                    if (playerView === view) playerView = null
                },
                modifier = Modifier
                    .align(if (anchoredBottom) Alignment.BottomCenter else Alignment.Center)
                    // Permukaan software bisa lebih tinggi dari wadah. `wrapContentSize` yang
                    // memusatkan anak membuat separuh luberan jatuh ke bawah layar dan memotong
                    // hardsub; saat anchor bawah aktif, luberan harus keluar lewat tepi atas.
                    .wrapContentSize(if (anchoredBottom) Alignment.BottomCenter else Alignment.Center, unbounded = true)
                    .requiredSize(surfaceWidth.dp, surfaceHeight.dp),
            )
            }
        }

        if (!inPip && !controlsLocked) {
            // Permukaan gestur berada di atas video tetapi di bawah kontrol, sehingga tombol tetap menang.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(state.entry?.uri) {
                        var axis = 0 // 1 seek, 2 volume, 3 kecerahan manual.
                        var total = Offset.Zero
                        var startPosition = 0L
                        var startVolume = 0
                        var startBrightness = 0.5f
                        var startX = 0f
                        detectDragGestures(
                            onDragStart = { offset ->
                                startX = offset.x
                                axis = 0
                                total = Offset.Zero
                                startPosition = video.state.value.positionMs
                                startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                startBrightness = if (video.state.value.brightnessMode == VideoBrightnessMode.MANUAL)
                                    video.state.value.manualBrightness else currentVideoBrightness(activity)
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
                                        val level = VideoBrightnessPolicy.clamp(startBrightness - total.y / size.height)
                                        video.setManualBrightness(level, persist = false)
                                        feedback = GestureFeedback.Brightness((level * 100).toInt())
                                    }
                                }
                            },
                            onDragEnd = {
                                if (axis == 1) {
                                    (feedback as? GestureFeedback.Seek)?.let { video.seekTo(it.targetMs) }
                                }
                                if (axis == 3) video.setManualBrightness(video.state.value.manualBrightness)
                                axis = 0
                            },
                            onDragCancel = {
                                if (axis == 3) video.setManualBrightness(video.state.value.manualBrightness)
                                axis = 0
                            },
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

            if (bufferingVisible && state.error == null) {
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
                    // Kontrol atas hanya berisi navigasi keluar/antrean/opsi; pengaturan cepat
                    // menjadi deretan ikon tanpa label tepat di bawahnya, terpisah dari tombol putar.
                    Column(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            // Gradien menutupi bilah atas DAN deretan pengaturan cepat sekaligus:
                            // judul serta label nilainya tetap terbaca di atas adegan video terang.
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.80f),
                                    0.55f to Color.Black.copy(alpha = 0.42f),
                                    1f to Color.Transparent,
                                ),
                            ),
                    ) {
                        PlayerTopBar(
                            state = state,
                            quickSettingsVisible = quickSettingsVisible,
                            onToggleQuickSettings = {
                                quickSettingsVisible = !quickSettingsVisible
                                touch()
                            },
                            onClose = video::close,
                            onQueue = { sheet = VideoSheet.QUEUE },
                            onMore = { sheet = VideoSheet.MORE },
                        )
                        QuickSettingsBar(
                            state = state,
                            visible = quickSettingsVisible,
                            onSpeed = {
                                video.cycleSpeed()
                                notify(
                                    PlayerNotice(
                                        Icons.Outlined.Speed,
                                        "Kecepatan putar",
                                        VideoPlayback.speedLabel(video.state.value.speed),
                                    ),
                                )
                            },
                            onSubtitles = {
                                video.cycleSubtitle()
                                notify(
                                    PlayerNotice(
                                        Icons.Outlined.ClosedCaption,
                                        "Subtitle",
                                        subtitleChipDescription(video.state.value),
                                    ),
                                )
                            },
                            onEqualizer = {
                                video.cycleEqualizer()
                                notify(
                                    PlayerNotice(
                                        Icons.Outlined.GraphicEq,
                                        "Equalizer",
                                        video.state.value.equalizer.label,
                                    ),
                                )
                            },
                            onAudioEffect = {
                                video.cycleAudioEffect()
                                notify(
                                    PlayerNotice(
                                        Icons.Outlined.SurroundSound,
                                        "Efek audio",
                                        video.state.value.audioEffect.label,
                                    ),
                                )
                            },
                            onEnhance = {
                                video.cycleEnhance()
                                notify(
                                    PlayerNotice(
                                        Icons.Outlined.AutoAwesome,
                                        "Smart Enhance",
                                        video.state.value.enhance.label,
                                    ),
                                )
                            },
                            onDecoder = {
                                video.cycleDecoderMode()
                                notify(
                                    PlayerNotice(
                                        Icons.Outlined.Memory,
                                        "Dekoder video",
                                        video.state.value.decoderMode.label,
                                    ),
                                )
                            },
                            onScreenshot = ::takeScreenshot,
                            onDisplay = {
                                video.cycleAspect()
                                val current = video.state.value
                                notify(
                                    PlayerNotice(
                                        Icons.Outlined.AspectRatio,
                                        "Tampilan video",
                                        id.local.tubeflow.core.VideoPlayback.aspectDetail(
                                            current.aspect,
                                            current.videoWidth,
                                            current.videoHeight,
                                            current.pixelRatio,
                                        ),
                                    ),
                                )
                            },
                        )
                    }
                    if (!state.playing && !bufferingVisible && state.error == null) {
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
                        bufferingVisible = bufferingVisible,
                        scrub = scrub,
                        onScrub = { scrub = it; touch() },
                        onScrubFinished = {
                            scrub?.let { video.seekTo(it.toLong()) }
                            scrub = null
                            touch()
                        },
                        onToggle = { video.togglePlay(); touch() },
                        onPrevious = { video.previous(); touch() },
                        onNext = { video.next(); touch() },
                        supportsPip = supportsPip,
                        orientation = orientation,
                        onEnterPip = ::enterPip,
                        onRotate = {
                            val next = orientation.next()
                            orientation = next
                            notify(PlayerNotice(next.icon, "Orientasi", next.label))
                        },
                        onLock = { controlsLocked = true },
                        isLandscape = landscape,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            feedback?.let { value ->
                GestureOverlay(value, Modifier.align(Alignment.Center))
            }

            notice?.let { value ->
                PlayerNoticeOverlay(
                    value,
                    Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        .padding(top = 130.dp, start = 16.dp, end = 16.dp),
                )
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
                    shape = MaterialTheme.shapes.extraLarge,
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
                    showAutomaticFallback = state.decoderMode == VideoDecoderMode.SOFTWARE,
                    onRetry = video::retry,
                    onUseSoftware = video::useSoftwareDecoder,
                    onUseAutomatic = video::useAutomaticDecoder,
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
            if (sheet == VideoSheet.QUEUE) {
                VideoQueuePanel(
                    state = state,
                    onJump = {
                        video.jumpTo(it)
                        sheet = null
                    },
                    onDismiss = { sheet = null },
                )
            } else sheet?.let { kind ->
                VideoOptionsSheet(
                    kind = kind,
                    state = state,
                    video = video,
                    orientation = orientation,
                    supportsPip = supportsPip,
                    brightnessOverride = brightnessOverride,
                    onScreenshot = {
                        takeScreenshot()
                        sheet = null
                    },
                    onPickSubtitle = {
                        pickingSubtitle = true
                        VideoPip.suspendAutoEnter(activity, video.state.value)
                        runCatching {
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
                        }.onFailure {
                            finishSubtitlePick()
                            Toast.makeText(context, "Tidak dapat membuka pemilih subtitle.", Toast.LENGTH_LONG).show()
                        }
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
    quickSettingsVisible: Boolean,
    onToggleQuickSettings: () -> Unit,
    onClose: () -> Unit,
    onQueue: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transformers = LocalTransformersTheme.current
    Row(
        modifier
            .fillMaxWidth()
            // Scrim gelap disediakan oleh kolom pembungkus di layar; bilah ini cukup transparan
            // agar judul tidak dobel-gelap.
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
        if (transformers) {
            TransformerEmblem(Modifier.size(24.dp), tint = PlayerContent)
            Spacer(Modifier.width(6.dp))
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
        IconButton(
            onClick = onToggleQuickSettings,
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (quickSettingsVisible) PlayerAccent() else PlayerContent,
            ),
        ) {
            Icon(
                Icons.Outlined.Tune,
                if (quickSettingsVisible) "Sembunyikan pengaturan cepat" else "Tampilkan pengaturan cepat",
            )
        }
        IconButton(
            onClick = onQueue,
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = PlayerContent),
        ) {
            Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, "Antrean video")
        }
        IconButton(
            onClick = onMore,
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = PlayerContent),
        ) {
            Icon(Icons.Outlined.MoreVert, "Opsi pemutar")
        }
    }
}

/** Dibacakan TalkBack saat fokus berpindah ke bilah progres. */
private fun stateDescriptionFor(positionMs: Long, durationMs: Long): String =
    if (durationMs > 0) {
        "${VideoPlayback.timeLabel(positionMs)} dari ${VideoPlayback.timeLabel(durationMs)}"
    } else {
        "Durasi belum diketahui"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerBottomBar(
    state: VideoPlayerViewModel.State,
    bufferingVisible: Boolean,
    scrub: Float?,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    supportsPip: Boolean,
    orientation: VideoOrientation,
    onEnterPip: () -> Unit,
    onRotate: () -> Unit,
    onLock: () -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val duration = state.durationMs.coerceAtLeast(1)
    val shownPosition = scrub?.toLong() ?: state.positionMs
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.94f))))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            // Bar sistem disembunyikan saat memutar, jadi safeDrawing bawah = 0 dan kontrol sempat
            // menempel ke tepi layar. Sisakan margin tetap yang cukup untuk navigasi gestur maupun
            // tombol sistem tiga tombol (sekitar 48 dp) yang muncul sekilas.
            .padding(
                start = if (isLandscape) 18.dp else 12.dp,
                end = if (isLandscape) 18.dp else 12.dp,
                top = 4.dp,
                bottom = if (isLandscape) 28.dp else 48.dp,
            ),
    ) {
        // Waktu kiri/kanan mengapit track dengan lebar sama supaya progres tetap seimbang.
        Row(
            Modifier.fillMaxWidth().height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                VideoPlayback.timeLabel(shownPosition),
                color = PlayerContent,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                textAlign = TextAlign.Center,
                modifier = Modifier.width(54.dp),
            )
            Scrubber(
                value = (scrub ?: state.positionMs.toFloat()).coerceIn(0f, duration.toFloat()),
                duration = duration.toFloat(),
                buffered = state.bufferedMs.toFloat(),
                onScrub = onScrub,
                onScrubFinished = onScrubFinished,
                contentColor = PlayerContent,
                enabled = state.durationMs > 0,
                trackHeight = 3.dp,
                thumbRadius = 5.dp,
                touchHeight = 34.dp,
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                contentDescription = "Posisi pemutaran video",
                stateDescription = stateDescriptionFor(shownPosition, state.durationMs),
            )
            Text(
                if (state.durationMs > 0) VideoPlayback.timeLabel(state.durationMs) else "--:--",
                color = PlayerMuted,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                textAlign = TextAlign.Center,
                modifier = Modifier.width(54.dp),
            )
        }
        // Navigasi putar di tengah; kunci di kiri dan PiP/orientasi di kanan, seperti pemutar
        // video biasa. Tidak ada chip pengaturan yang menumpuk di baris ini.
        Box(
            Modifier.fillMaxWidth().height(if (isLandscape) 52.dp else 58.dp),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = onLock,
                modifier = Modifier.align(Alignment.CenterStart).size(44.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = PlayerContent),
            ) {
                Icon(Icons.Outlined.Lock, "Kunci kontrol pemutar")
            }
            TransportControls(
                state = state,
                bufferingVisible = bufferingVisible,
                onToggle = onToggle,
                onPrevious = onPrevious,
                onNext = onNext,
                compact = isLandscape,
                modifier = Modifier.align(Alignment.Center),
            )
            Row(
                Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
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
            }
        }
    }
}

/**
 * Panel pengaturan cepat berbentuk ikon. Nilai/status aktif ditandai warna aksen; setelah
 * diketuk muncul ringkasan singkat di [PlayerNoticeOverlay]. Panel bisa disembunyikan dari
 * bilah atas dan membungkus ikon ke beberapa baris agar semua aksi tetap terjangkau.
 */
@Composable
private fun QuickSettingsBar(
    state: VideoPlayerViewModel.State,
    visible: Boolean,
    onSpeed: () -> Unit,
    onSubtitles: () -> Unit,
    onEqualizer: () -> Unit,
    onAudioEffect: () -> Unit,
    onEnhance: () -> Unit,
    onDecoder: () -> Unit,
    onScreenshot: () -> Unit,
    onDisplay: () -> Unit,
) {
    val subtitlesActive = state.textTracks.isNotEmpty() && !state.subtitlesDisabled
    AnimatedVisibility(visible = visible) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // HP: empat ikon per baris (dua baris). Layar lebar: delapan ikon satu baris.
            val columns = if (maxWidth >= 420.dp) 8 else 4
            val itemWidth = if (columns == 8) 48.dp else 56.dp
            FlowRow(
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .padding(PaddingValues(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 4.dp)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                maxItemsInEachRow = columns,
            ) {
                QuickIcon(
                    icon = Icons.Outlined.Speed,
                    description = "Kecepatan ${VideoPlayback.speedLabel(state.speed)}. Ketuk untuk berikutnya",
                    active = abs(state.speed - 1f) > 0.001f,
                    onClick = onSpeed,
                    modifier = Modifier.width(itemWidth),
                )
                QuickIcon(
                    icon = Icons.Outlined.ClosedCaption,
                    description = "Subtitle ${subtitleChipDescription(state)}. Ketuk untuk berikutnya",
                    active = subtitlesActive,
                    onClick = onSubtitles,
                    modifier = Modifier.width(itemWidth),
                )
                QuickIcon(
                    icon = Icons.Outlined.GraphicEq,
                    description = "Equalizer ${state.equalizer.label}. Ketuk untuk berikutnya",
                    active = state.equalizer != VideoEqualizer.OFF,
                    onClick = onEqualizer,
                    modifier = Modifier.width(itemWidth),
                )
                QuickIcon(
                    icon = Icons.Outlined.SurroundSound,
                    description = "Efek audio ${state.audioEffect.label}. Ketuk untuk berikutnya",
                    active = state.audioEffect != VideoAudioEffect.OFF,
                    onClick = onAudioEffect,
                    modifier = Modifier.width(itemWidth),
                )
                QuickIcon(
                    icon = Icons.Outlined.AutoAwesome,
                    description = if (id.local.tubeflow.core.PlaybackPower.saving) {
                        androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.power_saver_effects)
                    } else {
                        "Smart Enhance ${state.enhance.label}. Ketuk untuk berikutnya"
                    },
                    active = !id.local.tubeflow.core.PlaybackPower.saving && state.enhance != VideoEnhance.OFF,
                    onClick = onEnhance,
                    modifier = Modifier.width(itemWidth),
                )
                QuickIcon(
                    icon = Icons.Outlined.Memory,
                    description = androidx.compose.ui.res.stringResource(
                        id.local.tubeflow.R.string.video_decoder_quick_description,
                        state.decoderMode.label,
                    ),
                    active = state.decoderMode != VideoDecoderMode.AUTO,
                    onClick = onDecoder,
                    modifier = Modifier.width(itemWidth),
                )
                QuickIcon(
                    icon = Icons.Outlined.PhotoCamera,
                    description = "Ambil screenshot video",
                    active = false,
                    onClick = onScreenshot,
                    modifier = Modifier.width(itemWidth),
                )
                QuickIcon(
                    icon = Icons.Outlined.AspectRatio,
                    description = "Tampilan ${state.aspect.label}. Ketuk untuk ${state.aspect.next().label.lowercase()}",
                    active = state.aspect != VideoAspect.FIT,
                    onClick = onDisplay,
                    modifier = Modifier.width(itemWidth),
                )
            }
        }
    }
}

@Composable
private fun QuickIcon(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = description, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = if (active) accent.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.42f),
            contentColor = if (active) accent else PlayerContent,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp))
            }
        }
    }
}

private fun subtitleChipDescription(state: VideoPlayerViewModel.State): String = when {
    state.subtitlesDisabled -> "nonaktif"
    state.textTracks.isEmpty() -> "belum tersedia"
    else -> state.textTracks.firstOrNull { it.selected }?.label ?: "otomatis"
}

@Composable
private fun PlayerNoticeOverlay(notice: PlayerNotice, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = MaterialTheme.shapes.medium,
        color = Color.Black.copy(alpha = 0.78f),
        contentColor = PlayerContent,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(notice.icon, null, Modifier.size(22.dp), tint = PlayerAccent())
            Column {
                Text(notice.title, color = PlayerMuted, style = MaterialTheme.typography.labelSmall)
                Text(notice.value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TransportControls(
    state: VideoPlayerViewModel.State,
    bufferingVisible: Boolean,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val buttonSize = if (compact) 40.dp else 44.dp
    val playSize = if (compact) 50.dp else 56.dp
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
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
        FilledIconButton(
            onClick = onToggle,
            modifier = Modifier.size(playSize),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = PlayerContent,
                contentColor = PlayerBackground,
            ),
        ) {
            if (bufferingVisible) {
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
        is GestureFeedback.Brightness -> androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.video_brightness_percent, feedback.percent)
    }
    Surface(
        modifier,
        shape = MaterialTheme.shapes.medium,
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
    showAutomaticFallback: Boolean,
    onRetry: () -> Unit,
    onUseSoftware: () -> Unit,
    onUseAutomatic: () -> Unit,
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
            shape = MaterialTheme.shapes.large,
            color = PlayerSheet(),
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
                if (showAutomaticFallback) {
                    TextButton(onClick = onUseAutomatic) {
                        Text("Coba dekoder otomatis", color = PlayerContent)
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
    brightnessOverride: Float,
    onPickSubtitle: () -> Unit,
    onScreenshot: () -> Unit,
    onOpen: (VideoSheet) -> Unit,
    onOrientation: (VideoOrientation) -> Unit,
    onEnterPip: () -> Unit,
    onLock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val host = LocalActivity.current
    val transformers = LocalTransformersTheme.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = if (transformers) CutCornerShape(topStart = 28.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 0.dp) else BottomSheetDefaults.ExpandedShape,
        containerColor = PlayerSheet(),
        contentColor = PlayerContent,
        dragHandle = {
            if (transformers) {
                Box(
                    Modifier.padding(top = 10.dp, bottom = 2.dp).width(48.dp).height(4.dp).background(
                        Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary)),
                        CutCornerShape(topStart = 2.dp, bottomEnd = 2.dp),
                    ),
                )
            } else {
                BottomSheetDefaults.DragHandle(color = PlayerContent.copy(alpha = 0.4f))
            }
        },
    ) {
        VideoDialogBrightness((LocalView.current.parent as? DialogWindowProvider)?.window, brightnessOverride)
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
                        // Bilah atas sudah menyebut posisi; lembar ini mengulanginya agar pengguna
                        // tidak perlu menutup sheet untuk tahu sedang di video ke berapa.
                        VideoSheet.QUEUE -> if (state.entries.size > 1) {
                            "Antrean video · ${state.index + 1} dari ${state.entries.size}"
                        } else {
                            "Antrean video"
                        }
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
                    SheetSection(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.video_brightness_title))
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChoicePill(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.video_brightness_auto), state.brightnessMode == VideoBrightnessMode.AUTO) {
                            video.setBrightnessMode(VideoBrightnessMode.AUTO, currentVideoBrightness(host))
                        }
                        ChoicePill(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.video_brightness_manual), state.brightnessMode == VideoBrightnessMode.MANUAL) {
                            video.setBrightnessMode(VideoBrightnessMode.MANUAL, currentVideoBrightness(host))
                        }
                    }
                    if (state.brightnessMode == VideoBrightnessMode.MANUAL) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${(state.manualBrightness * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                            Scrubber(value = state.manualBrightness * 100, duration = 100f,
                                onScrub = { video.setManualBrightness(it / 100, persist = false) },
                                onScrubFinished = { video.setManualBrightness(video.state.value.manualBrightness) },
                                contentColor = MaterialTheme.colorScheme.primary, touchHeight = 48.dp,
                                contentDescription = androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.video_brightness_title),
                                modifier = Modifier.weight(1f))
                        }
                    }
                    SheetHint(androidx.compose.ui.res.stringResource(if (state.brightnessMode == VideoBrightnessMode.AUTO)
                        id.local.tubeflow.R.string.video_brightness_auto_help else id.local.tubeflow.R.string.video_brightness_manual_help))
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
                    val wideSource = state.videoWidth <= 0 || state.videoHeight <= 0 ||
                        state.videoWidth * (state.pixelRatio.takeIf { it.isFinite() && it > 0f } ?: 1f) >= state.videoHeight
                    SheetHint(
                        when (state.aspect) {
                            VideoAspect.FIT -> "Seluruh gambar terlihat. Bilah hitam hanya muncul bila bentuk video berbeda dari layar."
                            VideoAspect.FILL -> if (wideSource) {
                                "Isi layar memenuhi layar; tepi atas terpotong, tetapi subtitle hardsub di bagian bawah tetap terlihat. Pilih Utuh untuk seluruh gambar."
                            } else {
                                "Isi layar memenuhi layar; tepi kiri dan kanan terpotong. Pilih Utuh untuk melihat seluruh gambar."
                            }
                            VideoAspect.ZOOM -> if (wideSource) {
                                "Zoom memperbesar gambar; tepi atas terpotong lebih banyak, sedangkan subtitle hardsub di bawah tetap terlihat. Pilih Utuh untuk seluruh gambar."
                            } else {
                                "Zoom memperbesar gambar dan memotong tepi kiri-kanan. Pilih Utuh untuk melihat seluruh gambar."
                            }
                        },
                    )
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
                    SheetSection(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.video_volume_boost_title))
                    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VideoVolumePolicy.steps.forEach { db ->
                            ChoicePill(if (db == 0) androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.video_volume_normal)
                                else androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.video_volume_boost_db, db), state.volumeBoostDb == db) {
                                video.setVolumeBoost(db)
                            }
                        }
                    }
                    SheetHint(androidx.compose.ui.res.stringResource(when {
                        state.volumeBoostDb == 0 -> id.local.tubeflow.R.string.video_volume_boost_help
                        state.volumeBoostAvailable == false -> id.local.tubeflow.R.string.video_volume_boost_unavailable
                        state.volumeBoostAvailable == true -> id.local.tubeflow.R.string.video_volume_boost_active
                        else -> id.local.tubeflow.R.string.video_volume_boost_pending
                    }))
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
                    if (id.local.tubeflow.core.PlaybackPower.saving) SheetHint(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.power_saver_effects))
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
                    // Baris memakai QueueRow yang sama dengan antrean audio (sampul, judul,
                    // status lampiran, label "Sedang diputar"); warna tetap palet pemutar.
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        itemsIndexed(
                            state.entries,
                            key = { index, entry -> "${entry.uri}#$index" },
                        ) { index, entry ->
                            QueueRow(
                                coverPath = "",
                                mode = MediaMode.VIDEO,
                                title = entry.displayTitle,
                                current = index == state.index,
                                accent = PlayerAccent(),
                                containerColor = PlayerSheet(),
                                contentColor = PlayerContent,
                                metaColor = PlayerMuted,
                                onClick = {
                                    video.jumpTo(index)
                                    onDismiss()
                                },
                                attachments = {
                                    AttachmentInfo(entry.uri, false, sidecars = entry.subtitles.joinToString("\n") { "sub|${it.uri}" })
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
                    SheetSection("Gambar & suara")
                    SheetChoice("Tampilan · ${state.aspect.label} · ${orientation.label}", false) { onOpen(VideoSheet.DISPLAY) }
                    SheetChoice("Equalizer & efek audio · ${state.equalizer.label} · ${state.audioEffect.label}", false) {
                        onOpen(VideoSheet.EFFECTS)
                    }
                    SheetChoice("Smart Enhance · ${state.enhance.label}", false) { onOpen(VideoSheet.EFFECTS) }
                    SheetChoice("Dekoder · ${state.decoderMode.label}", false) { onOpen(VideoSheet.DECODER) }
                    SheetChoice("Antrean · ${state.index + 1} dari ${state.entries.size}", false) { onOpen(VideoSheet.QUEUE) }
                    SheetSection("Tangkapan & jendela")
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

/**
 * Antrean video sebagai kartu di sisi kanan pemutar, bukan lembar bawah yang menutupi gambar.
 * Baris memakai QueueRow yang sama dengan antrean audio/video agar konsisten.
 */
@Composable
private fun VideoQueuePanel(
    state: VideoPlayerViewModel.State,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(onClick = onDismiss),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.88f)
                .widthIn(max = 400.dp)
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            color = PlayerSheet(),
            contentColor = PlayerContent,
            shape = MaterialTheme.shapes.large,
            shadowElevation = 18.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(vertical = 10.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Antrean video",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${state.index + 1} dari ${state.entries.size} · ketuk untuk memutar",
                            style = MaterialTheme.typography.labelSmall,
                            color = PlayerMuted,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Tutup", color = PlayerContent) }
                }
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    itemsIndexed(
                        state.entries,
                        key = { index, entry -> "${entry.uri}#$index" },
                    ) { index, entry ->
                        QueueRow(
                            coverPath = "",
                            mode = MediaMode.VIDEO,
                            title = entry.displayTitle,
                            current = index == state.index,
                            accent = PlayerAccent(),
                            containerColor = PlayerSheet(),
                            contentColor = PlayerContent,
                            metaColor = PlayerMuted,
                            onClick = { onJump(index) },
                            attachments = {
                                AttachmentInfo(entry.uri, false, sidecars = entry.subtitles.joinToString("\n") { "sub|${it.uri}" })
                            },
                        )
                    }
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
        if (selected) Icon(Icons.Outlined.Check, null, tint = PlayerAccent())
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
