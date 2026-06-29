package com.qw.player.core.media

/**
 * 新协议中的媒体大类。
 *
 * 这个字段只应该用于真正与媒体形态相关的能力判断，比如：
 * - 是否需要视频输出
 * - 默认交互策略是否更偏向音频或视频
 */
enum class MediaType {
    AUDIO,
    VIDEO
}
