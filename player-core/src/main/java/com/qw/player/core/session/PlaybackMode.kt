package com.qw.player.core.session

import com.qw.player.core.session.RepeatMode

/**
 * 队列级别的播放策略。
 *
 * 由 [PlaybackSession] 持有，并用于决定下一首、上一首以及是否随机播放。
 */
data class PlaybackMode(
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val shuffleEnabled: Boolean = false
)
