package id.local.tubeflow.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import id.local.tubeflow.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Foreground service pemutaran: menjaga audio tetap berjalan saat layar mati/aplikasi ke latar,
 * menampilkan notifikasi MediaStyle dengan kontrol play/pause/prev/next, dan menerima tombol media
 * headset/Bluetooth lewat MediaSession. Engine tetap satu ([PlayerViewModel]); service ini hanya
 * wadah notifikasi, sesi media, dan partial wake lock.
 */
class PlaybackService : Service() {
    private var session: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var playing = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var autoStart: Job? = null
    private val renew = object : Runnable {
        override fun run() {
            if (playing) { holdWakeLock(); handler.postDelayed(this, RENEW_MS) }
        }
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 42
        private const val AUTOPLAY_NOTICE_ID = 43
        private val DEFAULT_ACCENT = 0xFFC6B7FF.toInt()
        private val coverLock = Any()
        private var coverKey = ""
        private var coverBitmap: Bitmap? = null
        // Batas wajib agar OS selalu melepas lock yang tertinggal; diperpanjang selama audio berbunyi.
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L
        private const val RENEW_MS = 5 * 60 * 1000L
        /** Kabel mengirim HEADSET_PLUG dan AudioDeviceCallback sekaligus; satu permintaan cukup. */
        private const val AUTOPLAY_DEDUPE_MS = 1_000L
        const val ACTION_PLAY_PAUSE = "id.local.tubeflow.action.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "id.local.tubeflow.action.PREVIOUS"
        const val ACTION_NEXT = "id.local.tubeflow.action.NEXT"
        const val ACTION_STOP = "id.local.tubeflow.action.STOP"
        const val ACTION_AUTO_PLAY = "id.local.tubeflow.action.AUTO_PLAY_CONNECTED"
        @Volatile private var instance: PlaybackService? = null
        private var lastAutoPlayAt = 0L

        /** Transfer ownership to a service before receiver return / asynchronous catalogue I/O. */
        fun requestAutoPlay(context: Context) {
            val now = SystemClock.elapsedRealtime()
            synchronized(this) {
                if (now - lastAutoPlayAt < AUTOPLAY_DEDUPE_MS) return
                lastAutoPlayAt = now
            }
            try {
                ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java).setAction(ACTION_AUTO_PLAY))
            } catch (error: RuntimeException) {
                android.util.Log.w("TubeFlow-autoload", "foreground start denied: ${error.javaClass.simpleName}")
                showAutoPlayRetry(context)
            }
        }

        private fun showAutoPlayRetry(context: Context) {
            runCatching {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Pemutaran", NotificationManager.IMPORTANCE_LOW))
                val retry = PendingIntent.getForegroundService(context, AUTOPLAY_NOTICE_ID,
                    Intent(context, PlaybackService::class.java).setAction(ACTION_AUTO_PLAY), PendingIntent.FLAG_IMMUTABLE)
                manager.notify(AUTOPLAY_NOTICE_ID, Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_playback)
                    .setContentTitle(context.getString(R.string.player_autoplay_retry))
                    .setContentIntent(AppIntents.pending(context, AppDestination.PLAYER))
                    .addAction(Notification.Action.Builder(
                        Icon.createWithResource(context, android.R.drawable.ic_media_play),
                        context.getString(R.string.notification_retry_play),
                        retry,
                    ).build())
                    .setAutoCancel(true).build())
            }
        }

        /** Sinkronkan service dengan state engine: tampilkan saat ada track, hentikan saat kosong. */
        fun sync(context: Context, state: PlayerViewModel.State) {
            val service = instance
            if (state.track == null) {
                context.stopService(Intent(context, PlaybackService::class.java))
                return
            }
            if (service != null) service.render(state)
            else runCatching { ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java)) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Pemutaran", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Kontrol pemutaran musik"
            })
        session = MediaSession(this, "TubeFlow").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { command(ACTION_PLAY_PAUSE, wantPlaying = true) }
                override fun onPause() { command(ACTION_PLAY_PAUSE, wantPlaying = false) }
                override fun onSkipToNext() { command(ACTION_NEXT) }
                override fun onSkipToPrevious() { command(ACTION_PREVIOUS) }
                override fun onStop() { command(ACTION_STOP) }
                override fun onSeekTo(pos: Long) { PlayerViewModel.active?.seek(pos) }
            })
            setSessionActivity(AppIntents.pending(this@PlaybackService, AppDestination.PLAYER))
            isActive = true
        }
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TubeFlow:playback").apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val restoring = intent == null && PlayerViewModel.active?.state?.value?.track == null &&
            PlayerViewModel.hasRestorableSession(this)
        if (intent?.action == ACTION_AUTO_PLAY || restoring) {
            getSystemService(NotificationManager::class.java).cancel(AUTOPLAY_NOTICE_ID)
            AppLog.info(LogCategory.PLAYER, if (restoring) "Layanan memulihkan sesi terakhir."
                else "Permintaan auto-play diterima layanan.")
            val state = PlayerViewModel.active?.state?.value
            // Foreground first: no Room query or audio-focus request may precede this point.
            startForeground(NOTIFICATION_ID, if (state?.track != null) build(state) else
                Notification.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_playback)
                    .setContentTitle(getString(R.string.player_autoplay_prepare))
                    .setContentIntent(AppIntents.pending(this, AppDestination.PLAYER)).setOngoing(true).build())
            if (autoStart?.isActive == true) {
                AppLog.info(LogCategory.PLAYER, "Auto-play diabaikan: permintaan sebelumnya masih berjalan.")
                return START_STICKY
            }
            autoStart = scope.launch {
                try {
                    withTimeout(10000) {
                        val player = PlayerViewModel.obtain(application)
                        if (restoring) player.restorePlaybackSession() else player.resumeForConnectedDevice()
                    }
                } catch (_: TimeoutCancellationException) {
                    showAutoPlayRetry(this@PlaybackService)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    android.util.Log.w("TubeFlow-autoload", "restore failed: ${error.javaClass.simpleName}")
                    showAutoPlayRetry(this@PlaybackService)
                } finally {
                    // Jangan menutup foreground saat track masih dimuat: notifikasi dan wake lock
                    // akan hilang meski musik langsung berbunyi setelah siap.
                    val latest = PlayerViewModel.active?.state?.value
                    if (latest?.track == null && latest?.loading != true) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP) autoStart?.cancel()
        if (intent?.action != null) handle(intent.action!!)
        val state = PlayerViewModel.active?.state?.value
        if (state == null || state.track == null) { stopSelf(); return START_NOT_STICKY }
        startForeground(NOTIFICATION_ID, build(state))
        renderLock(state)
        return START_STICKY
    }

    private fun handle(action: String) {
        val player = PlayerViewModel.active ?: return
        when (action) {
            ACTION_PLAY_PAUSE -> player.toggle()
            ACTION_PREVIOUS -> player.previous()
            ACTION_NEXT -> player.next()
            ACTION_STOP -> player.stop()
        }
    }

    private fun command(action: String, wantPlaying: Boolean? = null) {
        val player = PlayerViewModel.active ?: return
        // An explicit Pause while already interrupted must revoke pending automatic resume too.
        if (wantPlaying == false) { player.pause(); return }
        if (wantPlaying != null && player.state.value.playing == wantPlaying) return
        handle(action)
    }

    private fun render(state: PlayerViewModel.State) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(state))
        renderLock(state)
    }

    private fun renderLock(state: PlayerViewModel.State) {
        playing = state.playing
        handler.removeCallbacks(renew)
        if (state.playing) { holdWakeLock(); handler.postDelayed(renew, RENEW_MS) } else releaseWakeLock()
        // Metadata durasi wajib: tanpa DURATION, progressbar notifikasi/layar kunci dianggap
        // penuh ("mentok di akhir") meski lagu baru mulai. Posisi ikut dikirim ulang tiap render
        // sehingga lagu baru selalu mulai dari 0 di UI sistem.
        val track = state.track
        val meta = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, track?.title ?: "TubeFlow")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, track?.artist?.ifBlank { null } ?: "Artis belum diketahui")
        if (state.durationMs > 0) meta.putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs)
        coverIcon(track?.cover.orEmpty())?.let { meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
        session?.setMetadata(meta.build())
        session?.setPlaybackState(PlaybackState.Builder()
            .setState(if (state.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, state.positionMs, if (state.playing) state.speed else 0f)
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP)
            .build())
    }

    private fun build(state: PlayerViewModel.State): Notification {
        val track = state.track
        val open = AppIntents.pending(this, AppDestination.PLAYER)
        val cover = track?.cover.orEmpty()
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_playback)
            .setContentTitle(track?.title ?: "TubeFlow")
            .setContentText(track?.artist?.ifBlank { null } ?: "Artis belum diketahui")
            .setContentIntent(open)
            .setOngoing(state.playing)
            .setLargeIcon(coverIcon(cover))
            .setDeleteIntent(actionIntent(ACTION_STOP))
            .setColor(runCatching { id.local.tubeflow.ui.CoverColors.dominant(cover) }.getOrNull() ?: DEFAULT_ACCENT)
            .setStyle(Notification.MediaStyle()
                .setMediaSession(session?.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .addAction(action(ACTION_PREVIOUS, android.R.drawable.ic_media_previous, "Sebelumnya"))
            .addAction(action(ACTION_PLAY_PAUSE, if (state.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (state.playing) "Jeda" else "Putar"))
            .addAction(action(ACTION_NEXT, android.R.drawable.ic_media_next, "Berikutnya"))
            .addAction(action(ACTION_STOP, R.drawable.ic_notification_close, getString(R.string.player_stop)))
            .build()
    }

    /** Bounded artwork is shared by the native notification and MediaSession. */
    private fun coverIcon(path: String): Bitmap? {
        if (path.isBlank()) return null
        synchronized(coverLock) {
            if (coverKey == path) return coverBitmap
            val bitmap = runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0) return null
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
            coverKey = path; coverBitmap = bitmap
            return bitmap
        }
    }

    /** Wake lock partial hanya dipegang selama audio benar-benar berbunyi; dilepas saat jeda/stop/destroy. */
    private fun holdWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) lock.release()
        lock.acquire(WAKELOCK_TIMEOUT_MS)
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) lock.release()
    }

    private fun actionIntent(action: String): PendingIntent =
        PendingIntent.getService(this, action.hashCode(), Intent(this, PlaybackService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)

    private fun action(action: String, icon: Int, title: String): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(this, icon), title, actionIntent(action)).build()

    override fun onDestroy() {
        scope.cancel()
        instance = null
        playing = false
        handler.removeCallbacks(renew)
        session?.isActive = false
        session?.release()
        session = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        android.util.Log.i("TubeFlow-autoload", "task removed; active media session retained")
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
