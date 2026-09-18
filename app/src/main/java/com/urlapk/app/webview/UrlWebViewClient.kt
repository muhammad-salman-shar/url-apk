package com.urlapk.app.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * WebViewClient that keeps ALL navigation inside the app
 * (no external browser handoff) and reports page lifecycle
 * to the host via callbacks.
 *
 * Design: website must render exactly as in a normal browser —
 * no URL rewriting, no injected headers, no redirects.
 */
class UrlWebViewClient(
    private val onPageStarted: (String?) -> Unit,
    private val onPageFinished: (String?) -> Unit,
    private val onPageError: (Int, String?) -> Unit
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        // Keep EVERYTHING inside the WebView. Returning false
        // lets the WebView load the URL itself (in-app).
        return false
    }

    @Deprecated("Kept for API < 24 compatibility (minSdk 24, so unused on modern).")
    override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
        return false
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        onPageFinished(url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        // Only main-frame errors should show the error screen.
        if (request.isForMainFrame) {
            onPageError(error.errorCode, error.description?.toString())
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request.isForMainFrame && errorResponse.statusCode >= 400) {
            onPageError(errorResponse.statusCode, errorResponse.reasonPhrase)
        }
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError
    ) {
        // Security: NEVER proceed on SSL errors. Cancel.
        handler.cancel()
        onPageError(-1, "SSL error: ${error.primaryError}")
    }
}
