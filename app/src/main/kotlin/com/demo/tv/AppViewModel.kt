package com.demo.tv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.demo.xtream.AccountInfo
import com.demo.xtream.Category
import com.demo.xtream.Credentials
import com.demo.xtream.EpgEntry
import com.demo.xtream.LiveStream
import com.demo.xtream.SeriesDetails
import com.demo.xtream.SeriesItem
import com.demo.xtream.VodStream
import com.demo.xtream.XtreamClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Loading : Load<Nothing>
    data class Failed(val message: String) : Load<Nothing>
    data class Done<T>(val data: T) : Load<T>
}

/** One catalogue section (live / movies / series): categories plus items. */
data class Catalog<T>(val categories: List<Category>, val items: List<T>)

/** One thing to play. [urls] are tried in order if playback fails (e.g. m3u8 then ts). */
data class PlayItem(val title: String, val urls: List<String>, val epgStreamId: String? = null, val subtitle: String = "")

data class PlayRequest(val items: List<PlayItem>, val startIndex: Int, val isLive: Boolean)

enum class Screen { Login, Home, SeriesDetail }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SessionStore(app)

    var screen by mutableStateOf(Screen.Login); private set
    var loginBusy by mutableStateOf(false); private set
    var loginError by mutableStateOf<String?>(null); private set
    var savedLogin by mutableStateOf<Credentials?>(store.load()); private set

    var account by mutableStateOf<AccountInfo?>(null); private set
    var live by mutableStateOf<Load<Catalog<LiveStream>>>(Load.Idle); private set
    var movies by mutableStateOf<Load<Catalog<VodStream>>>(Load.Idle); private set
    var series by mutableStateOf<Load<Catalog<SeriesItem>>>(Load.Idle); private set
    var seriesDetail by mutableStateOf<Load<SeriesDetails>>(Load.Idle); private set
    var selectedSeries by mutableStateOf<SeriesItem?>(null); private set
    var playing by mutableStateOf<PlayRequest?>(null); private set
    var userAgent by mutableStateOf(Credentials.DEFAULT_USER_AGENT); private set

    private var client: XtreamClient? = null
    private var liveExt = "ts"
    private val epgCache = HashMap<String, List<EpgEntry>>()
    private var loginJob: Job? = null

    init {
        // Auto-login with the saved account.
        savedLogin?.let { login(it, remember = false) }
    }

    // ---- Login ------------------------------------------------------------------------------

    fun login(creds: Credentials, remember: Boolean = true) {
        loginJob?.cancel()
        loginBusy = true
        loginError = null
        loginJob = viewModelScope.launch {
            try {
                val c = XtreamClient(creds)
                val info = c.authenticate()
                client = c
                account = info
                userAgent = creds.userAgent
                // HLS is far more robust than raw TS on flaky links; use it when the account allows it.
                liveExt = if ("m3u8" in info.allowedFormats) "m3u8" else "ts"
                if (remember) store.save(creds)
                savedLogin = creds
                live = Load.Idle; movies = Load.Idle; series = Load.Idle; epgCache.clear()
                screen = Screen.Home
                loadLive()
            } catch (e: Exception) {
                loginError = e.message ?: "Login failed"
                screen = Screen.Login
            } finally {
                loginBusy = false
            }
        }
    }

    fun logout() {
        loginJob?.cancel()
        store.clear()
        savedLogin = null
        client = null
        account = null
        playing = null
        live = Load.Idle; movies = Load.Idle; series = Load.Idle
        screen = Screen.Login
    }

    // ---- Catalogues -------------------------------------------------------------------------

    fun loadLive(force: Boolean = false) {
        val c = client ?: return
        if (!force && live !is Load.Idle && live !is Load.Failed) return
        live = Load.Loading
        viewModelScope.launch {
            live = fetch { coroutineScope {
                val cats = async { c.liveCategories() }
                val items = async { c.liveStreams() }
                Catalog(cats.await(), items.await())
            } }
        }
    }

    fun loadMovies(force: Boolean = false) {
        val c = client ?: return
        if (!force && movies !is Load.Idle && movies !is Load.Failed) return
        movies = Load.Loading
        viewModelScope.launch {
            movies = fetch { coroutineScope {
                val cats = async { c.vodCategories() }
                val items = async { c.vodStreams() }
                Catalog(cats.await(), items.await())
            } }
        }
    }

    fun loadSeries(force: Boolean = false) {
        val c = client ?: return
        if (!force && series !is Load.Idle && series !is Load.Failed) return
        series = Load.Loading
        viewModelScope.launch {
            series = fetch { coroutineScope {
                val cats = async { c.seriesCategories() }
                val items = async { c.series() }
                Catalog(cats.await(), items.await())
            } }
        }
    }

    private suspend fun <T> fetch(block: suspend () -> T): Load<T> =
        try { Load.Done(block()) } catch (e: Exception) { Load.Failed(e.message ?: "Failed to load") }

    // ---- Series detail ----------------------------------------------------------------------

    fun openSeries(item: SeriesItem) {
        val c = client ?: return
        selectedSeries = item
        seriesDetail = Load.Loading
        screen = Screen.SeriesDetail
        viewModelScope.launch {
            seriesDetail = fetch { c.seriesDetails(item.seriesId) ?: error("No information for this series") }
        }
    }

    fun closeSeries() {
        screen = Screen.Home
    }

    // ---- Playback ---------------------------------------------------------------------------

    fun playChannel(channels: List<LiveStream>, index: Int) {
        val c = client ?: return
        val items = channels.map { ch ->
            val urls = buildList {
                add(c.urls.live(ch.streamId, liveExt))
                if (liveExt != "ts") add(c.urls.live(ch.streamId, "ts"))
            }
            PlayItem(ch.name, urls, epgStreamId = ch.streamId)
        }
        playing = PlayRequest(items, index, isLive = true)
    }

    fun playMovie(m: VodStream) {
        val c = client ?: return
        playing = PlayRequest(listOf(PlayItem(m.name, listOf(c.urls.movie(m.streamId, m.extension)))), 0, isLive = false)
    }

    fun playEpisodes(list: List<com.demo.xtream.Episode>, index: Int) {
        val c = client ?: return
        val show = selectedSeries?.name.orEmpty()
        val items = list.map { e ->
            PlayItem(
                title = "$show  S${e.season}E${e.episode}",
                urls = listOf(c.urls.episode(e.id, e.extension)),
                subtitle = e.title,
            )
        }
        playing = PlayRequest(items, index, isLive = false)
    }

    fun stopPlaying() { playing = null }

    suspend fun epg(streamId: String): List<EpgEntry> {
        epgCache[streamId]?.let { if (it.isNotEmpty() && it.last().endEpochSecs > System.currentTimeMillis() / 1000) return it }
        val result = try { client?.shortEpg(streamId, 3).orEmpty() } catch (_: Exception) { emptyList() }
        epgCache[streamId] = result
        return result
    }

    fun resumePosition(key: String) = store.resumeMs(key)
    fun saveResumePosition(key: String, ms: Long) = store.saveResumeMs(key, ms)
}
