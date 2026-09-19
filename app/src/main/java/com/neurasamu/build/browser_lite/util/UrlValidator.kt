package com.neurasamu.build.browser_lite.util

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


    /**
     * Chrome-like input resolver.
     *
     * If the input looks like a URL (has a scheme, a TLD, or a "localhost"
     * style host), it is returned as-is after normalisation. Otherwise the
     * input is treated as a search query and routed to Google.
     */
    fun resolveInput(input: String?): String? {
        val trimmed = input?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        if (schemeRegex.containsMatchIn(trimmed)) {
            return if (Patterns.WEB_URL.matcher(trimmed).matches()) trimmed else searchUrl(trimmed)
        }

        val looksLikeDomain = !trimmed.contains(' ') &&
            trimmed.contains('.') &&
            trimmed.substringAfterLast('.').length in 2..24 &&
            trimmed.substringAfterLast('.').all { it.isLetter() }

        val candidate = if (looksLikeDomain) "https://$trimmed" else null
        if (candidate != null && Patterns.WEB_URL.matcher(candidate).matches()) {
            return candidate
        }

        return searchUrl(trimmed)
    }

    private fun searchUrl(query: String): String {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return "https://www.google.com/search?q=$encoded"
    }
}