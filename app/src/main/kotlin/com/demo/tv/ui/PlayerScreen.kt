package com.demo.tv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.demo.tv.AppViewModel
import com.demo.tv.PlayRequest
import com.demo.xtream.EpgEntry
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private const val OVERLAY_MS = 4_000L
private const val SEEK_MS = 10_000L

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: AppViewModel, request: PlayRequest) {
    val context = LocalContext.current
    val view = LocalView.current

    var index by remember { mutableIntStateOf(request.startIndex.coerceIn(0, request.items.lastIndex)) }
    var urlTry by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(1) }
    var overlayVisible by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    val focus = remember { FocusRequester() }

    val item = request.items[index]

    val player = remember {
        val http = OkHttpDataSource.Factory(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build(),
        ).setUserAgent(vm.userAgent)
        // Slightly larger live buffer rides out jittery IPTV links on low-end sticks.
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 2_500, 5_000)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
            .setLoadControl(load)
            .build()
    }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED && !request.isLive && index < request.items.lastIndex) {
                    index++; urlTry = 0 // auto-play next episode
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                if (urlTry + 1 < request.items[index].urls.size) urlTry++ else error = "Playback failed: ${e.errorCodeName}"
            }
        }
        player.addListener(listener)
        onDispose {
            view.keepScreenOn = false
            if (!request.isLive) {
                // Remember where we stopped (unless we're basically at the end).
                val dur = player.duration
                val pos = player.currentPosition
                val key = request.items[index].urls.first()
                vm.saveResumePosition(key, if (dur > 0 && pos > dur - 30_000) 0 else pos)
            }
            player.removeListener(listener)
            player.release()
        }
    }

    // Load whichever stream is current (also re-runs for the fallback URL after an error).
    LaunchedEffect(index, urlTry) {
        error = null
        buffering = true
        paused = false
        player.setMediaItem(MediaItem.fromUri(item.urls[urlTry]))
        if (!request.isLive) {
            val resume = vm.resumePosition(item.urls.first())
            if (resume > 0) player.seekTo(resume)
        }
        player.prepare()
        player.playWhenReady = true
        overlayTick++
    }

    // Now/next for live channels.
    LaunchedEffect(index) {
        epg = emptyList()
        item.epgStreamId?.let { epg = vm.epg(it) }
    }

    // Auto-hide the info overlay.
    LaunchedEffect(overlayTick) {
        overlayVisible = true
        delay(OVERLAY_MS)
        overlayVisible = false
    }

    // Progress ticker for VOD.
    LaunchedEffect(overlayVisible, request.isLive) {
        while (overlayVisible && !request.isLive) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            delay(500)
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler { vm.stopPlaying() }

    fun switchBy(delta: Int) {
        val next = index + delta
        if (next in request.items.indices) { index = next; urlTry = 0 }
        overlayTick++
    }

    fun seekBy(deltaMs: Long) {
        if (request.isLive) return
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0, player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
        overlayTick++
    }

    fun togglePause() {
        if (player.isPlaying) { player.pause(); paused = true } else { player.play(); paused = false }
        overlayTick++
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_UP ->
                        if (request.isLive) switchBy(-1) else overlayTick++
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN ->
                        if (request.isLive) switchBy(+1) else overlayTick++
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_MEDIA_REWIND ->
                        if (request.isLive) overlayTick++ else seekBy(-SEEK_MS)
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD ->
                        if (request.isLive) overlayTick++ else seekBy(SEEK_MS)
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_SPACE ->
                        if (request.isLive) overlayTick++ else togglePause()
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); paused = false; overlayTick++ }
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); paused = true; overlayTick++ }
                    AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> switchBy(+1)
                    AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> switchBy(-1)
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        error?.let {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(it, color = Color(0xFFFF6B6B), fontSize = 22.sp)
                Text("Press Back to return", color = TextDim, fontSize = 16.sp)
            }
        }

        if (overlayVisible || paused) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .padding(horizontal = 40.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    (if (request.isLive) "${index + 1}/${request.items.size}  " else "") + item.title,
                    fontSize = 26.sp, color = Color.White,
                )
                if (item.subtitle.isNotBlank()) Text(item.subtitle, fontSize = 18.sp, color = TextDim)
                if (request.isLive) {
                    epg.firstOrNull()?.let { Text("Now: ${it.title}", fontSize = 18.sp, color = Color.White) }
                    epg.getOrNull(1)?.let { Text("Next: ${it.title}", fontSize = 16.sp, color = TextDim) }
                    Text("Up / Down: change channel   Back: exit", fontSize = 14.sp, color = TextDim)
                } else {
                    LinearProgressIndicator(
                        progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    Text("${fmt(position)} / ${fmt(duration)}${if (paused) "   ⏸ Paused" else ""}", fontSize = 16.sp, color = TextDim)
                    Text("OK: pause   Left / Right: seek 10s   Back: exit", fontSize = 14.sp, color = TextDim)
                }
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
