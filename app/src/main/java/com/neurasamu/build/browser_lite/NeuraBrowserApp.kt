package com.neurasamu.build.browser_lite

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebView
import com.neurasamu.build.browser_lite.util.CrashLogger
import com.neurasamu.build.browser_lite.util.DownloadNotifications

class NeuraBrowserApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        DownloadNotifications.ensureChannel(this)

        // Enable cookie persistence app-wide BEFORE any WebView is created.
        // Without this, some sites behave as if cookies are disabled.
        CookieManager.getInstance().setAcceptCookie(true)

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
