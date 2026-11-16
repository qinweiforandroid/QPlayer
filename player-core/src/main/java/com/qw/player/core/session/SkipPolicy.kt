package com.qw.player.core.session

import kotlin.random.Random

/**
 * 切歌索引计算策略。
 *
 * 不同 [PlaybackMode] 组合（随机 / 单曲重复 / 列表循环 / 不循环）本质上只是
 * “从当前索引走到哪一项”的不同算法，因此建模成策略对象，
 * 由 [PlaybackMode.skipPolicy] 根据模式生成，[DefaultPlaybackSession] 不再写分支。
 */
internal interface SkipPolicy {
    /** 计算下一首索引；返回 null 表示不切歌。 */
    fun nextIndex(currentIndex: Int, size: Int, auto: Boolean): Int?

    /** 计算上一首索引；返回 null 表示不切歌。 */
    fun previousIndex(currentIndex: Int, size: Int, auto: Boolean): Int?
}

/** 按队列顺序移动；[RepeatMode.ALL] 时越界绕回另一端。 */
internal class LinearSkipPolicy(private val repeatMode: RepeatMode) : SkipPolicy {

    override fun nextIndex(currentIndex: Int, size: Int, auto: Boolean): Int? {
        val wrapTarget = if (repeatMode == RepeatMode.ALL) 0 else null
        return step(currentIndex, size, delta = 1, wrapTarget = wrapTarget)
    }

    override fun previousIndex(currentIndex: Int, size: Int, auto: Boolean): Int? {
        val wrapTarget = if (repeatMode == RepeatMode.ALL) size - 1 else null
        return step(currentIndex, size, delta = -1, wrapTarget = wrapTarget)
    }

    private fun step(currentIndex: Int, size: Int, delta: Int, wrapTarget: Int?): Int? {
        if (size <= 0) {
            return null
        }
        if (currentIndex !in 0 until size) {
            return 0
        }
        val target = currentIndex + delta
        return if (target in 0 until size) target else wrapTarget
    }
}

/** 随机跳到任意其他项；队列不足两项时退回 [fallback]。 */
internal class ShuffleSkipPolicy(private val fallback: SkipPolicy) : SkipPolicy {

    override fun nextIndex(currentIndex: Int, size: Int, auto: Boolean): Int? =
        pickRandomOr(currentIndex, size, auto, fallback::nextIndex)

    override fun previousIndex(currentIndex: Int, size: Int, auto: Boolean): Int? =
        pickRandomOr(currentIndex, size, auto, fallback::previousIndex)

    private fun pickRandomOr(
        currentIndex: Int,
        size: Int,
        auto: Boolean,
        fallbackPolicy: (Int, Int, Boolean) -> Int?
    ): Int? {
        if (size <= 1 || currentIndex !in 0 until size) {
            return fallbackPolicy(currentIndex, size, auto)
        }
        var candidate = currentIndex
        while (candidate == currentIndex) {
            candidate = Random.nextInt(size)
        }
        return candidate
    }
}

/**
 * [RepeatMode.ONE] 只约束自动切歌：播放结束时停留在当前项；
 * 用户手动切歌仍然照常前进，因此用装饰器叠加在真实策略之外。
 */
internal class AutoRepeatOneSkipPolicy(private val inner: SkipPolicy) : SkipPolicy {

    override fun nextIndex(currentIndex: Int, size: Int, auto: Boolean): Int? =
        if (auto && size > 0 && currentIndex in 0 until size) currentIndex
        else inner.nextIndex(currentIndex, size, auto)

    override fun previousIndex(currentIndex: Int, size: Int, auto: Boolean): Int? =
        if (auto && size > 0 && currentIndex in 0 until size) currentIndex
        else inner.previousIndex(currentIndex, size, auto)
}

/** 当前模式下的切歌策略，每次调用都会生成一个轻量对象。 */
internal fun PlaybackMode.skipPolicy(): SkipPolicy {
    val linear = LinearSkipPolicy(repeatMode)
    val positional = if (shuffleEnabled) ShuffleSkipPolicy(linear) else linear
    return if (repeatMode == RepeatMode.ONE) AutoRepeatOneSkipPolicy(positional) else positional
}
