package com.neurasamu.build.browser_lite.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.neurasamu.build.browser_lite.MainActivity

/**
 * Posts download progress / completion notifications so the user can see
 * live status in the system tray and tap to return to the app.
 *
 * Progress notifications are ongoing (not dismissible) until the download
 * finishes; completion notifications auto-cancel on tap.
 */
object DownloadNotifications {

    const val CHANNEL_ID = "neura_downloads"
    private const val CHANNEL_NAME = "Downloads"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Live progress for ongoing downloads"
            setShowBadge(true)
        }
        mgr.createNotificationChannel(ch)
    }

    fun showProgress(
        context: Context,
        id: Long,
        fileName: String,
        done: Long,
        total: Long,
        speed: Long
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(fileName)
            .setContentText(progressText(done, total, speed))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (total > 0) {
            val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
            builder.setProgress(100, pct, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        notify(context, id, builder)
    }

    fun showPaused(context: Context, id: Long, fileName: String, done: Long, total: Long) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("$fileName (paused)")
            .setContentText(progressText(done, total, 0L))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
        if (total > 0) {
            val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
            builder.setProgress(100, pct, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        notify(context, id, builder)
    }

    fun showComplete(context: Context, id: Long, fileName: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(fileName)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
        notify(context, id, builder)
    }

    fun showFailed(context: Context, id: Long, fileName: String, reason: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText("$fileName — $reason")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
        notify(context, id, builder)
    }

    fun cancel(context: Context, id: Long) {
        try {
            NotificationManagerCompat.from(context).cancel(id.toInt())
        } catch (_: Throwable) {}
    }

    private fun notify(context: Context, id: Long, builder: NotificationCompat.Builder) {
        try {
            NotificationManagerCompat.from(context).notify(id.toInt(), builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted; silently ignore
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun progressText(done: Long, total: Long, speed: Long): String {
        val d = fmt(done)
        val s = fmt(speed)
        return if (total > 0) "$d / ${fmt(total)}  ·  $s/s"
        else "$d  ·  $s/s"
    }

    private fun fmt(b: Long): String {
        if (b < 1024) return "$b B"
        val kb = b / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        return String.format("%.2f GB", mb / 1024.0)
    }
}
