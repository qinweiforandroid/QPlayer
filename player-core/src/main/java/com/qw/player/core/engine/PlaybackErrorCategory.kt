package com.qw.player.core.engine

/**
 * 错误分类。
 *
 * 上层做“提示什么文案 / 要不要引导重试”这类决策时只需要看这个枚举，
 * 不必解析具体内核暴露的错误码或字符串常量。
 */
enum class PlaybackErrorCategory {
    /** 网络连通性问题。 */
    NETWORK,

    /** 资源 / 容器 / 清单格式问题，通常表现为“加载失败”。 */
    SOURCE,

    /** 解码失败。 */
    DECODE,

    /** 无法归类。 */
    UNKNOWN
}
