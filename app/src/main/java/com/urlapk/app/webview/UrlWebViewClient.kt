package com.urlapk.app.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.urlapk.app.util.AdBlocker
import java.io.ByteArrayInputStream

/**
 * WebViewClient that keeps navigation inside the app and applies the
 * three-layer ad blocker:
 *
 *  Layer 1 (request interception):
 *    shouldInterceptRequest returns an empty body for any resource whose
 *    host is on the AdBlocker list. The request never leaves the device.
 *
 *  Layer 2 (navigation blocking):
 *    shouldOverrideUrlLoading refuses navigation to ad hosts, which kills
 *    most ad-click redirects before they take the user away.
 *
 *  Layer 3 (cosmetic hiding + JS defense):
 *    onPageFinished injects CSS to hide leftover ad containers and a small
 *    JS shim that neutralises window.open() and anchor hijacks pointing
 *    at known ad domains.
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
        // Block navigation to known ad hosts; allow everything else in-app.
        return AdBlocker.isAd(request.url?.toString())
    }

    @Deprecated("Kept for API < 24 compatibility.")
    override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
        return AdBlocker.isAd(url)
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url?.toString()
        if (AdBlocker.isAd(url)) {
            // Return an empty 204-style response. The ad resource never loads.
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                200,
                "OK",
                mapOf("Access-Control-Allow-Origin" to "*"),
                ByteArrayInputStream(ByteArray(0))
            )
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)

        // Layer 3: hide leftover ad containers and neutralise popup/click
        // hijacks. Both scripts are idempotent, so calling them again on
        // every page finish is safe.
        view.evaluateJavascript(AdBlocker.injectAdHideCss(), null)
        view.evaluateJavascript(AdBlocker.injectAdDefense(), null)

        onPageFinished(url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
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
        // Security: never proceed on SSL errors.
        handler.cancel()
        onPageError(-1, "SSL error: ${error.primaryError}")
    }
}
