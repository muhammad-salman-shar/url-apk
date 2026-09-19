package com.neurasamu.build.browser_lite.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes uncaught exceptions to a file in the app's external files dir
 * so we can read them from Termux/adb even if the app dies before logcat
 * is captured.
 *
 * File: /storage/emulated/0/Android/data/<pkg>/files/crash.txt
 */
object CrashLogger {

    private const val TAG = "NeuraBrowserCrash"
    private const val FILE_NAME = "crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(appContext, thread, throwable)
            } catch (t: Throwable) {
                Log.e(TAG, "CrashLogger failed: ${t.message}")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val header = "=== CRASH at $timestamp (thread: ${thread.name}) ===\n"
        val body = header + sw.toString() + "\n\n"

        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, FILE_NAME)
        file.appendText(body)
        Log.e(TAG, body)
    }
}
