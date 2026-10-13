package id.local.tubeflow

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.mutableStateOf
import id.local.tubeflow.core.DownloadViewModel
import id.local.tubeflow.core.SettingsStore
import id.local.tubeflow.core.PlayerViewModel
import id.local.tubeflow.ui.TubeFlowApp
import id.local.tubeflow.ui.theme.TubeFlowTheme

class MainActivity : ComponentActivity() {
    private lateinit var player: PlayerViewModel
    private val incomingUrl = mutableStateOf("")
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readSharedLink(intent)
        SettingsStore.load(this)
        val model = ViewModelProvider(this)[DownloadViewModel::class.java]
        val library = ViewModelProvider(this)[id.local.tubeflow.core.MediaLibraryViewModel::class.java]
        player = ViewModelProvider(this)[PlayerViewModel::class.java]
        setContent {
            TubeFlowTheme(
                darkTheme = SettingsStore.isDark(isSystemInDarkTheme()),
                dynamicColor = SettingsStore.dynamicColor,
            ) { TubeFlowApp(model, library, player, incomingUrl.value, ::requestDownloadNotifications) }
        }
    }
    override fun onStart() { super.onStart(); if (::player.isInitialized) player.setForeground(true) }
    override fun onStop() { if (::player.isInitialized && !isChangingConfigurations) player.setForeground(false); super.onStop() }
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
        readSharedLink(intent)
    }
    private fun readSharedLink(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        incomingUrl.value = Regex("https?://[^\\s]+").find(text)?.value ?: text.trim()
    }
}
