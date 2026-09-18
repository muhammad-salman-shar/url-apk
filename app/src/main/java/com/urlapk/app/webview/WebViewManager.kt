package com.urlapk.app.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import com.urlapk.app.util.Constants

/**
 * Central place for all WebView configuration.
 *
 * Design principle: the site must look EXACTLY like it does in a normal
 * mobile browser — same viewport, same UA (in mobile mode), same CSS.
 * No injected stylesheets, no DOM rewriting.
 */
object WebViewManager {

    @SuppressLint("SetJavaScriptEnabled")
    fun buildConfiguredWebView(context: Context): WebView {
        val webView = WebView(context)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        webView.settings.applySettings()
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
        webView.setBackgroundColor(android.graphics.Color.WHITE)
        return webView
    }

    private fun WebSettings.applySettings() {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        loadsImagesAutomatically = true

        useWideViewPort = true
        loadWithOverviewMode = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        textZoom = 100

        allowFileAccess = true
        allowContentAccess = true

        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false

        mediaPlaybackRequiresUserGesture = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            safeBrowsingEnabled = true
        }

        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        cacheMode = WebSettings.LOAD_DEFAULT
        setGeolocationEnabled(false)

        // NOTE: do NOT set userAgentString to null — some OEM WebViews crash.
        // Leaving it unset = system default mobile UA (what we want).
    }

    fun applyCookiePolicy(webView: WebView) {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    }

    /**
     * Toggles between desktop and mobile UA and reloads the page.
     * When switching back to mobile we use the system default UA
     * (via WebSettings.getDefaultUserAgent) so the site sees a real
     * mobile browser fingerprint.
     */
    fun applyDesktopMode(context: Context, webView: WebView, enabled: Boolean) {
        webView.settings.userAgentString = if (enabled) {
            Constants.DESKTOP_UA
        } else {
            runCatching { WebSettings.getDefaultUserAgent(context) }
                .getOrElse { Constants.DESKTOP_UA }
        }
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.reload()
    }

    fun applyZoom(webView: WebView, zoomPercent: Int) {
        val clamped = zoomPercent.coerceIn(Constants.MIN_ZOOM, Constants.MAX_ZOOM)
        webView.setInitialScale(clamped)
    }

    fun destroyWebView(webView: WebView?, container: ViewGroup?) {
        if (webView == null) return
        container?.removeView(webView)
        webView.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
    }
}
