package com.qw.player.core.source

import com.qw.player.core.engine.PlaybackError
import com.qw.player.core.media.PlayableMedia
import com.qw.player.core.media.ResolvedMedia

/**
 * 把业务媒体对象解析为真正可播放 source 的协议边界。
 *
 * 它负责承接：
 * - 签名 URL 获取
 * - 多 source 构造
 * - DRM 信息下发
 * - 字幕补充
 * - source 过期刷新
 */
interface MediaSourceResolver {
    /**
     * 异步解析媒体对象。
     *
     * 返回的 source 应该是当前 engine 立刻可消费的可播放结果。
     */
    fun resolve(media: PlayableMedia, callback: Callback)

    /**
     * 异步解析回调。
     */
    interface Callback {
        /** 至少解析出一条可用 source 时回调成功。 */
        fun onSuccess(result: ResolvedMedia)

        /** 无法解析出可播放 source 集合时回调失败。 */
        fun onFailure(error: PlaybackError)
    }
}
