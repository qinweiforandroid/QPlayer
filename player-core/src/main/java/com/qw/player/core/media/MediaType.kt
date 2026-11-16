package com.qw.player.core.media

/**
 * 新协议中的媒体大类。
 *
 * 这个字段只应该用于真正与媒体形态相关的能力判断，比如：
 * - 是否需要视频输出
 * - 默认交互策略是否更偏向音频或视频
 * - 由 [com.qw.player.core.engine.RoutingPlayerEngine] 决定交给哪个 engine 执行
 *
 * 它不承载展示信息，也不承载具体播放实现。
 */
enum class MediaType {
    AUDIO,
    VIDEO,

    /** 文本朗读（TTS）类媒体，正文通过 [PlaySource.inlineText] 或远程 url 提供。 */
    TEXT
}
