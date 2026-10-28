package id.local.tubeflow.core

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational

/**
 * Satu tempat untuk parameter PiP video: rasio aman, source rect, aksi play/pause, dan
 * auto-enter. Dipakai layar pemutar dan MainActivity (onUserLeaveHint untuk Android 10–11),
 * jadi tombol serta perilaku Home tidak berbeda antar-versi.
 */
object VideoPip {
    const val ACTION_TOGGLE = "id.local.tubeflow.action.VIDEO_PIP_TOGGLE"
    private const val REQUEST_TOGGLE = 4101

    fun supports(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun enter(activity: Activity, state: VideoPlayerViewModel.State): Boolean {
        if (!supports(activity)) return false
        return runCatching {
            activity.enterPictureInPictureMode(params(activity, state, autoEnter = false))
        }.getOrDefault(false)
    }

    /** Android 12+ memakai setAutoEnterEnabled; Android 10–11 masuk PiP saat pengguna menekan Home. */
    fun enterOnLeave(activity: Activity, state: VideoPlayerViewModel.State) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        if (!VideoPlayback.shouldEnterPipOnLeave(state.active, state.playing, autoEnterSuspended)) return
        enter(activity, state)
    }

    /**
     * Tahan auto-PiP saat layar membuka picker/dokumen lain. `onUserLeaveHint` juga terpanggil
     * untuk ActivityResult, bukan hanya Home; tanpa ini video bisa mengecil ke PiP ketika pengguna
     * baru hendak memilih subtitle.
     */
    fun suspendAutoEnter(activity: Activity?, state: VideoPlayerViewModel.State) {
        autoEnterSuspended = true
        if (activity != null) apply(activity, state, autoEnter = false)
    }

    fun resumeAutoEnter() {
        autoEnterSuspended = false
    }

    fun apply(activity: Activity, state: VideoPlayerViewModel.State, autoEnter: Boolean) {
        if (!supports(activity)) return
        runCatching { activity.setPictureInPictureParams(params(activity, state, autoEnter)) }
    }

    fun disableAutoEnter(activity: Activity?) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            activity.setPictureInPictureParams(
                PictureInPictureParams.Builder().setAutoEnterEnabled(false).build(),
            )
        }
    }

    /** Layar pemutar memasang handler; receiver manifest meneruskannya saat aksi PiP ditekan. */
    fun attachToggle(handler: () -> Unit) {
        toggleHandler = handler
    }

    fun detachToggle() {
        toggleHandler = null
    }

    @Volatile
    private var toggleHandler: (() -> Unit)? = null

    @Volatile
    private var autoEnterSuspended = false

    private fun params(
        activity: Activity,
        state: VideoPlayerViewModel.State,
        autoEnter: Boolean,
    ): PictureInPictureParams {
        val (width, height) = VideoPlayback.pipAspect(state.videoWidth, state.videoHeight)
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(width, height))
        activity.window?.decorView?.let { decor ->
            if (decor.width > 0 && decor.height > 0) {
                builder.setSourceRectHint(Rect(0, 0, decor.width, decor.height))
            }
        }
        builder.setActions(listOf(toggleAction(activity, state.playing)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter && !autoEnterSuspended)
        }
        return builder.build()
    }

    private fun toggleAction(activity: Activity, playing: Boolean): RemoteAction {
        val icon = Icon.createWithResource(
            activity,
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        )
        return RemoteAction(
            icon,
            if (playing) "Jeda" else "Putar",
            if (playing) "Jeda video" else "Putar video",
            toggleIntent(activity),
        )
    }

    private fun toggleIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_TOGGLE,
        Intent(context, VideoPipReceiver::class.java)
            .setAction(ACTION_TOGGLE)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun toggle() {
        toggleHandler?.invoke()
    }
}

/** Receiver eksplisit, tidak diekspor; hanya bisa dipicu PendingIntent milik aplikasi ini. */
class VideoPipReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == VideoPip.ACTION_TOGGLE) VideoPip.toggle()
    }
}
