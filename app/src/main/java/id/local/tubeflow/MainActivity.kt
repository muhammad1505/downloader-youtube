package id.local.tubeflow

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.mutableStateOf
import id.local.tubeflow.core.BstationAuth
import id.local.tubeflow.core.DownloadViewModel
import id.local.tubeflow.core.SettingsStore
import id.local.tubeflow.core.PlayerViewModel
import id.local.tubeflow.core.VideoPip
import id.local.tubeflow.core.VideoPlayerViewModel
import id.local.tubeflow.core.AppDestination
import id.local.tubeflow.core.AppNavigationRequest
import id.local.tubeflow.core.IncomingMedia
import id.local.tubeflow.core.MediaIntent
import id.local.tubeflow.ui.TubeFlowApp
import id.local.tubeflow.ui.theme.TubeFlowTheme

class MainActivity : ComponentActivity() {
    private lateinit var player: PlayerViewModel
    private lateinit var video: VideoPlayerViewModel
    private val incomingUrl = mutableStateOf("")
    private val incomingMedia = mutableStateOf<IncomingMedia?>(null)
    private val navigation = mutableStateOf<AppNavigationRequest?>(null)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readLaunchIntent(intent)
        else AppDestination.fromAction(savedInstanceState.getString("pending_destination"))?.let {
            navigation.value = AppNavigationRequest(it)
        }
        SettingsStore.load(this)
        BstationAuth.load(this)
        val model = ViewModelProvider(this)[DownloadViewModel::class.java]
        val library = ViewModelProvider(this)[id.local.tubeflow.core.MediaLibraryViewModel::class.java]
        player = PlayerViewModel.obtain(application)
        video = ViewModelProvider(this)[VideoPlayerViewModel::class.java]
        val karaoke = ViewModelProvider(this)[id.local.tubeflow.core.KaraokeViewModel::class.java]
        setContent {
            TubeFlowTheme(
                darkTheme = SettingsStore.isDark(isSystemInDarkTheme()),
                dynamicColor = SettingsStore.dynamicColor,
                cyberpunk = SettingsStore.themeMode == SettingsStore.MODE_CYBERPUNK,
            ) {
                TubeFlowApp(model, library, player, karaoke, video, incomingUrl.value, ::requestDownloadNotifications,
                    navigation = navigation.value,
                    incomingMedia = incomingMedia.value,
                    onMediaHandled = { incomingMedia.value = null },
                    onNavigationHandled = { id -> if (navigation.value?.id == id) navigation.value = null })
            }
        }
    }
    private fun requestDownloadNotifications() {
        val preferences = getSharedPreferences("permissions", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            !preferences.getBoolean("notifications_asked", false)) {
            preferences.edit { putBoolean("notifications_asked", true) }
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readLaunchIntent(intent)
    }
    /** Android 10–11 belum punya setAutoEnterEnabled; Home saat video diputar masuk PiP dari sini. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (::video.isInitialized) VideoPip.enterOnLeave(this, video.state.value)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        navigation.value?.let { outState.putString("pending_destination", it.destination.action) }
        super.onSaveInstanceState(outState)
    }
    private fun readLaunchIntent(intent: Intent?) {
        val destination = AppDestination.fromAction(intent?.action)
        if (destination != null) {
            navigation.value = AppNavigationRequest(destination)
            return
        }
        if (readMediaIntent(intent)) return
        readSharedLink(intent)
    }
    /**
     * "Buka dengan" dari aplikasi lain mengirim ACTION_VIEW berisi content:// atau file://.
     * Berkas audio memakai pemutar lagu yang sudah ada, video memakai pemutar Media3.
     */
    private fun readMediaIntent(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_VIEW) return false
        val data = intent.data ?: return false
        val mime = intent.type ?: runCatching { contentResolver.getType(data) }.getOrNull()
        val media = MediaIntent.from(data.toString(), mime, displayName(data)) ?: return false
        incomingMedia.value = media
        return true
    }
    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    private fun readSharedLink(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        navigation.value = AppNavigationRequest(AppDestination.DOWNLOAD)
        incomingUrl.value = Regex("https?://[^\\s]+").find(text)?.value ?: text.trim()
    }
}
