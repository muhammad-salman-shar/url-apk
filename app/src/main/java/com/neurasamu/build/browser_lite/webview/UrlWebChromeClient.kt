package com.neurasamu.build.browser_lite.webview

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
 * Bridges WebView's native feature requests into the Compose/Activity layer.
 *
 * Popup strategy (updated):
 *   Instead of BLOCKING window.open() popups, we create a HEADLESS WebView
 *   that loads the popup URL in the background. The popup WebView is never
 *   attached to the view hierarchy and is destroyed after use.
 *
 *   Why: sites like savefrom.net fire their real download URL inside a
 *   window.open() popup, while the main window is redirected to an ad.
 *   Blocking the popup loses the download URL; running it headless lets
 *   the DownloadListener fire on the popup's WebView.
 */
class UrlWebChromeClient(
    private val activity: Activity,
    private val onProgressChanged: (Int) -> Unit,
    private val onTitleReceived: (String?) -> Unit,
    private val onFullscreenViewRequested: (View?, WebChromeClient.CustomViewCallback?) -> Unit,
    private val launchFileChooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean,
    private val onPopupCreated: (WebView) -> Unit
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

        if ((!needsCamera || cameraGranted) && (!needsMic || micGranted)) {
            request.grant(resources)
        } else {
            request.deny()
        }
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean = launchFileChooser(filePathCallback, fileChooserParams)

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        onFullscreenViewRequested(view, callback)
    }

    override fun onHideCustomView() {
        onFullscreenViewRequested(null, null)
    }

    /**
     * Handle window.open() by creating a HEADLESS WebView.
     *
     * The new WebView is NOT attached to any parent, so the user never sees
     * it. It receives the same WebViewClient configuration (so
     * shouldOverrideUrlLoading and onPageFinished work) and its own
     * DownloadListener, so any file the popup tries to download will be
     * routed through our DownloadManager path.
     *
     * After the popup navigates away from its initial URL or after a short
     * grace period, MainScreen destroys it to free memory.
     */
    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message
    ): Boolean {
        val popup = WebView(view.context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = view.settings.userAgentString
        }
        popup.webViewClient = view.webViewClient
        popup.webChromeClient = this
        onPopupCreated(popup)

        val transport = resultMsg.obj as? WebView.WebViewTransport
        if (transport != null) {
            transport.webView = popup
            resultMsg.sendToTarget()
        }
        return true
    }
}
