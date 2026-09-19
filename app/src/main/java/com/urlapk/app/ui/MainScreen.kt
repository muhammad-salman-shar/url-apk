package com.urlapk.app.ui

import android.app.Activity
import android.content.ClipData
import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urlapk.app.R
import com.urlapk.app.util.DownloadTracker
import com.urlapk.app.util.UrlValidator
import com.urlapk.app.webview.UrlWebChromeClient
import com.urlapk.app.webview.UrlWebViewClient
import com.urlapk.app.webview.WebViewDownloader
import com.urlapk.app.webview.WebViewManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class LongPressTarget(
    val url: String,
    val mimeType: String?,
    val suggestedName: String
)

data class HistoryEntry(
    val title: String,
    val url: String,
    val isCurrent: Boolean
)

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var webView by remember { mutableStateOf<WebView?>(null) }
    var longPressTarget by remember { mutableStateOf<LongPressTarget?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    val downloadItems by DownloadTracker.items.collectAsStateWithLifecycle()
    var historyEntries by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }

    val downloader = remember {
        WebViewDownloader(
            context = context,
            onToast = { msg -> scope.launch { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() } },
            onLongPressMedia = { url, mime, name ->
                longPressTarget = LongPressTarget(url, mime, name)
            }
        )
    }

    var popupViews by remember { mutableStateOf<MutableList<WebView>>(mutableListOf()) }

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
            popupViews.forEach { pw ->
                try {
                    pw.stopLoading()
                    pw.loadUrl("about:blank")
                    pw.destroy()
                } catch (_: Throwable) {}
            }
            popupViews.clear()
        }
    }

    LaunchedEffect(webView) {
        webView?.loadUrl(context.getString(R.string.home_url))
    }

    LaunchedEffect(state.isDesktopMode) {
        webView?.let { WebViewManager.applyDesktopMode(context, it, state.isDesktopMode) }
    }

    // Poll DownloadManager while the downloads dialog is open.
    LaunchedEffect(showDownloads) {
        while (showDownloads) {
            DownloadTracker.refresh(context)
            kotlinx.coroutines.delay(500L)
        }
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
                        },
                        onPopupCreated = { popup ->
                            // Headless popup: attach the same downloader so any
                            // file it triggers goes through DownloadManager.
                            downloader.attach(popup)
                            popupViews.add(popup)
                            // Destroy after 30s to free memory. Real downloads
                            // are already handed off to DownloadManager by then.
                            android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed({
                                    try {
                                        popup.stopLoading()
                                        popup.loadUrl("about:blank")
                                        popup.destroy()
                                    } catch (_: Throwable) {}
                                    popupViews.remove(popup)
                                }, 30_000L)
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
            canBack = state.canGoBack,
            canForward = state.canGoForward,
            isDesktop = state.isDesktopMode,
            onNavigate = { raw ->
                val normalized = UrlValidator.normalize(raw)
                if (normalized != null) {
                    webView?.loadUrl(normalized)
                    true
                } else {
                    Toast.makeText(context, "Invalid URL", Toast.LENGTH_SHORT).show()
                    false
                }
            },
            onBack = { webView?.goBack() },
            onForward = { webView?.goForward() },
            onReload = { webView?.reload() },
            onToggleDesktop = { viewModel.toggleDesktopMode() },
            onOpenHistory = {
                val wv = webView
                if (wv != null) {
                    val list = wv.copyBackForwardList()
                    historyEntries = (0 until list.size).map { i ->
                        val item = list.getItemAtIndex(i)
                        HistoryEntry(
                            title = item.title ?: item.url.orEmpty(),
                            url = item.url.orEmpty(),
                            isCurrent = i == list.currentIndex
                        )
                    }.reversed()
                    showHistory = true
                }
            },
            onOpenDownloads = {
                DownloadTracker.refresh(context)
                showDownloads = true
            },
            downloadCount = downloadItems.count { !it.isFinished }
        )
    }

    longPressTarget?.let { target ->
        LongPressDialog(
            target = target,
            onDownload = {
                downloader.downloadUrl(target.url)
                longPressTarget = null
            },
            onCopyLink = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("url", target.url))
                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                longPressTarget = null
            },
            onDismiss = { longPressTarget = null }
        )
    }

    if (showHistory) {
        HistoryDialog(
            entries = historyEntries,
            onSelect = { url ->
                webView?.loadUrl(url)
                showHistory = false
            },
            onDismiss = { showHistory = false }
        )
    }

    if (showDownloads) {
        DownloadsDialog(
            items = downloadItems,
            onCancel = { id -> DownloadTracker.cancel(context, id) },
            onClearFinished = { DownloadTracker.clearFinished() },
            onDismiss = {
                DownloadTracker.clearFinished()
                showDownloads = false
            }
        )
    }
}

