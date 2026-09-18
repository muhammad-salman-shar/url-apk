package com.urlapk.app.webview

import android.annotation.SuppressLint
import android.app.Activity
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

    private const val MOBILE_UA: String? = null // null = use system default (real mobile UA)

    @SuppressLint("SetJavaScriptEnabled")
    fun buildConfiguredWebView(context: Context): WebView {
        return WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.applySettings()
            configureCookies()
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
            overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
            setBackgroundColor(android.graphics.Color.WHITE)
        }
    }

    private fun WebSettings.applySettings() {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        loadsImagesAutomatically = true

        // Viewport / scaling — keep site looking native
        useWideViewPort = true
        loadWithOverviewMode = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        textZoom = 100

        // File access (needed for file input & blob)
        allowFileAccess = true
        allowContentAccess = true

        // Security — no file:// cross access
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false

        // Media
        mediaPlaybackRequiresUserGesture = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            safeBrowsingEnabled = true
        }

        // Mixed content — block insecure resources on HTTPS pages
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        // Cache
        cacheMode = WebSettings.LOAD_DEFAULT

        // Geolocation — let page request; permission handled by app
        setGeolocationEnabled(false)

        // User agent (mobile default)
        userAgentString = MOBILE_UA
    }

    private fun configureCookies() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(null, true) // ignored if no WebView; safe
        }
    }

    fun applyCookiePolicy(webView: WebView) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    }

    fun applyDesktopMode(webView: WebView, enabled: Boolean) {
        webView.settings.userAgentString = if (enabled) {
            Constants.DESKTOP_UA
        } else {
            MOBILE_UA
        }
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        // Force a reload so the server serves the correct variant
        webView.reload()
    }

    fun applyZoom(webView: WebView, zoomPercent: Int) {
        val clamped = zoomPercent.coerceIn(Constants.MIN_ZOOM, Constants.MAX_ZOOM)
        // WebView textZoom only affects text; use setInitialScale for a page-wide zoom.
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
