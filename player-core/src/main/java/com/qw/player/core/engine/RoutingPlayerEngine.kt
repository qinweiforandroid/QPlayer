package com.qw.player.core.engine

import com.qw.player.core.media.MediaType
import com.qw.player.core.media.PlaySource
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.telemetry.PlaybackTelemetry

/**
 * 按 [MediaType] 把播放指令派发给对应 [PlayerEngine] 的路由 engine。
 *
 * [com.qw.player.core.session.PlaybackSession] 只认识一个 engine，
 * 它不应该、也不需要知道“这条媒体该交给谁播”——这正是这个类存在的意义：
 * 把“招聘/派活”这一层单独收口在这里，session 和具体 engine 实现都不感知彼此的存在。
 *
 * 同一时刻只有一个 MediaType 处于“活跃”状态：
 * - 活跃类型所对应 engine 实例的事件原样转发给外部监听器
 * - 非活跃 engine 的事件被丢弃，避免上一次播放残留的回调（例如 ENDED）干扰当前会话
 * - 媒体类型切换时，旧的活跃 engine 会先被 `stop(reset = true)`
 *
 * 注意：[engines] 允许多个 [MediaType] 映射到同一个 engine 实例（典型场景：
 * 音频和视频共用同一个 Media3 engine）。因为 [PlayerEngine.setListener] 只有单槽位，
 * 不能按“每个 Map entry 调一次 setListener”，否则后注册的类型会把先注册的覆盖掉，
 * 导致这个引擎在服务另一个类型时被误判为“不活跃”而把所有事件吞掉。
 * 这里按引擎实例去重，一个实例只 setListener 一次，Forwarder 判断的是
 * “当前活跃类型是否在这个引擎负责的类型集合里”，而不是“是否等于某一个固定类型”。
 */
