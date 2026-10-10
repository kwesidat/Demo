package com.demo.xtream

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlNormalizer {
    /**
     * Users paste all sorts of things: "provider.tv:8080", "http://x.tv/c/",
     * "http://x.tv/player_api.php?username=..&password=..", etc. Reduce to the
     * server root ("http://x.tv:8080") that player_api.php hangs off.
     */
    fun serverRoot(input: String): String {
        var s = input.trim()
        if (s.isEmpty()) throw XtreamException("Server URL is empty")
        if (!s.contains("://")) s = "http://$s"
        val url = s.toHttpUrlOrNull() ?: throw XtreamException("Invalid server URL: $input")
        val defaultPort = if (url.scheme == "https") 443 else 80
        val port = if (url.port != defaultPort) ":${url.port}" else ""
        return "${url.scheme}://${url.host}$port"
    }

    /**
     * Pulls username/password out of a pasted get.php / player_api.php link,
     * or returns null when the URL carries no credentials.
     */
    fun credentialsFromLink(input: String): Pair<String, String>? {
        val s = input.trim().let { if (it.contains("://")) it else "http://$it" }
        val url = s.toHttpUrlOrNull() ?: return null
        val user = url.queryParameter("username") ?: return null
        val pass = url.queryParameter("password") ?: return null
        return user to pass
    }
}
