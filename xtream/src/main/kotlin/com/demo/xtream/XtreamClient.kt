package com.demo.xtream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Xtream Codes `player_api.php` client.
 * Port of the logic in ynotv's `xtream-client.ts`.
 */
class XtreamClient(
    private val creds: Credentials,
    private val http: OkHttpClient = defaultHttp(),
) {
    private val root = UrlNormalizer.serverRoot(creds.baseUrl)
    val urls = StreamUrls(creds)

    private fun apiUrl(action: String? = null, vararg params: Pair<String, String>): String {
        val b = root.toHttpUrl().newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", creds.username)
            .addQueryParameter("password", creds.password)
        if (action != null) b.addQueryParameter("action", action)
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build().toString()
    }

    private suspend fun getJson(url: String): JsonElement = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", creds.userAgent).build()
        try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw XtreamException("Server returned HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) throw XtreamException("Server returned an empty response")
                try {
                    lenientJson.parseToJsonElement(body)
                } catch (e: Exception) {
                    throw XtreamException("Server did not return valid JSON (is this an Xtream Codes URL?)", e)
                }
            }
        } catch (e: IOException) {
            throw XtreamException("Cannot reach server: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    /** Some panels answer with {"user_info":{"auth":0}} or {"error":..} instead of an HTTP error. */
    private fun checkAuth(json: JsonElement) {
        val o = json.obj() ?: return
        if (o["user_info"].obj()?.get("auth").int(1) == 0) throw XtreamException("Invalid username or password")
    }

    // ---- Account ----------------------------------------------------------------------------

    suspend fun authenticate(): AccountInfo {
        val json = getJson(apiUrl())
        val o = json.obj() ?: throw XtreamException("Unexpected response from server")
        val user = o["user_info"].obj() ?: throw XtreamException("Server did not return account info")
        if (user["auth"].int(0) != 1) throw XtreamException("Invalid username or password")
        val formats = user["allowed_output_formats"].items().map { it.str().lowercase() }
        return AccountInfo(
            username = user.s("username"),
            status = user.s("status"),
            expiresEpochSecs = user["exp_date"].long(),
            activeConnections = user["active_cons"].int(),
            maxConnections = user["max_connections"].int(),
            allowedFormats = formats,
        )
    }

    // ---- Live -------------------------------------------------------------------------------

    suspend fun liveCategories(): List<Category> = categories("get_live_categories")

    suspend fun liveStreams(categoryId: String? = null): List<LiveStream> {
        val json = getJson(listUrl("get_live_streams", categoryId))
        checkAuth(json)
        return json.items().mapNotNull { it.obj() }.mapIndexed { i, o ->
            LiveStream(
                streamId = o.s("stream_id"),
                num = o["num"].int(i + 1),
                name = o.s("name"),
                icon = o.s("stream_icon"),
                epgChannelId = o.s("epg_channel_id"),
                categoryId = o.s("category_id"),
                hasArchive = o["tv_archive"].int() == 1,
            )
        }.filter { it.streamId.isNotBlank() }
    }

    /** Short EPG (next few programmes). Titles/descriptions arrive base64 encoded. */
    suspend fun shortEpg(streamId: String, limit: Int = 4): List<EpgEntry> {
        val json = getJson(apiUrl("get_short_epg", "stream_id" to streamId, "limit" to limit.toString()))
        return json.obj()?.get("epg_listings").items().mapNotNull { it.obj() }.map { o ->
            EpgEntry(
                title = decodeB64(o.s("title")),
                description = decodeB64(o.s("description")),
                startEpochSecs = o["start_timestamp"].long() ?: 0L,
                endEpochSecs = o["stop_timestamp"].long() ?: 0L,
            )
        }
    }

    // ---- Movies -----------------------------------------------------------------------------

    suspend fun vodCategories(): List<Category> = categories("get_vod_categories")

    suspend fun vodStreams(categoryId: String? = null): List<VodStream> {
        val json = getJson(listUrl("get_vod_streams", categoryId))
        checkAuth(json)
        return json.items().mapNotNull { it.obj() }.map { o ->
            VodStream(
                streamId = o.s("stream_id"),
                name = o.s("name"),
                icon = o.s("stream_icon"),
                categoryId = o.s("category_id"),
                extension = o.s("container_extension").ifBlank { "mp4" },
                rating = o.s("rating"),
                added = o.s("added"),
            )
        }.filter { it.streamId.isNotBlank() }
    }

    suspend fun vodDetails(streamId: String): VodDetails? {
        val o = getJson(apiUrl("get_vod_info", "vod_id" to streamId)).obj()?.get("info").obj() ?: return null
        return VodDetails(
            plot = o.s("plot").ifBlank { o.s("description") },
            cast = o.s("cast").ifBlank { o.s("actors") },
            director = o.s("director"),
            genre = o.s("genre"),
            releaseDate = o.s("releasedate").ifBlank { o.s("release_date") },
            rating = o.s("rating"),
            durationSecs = o["duration_secs"].int(),
            backdrop = o["backdrop_path"].items().firstOrNull().str().ifBlank { o.s("backdrop_path") },
        )
    }

    // ---- Series -----------------------------------------------------------------------------

    suspend fun seriesCategories(): List<Category> = categories("get_series_categories")

    suspend fun series(categoryId: String? = null): List<SeriesItem> {
        val json = getJson(listUrl("get_series", categoryId))
        checkAuth(json)
        return json.items().mapNotNull { it.obj() }.map { o ->
            SeriesItem(
                seriesId = o.s("series_id"),
                name = o.s("name"),
                cover = o.s("cover"),
                categoryId = o.s("category_id"),
                plot = o.s("plot"),
                genre = o.s("genre"),
                rating = o.s("rating"),
                year = o.s("year").ifBlank { o.s("releaseDate").take(4) },
            )
        }.filter { it.seriesId.isNotBlank() }
    }

    suspend fun seriesDetails(seriesId: String): SeriesDetails? {
        val o = getJson(apiUrl("get_series_info", "series_id" to seriesId)).obj() ?: return null
        val info = o["info"].obj()
        // "episodes" is {"1":[...], "2":[...]} normally, but some panels send a bare array of seasons.
        val episodesNode = o["episodes"]
        val groups: List<Pair<Int, List<JsonElement>>> = when (episodesNode) {
            is JsonObject -> episodesNode.entries.map { (k, v) -> (k.toIntOrNull() ?: 0) to v.items() }
            is JsonArray -> episodesNode.mapIndexed { i, v -> (i + 1) to v.items() }
            else -> emptyList()
        }
        val seasons = groups.map { (seasonNum, eps) ->
            Season(
                number = seasonNum,
                episodes = eps.mapNotNull { it.obj() }.map { e ->
                    val ei = e["info"].obj()
                    Episode(
                        id = e.s("id"),
                        title = e.s("title").ifBlank { "Episode ${e.s("episode_num")}" },
                        season = e["season"].int(seasonNum),
                        episode = e["episode_num"].int(),
                        extension = e.s("container_extension").ifBlank { "mp4" },
                        plot = ei?.s("plot").orEmpty(),
                        durationSecs = ei?.get("duration_secs").int(),
                        image = ei?.s("movie_image").orEmpty(),
                    )
                }.filter { it.id.isNotBlank() }.sortedBy { it.episode },
            )
        }.filter { it.episodes.isNotEmpty() }.sortedBy { it.number }
        return SeriesDetails(
            name = info?.s("name").orEmpty(),
            plot = info?.s("plot").orEmpty(),
            cover = info?.s("cover").orEmpty(),
            seasons = seasons,
        )
    }

    // ---- helpers ----------------------------------------------------------------------------

    private suspend fun categories(action: String): List<Category> {
        val json = getJson(apiUrl(action))
        checkAuth(json)
        return json.items().mapNotNull { it.obj() }
            .map { Category(it.s("category_id"), it.s("category_name")) }
            .filter { it.id.isNotBlank() }
    }

    private fun listUrl(action: String, categoryId: String?) =
        if (categoryId.isNullOrBlank()) apiUrl(action) else apiUrl(action, "category_id" to categoryId)

    private fun decodeB64(s: String): String =
        if (s.isBlank()) "" else try {
            String(Base64.getMimeDecoder().decode(s), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            s // some panels already send plain text
        }

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // large providers send tens of MB of channel JSON
            .followRedirects(true)
            .build()
    }
}
