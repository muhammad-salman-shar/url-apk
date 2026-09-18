package com.urlapk.app.ui

import android.app.Activity
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urlapk.app.util.Constants
import com.urlapk.app.util.UrlValidator
import com.urlapk.app.webview.UrlWebChromeClient
import com.urlapk.app.webview.UrlWebViewClient
import com.urlapk.app.webview.WebViewDownloader
import com.urlapk.app.webview.WebViewManager
import kotlinx.coroutines.launch

/**
 * Full-screen WebView + edge handle for controls.
 *
 * Layout:
 *   [   WebView (fills entire screen, no bars, no overlap)   ]
 *   [                                              ] handle  ]
 *   [                              (right edge)              ]
 *
 * Tap the thin vertical handle on the right edge -> small
 * control panel opens with URL input + back/forward/reload
 * + desktop/mobile toggle. Close -> panel disappears.
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var webView by remember { mutableStateOf<WebView?>(null) }
    var panelOpen by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf(state.currentUrl) }

    val downloader = remember {
        WebViewDownloader(context) { msg ->
            scope.launch { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
        }
    }

    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cb = filePathCallback ?: return@rememberLauncherForActivityResult
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        cb.onReceiveValue(uris)
        filePathCallback = null
    }

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

    // Back press: close panel first, then navigate WebView history
    BackHandler(enabled = panelOpen) { panelOpen = false }
    BackHandler(enabled = !panelOpen && state.canGoBack) { webView?.goBack() }

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

    LaunchedEffect(webView) {
        webView?.loadUrl(Constants.DEFAULT_HOME_URL)
    }

    LaunchedEffect(state.isDesktopMode) {
        webView?.let { WebViewManager.applyDesktopMode(context, it, state.isDesktopMode) }
    }

    // Refresh URL field whenever the panel is (re)opened
    LaunchedEffect(panelOpen) {
        if (panelOpen) urlInput = state.currentUrl
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-screen WebView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
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

        // Thin vertical handle on right edge, ~70% down
        EdgeHandle(
            visible = !panelOpen,
            onClick = { panelOpen = true },
            modifier = Modifier
                .align(Alignment.CenterEnd)
        )

        // Control panel slides in from the right
        AnimatedVisibility(
            visible = panelOpen,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 60.dp, end = 6.dp)
        ) {
            ControlPanel(
                urlInput = urlInput,
                onUrlChange = { urlInput = it },
                onUrlGo = {
                    val normalized = UrlValidator.normalize(urlInput)
                    if (normalized != null) {
                        webView?.loadUrl(normalized)
                        panelOpen = false
                    } else {
                        Toast.makeText(context, "Invalid URL", Toast.LENGTH_SHORT).show()
                    }
                },
                canBack = state.canGoBack,
                canForward = state.canGoForward,
                isDesktop = state.isDesktopMode,
                onBack = { webView?.goBack() },
                onForward = { webView?.goForward() },
                onReload = { webView?.reload() },
                onToggleDesktop = { viewModel.toggleDesktopMode() },
                onRequestPermissions = {
                    permissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.CAMERA,
                            android.Manifest.permission.RECORD_AUDIO
                        )
                    )
                },
                onClose = { panelOpen = false }
            )
        }
    }
}

@Composable
private fun EdgeHandle(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(72.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)
                )
                .clickable { onClick() }
        )
    }
}

@Composable
private fun ControlPanel(
    urlInput: String,
    onUrlChange: (String) -> Unit,
    onUrlGo: () -> Unit,
    canBack: Boolean,
    canForward: Boolean,
    isDesktop: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onToggleDesktop: () -> Unit,
    onRequestPermissions: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.width(300.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Controls",
                    style = MaterialTheme.typography.titleSmall
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            OutlinedTextField(
                value = urlInput,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://…") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { onUrlGo() })
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, enabled = canBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = onForward, enabled = canForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                }
                IconButton(onClick = onReload) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                }
                IconButton(onClick = onToggleDesktop) {
                    Icon(
                        imageVector = if (isDesktop) Icons.Filled.PhoneAndroid
                        else Icons.Filled.DesktopWindows,
                        contentDescription = if (isDesktop) "Mobile site" else "Desktop site"
                    )
                }
                IconButton(onClick = onRequestPermissions) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Request permissions",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}
