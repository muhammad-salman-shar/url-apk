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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urlapk.app.util.Constants
import com.urlapk.app.webview.UrlWebChromeClient
import com.urlapk.app.webview.UrlWebViewClient
import com.urlapk.app.webview.WebViewDownloader
import com.urlapk.app.webview.WebViewManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var webView by remember { mutableStateOf<WebView?>(null) }

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

    BackHandler(enabled = state.canGoBack) { webView?.goBack() }

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

    Box(modifier = Modifier.fillMaxSize()) {
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

        FloatingControl(
            isDesktop = state.isDesktopMode,
            onToggleDesktop = { viewModel.toggleDesktopMode() }
        )
    }
}

/**
 * Tiny pill that stays exactly where the user leaves it.
 * Position stored as Float Offset to keep drag butter-smooth.
 */
@Composable
private fun FloatingControl(
    isDesktop: Boolean,
    onToggleDesktop: () -> Unit
) {
    val density = LocalDensity.current
    val marginPx = with(density) { 12.dp.toPx() }
    val buttonPx = with(density) { 22.dp.toPx() }
    val iconSize = 14.dp

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var expanded by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf<Offset?>(null) }

    // Compute pill's current pixel width based on expanded state
    val pillWidthPx: Float = buttonPx * (if (expanded) 2f else 1f)
    val pillHeightPx: Float = buttonPx

    // Place default position once we know the container size
    LaunchedEffect(containerSize) {
        if (containerSize.width > 0 && position == null) {
            position = Offset(
                x = (containerSize.width - pillWidthPx - marginPx).coerceAtLeast(0f),
                y = marginPx
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        val pos = position ?: Offset(0f, 0f)

        Row(
            modifier = Modifier
                .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f))
                .pointerInput(containerSize, expanded) {
                    detectDragGestures(
                        onDrag = { change, drag ->
                            change.consume()
                            val p = position ?: Offset(0f, 0f)
                            val maxX = (containerSize.width - pillWidthPx).coerceAtLeast(0f)
                            val maxY = (containerSize.height - pillHeightPx).coerceAtLeast(0f)
                            position = Offset(
                                x = (p.x + drag.x).coerceIn(0f, maxX),
                                y = (p.y + drag.y).coerceIn(0f, maxY)
                            )
                        }
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clickable {
                        val p = position ?: Offset(0f, 0f)
                        position = if (!expanded) {
                            // Growing: keep chevron roughly in place, shift pill left
                            Offset(
                                x = (p.x - buttonPx).coerceIn(
                                    0f,
                                    (containerSize.width - pillWidthPx * 2f).coerceAtLeast(0f)
                                ),
                                y = p.y
                            )
                        } else {
                            // Shrinking: shift back right, clamp to screen
                            Offset(
                                x = (p.x + buttonPx).coerceIn(
                                    0f,
                                    (containerSize.width - buttonPx).coerceAtLeast(0f)
                                ),
                                y = p.y
                            )
                        }
                        expanded = !expanded
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ChevronRight
                    else Icons.Filled.ChevronLeft,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }

            if (expanded) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onToggleDesktop() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDesktop) Icons.Filled.PhoneAndroid
                        else Icons.Filled.DesktopWindows,
                        contentDescription = if (isDesktop) "Switch to mobile" else "Switch to desktop",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}
