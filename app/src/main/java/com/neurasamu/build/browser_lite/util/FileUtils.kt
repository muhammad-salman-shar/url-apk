package com.neurasamu.build.browser_lite.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException

/**
 * Handles saving downloaded blobs/bytes to public Downloads or Pictures.
 */
object FileUtils {

    /** Builds a unique filename for a downloaded blob. */
    fun buildFileName(mimeType: String?): String {
        val ext = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "bin"
        return "download_${System.currentTimeMillis()}.$ext"
    }

    /**
     * Saves bytes to MediaStore Downloads (API 29+) or public Downloads dir (older).
     * Returns the saved Uri or null on failure.
     */
    fun saveBlobToDownloads(
        context: Context,
        bytes: ByteArray,
        mimeType: String?,
        fileName: String
    ): Uri? {
        val resolvedMime = mimeType ?: "application/octet-stream"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, bytes, resolvedMime, fileName)
            } else {
                saveLegacy(bytes, resolvedMime, fileName)
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun saveViaMediaStore(
        context: Context,
        bytes: ByteArray,
        mimeType: String,
        fileName: String
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun saveLegacy(bytes: ByteArray, mimeType: String, fileName: String): Uri? {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, fileName)
        file.outputStream().use { it.write(bytes) }
        @Suppress("DEPRECATION")
        return Uri.fromFile(file)
    }
}
