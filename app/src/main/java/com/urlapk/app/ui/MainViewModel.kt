package com.urlapk.app.ui

import androidx.lifecycle.ViewModel
import com.urlapk.app.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Immutable UI state for the browser screen.
 *
 * IMPORTANT: this ViewModel never holds a WebView reference — the WebView
 * lives in the Compose layer and actions are passed through callbacks so
 * we don't leak Activity/Context.
 */
data class BrowserUiState(
    val urlInput: String = Constants.DEFAULT_HOME_URL,
    val currentUrl: String = Constants.DEFAULT_HOME_URL,
    val pageTitle: String = "",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isDesktopMode: Boolean = false,
    val zoomPercent: Int = 100,
    val errorMessage: String? = null,
    val showUrlInput: Boolean = false
)

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    fun onUrlInputChanged(value: String) {
        _state.update { it.copy(urlInput = value) }
    }

    fun onUrlSubmitted() {
        _state.update { it.copy(showUrlInput = false) }
    }

    fun onShowUrlInput(show: Boolean) {
        _state.update { it.copy(showUrlInput = show) }
    }

    fun onPageStarted(url: String?) {
        _state.update {
            it.copy(
                isLoading = true,
                progress = 0,
                errorMessage = null,
                currentUrl = url ?: it.currentUrl,
                urlInput = url ?: it.urlInput
            )
        }
    }

    fun onPageFinished(url: String?, canBack: Boolean, canForward: Boolean) {
        _state.update {
            it.copy(
                isLoading = false,
                progress = 100,
                currentUrl = url ?: it.currentUrl,
                urlInput = url ?: it.urlInput,
                canGoBack = canBack,
                canGoForward = canForward
            )
        }
    }

    fun onProgress(progress: Int) {
        _state.update { it.copy(progress = progress, isLoading = progress < 100) }
    }

    fun onTitle(title: String?) {
        _state.update { it.copy(pageTitle = title.orEmpty()) }
    }

    fun onNavigationStateChanged(canBack: Boolean, canForward: Boolean) {
        _state.update { it.copy(canGoBack = canBack, canGoForward = canForward) }
    }

    fun onError(message: String?) {
        _state.update { it.copy(isLoading = false, errorMessage = message) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun toggleDesktopMode(): Boolean {
        val next = !_state.value.isDesktopMode
        _state.update { it.copy(isDesktopMode = next) }
        return next
    }

    fun setZoom(percent: Int) {
        val clamped = percent.coerceIn(Constants.MIN_ZOOM, Constants.MAX_ZOOM)
        _state.update { it.copy(zoomPercent = clamped) }
    }

    fun zoomIn() = setZoom(_state.value.zoomPercent + Constants.ZOOM_STEP)

    fun zoomOut() = setZoom(_state.value.zoomPercent - Constants.ZOOM_STEP)

    fun resetZoom() = setZoom(100)
}
