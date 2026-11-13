package com.qw.player.demo.cctv

/** 频道分类。 */
enum class ChannelCategory(val displayName: String) {
    CCTV("央视"),
    SATELLITE("卫视"),
    LOCAL("地方台")
}

/** 同一频道的一条线路 / 清晰度。 */
data class TvLine(
    val id: String,
    val label: String,
    val url: String
)

/** 单条 EPG 节目信息。 */
data class EpgEntry(
    val title: String,
    val startMs: Long,
    val endMs: Long
)

/** 直播频道。 */
data class TvChannel(
    val id: String,
    val name: String,
    val category: ChannelCategory,
    val logoUrl: String = "",
    val lines: List<TvLine> = emptyList(),
    val epg: List<EpgEntry> = emptyList()
) {
    /** logo 缺失时用名称首字做占位徽标。 */
    val badgeText: String
        get() = name.take(1)

    /** 当前时间对应的节目，没有数据时返回 null。 */
    fun currentEpg(nowMs: Long): EpgEntry? {
        return epg.firstOrNull { nowMs in it.startMs until it.endMs }
            ?: epg.firstOrNull { it.endMs > nowMs }
    }
}
