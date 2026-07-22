package fr.eiter.plexiwine

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Coque native = **strictement la webapp Weeno**.
 * Pas d'UI Compose Beer/fork : même HTML/CSS/JS que https://eiter.freeboxos.fr/wine/
 */
class MainActivity : ComponentActivity() {

    companion object {
        /** URL publique prod (parité web). */
        const val START_URL = "https://eiter.freeboxos.fr/wine/app"
        const val FALLBACK_LAN = "https://192.168.1.50:8444/wine/app"
        /** Theme webapp --bg */
        const val BG = 0xFF120A0E.toInt()
        private const val UA_SUFFIX = " WeenoNativeAndroid/0.3 WebViewShell"
    }

    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraPermission: PermissionRequest? = null

    private val fileChooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val cb = fileCallback
            fileCallback = null
            if (cb == null) return@registerForActivityResult
            val data = result.data
            val uris: Array<Uri>? =
                when {
                    result.resultCode != RESULT_OK -> null
                    data?.clipData != null -> {
                        val n = data.clipData!!.itemCount
                        Array(n) { i -> data.clipData!!.getItemAt(i).uri }
                    }
                    data?.data != null -> arrayOf(data.data!!)
                    else -> WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
                }
            cb.onReceiveValue(uris)
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val req = pendingCameraPermission
            pendingCameraPermission = null
            if (req == null) return@registerForActivityResult
            val ok = grants.values.any { it }
            if (ok) {
                req.grant(req.resources)
            } else {
                req.deny()
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(BG)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = settings.userAgentString + UA_SUFFIX
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            // Cookies session webapp (login + invites)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url?.toString() ?: return false
                    return handleExternalOrKeep(url, view)
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    return handleExternalOrKeep(url, view)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view?.setBackgroundColor(BG)
                }

                override fun onReceivedError(
                    view: WebView,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    // Si l'URL publique casse, tenter le LAN (owner à la maison)
                    if (failingUrl != null && failingUrl.startsWith("https://eiter.freeboxos.fr/wine")
                        && view.url == failingUrl
                    ) {
                        view.loadUrl(FALLBACK_LAN)
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    if (request == null) return
                    val needsCam = request.resources.any {
                        it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                    }
                    if (!needsCam) {
                        request.grant(request.resources)
                        return
                    }
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        request.grant(request.resources)
                    } else {
                        pendingCameraPermission = request
                        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                    }
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = filePathCallback
                    val intent = try {
                        fileChooserParams?.createIntent()
                            ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "image/*"
                            }
                    } catch (_: Exception) {
                        Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                        }
                    }
                    return try {
                        fileChooser.launch(intent)
                        true
                    } catch (_: ActivityNotFoundException) {
                        fileCallback = null
                        false
                    }
                }
            }
        }

        setContentView(webView)

        val start = resolveStartUrl(intent)
        webView.loadUrl(start)
    }

    private fun resolveStartUrl(intent: Intent?): String {
        val data = intent?.data?.toString()
        if (!data.isNullOrBlank() && data.contains("/wine")) {
            return data
        }
        return START_URL
    }

    private fun handleExternalOrKeep(url: String, view: WebView): Boolean {
        // Liens Weeno / même host → WebView
        if (url.contains("eiter.freeboxos.fr") ||
            url.contains("192.168.1.") ||
            url.startsWith("https://eiter.")
        ) {
            return false
        }
        // tel: mailto: etc.
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val data = intent.data?.toString()
        if (!data.isNullOrBlank() && data.contains("/wine") && ::webView.isInitialized) {
            webView.loadUrl(data)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
        if (::webView.isInitialized) webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }
}
