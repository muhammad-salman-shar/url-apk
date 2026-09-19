package com.urlapk.app.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * Custom HTTP download engine backed by OkHttp.
 *
 * Why not DownloadManager: Android's DownloadManager does not support
 * pause/resume. It also gives no live speed or ETA. This engine handles
 * all three natively.
 *
 * Storage strategy: bytes are written to a private cache file under
 * context.cacheDir/neura_downloads/. When the download completes, the file
 * is streamed to the public Downloads folder via MediaStore (API 29+) or
 * to Environment.DIRECTORY_DOWNLOADS (older). Pausing/resuming just keeps
 * appending to the cache file, so no MediaStore quirks block us mid-flight.
 *
 * Resume strategy: standard HTTP Range request (bytes=<offset>-).
 * If the server ignores Range and returns 200, we restart from zero.
 *
 * Threading: all work happens on a shared IO scope owned by this object.
 * State is exposed as a StateFlow of immutable Items.
 */
object DownloadEngine {

    enum class State { QUEUED, RUNNING, PAUSED, DONE, FAILED }

    data class Item(
        val id: Long,
        val url: String,
        val fileName: String,
        val mimeType: String?,
        val bytesDone: Long,
        val bytesTotal: Long,      // -1 if unknown
        val state: State,
        val bytesPerSecond: Long,  // 0 when not running
        val etaSeconds: Long,      // 0 when unknown
        val errorMessage: String?
    ) {
        val progressFraction: Float
            get() = if (bytesTotal > 0) (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f) else 0f

        val progressText: String
            get() {
                val d = fmt(bytesDone)
                return if (bytesTotal > 0) "$d / ${fmt(bytesTotal)}" else d
            }

        val speedText: String
            get() = if (state == State.RUNNING && bytesPerSecond > 0) "${fmt(bytesPerSecond)}/s" else ""

        val etaText: String
            get() {
                if (state != State.RUNNING || etaSeconds <= 0L) return ""
                return if (etaSeconds < 60) "${etaSeconds}s left"
                else "${etaSeconds / 60}m ${etaSeconds % 60}s left"
            }

        companion object {
            fun fmt(b: Long): String {
                if (b < 1024) return "$b B"
                val kb = b / 1024.0
                if (kb < 1024) return String.format("%.1f KB", kb)
                val mb = kb / 1024.0
                if (mb < 1024) return String.format("%.1f MB", mb)
                return String.format("%.2f GB", mb / 1024.0)
            }
        }
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    private class Runtime(
        val id: Long,
        val url: String,
        val fileName: String,
        val mimeType: String?,
        val headers: Map<String, String>,
        val cacheFile: File
    ) {
        val bytesDone = AtomicLong(0L)
        @Volatile var bytesTotal: Long = -1L
        var job: Job? = null
    }

    private val runtimes = mutableMapOf<Long, Runtime>()
    private val nextId = AtomicLong(1L)
    private val lock = Any()

    /** Enqueue a download and start it immediately. Returns the id. */
    fun enqueue(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String? = null,
        headers: Map<String, String> = emptyMap()
    ): Long {
        val id = nextId.getAndIncrement()
        val safeName = fileName.replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "download_$id" }
        val cacheDir = File(context.cacheDir, "neura_downloads").apply { mkdirs() }
        val cacheFile = File(cacheDir, "$id-$safeName")
        val rt = Runtime(id, url, safeName, mimeType, headers, cacheFile)
        synchronized(lock) { runtimes[id] = rt }

        _items.update { current ->
            current + Item(
                id = id,
                url = url,
                fileName = safeName,
                mimeType = mimeType,
                bytesDone = 0L,
                bytesTotal = -1L,
                state = State.QUEUED,
                bytesPerSecond = 0L,
                etaSeconds = 0L,
                errorMessage = null
            )
        }
        startJob(context, rt, resume = false)
        return id
    }

    /** Pause a running or queued download. Partial file stays on disk. */
    fun pause(id: Long) {
        val rt = synchronized(lock) { runtimes[id] } ?: return
        rt.job?.cancel()
        rt.job = null
        _items.update { list ->
            list.map { if (it.id == id) it.copy(state = State.PAUSED, bytesPerSecond = 0L, etaSeconds = 0L) else it }
        }
    }

