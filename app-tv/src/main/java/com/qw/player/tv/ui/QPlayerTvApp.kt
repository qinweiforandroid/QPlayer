package com.qw.player.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.qw.player.tv.playback.TvPlaybackRuntime
import com.qw.player.tv.ui.live.LivePlayerScreen
import com.qw.player.tv.ui.vod.VodPlayerScreen

/** 页面栈用遥控器 BACK 逐级返回；规模不需要引入 Navigation-Compose。 */
private sealed interface TvRoute {
    data object Home : TvRoute
    data class Live(val channelId: String) : TvRoute
    data class Vod(val episodeId: String) : TvRoute
}

private val RouteSaver = Saver<TvRoute, String>(
    save = { route ->
        when (route) {
            is TvRoute.Home -> HOME
            is TvRoute.Live -> "$LIVE${route.channelId}"
            is TvRoute.Vod -> "$VOD${route.episodeId}"
        }
    },
    restore = { encoded ->
        when {
            encoded.startsWith(LIVE) -> TvRoute.Live(encoded.removePrefix(LIVE))
            encoded.startsWith(VOD) -> TvRoute.Vod(encoded.removePrefix(VOD))
            else -> TvRoute.Home
        }
    }
)

private const val HOME = "home"
private const val LIVE = "live:"
private const val VOD = "vod:"

@Composable
fun QPlayerTvApp(runtime: TvPlaybackRuntime) {
    var route by rememberSaveable(stateSaver = RouteSaver) { mutableStateOf(TvRoute.Home) }

    BackHandler(enabled = route !is TvRoute.Home) {
        route = TvRoute.Home
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = route) {
            is TvRoute.Home -> HomeScreen(
                onChannelSelected = { route = TvRoute.Live(it) },
                onEpisodeSelected = { route = TvRoute.Vod(it) }
            )

            is TvRoute.Live -> LivePlayerScreen(
                runtime = runtime,
                channelId = current.channelId,
                onChannelSwitched = { route = TvRoute.Live(it) },
                onBack = { route = TvRoute.Home }
            )

            is TvRoute.Vod -> VodPlayerScreen(
                runtime = runtime,
                episodeId = current.episodeId,
                onBack = { route = TvRoute.Home }
            )
        }
    }
}
