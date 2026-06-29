package com.qw.player.demo.widget

import com.qw.player.core.engine.PlaybackState

interface VideoPlayerControl {

    fun setActionListener(listener: ActionListener?)

    fun render(state: State)

    fun setControlsVisible(visible: Boolean)

    fun areControlsVisible(): Boolean

    fun isTrackingSeekBar(): Boolean

    interface ActionListener {
        fun onBackClicked()
        fun onPlayPauseClicked()
        fun onFullscreenClicked()
        fun onControllerTapped()
        fun onSeekFinished(positionMs: Long)
    }

    data class State(
        val title: String = "",
        val liveBadgeText: String = "",
        val clockText: String = "",
        val isFullscreen: Boolean = false,
        val playbackState: PlaybackState = PlaybackState.IDLE,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L
    )
}
