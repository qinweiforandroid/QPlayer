package com.qw.player.core.engine

/**
 * 视频缩放策略。
 *
 * 这里只描述协议层语义，具体 UI 组件可以映射为各自平台的 resizeMode。
 */
enum class VideoScaleMode {
    FIT,
    FILL,
    ZOOM
}
