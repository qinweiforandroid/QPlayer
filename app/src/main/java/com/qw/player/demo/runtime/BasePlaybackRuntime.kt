package com.qw.player.demo.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.qw.player.core.session.DefaultPlaybackSession
import com.qw.player.core.telemetry.PlaybackTelemetry
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
    private val manageAudioFocus: Boolean,
    private val resumeOnFocusGain: Boolean = true
) {

    private var appContext: Context? = null
    private var engine: Media3PlayerEngine? = null
    private var session: DefaultPlaybackSession? = null
    private var audioFocusRequest: AudioFocusRequestCompat? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private var noisyReceiverRegistered = false
    private var wifiLockHeld = false
    private var resumeOnFocusGainFlag = resumeOnFocusGain

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                // 拔耳机 / 蓝牙断开：立即暂停，且不因焦点恢复自动续播。
                pause(resumeWhenFocusGain = false)
            }
        }
    }

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
                if (resumeOnFocusGainFlag) {
                    session?.resume()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                pause(resumeWhenFocusGain = false)
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 视频页要求被占用即暂停且不自动恢复，这里跟随构造配置。
                pause(resumeWhenFocusGain = resumeOnFocusGain)
            }
        }
    }

    fun initialize(
        context: Context,
        resolver: MediaSourceResolver? = null
    ) {
        if (engine != null) {
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
        registerNoisyReceiver()
    }

    /**
     * 彻底释放 engine 与 session，但保留 application context。
     *
     * 下次调用 [initialize] 会重建完整的 engine / session。
     */
    fun reset() {
        engine?.setListener(null)
        engine?.release()
        session?.release()
        engine = null
        session = null
        unregisterNoisyReceiver()
        releaseWifiLock()
        if (manageAudioFocus) {
            abandonAudioFocus()
        }
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) {
            return
        }
        val context = appContext ?: return
        ContextCompat.registerReceiver(
            context,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        noisyReceiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) {
            return
        }
        appContext?.let { runCatching { it.unregisterReceiver(noisyReceiver) } }
        noisyReceiverRegistered = false
    }

    /**
     * 后台音频流媒体占住 WifiLock，避免熄屏后 WiFi 进入省电模式导致缓冲中断。
     *
     * 只有允许后台播放的 runtime（音频）才持有；视频页不需要。
     */
    private fun acquireWifiLock() {
        if (!allowBackgroundPlayback || wifiLockHeld) {
            return
        }
        val context = appContext ?: return
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            ?: return
        val lock = wifiManager.createWifiLock(
            android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "$runtimeName:playback"
        )
        lock.setReferenceCounted(false)
        wifiLock = lock
        lock.acquire()
        wifiLockHeld = true
    }

    private fun releaseWifiLock() {
        if (!wifiLockHeld) {
            return
        }
        runCatching { wifiLock?.release() }
        wifiLock = null
        wifiLockHeld = false
    }

    fun setSourceResolver(resolver: MediaSourceResolver?) {
        sessionOrThrow().setSourceResolver(resolver)
    }

    fun setQueue(queue: List<PlayableMedia>, startIndex: Int = -1) {
        sessionOrThrow().setQueue(queue, startIndex)
    }

    fun play(index: Int) {
        if (!manageAudioFocus || requestAudioFocus()) {
            acquireWifiLock()
            sessionOrThrow().play(index)
        }
    }

    fun play(mediaId: String) {
        if (!manageAudioFocus || requestAudioFocus()) {
            acquireWifiLock()
            sessionOrThrow().play(mediaId)
        }
    }

    fun resume() {
        if (!manageAudioFocus || requestAudioFocus()) {
            acquireWifiLock()
            sessionOrThrow().resume()
        }
    }

    fun pause(resumeWhenFocusGain: Boolean = false) {
        resumeOnFocusGainFlag = resumeWhenFocusGain
        sessionOrThrow().pause()
        releaseWifiLock()
    }

    fun stop() {
        sessionOrThrow().stop()
        releaseWifiLock()
        if (manageAudioFocus) {
            abandonAudioFocus()
        }
    }

    fun seekTo(positionMs: Long) {
        sessionOrThrow().seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        engineOrThrow().setPlaybackSpeed(speed)
    }

    fun setVolume(volume: Float) {
        engineOrThrow().setVolume(volume)
    }

    fun setMuted(muted: Boolean) {
        engineOrThrow().setMuted(muted)
    }

    fun setLooping(looping: Boolean) {
        engineOrThrow().setLooping(looping)
    }

    /**
     * 切换当前播放项的 source（清晰度 / 线路），并从当前位置重新起播。
     *
     * 通过回写队列里的 media 副本实现，不新增 core 协议；
     * 当前进度通过 session 的 pending start position 透传给下一次 setMedia。
     */
    fun switchSource(sourceId: String): Boolean {
        val session = sessionOrThrow()
        val index = session.getCurrentIndex()
        val current = session.getCurrentMedia() ?: return false
        if (current.playSources.none { it.sourceId == sourceId }) {
            return false
        }
        session.setPendingStartPosition(getSnapshot().currentPositionMs)
        val updated = current.copy(defaultSourceId = sourceId)
        val queue = session.getQueue().toMutableList()
        if (index in queue.indices) {
            queue[index] = updated
            session.setQueue(queue, index)
        }
        session.play(updated)
        return true
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

    /**
     * 注入可观测性上报实现，传 null 关闭上报。
     *
     * 具体后端（友盟 / 自建）由业务层实现 [PlaybackTelemetry]，runtime 只负责转交。
     */
    fun setTelemetry(telemetry: PlaybackTelemetry?) {
        engine?.setTelemetry(telemetry)
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
