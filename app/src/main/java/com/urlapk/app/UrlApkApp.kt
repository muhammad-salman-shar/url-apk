package com.urlapk.app

import android.app.Application
import android.webkit.WebView

class UrlApkApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Enable WebView debugging only in debug builds
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
