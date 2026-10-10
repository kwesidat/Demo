package com.demo.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demo.tv.AppViewModel
import com.demo.tv.Load

@Composable
fun SeriesScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp)) {
        Text(vm.selectedSeries?.name.orEmpty(), fontSize = 28.sp)
        when (val st = vm.seriesDetail) {
            Load.Idle, Load.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            is Load.Failed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(st.message, color = Color(0xFFFF6B6B), fontSize = 18.sp)
                    TvButton("Back", onClick = vm::closeSeries)
                }
            }
            is Load.Done -> {
                val d = st.data
                if (d.plot.isNotBlank()) Text(d.plot, color = TextDim, fontSize = 15.sp, maxLines = 3, modifier = Modifier.padding(vertical = 8.dp))
                if (d.seasons.isEmpty()) {
                    Text("No episodes available", color = TextDim)
                    return@Column
                }
                var seasonIdx by remember { mutableIntStateOf(0) }
                val season = d.seasons[seasonIdx.coerceIn(0, d.seasons.lastIndex)]
                Row(Modifier.fillMaxSize().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    LazyColumn(Modifier.width(220.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(d.seasons) { i, s ->
                            FocusRow("Season ${s.number}", selected = i == seasonIdx, onFocus = { seasonIdx = i }, onClick = { seasonIdx = i })
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(season.episodes, key = { _, e -> e.id }) { i, e ->
                            FocusRow(
                                "${e.episode}.  ${e.title}",
                                secondary = e.durationSecs.takeIf { it > 0 }?.let { "${it / 60} min" },
                                onClick = { vm.playEpisodes(season.episodes, i) },
                            )
                        }
                    }
                }
            }
        }
    }
}
