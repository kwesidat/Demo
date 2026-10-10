package com.demo.xtream

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Builds playable URLs. Path segments are percent-encoded properly. */
class StreamUrls(private val creds: Credentials) {
    private val root = UrlNormalizer.serverRoot(creds.baseUrl)

    private fun build(vararg segments: String): String {
        val b: HttpUrl.Builder = root.toHttpUrl().newBuilder()
        segments.forEach { b.addPathSegment(it) }
        return b.build().toString()
    }

    /** [extension] "m3u8" for HLS (preferred when the account allows it) or "ts". */
    fun live(streamId: String, extension: String = "ts") =
        build("live", creds.username, creds.password, "$streamId.$extension")

    fun movie(streamId: String, extension: String) =
        build("movie", creds.username, creds.password, "$streamId.${extension.ifBlank { "mp4" }}")

    fun episode(episodeId: String, extension: String) =
        build("series", creds.username, creds.password, "$episodeId.${extension.ifBlank { "mp4" }}")

    /** Catch-up: http://host/timeshift/user/pass/<minutes>/<YYYY-MM-DD:HH-MM>/<id>.ts (UTC). */
    fun timeshift(streamId: String, durationMinutes: Int, start: Instant, extension: String = "ts"): String {
        val stamp = TIMESHIFT_FORMAT.format(start.atOffset(ZoneOffset.UTC))
        return build("timeshift", creds.username, creds.password, durationMinutes.toString(), stamp, "$streamId.$extension")
    }

    private companion object {
        val TIMESHIFT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")
    }
}
