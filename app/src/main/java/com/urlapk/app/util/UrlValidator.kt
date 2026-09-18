package com.urlapk.app.util

import android.util.Patterns

/**
 * Normalizes and validates user-entered URLs.
 * If no scheme is provided, prepends https://
 */
object UrlValidator {

    private val schemeRegex = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    /** Returns a valid http(s) URL, or null if the input is not usable. */
    fun normalize(input: String?): String? {
        val trimmed = input?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val withScheme = if (schemeRegex.containsMatchIn(trimmed)) {
            trimmed
        } else {
            "https://$trimmed"
        }

        return if (Patterns.WEB_URL.matcher(withScheme).matches()) withScheme else null
    }

    /** True if the given URL is a Google search result page or similar. */
    fun isSearchUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return lower.contains("google.") && (lower.contains("/search") || lower.contains("?q="))
    }
}