@Composable
private fun LongPressDialog(
    target: LongPressTarget,
    onDownload: () -> Unit,
    onCopyLink: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Image options") },
        text = {
            Text(
                text = target.url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDownload) { Text("Download") } },
        dismissButton = {
            Row {
                androidx.compose.material3.TextButton(onClick = onCopyLink) { Text("Copy link") }
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun HistoryDialog(
    entries: List<HistoryEntry>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("History") },
        text = {
            if (entries.isEmpty()) {
                Text("No history yet")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(entries) { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(entry.url) }
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (entry.isCurrent) "▶ ${entry.title}" else entry.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = entry.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
@Composable
private fun DownloadsDialog(
    items: List<DownloadTracker.Item>,
    onCancel: (Long) -> Unit,
    onClearFinished: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Downloads") },
        text = {
            if (items.isEmpty()) {
                Text("No active downloads")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = item.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Box(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { item.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                            Box(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (item.status) {
                                        DownloadManager.STATUS_SUCCESSFUL -> "Done - ${item.progressText}"
                                        DownloadManager.STATUS_FAILED -> "Failed"
                                        DownloadManager.STATUS_PAUSED -> "Paused - ${item.progressText}"
                                        DownloadManager.STATUS_PENDING -> "Queued"
                                        else -> item.progressText
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                if (!item.isFinished) {
                                    IconButton(
                                        onClick = { onCancel(item.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Cancel download",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = onClearFinished,
                enabled = items.any { it.isFinished }
            ) { Text("Clear finished") }
        }
    )
}

private fun FloatingControl(
    currentUrl: String,
    canBack: Boolean,
    canForward: Boolean,
    isDesktop: Boolean,
    onNavigate: (String) -> Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onToggleDesktop: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    downloadCount: Int
) {
    val density = LocalDensity.current
    val marginPx = with(density) { 12.dp.toPx() }
    val buttonPx = with(density) { 26.dp.toPx() }
    val iconSize = 16.dp
    val cardWidth = 240.dp

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var expanded by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf<Offset?>(null) }
    var urlField by remember(currentUrl) { mutableStateOf(currentUrl) }

    val pillWidthPx: Float = buttonPx

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
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f))
                .pointerInput(containerSize) {
                    detectDragGestures(
                        onDrag = { change, drag ->
                            change.consume()
                            val p = position ?: Offset(0f, 0f)
                            val maxX = (containerSize.width - pillWidthPx).coerceAtLeast(0f)
                            val maxY = (containerSize.height - buttonPx).coerceAtLeast(0f)
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
                    .size(26.dp)
                    .clickable {
                        urlField = currentUrl
                        expanded = !expanded
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ChevronRight
                    else Icons.Filled.ChevronLeft,
                    contentDescription = if (expanded) "Close controls" else "Open controls",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        if (expanded) {
            val onRightHalf = pos.x > containerSize.width / 2f
            val onBottomHalf = pos.y > containerSize.height / 2f
            val cardWidthPx = with(density) { cardWidth.toPx() }

            val cardX = if (onRightHalf) {
                (pos.x - cardWidthPx + buttonPx).coerceIn(
                    0f,
                    (containerSize.width - cardWidthPx).coerceAtLeast(0f)
                )
            } else {
                pos.x.coerceIn(0f, (containerSize.width - cardWidthPx).coerceAtLeast(0f))
            }
            val cardY = if (onBottomHalf) {
                (pos.y - with(density) { 190.dp.toPx() }).coerceAtLeast(marginPx)
            } else {
                (pos.y + buttonPx + marginPx)
            }

            Card(
                modifier = Modifier
                    .offset { IntOffset(cardX.roundToInt(), cardY.roundToInt()) }
                    .width(cardWidth),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = urlField,
                        onValueChange = { urlField = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("https://…") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(onGo = {
                            if (onNavigate(urlField)) expanded = false
                        })
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
                        IconButton(onClick = onOpenHistory) {
                            Icon(Icons.Filled.History, contentDescription = "History")
                        }
                        Box {
                            IconButton(onClick = onOpenDownloads) {
                                Icon(Icons.Filled.Download, contentDescription = "Downloads")
                            }
                            if (downloadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .align(Alignment.TopEnd)
                                        .padding(top = 6.dp, end = 6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Red)
                                )
                            }
                        }
                        IconButton(onClick = onToggleDesktop) {
                            Icon(
                                imageVector = if (isDesktop) Icons.Filled.PhoneAndroid
                                else Icons.Filled.DesktopWindows,
                                contentDescription = if (isDesktop) "Mobile site" else "Desktop site"
                            )
                        }
                    }
                }
            }
        }
    }
}