    /** Resume a paused download using an HTTP Range request. */
    fun resume(context: Context, id: Long) {
        val rt = synchronized(lock) { runtimes[id] } ?: return
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.state != State.PAUSED) return
        startJob(context, rt, resume = true)
    }

    /** Stop and delete a download (cache file removed). */
    fun cancel(id: Long) {
        val rt = synchronized(lock) { runtimes.remove(id) } ?: return
        rt.job?.cancel()
        rt.job = null
        runCatching { rt.cacheFile.delete() }
        _items.update { list -> list.filterNot { it.id == id } }
    }

    /** Remove finished/failed items from the list (files already in Downloads). */
    fun clearFinished() {
        val toRemove = _items.value.filter { it.state == State.DONE || it.state == State.FAILED }.map { it.id }
        toRemove.forEach { id -> synchronized(lock) { runtimes.remove(id) }?.cacheFile?.delete() }
        _items.update { list -> list.filterNot { it.state == State.DONE || it.state == State.FAILED } }
    }

    private fun startJob(context: Context, rt: Runtime, resume: Boolean) {
        _items.update { list ->
            list.map { if (it.id == rt.id) it.copy(state = State.RUNNING, errorMessage = null) else it }
        }
        rt.job = scope.launch {
            try {
                doDownload(context, rt, resume)
            } catch (_: CancellationException) {
                // paused or cancelled by user
            } catch (t: Throwable) {
                _items.update { list ->
                    list.map {
                        if (it.id == rt.id) it.copy(
                            state = State.FAILED,
                            errorMessage = t.message ?: "Download failed",
                            bytesPerSecond = 0L,
                            etaSeconds = 0L
                        ) else it
                    }
                }
            }
        }
    }

    private suspend fun doDownload(context: Context, rt: Runtime, resume: Boolean) {
        var existing = if (resume && rt.cacheFile.exists()) rt.cacheFile.length() else {
            rt.cacheFile.delete()
            0L
        }
        rt.bytesDone.set(existing)

        val builder = Request.Builder()
            .url(rt.url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/122.0.0.0 Mobile Safari/537.36"
            )
        rt.headers.forEach { (k, v) -> builder.header(k, v) }
        if (existing > 0L) builder.header("Range", "bytes=$existing-")

        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")

            val body = resp.body ?: throw IOException("Empty body")

            // Server ignored Range (200 instead of 206): restart from zero
            if (existing > 0L && resp.code != 206) {
                rt.cacheFile.delete()
                existing = 0L
                rt.bytesDone.set(0L)
            }

            rt.bytesTotal = if (existing > 0L && resp.code == 206) {
                val cr = resp.header("Content-Range")       // bytes 100-999/1000
                cr?.substringAfter("/", "")?.toLongOrNull() ?: (existing + body.contentLength())
            } else {
                body.contentLength()
            }

            val raf = RandomAccessFile(rt.cacheFile, "rw")
            try {
                raf.seek(existing)
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    var lastTick = System.currentTimeMillis()
                    var bytesSinceTick = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n <= 0) break
                        raf.write(buf, 0, n)
                        val done = rt.bytesDone.addAndGet(n.toLong())
                        bytesSinceTick += n
                        val now = System.currentTimeMillis()
                        if (now - lastTick >= 500L) {
                            val dt = (now - lastTick).coerceAtLeast(1L) / 1000.0
                            val speed = (bytesSinceTick / dt).toLong()
                            val remaining = if (rt.bytesTotal > 0) rt.bytesTotal - done else -1L
                            val eta = if (speed > 0 && remaining > 0) remaining / speed else 0L
                            val total = rt.bytesTotal
                            _items.update { list ->
                                list.map {
                                    if (it.id == rt.id) it.copy(
                                        bytesDone = done,
                                        bytesTotal = total,
                                        bytesPerSecond = speed,
                                        etaSeconds = eta
                                    ) else it
                                }
                            }
                            lastTick = now
                            bytesSinceTick = 0L
                        }
                    }
                }
            } finally {
                runCatching { raf.close() }
            }

            val saved = copyToPublicDownloads(context, rt.cacheFile, rt.fileName, rt.mimeType)
            if (saved == null) {
                _items.update { list ->
                    list.map {
                        if (it.id == rt.id) it.copy(
                            state = State.FAILED,
                            errorMessage = "Could not save file",
                            bytesPerSecond = 0L,
                            etaSeconds = 0L
                        ) else it
                    }
                }
                return
            }
            runCatching { rt.cacheFile.delete() }
            val done = rt.bytesDone.get()
            val total = if (rt.bytesTotal > 0) rt.bytesTotal else done
            _items.update { list ->
                list.map {
                    if (it.id == rt.id) it.copy(
                        state = State.DONE,
                        bytesDone = done,
                        bytesTotal = total,
                        bytesPerSecond = 0L,
                        etaSeconds = 0L
                    ) else it
                }
            }
        }
    }

    private fun copyToPublicDownloads(
        context: Context,
        source: File,
        displayName: String,
        mimeType: String?
    ): String? {
        val resolvedMime = mimeType ?: "application/octet-stream"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, resolvedMime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val uri = resolver.insert(collection, values) ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out, 64 * 1024) }
                } ?: return null
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri.toString()
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists() && !dir.mkdirs()) return null
                val dest = File(dir, displayName)
                source.inputStream().use { input ->
                    dest.outputStream().use { out -> input.copyTo(out, 64 * 1024) }
                }
                dest.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }
}
