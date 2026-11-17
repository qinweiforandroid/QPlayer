package com.qw.player.core.live

import android.content.Context
import com.qw.player.core.common.PlayLog

/**
 * 直播频道仓库。
 *
 * 数据来自 assets 里的 JSON（默认 `live/channels.json`），应用启动时调用一次
 * [initialize] 载入到内存，之后各处同步读取，避免每次重组都去读磁盘。
 *
 * 设计取向是「宁可只保留旧数据，也不整页空掉」：解析失败时保留上一份可用清单，
 * 首次启动就失败则返回空列表，由页面自己决定提示文案。
 */
object LiveChannelRepository {

    @Volatile
    private var channelList: List<TvChannel> = emptyList()

    /** 本次是否已经从 assets 成功载入过。 */
    @Volatile
    var isLoaded: Boolean = false
        private set

    /**
     * 从 assets 载入频道清单，返回是否成功。
     * 重复调用是幂等的，失败不会清空已有数据。
     */
    fun initialize(context: Context, assetPath: String = DEFAULT_ASSET_PATH): Boolean {
        val json = runCatching {
            context.applicationContext.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrElse { error ->
            PlayLog.d("直播频道清单读取失败（$assetPath）: ${error.message}")
            return false
        }
        return replace(json)
    }

    /** 用一份原始 JSON 替换当前清单，供测试或远端下发复用。返回是否成功。 */
    fun replace(json: String): Boolean {
        return runCatching { LiveChannelJson.parse(json) }
            .onSuccess {
                channelList = it
                isLoaded = true
            }
            .onFailure { PlayLog.d("直播频道清单解析失败，沿用上一份数据: ${it.message}") }
            .isSuccess
    }

    fun loadChannels(): List<TvChannel> = channelList

    /** 给页面兜底用：和 [loadChannels] 同源，单独留名字方便将来区分本地缓存 / 远端。 */
    fun fallbackForUi(): List<TvChannel> = channelList

    fun byCategory(category: ChannelCategory): List<TvChannel> =
        channelList.filter { it.category == category }

    fun findById(channelId: String): TvChannel? = channelList.firstOrNull { it.id == channelId }

    fun indexOf(channelId: String): Int = channelList.indexOfFirst { it.id == channelId }

    const val DEFAULT_ASSET_PATH = "live/channels.json"
}
