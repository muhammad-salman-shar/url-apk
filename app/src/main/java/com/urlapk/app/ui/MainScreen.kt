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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.urlapk.app.util.UrlValidator
import com.urlapk.app.webview.UrlWebChromeClient
import com.urlapk.app.webview.UrlWebViewClient
import com.urlapk.app.webview.WebViewDownloader
import com.urlapk.app.webview.WebViewManager
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class Corner { TopStart, TopEnd, BottomStart, BottomEnd }

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
            currentUrl = state.currentUrl,
            isDesktop = state.isDesktopMode,
            onUrlSubmit = { raw ->
                val normalized = UrlValidator.normalize(raw)
                if (normalized != null) {
                    webView?.loadUrl(normalized)
                } else {
                    Toast.makeText(context, "Invalid URL", Toast.LENGTH_SHORT).show()
                }
            },
            onToggleDesktop = { viewModel.toggleDesktopMode() }
        )
    }
}

/**
 * Draggable floating pill anchored to one of the four screen corners.
 *
 * Collapsed: small pill with a chevron. Tap chevron -> expands and shows
 *            two extra icons (URL input, desktop/mobile toggle).
 * Expanded:  same pill grows leftward (or rightward if on the left side)
 *            revealing the extra icons. Tap chevron again -> collapse.
 *
 * Drag the pill anywhere; on release it snaps to the nearest corner.
 */
@Composable
private fun FloatingControl(
    currentUrl: String,
    isDesktop: Boolean,
    onUrlSubmit: (String) -> Unit,
    onToggleDesktop: () -> Unit
) {
    val density = LocalDensity.current
    val marginPx = with(density) { 12.dp.toPx() }
    val buttonSizePx = with(density) { 44.dp.toPx() }
    val expandedWidthPx = with(density) { 44.dp.toPx() * 3 }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var controlSize by remember { mutableStateOf(IntSize.Zero) }
    var corner by remember { mutableStateOf(Corner.TopEnd) }
    var expanded by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }

    // Drag-time transient position (null = use corner-derived position)
    var dragPos by remember { mutableStateOf<IntOffset?>(null) }

    // Compute position from corner
    val isRight = corner == Corner.TopEnd || corner == Corner.BottomEnd
    val isTop = corner == Corner.TopStart || corner == Corner.TopEnd

    val derivedX = if (isRight) {
        (containerSize.width - controlSize.width - marginPx).toInt()
    } else {
        marginPx.toInt()
    }
    val derivedY = if (isTop) {
        marginPx.toInt()
    } else {
        (containerSize.height - controlSize.height - marginPx).toInt()
    }

    val currentOffset = dragPos ?: IntOffset(derivedX, derivedY)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        Row(
            modifier = Modifier
                .offset { currentOffset }
                .onSizeChanged { controlSize = it }
                .pointerInput(containerSize, controlSize) {
                    detectDragGestures(
                        onDragStart = {
                            dragPos = IntOffset(derivedX, derivedY)
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            val p = dragPos ?: IntOffset(derivedX, derivedY)
                            val nx = (p.x + drag.x.toInt())
                                .coerceIn(marginPx.toInt(), (containerSize.width - controlSize.width - marginPx).toInt())
                            val ny = (p.y + drag.y.toInt())
                                .coerceIn(marginPx.toInt(), (containerSize.height - controlSize.height - marginPx).toInt())
                            dragPos = IntOffset(nx, ny)
                        },
                        onDragEnd = {
                            val p = dragPos
                            if (p != null) {
                                val cx = p.x + controlSize.width / 2
                                val cy = p.y + controlSize.height / 2
                                corner = when {
                                    cx < containerSize.width / 2 && cy < containerSize.height / 2 -> Corner.TopStart
                                    cx >= containerSize.width / 2 && cy < containerSize.height / 2 -> Corner.TopEnd
                                    cx < containerSize.width / 2 && cy >= containerSize.height / 2 -> Corner.BottomStart
                                    else -> Corner.BottomEnd
                                }
                            }
                            dragPos = null
                        }
                    )
                }
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chevron toggle (always visible)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable { expanded = !expanded },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ChevronRight
                    else Icons.Filled.ChevronLeft,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Color.White
                )
            }

            // Extra icons (only when expanded)
            if (expanded) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { showUrlDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = "Open URL",
                        tint = Color.White
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { onToggleDesktop() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDesktop) Icons.Filled.PhoneAndroid
                        else Icons.Filled.DesktopWindows,
                        contentDescription = if (isDesktop) "Switch to mobile" else "Switch to desktop",
                        tint = Color.White
                    )
                }
            }
        }
    }

    if (showUrlDialog) {
        UrlInputDialog(
            initialUrl = currentUrl,
            onDismiss = { showUrlDialog = false },
            onSubmit = { url ->
                onUrlSubmit(url)
                showUrlDialog = false
            }
        )
    }
}

@Composable
private fun UrlInputDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var text by remember(initialUrl) { mutableStateOf(initialUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open URL") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://…") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit(text) })
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(text) }) { Text("Open") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
