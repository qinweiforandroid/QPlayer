package com.qw.player.tv.ui.vod

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
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Speed
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
import com.qw.player.core.engine.PlaybackErrorCategory
import com.qw.player.core.engine.PlaybackState
import com.qw.player.core.media.MediaType
import com.qw.player.core.media.PlaySource
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.media.PlayerMediaMetadata
import com.qw.player.core.session.PlaybackMode
import com.qw.player.core.session.RepeatMode
import com.qw.player.tv.data.VodEpisode
import com.qw.player.tv.data.VodPlaylist
import com.qw.player.tv.data.VodRepository
import com.qw.player.tv.playback.TvPlaybackRuntime
import com.qw.player.tv.ui.playback.AutoHideControls
import com.qw.player.tv.ui.playback.OverscanHorizontal
import com.qw.player.tv.ui.playback.OverscanVertical
import com.qw.player.tv.ui.playback.PlayerProgressBar
import com.qw.player.tv.ui.playback.SEEK_STEP_MS
import com.qw.player.tv.ui.playback.RequestFocusWhenReady
import com.qw.player.tv.ui.playback.SheetOption
import com.qw.player.tv.ui.playback.SheetSectionTitle
import com.qw.player.tv.ui.playback.TvBufferingIndicator
import com.qw.player.tv.ui.playback.TvPlayerIconButton
import com.qw.player.tv.ui.playback.TvPlayerNoticeOverlay
import com.qw.player.tv.ui.playback.TvPlayerTextButton
import com.qw.player.tv.ui.playback.TvSideSheet
import com.qw.player.tv.ui.playback.rememberControlVisibility
import com.qw.player.tv.ui.rememberPlayerState

private enum class VodSheet { None, Quality, Episodes, Speed }

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * 点播播放页。
 *
 * 线上形态的三个要点：
 * - 画面优先：控制层闲置几秒自动收起，收起后方向键/确认键先把它唤出来
 * - 进度条自身可聚焦，左右键按 10 秒步进立即 seek，不做额外的拖动弹层
 * - 切清晰度保留进度，多 P 播完自动接下一集；失败给可读文案和重试入口
 */
