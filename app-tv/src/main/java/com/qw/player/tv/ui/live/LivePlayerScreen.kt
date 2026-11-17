package com.qw.player.tv.ui.live

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.qw.player.core.engine.PlaybackState
import com.qw.player.core.media.MediaType
import com.qw.player.core.media.PlaySource
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.media.PlayerMediaMetadata
import com.qw.player.tv.data.TvChannel
import com.qw.player.tv.data.TvChannelRepository
import com.qw.player.tv.playback.TvPlaybackRuntime
import com.qw.player.tv.ui.playback.AutoHideControls
import com.qw.player.tv.ui.playback.LiveBadge
import com.qw.player.tv.ui.playback.OverscanHorizontal
import com.qw.player.tv.ui.playback.OverscanVertical
import com.qw.player.tv.ui.playback.RequestFocusWhenReady
import com.qw.player.tv.ui.playback.SheetOption
import com.qw.player.tv.ui.playback.SheetSectionTitle
import com.qw.player.tv.ui.playback.TvBufferingIndicator
import com.qw.player.tv.ui.playback.TvPlayerBanner
import com.qw.player.tv.ui.playback.TvPlayerIconButton
import com.qw.player.tv.ui.playback.TvPlayerNoticeOverlay
import com.qw.player.tv.ui.playback.TvPlayerTextButton
import com.qw.player.tv.ui.playback.TvSideSheet
import com.qw.player.tv.ui.playback.rememberControlVisibility
import com.qw.player.tv.ui.rememberPlayerState
import kotlinx.coroutines.delay

private enum class LiveSheet { None, Lines, Channels }

/** 断流重连上限；到顶之后展示失败提示，由用户决定重试或换台。 */
private const val MAX_RECONNECT_ATTEMPTS = 4

/**
 * 直播播放页。
 *
 * 直播没有进度条，控制层只需要频道信息、播放/暂停和换线路/换台入口。
 * 断流处理是线上直播的关键路径，这里按「换线路 → 退避重连 → 失败提示」三级降级，
 * 任何一级成功都会把重连状态清掉。
 */
