package com.urlapk.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urlapk.app.util.Constants
import com.urlapk.app.util.UrlValidator
import com.urlapk.app.webview.UrlWebChromeClient
import com.urlapk.app.webview.UrlWebViewClient
import com.urlapk.app.webview.WebViewDownloader
import com.urlapk.app.webview.WebViewManager
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // WebView + downloader held as state so they survive recomposition
    var webView by remember { mutableStateOf<WebView?>(null) }
    val downloader = remember { WebViewDownloader(context) { msg ->
        scope.launch { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    } }

    // Fullscreen video container (WebView custom view)
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // File chooser state
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cb = filePathCallback ?: return@rememberLauncherForActivityResult
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        cb.onReceiveValue(uris)
        filePathCallback = null
    }

    // Runtime permissions (camera, mic) for WebRTC
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        Toast.makeText(
            context,
            if (allGranted) "Permissions granted. Reload page." else "Permission denied",
            Toast.LENGTH_SHORT
        ).show()
    }

    // Back press: WebView history first
    BackHandler(enabled = state.canGoBack) {
        webView?.goBack()
    }

    // Lifecycle: pause/resume WebView to save battery
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            WebViewManager.destroyWebView(webView, webView?.parent as? ViewGroup)
            webView = null
        }
    }

    // Load default URL once WebView is ready
    LaunchedEffect(webView) {
        webView?.loadUrl(Constants.DEFAULT_HOME_URL)
    }

    // React to desktop mode / zoom changes
    LaunchedEffect(state.isDesktopMode) {
        webView?.let { WebViewManager.applyDesktopMode(it, state.isDesktopMode) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        UrlBar(
            currentUrl = state.currentUrl,
            pageTitle = state.pageTitle,
            progress = state.progress,
            isLoading = state.isLoading,
            isSecure = state.currentUrl.startsWith("https://"),
            onUrlSubmit = { raw ->
                val normalized = UrlValidator.normalize(raw)
                if (normalized != null) {
                    webView?.loadUrl(normalized)
                    viewModel.onUrlSubmitted()
                } else {
                    Toast.makeText(context, "Invalid URL", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { ctx ->
                WebViewManager.buildConfiguredWebView(ctx).also { wv ->
                    WebViewManager.applyCookiePolicy(wv)
                    wv.webViewClient = UrlWebViewClient(
                        onPageStarted = { viewModel.onPageStarted(it) },
                        onPageFinished = {
                            viewModel.onPageFinished(it, wv.canGoBack(), wv.canGoForward())
                        },
                        onPageError = { _, desc -> viewModel.onError(desc) }
                    )
                    wv.webChromeClient = UrlWebChromeClient(
                        activity = activity ?: return@also,
                        onProgressChanged = { viewModel.onProgress(it) },
                        onTitleReceived = { viewModel.onTitle(it) },
                        onFullscreenViewRequested = { view, cb ->
                            fullscreenView = view
                            fullscreenCallback = cb
                        },
                        launchFileChooser = { cb, params ->
                            filePathCallback = cb
                            val intent = params.createIntent()
                            fileChooserLauncher.launch(intent)
                            true
                        }
                    )
                    downloader.attach(wv)
                    webView = wv
                }
            },
            update = { wv ->
                viewModel.onNavigationStateChanged(wv.canGoBack(), wv.canGoForward())
            }
        )

        BottomBar(
            canGoBack = state.canGoBack,
            canGoForward = state.canGoForward,
            isDesktopMode = state.isDesktopMode,
            onBack = { webView?.goBack() },
            onForward = { webView?.goForward() },
            onReload = { webView?.reload() },
            onZoomIn = {
                viewModel.zoomIn()
                webView?.let { WebViewManager.applyZoom(it, viewModel.state.value.zoomPercent) }
            },
            onZoomOut = {
                viewModel.zoomOut()
                webView?.let { WebViewManager.applyZoom(it, viewModel.state.value.zoomPercent) }
            },
            onToggleDesktop = {
                viewModel.toggleDesktopMode()
            },
            onMore = {
                val perms = arrayOf(
                    android.Manifest.permission.CAMERA,
                    android.Manifest.permission.RECORD_AUDIO
                )
                permissionLauncher.launch(perms)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
