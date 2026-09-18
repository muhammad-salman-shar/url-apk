package com.urlapk.app.webview

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebView
import com.urlapk.app.util.FileUtils

/**
 * Handles downloads from WebView:
 *  1. HTTP(S) downloads     -> DownloadManager (system UI, resumable)
 *  2. Blob / data: URLs     -> JS reads bytes -> Base64 -> save
 *  3. Long-press on image   -> hit-test -> offer to download the image
 */
class WebViewDownloader(
    private val context: Context,
    private val onToast: (String) -> Unit,
    private val onLongPressMedia: (url: String, mimeType: String?, suggestedName: String) -> Unit
) : DownloadListener {

    override fun onDownloadStart(
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?,
        contentLength: Long
    ) {
        if (url.isNullOrBlank()) return

        when {
            url.startsWith("blob:") || url.startsWith("data:") -> triggerBlobDownload(url, mimetype)
            else -> enqueueHttpDownload(url, userAgent, contentDisposition, mimetype)
        }
    }

    private fun enqueueHttpDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?
    ) {
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimetype)
                addRequestHeader("User-Agent", userAgent ?: "")
                addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                setTitle(fileName)
                setDescription("Downloading…")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            onToast("Download started: $fileName")
        } catch (e: Exception) {
            onToast("Download failed: ${e.message ?: "unknown"}")
        }
    }

    /** Public so MainScreen can trigger a download for a URL found via hit-test. */
    fun downloadUrl(url: String) {
        enqueueHttpDownload(url, null, null, null)
    }

    private fun triggerBlobDownload(blobUrl: String, mimeType: String?) {
        val webView = currentWebView ?: return
        val resolvedMime = mimeType ?: "application/octet-stream"

        val js = """
            (function() {
                try {
                    var xhr = new XMLHttpRequest();
                    xhr.open('GET', '$blobUrl', true);
                    xhr.responseType = 'blob';
                    xhr.onload = function() {
                        var blob = xhr.response;
                        var reader = new FileReader();
                        reader.onloadend = function() {
                            var base64 = reader.result.split(',')[1];
                            AndroidBlob.onBlobReady(base64, blob.type || '$resolvedMime');
                        };
                        reader.readAsDataURL(blob);
                    };
                    xhr.onerror = function() { AndroidBlob.onError('xhr failed'); };
                    xhr.send();
                } catch (e) { AndroidBlob.onError(String(e)); }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    private var currentWebView: WebView? = null

    /** Attach the downloader to a WebView, expose blob bridge, and enable long-press. */
    fun attach(webView: WebView) {
        currentWebView = webView
        webView.setDownloadListener(this)
        webView.addJavascriptInterface(
            JsBlobBridge { base64, mime -> handleBlobBytes(base64, mime) },
            JS_BRIDGE_NAME
        )
        attachLongPressHandler(webView)
    }

    /**
     * Long-press anywhere -> inspect hitTestResult.
     * If it's an image, image-anchor, or video src, notify the host so it can
     * show a "Download" dialog. Returns false so the default WebView menu
     * (text selection etc.) still works for non-media hits.
     */
    private fun attachLongPressHandler(webView: WebView) {
        webView.isLongClickable = true
        webView.setOnLongClickListener {
            val result = webView.hitTestResult
            val type = result.type
            val extra = result.extra

            val (isMedia, mime) = when (type) {
                WebView.HitTestResult.IMAGE_TYPE -> true to null
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> true to null
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> true to null
                else -> false to null
            }

            if (isMedia && !extra.isNullOrBlank() &&
                (extra.startsWith("http://") || extra.startsWith("https://") ||
                 extra.startsWith("data:") || extra.startsWith("blob:"))
            ) {
                val name = URLUtil.guessFileName(extra, null, mime)
                onLongPressMedia(extra, mime, name)
                true
            } else {
                false
            }
        }
    }

    private fun handleBlobBytes(base64: String, mimeType: String?) {
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val fileName = FileUtils.buildFileName(mimeType)
            val uri = FileUtils.saveBlobToDownloads(context, bytes, mimeType, fileName)
            if (uri != null) {
                onToast("Saved: $fileName")
            } else {
                onToast("Could not save file")
            }
        } catch (e: IllegalArgumentException) {
            onToast("Invalid blob data")
        }
    }

    companion object {
        const val JS_BRIDGE_NAME = "AndroidBlob"
    }
}

private class JsBlobBridge(
    private val onBlob: (String, String?) -> Unit
) {
    @android.webkit.JavascriptInterface
    fun onBlobReady(base64: String, mimeType: String?) {
        onBlob(base64, mimeType)
    }

    @android.webkit.JavascriptInterface
    fun onError(message: String) {
        // no-op
    }
}