@OptIn(UnstableApi::class)
@Composable
fun LivePlayerScreen(
    runtime: TvPlaybackRuntime,
    channelId: String,
    onChannelSwitched: (String) -> Unit,
    onBack: () -> Unit
) {
    val channel = remember(channelId) {
        TvChannelRepository.findById(channelId) ?: TvChannelRepository.loadChannels().first()
    }
    var lineIndex by remember(channelId) { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf(LiveSheet.None) }

    val holder = rememberPlayerState(runtime)
    val snapshot = holder.snapshot
    val controls = rememberControlVisibility(initiallyVisible = true)
    val rootFocus = remember { FocusRequester() }
    val firstControlFocus = remember { FocusRequester() }

    var reconnectAttempt by remember(channelId) { mutableIntStateOf(0) }
    var reconnecting by remember(channelId) { mutableStateOf(false) }

    /** 每次「播放/重连」自增；单线路频道重连时 lineIndex 不变，靠它触发重新起播。 */
    var playGeneration by remember(channelId) { mutableIntStateOf(0) }

    val isPlaying = snapshot.isPlaying || snapshot.state == PlaybackState.BUFFERING

    KeepScreenOn()

    LaunchedEffect(channel.id, lineIndex, playGeneration) {
        val line = channel.lines.getOrNull(lineIndex) ?: return@LaunchedEffect
        runtime.setMediaList(listOf(channel.toPlayableMedia(line)), 0)
        runtime.play(0)
    }

    // 成功出流即视为重连完成；断流（ERROR）后先换线路，线路用尽再退避重连。
    LaunchedEffect(snapshot.state) {
        when (snapshot.state) {
            PlaybackState.PLAYING -> {
                if (reconnecting) {
                    reconnecting = false
                    reconnectAttempt = 0
                }
            }

            PlaybackState.ERROR -> {
                val nextLine = lineIndex + 1
                if (nextLine in channel.lines.indices) {
                    lineIndex = nextLine
                } else if (reconnectAttempt < MAX_RECONNECT_ATTEMPTS) {
                    reconnecting = true
                    reconnectAttempt++
                    controls.show()
                    delay(RECONNECT_BASE_DELAY_MS shl (reconnectAttempt - 1))
                    if (channel.lines.size > 1) {
                        // 每次重连轮换线路，避免死磕同一条坏线路。
                        lineIndex = (lineIndex + 1) % channel.lines.size
                    } else {
                        playGeneration++
                    }
                } else {
                    reconnecting = false
                    controls.show()
                }
            }

            else -> Unit
        }
    }

    // 控制层出现时把焦点交给其中的主按钮，遥控器方向键才有落脚点。
    if (controls.visible && sheet == LiveSheet.None) {
        RequestFocusWhenReady(firstControlFocus, key = controls.visible)
    }

    AutoHideControls(visibility = controls, isPlaying = isPlaying, autoHideDelayMs = 6_000L)

    // 离开直播页同样要停止播放，否则返回首页后仍在后台出流。
    DisposableEffect(Unit) {
        onDispose { runtime.stop() }
    }

    LaunchedEffect(controls.visible, sheet) {
        if (!controls.visible && sheet == LiveSheet.None) {
            runCatching { rootFocus.requestFocus() }
        }
    }

    BackHandler(enabled = sheet != LiveSheet.None) {
        sheet = LiveSheet.None
        controls.show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when {
                    !controls.visible && event.key in setOf(
                        Key.DirectionCenter,
                        Key.Enter,
                        Key.DirectionUp,
                        Key.DirectionDown
                    ) -> {
                        controls.show()
                        true
                    }

                    event.key == Key.MediaPlayPause -> {
                        if (snapshot.isPlaying) runtime.pause() else runtime.resume()
                        controls.keepAlive()
                        true
                    }

                    event.key in setOf(
                        Key.DirectionLeft,
                        Key.DirectionRight,
                        Key.DirectionUp,
                        Key.DirectionDown,
                        Key.DirectionCenter,
                        Key.Enter
                    ) -> {
                        controls.keepAlive()
                        false
                    }

                    else -> false
                }
            }
    ) {
        PlayerSurface(
            player = runtime.getExoPlayer(),
            modifier = Modifier.fillMaxSize()
        )

        if (sheet == LiveSheet.None && snapshot.isBuffering && !reconnecting) {
            TvBufferingIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (reconnecting && snapshot.state != PlaybackState.PLAYING) {
            TvPlayerBanner(
                text = "信号中断，正在重连（$reconnectAttempt/$MAX_RECONNECT_ATTEMPTS）",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
            )
        }

        AnimatedVisibility(
            visible = controls.visible && sheet == LiveSheet.None,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ControlsLayer(
                channel = channel,
                lineLabel = channel.lines.getOrNull(lineIndex)?.label,
                isPlaying = snapshot.isPlaying,
                onTogglePlay = {
                    if (snapshot.isPlaying) runtime.pause() else runtime.resume()
                    controls.keepAlive()
                },
                onBack = onBack,
                onOpenLines = {
                    sheet = LiveSheet.Lines
                    controls.keepAlive()
                },
                onOpenChannels = {
                    sheet = LiveSheet.Channels
                    controls.keepAlive()
                },
                onInteraction = controls::keepAlive,
                firstFocus = firstControlFocus
            )
        }

        if (snapshot.state == PlaybackState.ERROR &&
            !reconnecting &&
            reconnectAttempt >= MAX_RECONNECT_ATTEMPTS
        ) {
            TvPlayerNoticeOverlay(
                title = "无法播放该频道",
                message = "所有线路都未能出流，可以重试或换一个频道",
                primaryLabel = "重试",
                onPrimary = {
                    reconnectAttempt = 0
                    reconnecting = false
                    runtime.play(0)
                },
                secondaryLabel = "换台",
                onSecondary = {
                    sheet = LiveSheet.Channels
                    reconnectAttempt = 0
                }
            )
        }

        when (sheet) {
            LiveSheet.Lines -> LinesSheet(
                channel = channel,
                selectedIndex = lineIndex,
                onSelect = { index ->
                    lineIndex = index
                    reconnectAttempt = 0
                    reconnecting = false
                    sheet = LiveSheet.None
                    controls.show()
                }
            )

            LiveSheet.Channels -> ChannelsSheet(
                current = channel,
                onSelect = { id ->
                    sheet = LiveSheet.None
                    onChannelSwitched(id)
                }
            )

            LiveSheet.None -> Unit
        }
    }
}