@OptIn(UnstableApi::class)
@Composable
fun VodPlayerScreen(
    runtime: TvPlaybackRuntime,
    episodeId: String,
    onBack: () -> Unit
) {
    val playlist = remember { VodRepository.loadPlaylist() }
    val queue = remember(playlist) { playlist.episodes.map { it.toPlayableMedia(playlist) } }

    var episodeIndex by remember(episodeId) {
        mutableIntStateOf(
            playlist.episodes.indexOfFirst { it.id == episodeId }.takeIf { it >= 0 } ?: 0
        )
    }
    var qualityId by remember(episodeId) {
        mutableStateOf(playlist.episodes.getOrNull(episodeIndex)?.qualities?.firstOrNull()?.id)
    }
    var sheet by remember { mutableStateOf(VodSheet.None) }

    val holder = rememberPlayerState(runtime)
    val snapshot = holder.snapshot
    val controls = rememberControlVisibility(initiallyVisible = true)
    val rootFocus = remember { FocusRequester() }
    val firstControlFocus = remember { FocusRequester() }

    val currentEpisode = playlist.episodes.getOrNull(episodeIndex)
        ?: playlist.episodes.firstOrNull()
    val isPlaying = snapshot.isPlaying || snapshot.state == PlaybackState.BUFFERING

    KeepScreenOn()

    LaunchedEffect(Unit) {
        runtime.updatePlaybackMode(PlaybackMode(repeatMode = RepeatMode.OFF))
        runtime.setMediaList(queue, episodeIndex)
        runtime.play(episodeIndex)
    }

    // 离开播放页必须停掉播放，否则回到首页后声音还在继续。
    DisposableEffect(Unit) {
        onDispose { runtime.stop() }
    }

    // 会话自动连播到下一集时，页面记录的集数跟着走，选集面板与标题才不会错位。
    LaunchedEffect(holder.currentMedia?.mediaId) {
        val mediaId = holder.currentMedia?.mediaId ?: return@LaunchedEffect
        val index = playlist.episodes.indexOfFirst { it.id == mediaId }
        if (index >= 0 && index != episodeIndex) {
            episodeIndex = index
            qualityId = playlist.episodes[index].qualities.firstOrNull()?.id
        }
    }

    // 续播由会话层负责（非最后一集播完会自动推进）。这里只处理失败与全部播完两种情况。
    LaunchedEffect(snapshot.state) {
        when (snapshot.state) {
            // 播放失败时把控制层叫出来，用户能直接看到重试按钮。
            PlaybackState.ERROR -> controls.show()
            PlaybackState.ENDED -> controls.show()
            else -> Unit
        }
    }

    // 控制层出现时把焦点交给其中的主按钮，遥控器方向键才有落脚点。
    if (controls.visible && sheet == VodSheet.None) {
        RequestFocusWhenReady(firstControlFocus, key = controls.visible)
    }

    AutoHideControls(visibility = controls, isPlaying = isPlaying)

    // 控制层收起或面板关闭后，把焦点交还给页面根节点。
    LaunchedEffect(controls.visible, sheet) {
        if (!controls.visible && sheet == VodSheet.None) {
            runCatching { rootFocus.requestFocus() }
        }
    }

    BackHandler(enabled = sheet != VodSheet.None) {
        sheet = VodSheet.None
        controls.show()
    }

    val seekBy: (Long) -> Unit = { delta ->
        val duration = snapshot.durationMs.coerceAtLeast(0L)
        val target = if (duration > 0) {
            (snapshot.currentPositionMs + delta).coerceIn(0L, duration)
        } else {
            (snapshot.currentPositionMs + delta).coerceAtLeast(0L)
        }
        runtime.seekTo(target)
        controls.keepAlive()
    }
    val togglePlay: () -> Unit = {
        when (snapshot.state) {
            PlaybackState.PLAYING, PlaybackState.BUFFERING, PlaybackState.PREPARING -> runtime.pause()
            PlaybackState.ENDED -> {
                // 播完后再点播放：最后一集从头重播，否则接着播下一集。
                val target = if (episodeIndex + 1 in playlist.episodes.indices) episodeIndex + 1 else episodeIndex
                episodeIndex = target
                qualityId = playlist.episodes[target].qualities.firstOrNull()?.id
                runtime.play(target)
            }

            else -> runtime.resume()
        }
        controls.keepAlive()
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
                    // 控制层收起时，先按键把它叫出来，避免「按了没反应」。
                    !controls.visible &&
                        event.key in setOf(
                            Key.DirectionCenter,
                            Key.Enter,
                            Key.DirectionUp,
                            Key.DirectionDown
                        ) -> {
                        controls.show()
                        true
                    }

                    event.key == Key.MediaPlayPause -> {
                        togglePlay()
                        true
                    }

                    event.key == Key.MediaRewind -> {
                        seekBy(-SEEK_STEP_MS)
                        true
                    }

                    event.key == Key.MediaFastForward -> {
                        seekBy(SEEK_STEP_MS)
                        true
                    }

                    // 用户还在控制层里导航时持续续期，别在他选按钮的途中把控制层收走。
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

        if (sheet == VodSheet.None && snapshot.isBuffering && snapshot.error == null) {
            TvBufferingIndicator(modifier = Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(
            visible = controls.visible && sheet == VodSheet.None,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ControlsLayer(
                playlistTitle = playlist.title,
                episode = currentEpisode,
                positionMs = snapshot.currentPositionMs,
                durationMs = snapshot.durationMs,
                bufferedMs = snapshot.bufferedPositionMs,
                isPlaying = snapshot.isPlaying,
                speed = snapshot.speed,
                qualityLabel = currentEpisode?.qualities?.firstOrNull { it.id == qualityId }?.label,
                episodeIndex = episodeIndex,
                episodeCount = playlist.episodes.size,
                onSeek = {
                    runtime.seekTo(it)
                    controls.keepAlive()
                },
                onTogglePlay = togglePlay,
                onSeekBy = seekBy,
                onBack = onBack,
                onOpenQuality = {
                    sheet = VodSheet.Quality
                    controls.keepAlive()
                },
                onOpenEpisodes = {
                    sheet = VodSheet.Episodes
                    controls.keepAlive()
                },
                onOpenSpeed = {
                    sheet = VodSheet.Speed
                    controls.keepAlive()
                },
                onInteraction = controls::keepAlive,
                firstFocus = firstControlFocus
            )
        }

        snapshot.error
            ?.takeIf { snapshot.state == PlaybackState.ERROR }
            ?.let { error ->
                TvPlayerNoticeOverlay(
                    title = if (error.category == PlaybackErrorCategory.NETWORK) "网络异常" else "播放失败",
                    message = error.toUserMessage(),
                    primaryLabel = "重试",
                    onPrimary = { runtime.play(episodeIndex) },
                    secondaryLabel = "返回",
                    onSecondary = onBack
                )
            }

        if (snapshot.state == PlaybackState.ENDED &&
            episodeIndex == playlist.episodes.lastIndex &&
            sheet == VodSheet.None
        ) {
            TvPlayerNoticeOverlay(
                title = "已播完",
                message = "${currentEpisode?.title.orEmpty()} 播放结束",
                primaryLabel = "重新播放",
                onPrimary = { runtime.play(episodeIndex) },
                secondaryLabel = "返回",
                onSecondary = onBack
            )
        }

        when (sheet) {
            VodSheet.Quality -> QualitySheet(
                episode = currentEpisode,
                selectedQualityId = qualityId,
                onSelect = { id ->
                    qualityId = id
                    runtime.switchSource(id)
                    sheet = VodSheet.None
                    controls.show()
                }
            )

            VodSheet.Episodes -> EpisodesSheet(
                playlist = playlist,
                currentIndex = episodeIndex,
                onSelect = { index ->
                    episodeIndex = index
                    qualityId = playlist.episodes[index].qualities.firstOrNull()?.id
                    runtime.play(index)
                    sheet = VodSheet.None
                    controls.show()
                }
            )

            VodSheet.Speed -> SpeedSheet(
                currentSpeed = snapshot.speed,
                onSelect = { speed ->
                    runtime.setPlaybackSpeed(speed)
                    sheet = VodSheet.None
                    controls.show()
                }
            )

            VodSheet.None -> Unit
        }
    }
}

@Composable
private fun ControlsLayer(
    playlistTitle: String,
    episode: VodEpisode?,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    isPlaying: Boolean,
    speed: Float,
    qualityLabel: String?,
    episodeIndex: Int,
    episodeCount: Int,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onBack: () -> Unit,
    onOpenQuality: () -> Unit,
    onOpenEpisodes: () -> Unit,
    onOpenSpeed: () -> Unit,
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
                        0.60f to Color.Transparent,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode?.title ?: playlistTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (episodeCount > 1) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$playlistTitle · 第 ${episodeIndex + 1} / $episodeCount 集",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = OverscanHorizontal, vertical = OverscanVertical)
        ) {
            PlayerProgressBar(
                positionMs = positionMs,
                durationMs = durationMs,
                bufferedMs = bufferedMs,
                onSeek = onSeek,
                onInteraction = onInteraction
            )
            Spacer(Modifier.height(16.dp))
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
                TvPlayerIconButton(
                    icon = Icons.Rounded.Replay10,
                    contentDescription = "快退 10 秒",
                    onClick = { onSeekBy(-SEEK_STEP_MS) }
                )
                TvPlayerIconButton(
                    icon = Icons.Rounded.Forward10,
                    contentDescription = "快进 10 秒",
                    onClick = { onSeekBy(SEEK_STEP_MS) }
                )
                Spacer(Modifier.width(12.dp))
                TvPlayerTextButton(
                    text = qualityLabel ?: "清晰度",
                    onClick = onOpenQuality,
                    leadingIcon = Icons.Rounded.HighQuality
                )
                TvPlayerTextButton(
                    text = if (episodeCount > 1) "选集 ${episodeIndex + 1}/$episodeCount" else "选集",
                    onClick = onOpenEpisodes,
                    leadingIcon = Icons.Rounded.PlaylistPlay
                )
                TvPlayerTextButton(
                    text = formatSpeed(speed),
                    onClick = onOpenSpeed,
                    leadingIcon = Icons.Rounded.Speed
                )
            }
        }
    }
}

