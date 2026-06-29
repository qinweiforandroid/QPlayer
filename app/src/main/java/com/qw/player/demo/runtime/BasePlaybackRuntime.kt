package com.qw.player.demo.runtime

import android.content.Context
import android.media.AudioManager
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.qw.player.core.session.DefaultPlaybackSession
import com.qw.player.core.source.MediaSourceResolver
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.session.PlaybackMode
import com.qw.player.core.session.PlaybackSessionListener
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.core.engine.PlaybackState
import com.qw.player.core.engine.PlayerConfig
import com.qw.player.media3.Media3PlayerEngine

internal class BasePlaybackRuntime(
    private val runtimeName: String,
    private val allowBackgroundPlayback: Boolean,
    private val manageAudioFocus: Boolean
) {

    private var appContext: Context? = null
    private var engine: Media3PlayerEngine? = null
    private var session: DefaultPlaybackSession? = null
    private var audioFocusRequest: AudioFocusRequestCompat? = null
    private var resumeOnFocusGain = true

    private val runtimeListeners = linkedSetOf<PlaybackSessionListener>()

    private val internalSessionListener = object : PlaybackSessionListener {
        override fun onPlaybackChanged(snapshot: PlaybackSnapshot) {
            runtimeListeners.forEach { it.onPlaybackChanged(snapshot) }
        }

        override fun onCurrentMediaChanged(current: PlayableMedia?, previous: PlayableMedia?) {
            runtimeListeners.forEach { it.onCurrentMediaChanged(current, previous) }
        }

        override fun onQueueChanged(queue: List<PlayableMedia>, currentIndex: Int) {
            runtimeListeners.forEach { it.onQueueChanged(queue, currentIndex) }
        }

        override fun onPlaybackModeChanged(mode: PlaybackMode) {
            runtimeListeners.forEach { it.onPlaybackModeChanged(mode) }
        }

        override fun onPlaybackCompleted(snapshot: PlaybackSnapshot) {
            runtimeListeners.forEach { it.onPlaybackCompleted(snapshot) }
        }

        override fun onPlaybackError(
            error: com.qw.player.core.engine.PlaybackError,
            snapshot: PlaybackSnapshot
        ) {
            runtimeListeners.forEach { it.onPlaybackError(error, snapshot) }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    session?.resume()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                pause(resumeWhenFocusGain = false)
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                pause(resumeWhenFocusGain = true)
            }
        }
    }

    fun initialize(
        context: Context,
        resolver: MediaSourceResolver? = null
    ) {
        if (appContext != null) {
            if (resolver != null) {
                session?.setSourceResolver(resolver)
            }
            return
        }
        appContext = context.applicationContext
        val createdEngine = Media3PlayerEngine(context).apply {
            initialize(
                PlayerConfig(
                    autoPlayWhenReady = true,
                    allowBackgroundPlayback = allowBackgroundPlayback,
                    respectAudioFocus = manageAudioFocus
                )
            )
        }
        val createdSession = DefaultPlaybackSession().apply {
            bindEngine(createdEngine)
            setSourceResolver(resolver)
            addListener(internalSessionListener)
        }
        engine = createdEngine
        session = createdSession
    }

    fun setSourceResolver(resolver: MediaSourceResolver?) {
        sessionOrThrow().setSourceResolver(resolver)
    }

    fun setQueue(queue: List<PlayableMedia>, startIndex: Int = -1) {
        sessionOrThrow().setQueue(queue, startIndex)
    }

    fun play(index: Int) {
        if (!manageAudioFocus || requestAudioFocus()) {
            sessionOrThrow().play(index)
        }
    }

    fun play(mediaId: String) {
        if (!manageAudioFocus || requestAudioFocus()) {
            sessionOrThrow().play(mediaId)
        }
    }

    fun resume() {
        if (!manageAudioFocus || requestAudioFocus()) {
            sessionOrThrow().resume()
        }
    }

    fun pause(resumeWhenFocusGain: Boolean = false) {
        resumeOnFocusGain = resumeWhenFocusGain
        sessionOrThrow().pause()
    }

    fun stop() {
        sessionOrThrow().stop()
        if (manageAudioFocus) {
            abandonAudioFocus()
        }
    }

    fun seekTo(positionMs: Long) {
        sessionOrThrow().seekTo(positionMs)
    }

    fun skipToNext() {
        if (!manageAudioFocus || requestAudioFocus()) {
            sessionOrThrow().skipToNext()
        }
    }

    fun skipToPrevious() {
        if (!manageAudioFocus || requestAudioFocus()) {
            sessionOrThrow().skipToPrevious()
        }
    }

    fun updatePlaybackMode(mode: PlaybackMode) {
        sessionOrThrow().updatePlaybackMode(mode)
    }

    fun getCurrentMedia(): PlayableMedia? {
        return sessionOrThrow().getCurrentMedia()
    }

    fun getCurrentIndex(): Int {
        return sessionOrThrow().getCurrentIndex()
    }

    fun getQueue(): List<PlayableMedia> {
        return sessionOrThrow().getQueue()
    }

    fun getSnapshot(): PlaybackSnapshot {
        return sessionOrThrow().getSnapshot()
    }

    fun addListener(listener: PlaybackSessionListener) {
        runtimeListeners.add(listener)
    }

    fun removeListener(listener: PlaybackSessionListener) {
        runtimeListeners.remove(listener)
    }

    fun getEngine(): Media3PlayerEngine {
        return engineOrThrow()
    }

    fun isPlaying(): Boolean {
        return getSnapshot().state == PlaybackState.PLAYING
    }

    fun isPaused(): Boolean {
        return getSnapshot().state == PlaybackState.PAUSED
    }

    fun isBuffering(): Boolean {
        val state = getSnapshot().state
        return state == PlaybackState.BUFFERING || state == PlaybackState.PREPARING
    }

    private fun requestAudioFocus(): Boolean {
        val context = appContext ?: return true
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributesCompat.Builder()
                    .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()
        audioFocusRequest = request
        val result = AudioManagerCompat.requestAudioFocus(audioManager, request)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val context = appContext ?: return
        val request = audioFocusRequest ?: return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, request)
        audioFocusRequest = null
    }

    private fun engineOrThrow(): Media3PlayerEngine {
        return requireNotNull(engine) { "$runtimeName 尚未初始化 engine" }
    }

    private fun sessionOrThrow(): DefaultPlaybackSession {
        return requireNotNull(session) { "$runtimeName 尚未初始化 session" }
    }
}