@Composable
private fun ControlsLayer(
    channel: TvChannel,
    lineLabel: String?,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onBack: () -> Unit,
    onOpenLines: () -> Unit,
    onOpenChannels: () -> Unit,
    onInteraction: () -> Unit,
    firstFocus: FocusRequester
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.7f),
                        0.32f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.9f)
                    )
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = OverscanHorizontal, vertical = OverscanVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvPlayerIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = onBack
            )
            Spacer(Modifier.width(20.dp))
            LiveBadge()
            Spacer(Modifier.width(10.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = OverscanHorizontal, vertical = OverscanVertical)
        ) {
            val epg = channel.currentEpg(System.currentTimeMillis())
            if (epg != null) {
                Text(
                    text = "正在播出 · ${epg.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = buildString {
                    append(channel.category.displayName)
                    lineLabel?.let { append(" · 当前线路 ").append(it) }
                },
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1
            )

            Spacer(Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TvPlayerIconButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    onClick = onTogglePlay,
                    primary = true,
                    modifier = Modifier.focusRequester(firstFocus)
                )
                Spacer(Modifier.width(12.dp))
                TvPlayerTextButton(
                    text = "线路",
                    onClick = onOpenLines,
                    leadingIcon = Icons.Rounded.SwapHoriz
                )
                TvPlayerTextButton(
                    text = "频道",
                    onClick = onOpenChannels,
                    leadingIcon = Icons.Rounded.LiveTv
                )
            }
        }
    }
}

@Composable
private fun LinesSheet(
    channel: TvChannel,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    TvSideSheet(title = "播放线路") { focus ->
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SheetSectionTitle("切换到其他线路")
            channel.lines.forEachIndexed { index, line ->
                SheetOption(
                    label = line.label,
                    selected = index == selectedIndex,
                    focusRequester = if (index == selectedIndex) focus else null,
                    onClick = { onSelect(index) }
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ChannelsSheet(
    current: TvChannel,
    onSelect: (String) -> Unit
) {
    val channels = remember(current.category) {
        TvChannelRepository.byCategory(current.category).ifEmpty { TvChannelRepository.loadChannels() }
    }
    TvSideSheet(title = "切换频道") { focus ->
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SheetSectionTitle("${current.category.displayName} · 共 ${channels.size} 个频道")
            channels.forEachIndexed { index, channel ->
                SheetOption(
                    label = channel.name,
                    selected = channel.id == current.id,
                    focusRequester = if (channel.id == current.id) focus else null,
                    onClick = { onSelect(channel.id) }
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
}

private const val RECONNECT_BASE_DELAY_MS = 2_000L

private fun TvChannel.toPlayableMedia(line: com.qw.player.tv.data.TvLine): PlayableMedia {
    val sources = lines.map {
        PlaySource(
            sourceId = it.id,
            url = it.url,
            qualityId = it.id,
            cacheable = false
        )
    }
    return PlayableMedia(
        mediaId = id,
        mediaType = MediaType.VIDEO,
        metadata = PlayerMediaMetadata(
            title = name,
            subtitle = line.label
        ),
        playSources = sources,
        defaultSourceId = line.id
    )
}
