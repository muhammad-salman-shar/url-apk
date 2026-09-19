package com.urlapk.app.util

import android.app.DownloadManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks DownloadManager downloads so the UI can show:
 *  - file name
 *  - bytes downloaded / total
 *  - status (pending / running / paused / done / failed)
 *  - cancel action
 *
 * The tracker does not own a coroutine scope. Callers drive refresh() on
 * a polling loop while the UI needs live progress.
 */
object DownloadTracker {

    data class Item(
        val id: Long,
        val fileName: String,
        val bytesDone: Long,
        val bytesTotal: Long,
        val status: Int,
        val reason: Int
    ) {
        val isFinished: Boolean
            get() = status == DownloadManager.STATUS_SUCCESSFUL ||
                    status == DownloadManager.STATUS_FAILED

        val progressFraction: Float
            get() = if (bytesTotal > 0) {
                (bytesDone.toFloat() / bytesTotal.toFloat()).coerceIn(0f, 1f)
            } else 0f

        val progressText: String
            get() {
                val done = formatBytes(bytesDone)
                return if (bytesTotal > 0) {
                    "$done / ${formatBytes(bytesTotal)}"
                } else {
                    done
                }
            }

        private fun formatBytes(b: Long): String {
            if (b < 1024) return "$b B"
            val kb = b / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.1f MB", mb)
            return String.format("%.2f GB", mb / 1024.0)
        }
    }

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    private val trackedIds = linkedSetOf<Long>()

    /** Register a DownloadManager id so refresh() will start reporting it. */
    fun track(context: Context, id: Long, fallbackName: String) {
        trackedIds.add(id)
        val item = queryOne(context, id, fallbackName) ?: return
        _items.value = _items.value.filterNot { it.id == id } + item
    }

    /** Poll DownloadManager for the latest state of every tracked download. */
    fun refresh(context: Context) {
        if (trackedIds.isEmpty()) {
            if (_items.value.isNotEmpty()) _items.value = emptyList()
            return
        }
        val current = _items.value.associateBy { it.id }
        val updated = mutableListOf<Item>()
        val gone = mutableListOf<Long>()
        for (id in trackedIds.toList()) {
            val fallback = current[id]?.fileName ?: "download"
            val item = queryOne(context, id, fallback)
            if (item == null) {
                gone.add(id)
            } else {
                updated.add(item)
            }
        }
        trackedIds.removeAll(gone.toSet())
        _items.value = updated
    }

    /** Cancel and remove a tracked download. */
    fun cancel(context: Context, id: Long) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        runCatching { dm.remove(id) }
        trackedIds.remove(id)
        _items.value = _items.value.filterNot { it.id == id }
    }

    /** Drop finished entries from the visible list (does not delete files). */
    fun clearFinished() {
        val finished = _items.value.filter { it.isFinished }.map { it.id }
        trackedIds.removeAll(finished.toSet())
        _items.value = _items.value.filterNot { it.isFinished }
    }

    /** Remove everything from the UI list (does not cancel active downloads). */
    fun clearAll() {
        trackedIds.clear()
        _items.value = emptyList()
    }

    private fun queryOne(context: Context, id: Long, fallbackName: String): Item? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(id)) ?: return null
        return cursor.use { c ->
            if (!c.moveToFirst()) return@use null
            val done = c.getLongOrZero(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val total = c.getLongOrZero(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val status = c.getIntOrZero(DownloadManager.COLUMN_STATUS)
            val reason = c.getIntOrZero(DownloadManager.COLUMN_REASON)
            val title = runCatching {
                c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
            }.getOrNull() ?: fallbackName
            Item(id, title, done, total, status, reason)
        }
    }

    private fun android.database.Cursor.getLongOrZero(col: String): Long {
        val idx = getColumnIndex(col)
        return if (idx >= 0) getLong(idx) else 0L
    }

    private fun android.database.Cursor.getIntOrZero(col: String): Int {
        val idx = getColumnIndex(col)
        return if (idx >= 0) getInt(idx) else 0
    }
}