@Composable
private fun QualitySheet(
    episode: VodEpisode?,
    selectedQualityId: String?,
    onSelect: (String) -> Unit
) {
    TvSideSheet(title = "清晰度") { focus ->
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (episode == null || episode.qualities.isEmpty()) {
                Text(
                    text = "当前视频没有可选清晰度",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SheetSectionTitle("切换后从当前进度继续播放")
                episode.qualities.forEachIndexed { index, quality ->
                    SheetOption(
                        label = quality.label,
                        selected = quality.id == selectedQualityId,
                        focusRequester = if (index == 0) focus else null,
                        onClick = { onSelect(quality.id) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun EpisodesSheet(
    playlist: VodPlaylist,
    currentIndex: Int,
    onSelect: (Int) -> Unit
) {
    TvSideSheet(title = "选集") { focus ->
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SheetSectionTitle("共 ${playlist.episodes.size} 集")
            playlist.episodes.forEachIndexed { index, episode ->
                SheetOption(
                    label = episode.title,
                    selected = index == currentIndex,
                    focusRequester = if (index == currentIndex) focus else null,
                    onClick = { onSelect(index) }
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun SpeedSheet(
    currentSpeed: Float,
    onSelect: (Float) -> Unit
) {
    TvSideSheet(title = "播放速度") { focus ->
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SheetSectionTitle("选择倍速")
            SPEED_OPTIONS.forEachIndexed { index, speed ->
                SheetOption(
                    label = formatSpeed(speed),
                    selected = kotlin.math.abs(currentSpeed - speed) < 0.01f,
                    focusRequester = if (index == 0) focus else null,
                    onClick = { onSelect(speed) }
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** TV 端没有熄屏超时以外的省电诉求，播放期间保持屏幕常亮。 */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
}

private fun formatSpeed(speed: Float): String {
    val rounded = (speed * 100).toInt()
    return if (rounded % 100 == 0) "${rounded / 100}x" else String.format("%.2fx", speed)
}

private fun com.qw.player.core.engine.PlaybackError.toUserMessage(): String {
    return when (category) {
        PlaybackErrorCategory.NETWORK -> "网络连接异常，请检查网络后重试"
        PlaybackErrorCategory.SOURCE -> "视频加载失败，片源可能已失效"
        PlaybackErrorCategory.DECODE -> "当前设备无法解码该视频"
        PlaybackErrorCategory.UNKNOWN -> "播放出了点问题，请重试"
    }
}

private fun VodEpisode.toPlayableMedia(playlist: VodPlaylist): PlayableMedia {
    val sources = qualities.map { quality ->
        PlaySource(
            sourceId = quality.id,
            url = quality.url,
            qualityId = quality.id
        )
    }
    return PlayableMedia(
        mediaId = id,
        mediaType = MediaType.VIDEO,
        metadata = PlayerMediaMetadata(
            title = title,
            subtitle = playlist.title,
            artist = playlist.author,
            coverUrl = playlist.coverUrl
        ),
        playSources = sources,
        defaultSourceId = sources.firstOrNull()?.sourceId
    )
}