class RoutingPlayerEngine(
    private val engines: Map<MediaType, PlayerEngine>,
    private val defaultType: MediaType
) : PlayerEngine {

    init {
        require(engines.containsKey(defaultType)) {
            "engines 中必须包含 defaultType=$defaultType 对应的 engine"
        }
    }

    private var config: PlayerConfig = PlayerConfig()
    private var externalListener: PlayerEventListener? = null
    private var activeType: MediaType = defaultType
    private var released = false

    /** 同一个 engine 实例可能服务多个 MediaType，按实例去重后只保留一份。 */
    private val uniqueEngines: List<PlayerEngine> = engines.values.distinct()

    /** 引擎实例 -> 它所服务的全部 MediaType。 */
    private val typesByEngine: Map<PlayerEngine, Set<MediaType>> =
        engines.entries.groupBy({ it.value }, { it.key }).mapValues { it.value.toSet() }

    init {
        uniqueEngines.forEach { engine ->
            engine.setListener(Forwarder(typesByEngine.getValue(engine)))
        }
    }

    /** 以路由方式转发事件；只有当前活跃类型命中这个 engine 负责的类型集合，事件才会继续往外传。 */
    private inner class Forwarder(private val types: Set<MediaType>) : PlayerEventListener {
        private fun isActive() = activeType in types

        override fun onStateChanged(state: PlaybackState) {
            if (isActive()) externalListener?.onStateChanged(state)
        }

        override fun onProgress(positionMs: Long, durationMs: Long) {
            if (isActive()) externalListener?.onProgress(positionMs, durationMs)
        }

        override fun onRenderedFirstFrame() {
            if (isActive()) externalListener?.onRenderedFirstFrame()
        }

        override fun onVideoSizeChanged(size: VideoSize) {
            if (isActive()) externalListener?.onVideoSizeChanged(size)
        }

        override fun onBufferingProgress(bufferedPositionMs: Long, durationMs: Long) {
            if (isActive()) externalListener?.onBufferingProgress(bufferedPositionMs, durationMs)
        }

        override fun onCompleted() {
            if (isActive()) externalListener?.onCompleted()
        }

        override fun onError(error: PlaybackError) {
            if (isActive()) externalListener?.onError(error)
        }
    }

    /** 返回某个媒体类型对应的 engine；未注册时返回 null。 */
    fun getEngine(type: MediaType): PlayerEngine? = engines[type]

    /** 返回当前活跃的 engine。 */
    fun getActiveEngine(): PlayerEngine? = engines[activeType]

    /** 返回当前活跃的媒体类型。 */
    fun getActiveType(): MediaType = activeType

    override fun initialize(config: PlayerConfig) {
        this.config = config
        uniqueEngines.forEach { it.initialize(config) }
    }

    override fun setMedia(media: PlayableMedia, source: PlaySource, startPositionMs: Long) {
        ensureNotReleased()
        val targetType = engineTypeFor(media)
        switchActiveTypeIfNeeded(targetType)
        requireActiveEngine().setMedia(media, source, startPositionMs)
    }

    override fun preload(media: PlayableMedia, source: PlaySource) {
        ensureNotReleased()
        val targetEngine = engines[engineTypeFor(media)] ?: return
        targetEngine.preload(media, source)
    }

    override fun prepare() {
        ensureNotReleased()
        requireActiveEngine().prepare()
    }

    override fun play() {
        ensureNotReleased()
        requireActiveEngine().play()
    }

    override fun pause() {
        ensureNotReleased()
        requireActiveEngine().pause()
    }

    override fun stop(reset: Boolean) {
        ensureNotReleased()
        requireActiveEngine().stop(reset)
    }

    override fun seekTo(positionMs: Long) {
        ensureNotReleased()
        requireActiveEngine().seekTo(positionMs)
    }

    override fun setSpeed(speed: Float) {
        ensureNotReleased()
        requireActiveEngine().setSpeed(speed)
    }

    override fun setLooping(looping: Boolean) {
        ensureNotReleased()
        requireActiveEngine().setLooping(looping)
    }

    /** 音量/静音是用户对“播放器”整体的偏好，广播给所有 engine，避免切换媒体类型后丢失设置。 */
    override fun setVolume(volume: Float) {
        ensureNotReleased()
        uniqueEngines.forEach { it.setVolume(volume) }
    }

    override fun setMuted(muted: Boolean) {
        ensureNotReleased()
        uniqueEngines.forEach { it.setMuted(muted) }
    }

    override fun attachVideoOutput(output: PlayerVideoOutput?) {
        ensureNotReleased()
        requireActiveEngine().attachVideoOutput(output)
    }

    override fun setListener(listener: PlayerEventListener?) {
        externalListener = listener
    }

    override fun setTelemetry(telemetry: PlaybackTelemetry?) {
        uniqueEngines.forEach { it.setTelemetry(telemetry) }
    }

    override fun getSnapshot(): PlaybackSnapshot {
        return engines[activeType]?.getSnapshot() ?: PlaybackSnapshot()
    }

    override fun getCapabilities(): PlayerCapabilities {
        return engines[activeType]?.getCapabilities() ?: PlayerCapabilities()
    }

    override fun release() {
        if (released) {
            return
        }
        released = true
        uniqueEngines.forEach { it.release() }
        externalListener = null
    }

    private fun engineTypeFor(media: PlayableMedia): MediaType {
        return if (engines.containsKey(media.mediaType)) media.mediaType else defaultType
    }

    private fun switchActiveTypeIfNeeded(targetType: MediaType) {
        if (targetType == activeType) {
            return
        }
        val previousType = activeType
        // 先翻转 activeType，再 stop 旧 engine：
        // 旧 engine.stop() 内部会同步触发 onStateChanged 等回调，
        // 如果翻转发生在 stop 之后，这条回调会在翻转前判定为“仍然活跃”而被错误转发出去。
        activeType = targetType
        // 新旧类型如果共用同一个 engine 实例（例如 AUDIO/VIDEO 都走 Media3），
        // 不应该 stop 这个实例——它接下来马上要被新类型继续使用。
        if (engines[previousType] !== engines[targetType]) {
            engines[previousType]?.stop(reset = true)
        }
    }

    private fun requireActiveEngine(): PlayerEngine {
        return engines.getValue(activeType)
    }

    private fun ensureNotReleased() {
        check(!released) { "RoutingPlayerEngine has already been released." }
    }
}
