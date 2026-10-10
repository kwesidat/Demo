package com.demo.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.demo.tv.AppViewModel
import com.demo.tv.Catalog
import com.demo.tv.Load
import com.demo.xtream.Category
import com.demo.xtream.EpgEntry
import java.text.DateFormat
import java.util.Date

private enum class Tab(val label: String) { Live("Live TV"), Movies("Movies"), Series("Series"), Account("Account") }

@Composable
fun HomeScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Live) }

    LaunchedEffect(tab) {
        when (tab) {
            Tab.Live -> vm.loadLive()
            Tab.Movies -> vm.loadMovies()
            Tab.Series -> vm.loadSeries()
            Tab.Account -> {}
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEach { t ->
                FocusRow(t.label, Modifier.width(150.dp), selected = t == tab, onFocus = { tab = t }, onClick = { tab = t })
            }
        }
        Box(Modifier.fillMaxSize().padding(top = 16.dp)) {
            when (tab) {
                Tab.Live -> Section(vm.live, { vm.loadLive(true) }) { LiveBrowser(vm, it) }
                Tab.Movies -> Section(vm.movies, { vm.loadMovies(true) }) { MovieBrowser(vm, it) }
                Tab.Series -> Section(vm.series, { vm.loadSeries(true) }) { SeriesBrowser(vm, it) }
                Tab.Account -> AccountPane(vm)
            }
        }
    }
}

@Composable
private fun <T> Section(state: Load<Catalog<T>>, retry: () -> Unit, content: @Composable (Catalog<T>) -> Unit) {
    when (state) {
        Load.Idle, Load.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Loading… large playlists can take a minute", color = TextDim)
            }
        }
        is Load.Failed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.message, color = Color(0xFFFF6B6B), fontSize = 18.sp)
                TvButton("Retry", onClick = retry)
            }
        }
        is Load.Done -> content(state.data)
    }
}

/** Search box + category column shared by all three browsers. */
@Composable
private fun <T> Browser(
    catalog: Catalog<T>,
    categoryOf: (T) -> String,
    nameOf: (T) -> String,
    right: @Composable (List<T>) -> Unit,
) {
    var category by rememberSaveable { mutableStateOf<String?>(null) } // null = All
    var query by rememberSaveable { mutableStateOf("") }

    val counts = remember(catalog) { catalog.items.groupingBy(categoryOf).eachCount() }
    // A search term looks across every category; otherwise show the selected one.
    val shown = remember(catalog, category, query) {
        val q = query.trim()
        catalog.items.filter {
            (q.isNotEmpty() || category == null || categoryOf(it) == category) &&
                (q.isEmpty() || nameOf(it).contains(q, ignoreCase = true))
        }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.width(280.dp).fillMaxHeight()) {
            OutlinedTextField(
                query, { query = it }, singleLine = true, placeholder = { Text("Search all") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item("all") {
                    FocusRow("All  (${catalog.items.size})", selected = category == null, onClick = { category = null })
                }
                items(catalog.categories, key = { it.id }) { c: Category ->
                    FocusRow("${c.name}  (${counts[c.id] ?: 0})", selected = category == c.id, onClick = { category = c.id })
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) { right(shown) }
    }
}

@Composable
private fun LiveBrowser(vm: AppViewModel, catalog: Catalog<com.demo.xtream.LiveStream>) {
    Browser(catalog, { it.categoryId }, { it.name }) { channels ->
        var focusedId by remember { mutableStateOf<String?>(null) }
        var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
        LaunchedEffect(focusedId) {
            epg = emptyList()
            focusedId?.let { epg = vm.epg(it) }
        }
        Column(Modifier.fillMaxSize()) {
            if (channels.isEmpty()) Text("No channels", color = TextDim)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(channels, key = { _, c -> c.streamId }) { i, ch ->
                    FocusRow(
                        "${ch.num}  ${ch.name}",
                        onFocus = { focusedId = ch.streamId },
                        onClick = { vm.playChannel(channels, i) },
                    )
                }
            }
            NowNext(epg)
        }
    }
}

@Composable
private fun NowNext(epg: List<EpgEntry>) {
    val fmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).background(Panel, RoundedCornerShape(8.dp)).padding(12.dp),
    ) {
        if (epg.isEmpty()) {
            Text("No programme information", color = TextDim, fontSize = 15.sp)
        } else {
            epg.take(2).forEachIndexed { i, e ->
                Text(
                    "${if (i == 0) "Now" else "Next"}  ${fmt.format(Date(e.startEpochSecs * 1000))}  ${e.title}",
                    color = if (i == 0) Color.White else TextDim, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MovieBrowser(vm: AppViewModel, catalog: Catalog<com.demo.xtream.VodStream>) {
    Browser(catalog, { it.categoryId }, { it.name }) { movies ->
        PosterGrid(movies, key = { it.streamId }, image = { it.icon }, title = { it.name }) { vm.playMovie(it) }
    }
}

@Composable
private fun SeriesBrowser(vm: AppViewModel, catalog: Catalog<com.demo.xtream.SeriesItem>) {
    Browser(catalog, { it.categoryId }, { it.name }) { list ->
        PosterGrid(list, key = { it.seriesId }, image = { it.cover }, title = { it.name }) { vm.openSeries(it) }
    }
}

@Composable
private fun <T> PosterGrid(
    items: List<T>,
    key: (T) -> Any,
    image: (T) -> String,
    title: (T) -> String,
    onClick: (T) -> Unit,
) {
    if (items.isEmpty()) {
        Text("Nothing here", color = TextDim)
        return
    }
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items, key = key) { item ->
            var focused by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(8.dp)
            Column(
                Modifier
                    .onFocusChanged { focused = it.hasFocus }
                    .border(BorderStroke(3.dp, if (focused) Accent else Color.Transparent), shape)
                    .clickable { onClick(item) }
                    .padding(4.dp),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Panel, shape)) {
                    val url = image(item)
                    if (url.isNotBlank()) {
                        AsyncImage(url, title(item), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                Text(
                    title(item), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (focused) Color.White else TextDim, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AccountPane(vm: AppViewModel) {
    val a = vm.account
    val fmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Account", fontSize = 24.sp)
        if (a != null) {
            Text("User: ${a.username}   Status: ${a.status}", color = TextDim, fontSize = 18.sp)
            Text(
                "Expires: ${a.expiresEpochSecs?.let { fmt.format(Date(it * 1000)) } ?: "never / unknown"}",
                color = TextDim, fontSize = 18.sp,
            )
            Text("Connections: ${a.activeConnections} / ${a.maxConnections}", color = TextDim, fontSize = 18.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
            TvButton("Reload lists") { vm.loadLive(true); vm.loadMovies(true); vm.loadSeries(true) }
            TvButton("Sign out", onClick = vm::logout)
        }
    }
}
