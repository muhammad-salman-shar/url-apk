package com.urlapk.app

import android.os.Bundle
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.urlapk.app.ui.MainScreen
import com.urlapk.app.ui.theme.UrlApkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UrlApkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Persist cookies to disk so login sessions survive app close.
        // flush() performs I/O, so run it off the UI thread.
        Thread { CookieManager.getInstance().flush() }.start()
    }
}
