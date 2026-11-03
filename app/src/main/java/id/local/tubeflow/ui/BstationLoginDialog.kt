package id.local.tubeflow.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.local.tubeflow.core.BstationAuth
import id.local.tubeflow.core.YouTubeAuth
import kotlinx.coroutines.delay

private const val BSTATION_HOME = "https://www.bilibili.tv/id"

/** Provider yang memakai dialog WebView resmi yang sama; cookie tetap disimpan terpisah. */
enum class LoginProvider { BSTATION, YOUTUBE }

/**
 * Perbaikan tampilan halaman login Bstation di WebView.
 *
 * Halaman Bstation memakai `max-height: 90vh` untuk kotak dialog login. Di WebView perangkat
 * uji, `vh` diukur 0 px walau `window.innerHeight` 715 px, sehingga kotak itu runtuh setinggi
 * nol: judul, syarat, dan checkbox terlihat menempel di atas halaman tanpa latar putih, dan
 * tombol Facebook/Google/Twitter/Email tidak tampak. Gaya di bawah mengganti batas tinggi
 * berbasis `vh` dengan piksel dari `innerHeight` yang benar.
 *
 * Interstitial "Silakan unduh aplikasi untuk konten lengkap" juga menutupi dialog login saat
 * dibuka dari WebView. Tombol batalnya ditekan otomatis supaya pengguna bisa memilih metode
 * login; skrip ini tidak menyentuh kredensial dan hanya berjalan di WebView login ini.
 */
private val LOGIN_LAYOUT_FIX_JS = """
    (function () {
      if (window.__tfLoginFix) return;
      window.__tfLoginFix = true;
      function applySize() {
        var viewport = window.innerHeight || document.documentElement.clientHeight || 0;
        var maxHeight = Math.max(320, Math.round(viewport * 0.86));
        var style = document.getElementById('tf-login-fix');
        if (!style) {
          style = document.createElement('style');
          style.id = 'tf-login-fix';
          document.head.appendChild(style);
        }
        style.textContent = '.dialog--mobile .dialog__container{max-height:' + maxHeight +
          'px !important;height:auto !important;}';
      }
      function closeStoreDialog() {
        var dialogs = document.querySelectorAll('[class*=dialog__wrap]');
        for (var i = 0; i < dialogs.length; i++) {
          var dialog = dialogs[i];
          if ((dialog.innerText || '').indexOf('Silakan unduh aplikasi') !== 0) continue;
          var cancel = dialog.querySelector('[class*=dialog__cancel]');
          if (cancel) cancel.click(); else dialog.remove();
        }
      }
      applySize();
      window.addEventListener('resize', applySize);
      closeStoreDialog();
      if (document.body) {
        new MutationObserver(closeStoreDialog).observe(document.body, {childList: true, subtree: true});
      }
    })();
""".trimIndent()

/**
 * Login resmi Bstation di WebView. Cookie HttpOnly ikut terbaca lewat CookieManager, lalu
 * disimpan terenkripsi oleh [BstationAuth]; halaman tidak pernah menyentuh kata sandi pengguna.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BstationLoginDialog(
    onDismiss: () -> Unit,
    onSignedIn: () -> Unit,
    provider: LoginProvider = LoginProvider.BSTATION,
) {
    val context = LocalContext.current
    val bstation = provider == LoginProvider.BSTATION
    val home = if (bstation) BSTATION_HOME else "https://m.youtube.com"
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var finishing by remember { mutableStateOf(false) }

    fun capture(): Boolean {
        if (finishing) return true
        return runCatching {
            if (bstation) BstationAuth.saveFromWebView(context) else YouTubeAuth.saveFromWebView(context)
        }
            .getOrElse { error ->
                saveError = error.message ?: "Sesi ${if (bstation) "Bstation" else "YouTube"} tidak dapat disimpan."
                false
            }
            .also { saved ->
                if (saved) {
                    finishing = true
                    onSignedIn()
                }
            }
    }

    // Login bisa selesai tanpa perpindahan halaman (modal/QR), jadi cookie diperiksa berkala.
    LaunchedEffect(Unit) {
        while (true) {
            delay(700)
            if (finishing || saveError != null) break
            if (capture()) break
        }
    }

    BackHandler(enabled = true) {
        if (webView?.canGoBack() == true) webView?.goBack() else onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, "Tutup login ${if (bstation) "Bstation" else "YouTube"}")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (bstation) "Masuk Bstation" else "Masuk YouTube", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (bstation) "Episode Premium · halaman resmi bilibili.tv"
                            else "Video terbatas · halaman resmi YouTube",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            if (bstation) "Ketuk Masuk/Log in di halaman Bstation. Setelah berhasil, jendela ini tertutup otomatis. Kata sandi tidak pernah disimpan TubeFlow."
                            else "Ketuk Masuk di halaman YouTube/Google. Setelah berhasil, jendela ini tertutup otomatis. Kata sandi tidak pernah disimpan TubeFlow.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                HorizontalDivider()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { viewContext ->
                            WebView(viewContext).apply {
                                setBackgroundColor(Color.WHITE)
                                settings.apply {
                                    // Benar-benar menghormati <meta viewport>: tanpa ini halaman
                                    // dilayout pada lebar fisik dengan rasio 1:1 sehingga teks dan
                                    // dialog login tampil terlalu kecil.
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    loadsImagesAutomatically = true
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    javaScriptCanOpenWindowsAutomatically = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    mediaPlaybackRequiresUserGesture = true
                                    setSupportMultipleWindows(false)
                                }
                                val browser = this
                                CookieManager.getInstance().apply {
                                    setAcceptCookie(true)
                                    setAcceptThirdPartyCookies(browser, true)
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        loading = true
                                        pageError = null
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        loading = false
                                        // Perbaikan tata letak hanya untuk halaman login Bstation.
                                        if (bstation) view?.evaluateJavascript(LOGIN_LAYOUT_FIX_JS, null)
                                    }

                                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                        if (request?.isForMainFrame == true) {
                                            loading = false
                                            pageError = "Halaman ${if (bstation) "Bstation" else "YouTube"} gagal dimuat. Periksa koneksi lalu coba lagi."
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val scheme = request?.url?.scheme?.lowercase()
                                        // Hanya HTTPS di WebView; skema aplikasi lain diabaikan.
                                        return scheme != "https"
                                    }
                                }
                                webView = this
                                loadUrl(home)
                            }
                        },
                        onRelease = { view ->
                            view.stopLoading()
                            view.destroy()
                            if (webView === view) webView = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (pageError != null) {
                        Column(
                            Modifier.fillMaxWidth().align(Alignment.TopCenter)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Text(
                                        pageError.orEmpty(),
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    TextButton(onClick = { webView?.reload() }) { Text("Muat ulang") }
                                }
                            }
                        }
                    }
                }
                if (saveError != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Text(
                            saveError.orEmpty(),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = {
                            saveError = null
                            capture()
                        }) { Text("Coba lagi") }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
