package com.qw.player.core.engine

/**
 * 统一的播放状态枚举。
 *
 * 这是 V2 协议中给 UI、通知、埋点、业务层共同使用的状态来源。
 */
enum class PlaybackState {
    /** 当前没有可播放媒体，或者播放器已被重置为空状态。 */
    IDLE,

    /** 播放器已初始化并完成基础配置，但还没有开始 prepare。 */
    INITIALIZED,

    /** 媒体已挂载，播放器正在准备解码器、缓冲区和渲染链路。 */
    PREPARING,

    /** 媒体已经可播，但当前没有真正推进播放。 */
    READY,

    /** 媒体正在实际输出音频或视频帧。 */
    PLAYING,

    /** 媒体处于已准备状态，并被用户或策略暂停。 */
    PAUSED,

    /** 播放暂时卡住，正在等待更多数据。 */
    BUFFERING,

    /** 当前 source 已自然播放完成。 */
    ENDED,

    /** 播放出现错误，通常需要上层处理或重试。 */
    ERROR,

    /** 播放器已释放，不应再接收任何播放指令。 */
    RELEASED
}
