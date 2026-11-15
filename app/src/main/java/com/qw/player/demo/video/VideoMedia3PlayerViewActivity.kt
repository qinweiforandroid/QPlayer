package com.qw.player.demo.video

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.qw.player.core.media.MediaType
import com.qw.player.core.media.PlaySource
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.session.PlaybackMode
import com.qw.player.core.session.PlaybackSessionListener
import com.qw.player.core.media.PlayerMediaMetadata
import com.qw.player.core.session.RepeatMode
import com.qw.player.demo.Constants
import com.qw.player.demo.databinding.VideoMedia3PlayerViewActivityBinding
import com.qw.player.demo.runtime.VideoPlaybackRuntime

/**
 * 基于视频专用 V2 runtime 的播放页面。
 *
 * 当前视频播放链路独立于音频 runtime，
 * 避免视频会话覆盖后台音频队列。
 */
class VideoMedia3PlayerViewActivity : AppCompatActivity() {
    private lateinit var bind: VideoMedia3PlayerViewActivityBinding
    private val videoMedia by lazy { buildDemoVideoMedia() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bind = VideoMedia3PlayerViewActivityBinding.inflate(layoutInflater)
        setContentView(bind.root)
        VideoPlaybackRuntime.apply {
            bindPlayerView(bind.playerView)
            updatePlaybackMode(PlaybackMode(RepeatMode.OFF, false))
            setMediaList(listOf(videoMedia), 0)
            addListener(sessionListener)
            play(0)
        }
    }

    override fun onPause() {
        super.onPause()
        if (VideoPlaybackRuntime.getCurrentMedia()?.mediaId == videoMedia.mediaId) {
            VideoPlaybackRuntime.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        val snapshot = VideoPlaybackRuntime.getSnapshot()
        if (VideoPlaybackRuntime.getCurrentMedia()?.mediaId == videoMedia.mediaId &&
            snapshot.isPrepared &&
            !snapshot.playWhenReady
        ) {
            VideoPlaybackRuntime.resume()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        VideoPlaybackRuntime.removeListener(sessionListener)
        bind.playerView.player = null
    }

    private fun buildDemoVideoMedia(): PlayableMedia {
        return PlayableMedia(
            mediaId = "demo-video-media3-v2",
            mediaType = MediaType.VIDEO,
            metadata = PlayerMediaMetadata(
                title = "Media3 V2 Demo",
                subtitle = "Sample video playback",
                coverUrl = ""
            ),
            playSources = listOf(
                PlaySource(
                    sourceId = "default",
                    url = Constants.VIDEO_URL
                )
            ),
            defaultSourceId = "default"
        )
    }

    private val sessionListener = object : PlaybackSessionListener {
        override fun onPlaybackStateChanged(
            media: PlayableMedia,
            state: com.qw.player.core.engine.PlaybackState
        ) {
            title = media.mediaId
        }

        override fun onPlaybackError(media: PlayableMedia?, error: PlaybackError) {
            Toast.makeText(
                this@VideoMedia3PlayerViewActivity,
                error.message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}