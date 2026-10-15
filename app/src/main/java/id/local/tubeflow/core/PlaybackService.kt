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
import android.media.AudioManager
import android.media.MediaMetadata
import android.view.View
import android.widget.RemoteViews
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import id.local.tubeflow.MainActivity
import id.local.tubeflow.R

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
    private val renew = object : Runnable {
        override fun run() {
            if (playing) { holdWakeLock(); handler.postDelayed(this, RENEW_MS) }
        }
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 42
        private val DEFAULT_ACCENT = 0xFFC6B7FF.toInt()
        private val coverLock = Any()
        private var coverKey = ""
        private var coverBitmap: Bitmap? = null
        // Batas wajib agar OS selalu melepas lock yang tertinggal; diperpanjang selama audio berbunyi.
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L
        private const val RENEW_MS = 5 * 60 * 1000L
        const val ACTION_PLAY_PAUSE = "id.local.tubeflow.action.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "id.local.tubeflow.action.PREVIOUS"
        const val ACTION_NEXT = "id.local.tubeflow.action.NEXT"
        const val ACTION_STOP = "id.local.tubeflow.action.STOP"
        @Volatile private var instance: PlaybackService? = null

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
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { command(ACTION_PLAY_PAUSE, wantPlaying = true) }
                override fun onPause() { command(ACTION_PLAY_PAUSE, wantPlaying = false) }
                override fun onSkipToNext() { command(ACTION_NEXT) }
                override fun onSkipToPrevious() { command(ACTION_PREVIOUS) }
                override fun onStop() { command(ACTION_STOP) }
                override fun onSeekTo(pos: Long) { PlayerViewModel.active?.seek(pos) }
            })
            isActive = true
        }
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TubeFlow:playback").apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != null) handle(intent.action!!)
        val state = PlayerViewModel.active?.state?.value
        if (state == null || state.track == null) { stopSelf(); return START_NOT_STICKY }
        startForeground(NOTIFICATION_ID, build(state))
        renderLock(state)
        return START_NOT_STICKY
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
        session?.setMetadata(meta.build())
        session?.setPlaybackState(PlaybackState.Builder()
            .setState(if (state.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, state.positionMs, if (state.playing) state.speed else 0f)
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP)
            .build())
    }

    private fun build(state: PlayerViewModel.State): Notification {
        val track = state.track
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val cover = track?.cover.orEmpty()
        val views = playerViews(state)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_playback)
            .setContentTitle(track?.title ?: "TubeFlow")
            .setContentText(track?.artist?.ifBlank { null } ?: "Artis belum diketahui")
            .setContentIntent(open)
            .setOngoing(state.playing)
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setColor(runCatching { id.local.tubeflow.ui.CoverColors.dominant(cover) }.getOrNull() ?: DEFAULT_ACCENT)
            // Versi ringkas: cukup sebelumnya/putar-berikutnya. Tombol tutup (X) menyatu di kartu,
            // jadi tidak ada aksi tersembunyi yang perlu dibuka lewat detail notifikasi.
            .setStyle(Notification.DecoratedMediaCustomViewStyle()
                .setMediaSession(session?.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .addAction(action(ACTION_PREVIOUS, android.R.drawable.ic_media_previous, "Sebelumnya"))
            .addAction(action(ACTION_PLAY_PAUSE, if (state.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (state.playing) "Jeda" else "Putar"))
            .addAction(action(ACTION_NEXT, android.R.drawable.ic_media_next, "Berikutnya"))
            .build()
    }

    /**
     * Isi notifikasi dibuat sendiri supaya tingginya ramping tetapi sampul tetap terlihat:
     * sampul 40dp, judul/artis satu baris, dan garis progres 2dp — sama seperti mini player di
     * aplikasi. Durasi tak diketahui memakai indikator tak tentu, bukan garis penuh (pesan tetap
     * jujur: notifikasi tidak berpura-pura tahu progres).
     */
    private fun playerViews(state: PlayerViewModel.State): RemoteViews {
        val track = state.track
        return RemoteViews(packageName, R.layout.notification_playback).apply {
            setTextViewText(R.id.playback_title, track?.title ?: "TubeFlow")
            setTextViewText(R.id.playback_artist, track?.artist?.ifBlank { null } ?: "Artis belum diketahui")
            val cover = coverIcon(track?.cover.orEmpty())
            if (cover == null) setViewVisibility(R.id.playback_cover, View.GONE)
            else {
                setViewVisibility(R.id.playback_cover, View.VISIBLE)
                setImageViewBitmap(R.id.playback_cover, cover)
            }
            if (state.durationMs > 0) {
                setProgressBar(R.id.playback_progress, 100, (100.0 * state.positionMs / state.durationMs).toInt().coerceIn(0, 100), false)
            } else setProgressBar(R.id.playback_progress, 100, 0, true)
            setOnClickPendingIntent(R.id.playback_close, actionIntent(ACTION_STOP))
        }
    }

    /** Sampul kecil (≤96 px) untuk tata letak ramping; di-cache per track agar render berulang murah. */
    private fun coverIcon(path: String): Bitmap? {
        if (path.isBlank()) return null
        synchronized(coverLock) {
            if (coverKey == path) return coverBitmap
            val bitmap = runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0) return null
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 96) sample *= 2
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
        Notification.Action.Builder(icon, title, actionIntent(action)).build()

    override fun onDestroy() {
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

    override fun onBind(intent: Intent?): IBinder? = null
}
