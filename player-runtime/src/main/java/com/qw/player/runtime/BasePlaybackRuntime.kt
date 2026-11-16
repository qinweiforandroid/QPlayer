package com.qw.player.runtime

import android.content.Context
import com.qw.player.core.engine.PlaybackSnapshot
import com.qw.player.core.engine.PlayerConfig
import com.qw.player.core.engine.RoutingPlayerEngine
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.session.DefaultPlaybackSession
import com.qw.player.core.session.PlaybackMode
import com.qw.player.core.session.PlaybackSessionListener
import com.qw.player.core.telemetry.PlaybackTelemetry

/**
 * 播放运行时门面。
 *
 * 只做三件事：
 * - 持有一次 [initialize] 装配出来的 engine / session 句柄
 * - 把调用方指令转交给 session 或活跃 engine
 * - 编排“播放前拿焦点 / 持 WiFi 锁，暂停后释放”这条策略
 *
 * 具体机制已经下沉到各自协议后面：
 * - [PlaybackEngineFactory]：engine 装配
 * - [AudioFocusController]：焦点申请、失焦响应、续播决策
 * - [WifiLockKeeper]：WiFi 锁
 * - [NoisyAudioWatcher]：耳机拔出广播
 * - [PlaybackListenerRegistry]：多观察者扇出
 */
class BasePlaybackRuntime(
    private val runtimeName: String,
    private val allowBackgroundPlayback: Boolean,
    private val manageAudioFocus: Boolean,
    private val resumeOnFocusGain: Boolean = true,
    private val engineFactory: PlaybackEngineFactory,
    private val focusFactory: AudioFocusControllerFactory = { context, onResume, onPause ->
        AndroidAudioFocusController(
            context = context,
            defaultResumeOnGain = resumeOnFocusGain,
            onResumePlayback = onResume,
            onPausePlayback = onPause
        )
    },
    private val wifiLockFactory: WifiLockFactory = { context, tag, enabled ->
        AndroidWifiLockKeeper(context, tag, enabled)
    },
    private val noisyAudioFactory: NoisyAudioWatcherFactory = { context, onNoisy ->
        SystemNoisyAudioWatcher(context, onNoisy)
    }
) {

    private var engine: RoutingPlayerEngine? = null
    private var session: DefaultPlaybackSession? = null
    private var audioFocus: AudioFocusController? = null
    private var wifiLock: WifiLockKeeper? = null
    private var noisyWatcher: NoisyAudioWatcher? = null

    private val listeners = PlaybackListenerRegistry()

    fun initialize(context: Context) {
        if (engine != null) {
            return
        }
        val applicationContext = context.applicationContext
        val config = PlayerConfig(
            autoPlayWhenReady = true,
            allowBackgroundPlayback = allowBackgroundPlayback,
            respectAudioFocus = manageAudioFocus
        )
        val createdEngine = engineFactory.create(applicationContext, config)
        val createdSession = DefaultPlaybackSession().apply {
            bindEngine(createdEngine)
            addListener(listeners)
        }
        engine = createdEngine
        session = createdSession
        if (manageAudioFocus) {
            audioFocus = focusFactory.create(
                context = applicationContext,
                onResumePlayback = { session?.resume() },
                onPausePlayback = { pause() }
            )
        }
        wifiLock = wifiLockFactory.create(applicationContext, "$runtimeName:playback", allowBackgroundPlayback)
        noisyWatcher = noisyAudioFactory.create(applicationContext, ::onBecameNoisy).also { it.register() }
    }

    /**
     * 彻底释放 engine 与 session，但保留 application context。
     *
     * 下次调用 [initialize] 会重建完整的 engine / session。
     * 监听者不随 reset 丢失，重新 initialize 后会继续收到事件。
     */
    fun reset() {
        engine?.setListener(null)
        // router 负责释放它持有的所有子 engine
        engine?.release()
        session?.release()
        engine = null
        session = null
        noisyWatcher?.unregister()
        noisyWatcher = null
        wifiLock?.release()
        wifiLock = null
        audioFocus?.release()
        audioFocus = null
    }

    fun setMediaList(queue: List<PlayableMedia>, startIndex: Int = -1) {
        sessionOrThrow().setMediaList(queue, startIndex)
    }

    fun play(index: Int) {
        playWithRequirements { it.play(index) }
    }

    fun play(mediaId: String) {
        playWithRequirements { it.play(mediaId) }
    }

    fun resume() {
        playWithRequirements { it.resume() }
    }

    fun pause(resumeWhenFocusGain: Boolean = false) {
        audioFocus?.onPlaybackPaused(resumeWhenFocusGain)
        sessionOrThrow().pause()
        wifiLock?.release()
    }

    fun stop() {
        sessionOrThrow().stop()
        wifiLock?.release()
        audioFocus?.release()
    }

    fun seekTo(positionMs: Long) {
        sessionOrThrow().seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        routerOrThrow().setSpeed(speed)
    }

    fun setVolume(volume: Float) {
        routerOrThrow().setVolume(volume)
    }

    fun setMuted(muted: Boolean) {
        routerOrThrow().setMuted(muted)
    }

    fun setLooping(looping: Boolean) {
        routerOrThrow().setLooping(looping)
    }

    /**
     * 切换当前播放项的 source（清晰度 / 线路），并从当前位置重新起播。
     */
    fun switchSource(sourceId: String): Boolean {
        return sessionOrThrow().switchSource(sourceId)
    }

    fun skipToNext() {
        playWithRequirements { it.skipToNext() }
    }

    fun skipToPrevious() {
        playWithRequirements { it.skipToPrevious() }
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
        return sessionOrThrow().getMediaList()
    }

    fun getSnapshot(): PlaybackSnapshot {
        return sessionOrThrow().getSnapshot()
    }

    fun addListener(listener: PlaybackSessionListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: PlaybackSessionListener) {
        listeners.remove(listener)
    }

    /**
     * 注入可观测性上报实现，传 null 关闭上报。
     *
     * 具体后端（友盟 / 自建）由业务层实现 [PlaybackTelemetry]，runtime 只负责转交。
     */
    fun setTelemetry(telemetry: PlaybackTelemetry?) {
        routerOrThrow().setTelemetry(telemetry)
    }

    fun getEngine(): RoutingPlayerEngine {
        return routerOrThrow()
    }

    fun isPlaying(): Boolean {
        return getSnapshot().isPlaying
    }

    fun isPaused(): Boolean {
        return getSnapshot().isPaused
    }

    fun isBuffering(): Boolean {
        return getSnapshot().isBuffering
    }

    /** 起播类指令的统一前置：拿焦点、持 WiFi 锁，然后才真正下发。 */
    private inline fun playWithRequirements(action: (DefaultPlaybackSession) -> Unit) {
        if (audioFocus?.acquire() != false) {
            wifiLock?.acquire()
            action(sessionOrThrow())
        }
    }

    /** 拔耳机 / 蓝牙断开：立即暂停，且不因焦点恢复自动续播。 */
    private fun onBecameNoisy() {
        pause(resumeWhenFocusGain = false)
    }

    private fun routerOrThrow(): RoutingPlayerEngine {
        return requireNotNull(engine) { "$runtimeName 尚未初始化 engine" }
    }

    private fun sessionOrThrow(): DefaultPlaybackSession {
        return requireNotNull(session) { "$runtimeName 尚未初始化 session" }
    }
}
