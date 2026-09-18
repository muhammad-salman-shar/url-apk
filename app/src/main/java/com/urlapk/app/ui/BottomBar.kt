package com.urlapk.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.urlapk.app.ui.theme.UrlApkTheme

/**
 * Bottom navigation bar. Pushes content above (does NOT overlap WebView).
 * Height is kept small (52dp) so the site gets maximum space.
 */
@Composable
fun BottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    isDesktopMode: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleDesktop: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .height(52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BarIcon(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            enabled = canGoBack,
            onClick = onBack
        )
        BarIcon(
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Forward",
            enabled = canGoForward,
            onClick = onForward
        )
        BarIcon(
            icon = Icons.Filled.Refresh,
            contentDescription = "Reload",
            enabled = true,
            onClick = onReload
        )
        BarIcon(
            icon = Icons.Filled.ZoomOut,
            contentDescription = "Zoom out",
            enabled = true,
            onClick = onZoomOut
        )
        BarIcon(
            icon = Icons.Filled.ZoomIn,
            contentDescription = "Zoom in",
            enabled = true,
            onClick = onZoomIn
        )
        BarIcon(
            icon = if (isDesktopMode) Icons.Filled.PhoneAndroid else Icons.Filled.DesktopWindows,
            contentDescription = if (isDesktopMode) "Mobile site" else "Desktop site",
            enabled = true,
            onClick = onToggleDesktop
        )
        BarIcon(
            icon = Icons.Filled.MoreVert,
            contentDescription = "More",
            enabled = true,
            onClick = onMore
        )
    }
}

@Composable
private fun BarIcon(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            }
        )
    }
}

@Preview
@Composable
private fun BottomBarPreview() {
    UrlApkTheme {
        BottomBar(
            canGoBack = true,
            canGoForward = false,
            isDesktopMode = false,
            onBack = {},
            onForward = {},
            onReload = {},
            onZoomIn = {},
            onZoomOut = {},
            onToggleDesktop = {},
            onMore = {}
        )
    }
}
