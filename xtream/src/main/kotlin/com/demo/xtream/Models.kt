package com.demo.xtream

/** Login details for one Xtream Codes provider. */
data class Credentials(
    val baseUrl: String,
    val username: String,
    val password: String,
    val userAgent: String = DEFAULT_USER_AGENT,
) {
    companion object {
        const val DEFAULT_USER_AGENT = "VLC/3.0.18 LibVLC/3.0.18"
    }
}

data class Category(val id: String, val name: String)

data class LiveStream(
    val streamId: String,
    val num: Int,
    val name: String,
    val icon: String,
    val epgChannelId: String,
    val categoryId: String,
    val hasArchive: Boolean,
)

data class VodStream(
    val streamId: String,
    val name: String,
    val icon: String,
    val categoryId: String,
    val extension: String,
    val rating: String,
    val added: String,
)

data class VodDetails(
    val plot: String,
    val cast: String,
    val director: String,
    val genre: String,
    val releaseDate: String,
    val rating: String,
    val durationSecs: Int,
    val backdrop: String,
)

data class SeriesItem(
    val seriesId: String,
    val name: String,
    val cover: String,
    val categoryId: String,
    val plot: String,
    val genre: String,
    val rating: String,
    val year: String,
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val episode: Int,
    val extension: String,
    val plot: String,
    val durationSecs: Int,
    val image: String,
)

data class Season(val number: Int, val episodes: List<Episode>)

data class SeriesDetails(
    val name: String,
    val plot: String,
    val cover: String,
    val seasons: List<Season>,
)

data class EpgEntry(
    val title: String,
    val description: String,
    val startEpochSecs: Long,
    val endEpochSecs: Long,
)

data class AccountInfo(
    val username: String,
    val status: String,
    val expiresEpochSecs: Long?,
    val activeConnections: Int,
    val maxConnections: Int,
    val allowedFormats: List<String>,
)

class XtreamException(message: String, cause: Throwable? = null) : Exception(message, cause)
