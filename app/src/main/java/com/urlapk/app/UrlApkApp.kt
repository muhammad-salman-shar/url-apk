package com.urlapk.app

import android.app.Application
import android.webkit.WebView
import com.urlapk.app.util.CrashLogger

class UrlApkApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
