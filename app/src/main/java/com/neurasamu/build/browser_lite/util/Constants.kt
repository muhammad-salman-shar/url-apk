package com.neurasamu.build.browser_lite.util

/**
 * NOTE: The real HOME URL lives in res/values/strings.xml as
 * <string name="home_url">...</string>. Change it there.
 *
 * This constant is only a code-side fallback used by the ViewModel
 * before any Context is available. Keep it in sync with strings.xml.
 */
object Constants {
    const val DEFAULT_HOME_URL = "https://www.google.com"
    const val DESKTOP_UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/122.0.0.0 Safari/537.36"
    const val ZOOM_STEP = 25
    const val MIN_ZOOM = 50
    const val MAX_ZOOM = 200
}
