package com.neurasamu.build.browser_lite.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles notification action buttons (Pause / Resume / Cancel).
 * DownloadEngine is a singleton, so a simple receiver can dispatch to it
 * directly without binding to any Activity.
 */
class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0L) return
        when (intent.action) {
            ACTION_PAUSE -> DownloadEngine.pause(id)
            ACTION_RESUME -> DownloadEngine.resume(context.applicationContext, id)
            ACTION_CANCEL -> DownloadEngine.cancel(id)
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.neurasamu.build.browser_lite.action.PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "com.neurasamu.build.browser_lite.action.RESUME_DOWNLOAD"
        const val ACTION_CANCEL = "com.neurasamu.build.browser_lite.action.CANCEL_DOWNLOAD"
        const val EXTRA_ID = "download_id"
    }
}
