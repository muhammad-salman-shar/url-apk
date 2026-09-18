package com.urlapk.app

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebView
import com.urlapk.app.util.CrashLogger

class UrlApkApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)

        // Enable cookie persistence app-wide BEFORE any WebView is created.
        // Without this, some sites behave as if cookies are disabled.
        CookieManager.getInstance().setAcceptCookie(true)

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
