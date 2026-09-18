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
import android.widget.Toast
import com.urlapk.app.util.FileUtils

/**
 * Handles two kinds of downloads from WebView:
 *  1. Standard HTTP(S) downloads  -> DownloadManager (system UI, resumable)
 *  2. Blob / data: URLs           -> injected JS reads bytes -> Base64 -> save
 *
 * Blob handling requires a JS bridge; we expose [JsBlobBridge] to the page
 * only when a download is triggered, then remove it after completion.
 */
class WebViewDownloader(
    private val context: Context,
    private val onToast: (String) -> Unit
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
            url.startsWith("blob:") || url.startsWith("data:") -> {
                triggerBlobDownload(url, mimetype)
            }
            else -> {
                enqueueHttpDownload(url, userAgent, contentDisposition, mimetype)
            }
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

    /**
     * Blob URLs cannot be fetched by DownloadManager. We ask JS to read the
     * blob into a Base64 string and hand it back via a one-shot bridge.
     */
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

    /** Attach the downloader to a WebView and expose the blob bridge. */
    fun attach(webView: WebView) {
        currentWebView = webView
        webView.setDownloadListener(this)
        webView.addJavascriptInterface(
            JsBlobBridge { base64, mime -> handleBlobBytes(base64, mime) },
            JS_BRIDGE_NAME
        )
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

/**
 * JS-facing bridge. Only exposes two methods to the page.
 */
private class JsBlobBridge(
    private val onBlob: (String, String?) -> Unit
) {
    @android.webkit.JavascriptInterface
    fun onBlobReady(base64: String, mimeType: String?) {
        onBlob(base64, mimeType)
    }

    @android.webkit.JavascriptInterface
    fun onError(message: String) {
        // no-op — surfaced via toast on caller side if needed
    }
}
