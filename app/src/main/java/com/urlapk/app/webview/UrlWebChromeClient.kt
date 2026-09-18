package com.urlapk.app.webview

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.content.ContextCompat

/**
 * Bridges WebView's native feature requests (camera, mic, file upload,
 * fullscreen video, progress, popup windows) into the Compose/Activity layer.
 *
 * Popup strategy: window.open() popups are BLOCKED. Sites like savefrom.net
 * fire their ad in a popup while the real download runs in the background.
 * Blocking the popup keeps the user on the original page and lets the
 * DownloadListener fire normally. This matches Chrome's built-in popup
 * blocker behaviour for the most common ad pattern.
 */
class UrlWebChromeClient(
    private val activity: Activity,
    private val onProgressChanged: (Int) -> Unit,
    private val onTitleReceived: (String?) -> Unit,
    private val onFullscreenViewRequested: (View?, WebChromeClient.CustomViewCallback?) -> Unit,
    private val launchFileChooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        super.onReceivedTitle(view, title)
        onTitleReceived(title)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        val resources = request.resources
        val needsCamera = resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        val needsMic = resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)

        val cameraGranted = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val micGranted = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val cameraOk = !needsCamera || cameraGranted
        val micOk = !needsMic || micGranted

        if (cameraOk && micOk) {
            request.grant(resources)
        } else {
            request.deny()
        }
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        return launchFileChooser(filePathCallback, fileChooserParams)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        onFullscreenViewRequested(view, callback)
    }

    override fun onHideCustomView() {
        onFullscreenViewRequested(null, null)
    }

    /**
     * Popup blocker. Returning true means "we handled the new window", but
     * since we never create a WebView, the popup is silently dropped and
     * the current page stays intact. The user's download trigger remains
     * in place, and any real file download continues via DownloadListener.
     */
    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message
    ): Boolean {
        return true
    }
}
