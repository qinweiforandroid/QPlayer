package com.qw.player.core.session

import com.qw.player.core.media.PlaySource
import com.qw.player.core.media.PlayableMedia

/**
 * 决定某个 [PlayableMedia] 应该播放哪条 [PlaySource]。
 *
 * [DefaultPlaybackSession] 只负责调用这个策略并驱动 engine，
 * 不再自己内联“挑哪条 source”的判断逻辑。
 */
interface SourceSelector {
    /** 选出媒体首次播放时应使用的 source。 */
    fun selectInitial(media: PlayableMedia): PlaySource?

    /**
     * 当前 source 播放失败后，选出下一条用于降级重试的 source。
     *
     * 没有可用的降级 source 时返回 null。
     */
    fun selectFallback(
        media: PlayableMedia,
        currentSource: PlaySource,
        failedSourceIds: Set<String>
    ): PlaySource?
}

/**
 * 默认降级策略：优先在当前 source 所属的 [PlaySource.qualityId] 分组内找未失败过的线路，
 * 分组内没有可用项了才跨分组兜底——避免线路失败重试时意外把清晰度也换掉。
 */
class DefaultSourceSelector : SourceSelector {

    override fun selectInitial(media: PlayableMedia): PlaySource? {
        return media.resolveDefaultSource()
    }

    override fun selectFallback(
        media: PlayableMedia,
        currentSource: PlaySource,
        failedSourceIds: Set<String>
    ): PlaySource? {
        val untried = media.playSources.filter { it.sourceId !in failedSourceIds }
        val sameQuality = untried.firstOrNull { it.qualityId == currentSource.qualityId }
        return sameQuality ?: untried.firstOrNull()
    }
}
