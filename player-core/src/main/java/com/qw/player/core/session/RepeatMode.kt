package com.qw.player.core.session

/**
 * 会话层的重复播放策略。
 *
 * 这个概念属于 session，而不是底层 engine。
 */
enum class RepeatMode {
    OFF,
    ONE,
    ALL
}
