package com.qw.player.demo.cctv

/**
 * 直播频道数据。
 *
 * 当前为内置清单：所有线路均已实测可出流（HLS），
 * 每个频道保留多条线路用于切换与失败降级。
 * 接入远端接口时，只要产出 [TvChannel] 列表即可替换这里。
 */
object TvChannelRepository {

    fun loadChannels(): List<TvChannel> = CHANNELS

    fun fallbackForUi(): List<TvChannel> = CHANNELS

    fun byCategory(category: ChannelCategory): List<TvChannel> =
        CHANNELS.filter { it.category == category }

    fun findById(channelId: String): TvChannel? = CHANNELS.firstOrNull { it.id == channelId }

    fun indexOf(channelId: String): Int = CHANNELS.indexOfFirst { it.id == channelId }

    private fun channel(
        id: String,
        name: String,
        category: ChannelCategory,
        vararg lines: Pair<String, String>
    ): TvChannel {
        return TvChannel(
            id = id,
            name = name,
            category = category,
            lines = lines.mapIndexed { index, (label, url) ->
                TvLine(id = "${id}_${index + 1}", label = label, url = url)
            }
        )
    }

    private val CHANNELS: List<TvChannel> = listOf(
        channel(
            "cctv1", "CCTV-1 综合", ChannelCategory.CCTV,
            "线路1" to "http://101.66.194.88:9901/tsfile/live/0001_1.m3u8?key=txiptv&playlive=0&authid=0",
            "线路2" to "http://153.0.171.163:9901/tsfile/live/0001_1.m3u8?key=txiptv&playlive=1&authid=0",
            "线路3" to "http://173.208.212.130:8181/720p/cctv1.m3u8"
        ),
        channel(
            "cctv2", "CCTV-2 财经", ChannelCategory.CCTV,
            "线路1" to "http://113.90.154.189:9901/tsfile/live/0002_1.m3u8?key=txiptv&playlive=1&authid=0",
            "线路2" to "http://120.40.39.246:352/newlive/live/hls/2/live.m3u8",
            "线路3" to "http://173.208.212.130:8181/1080p/cctv2.m3u8"
        ),
        channel(
            "cctv3", "CCTV-3 综艺", ChannelCategory.CCTV,
            "线路1" to "http://124.228.160.40:9901/tsfile/live/0019_1.m3u8?key=txiptv&playlive=1&authid=0",
            "线路2" to "http://192.151.150.154/live/cctv3hd.m3u8",
            "线路3" to "http://45.192.97.170:8880/play/391.m3u8"
        ),
        channel(
            "cctv4", "CCTV-4 中文国际", ChannelCategory.CCTV,
            "线路1" to "http://113.90.154.189:9901/tsfile/live/0004_1.m3u8?key=txiptv&playlive=1&authid=0",
            "线路2" to "http://124.90.45.52:9901/tsfile/live/0004_1.m3u8?key=txiptv&playlive=1&authid=0",
            "线路3" to "http://153.0.171.163:9901/tsfile/live/0004_1.m3u8?key=txiptv&playlive=1&authid=0"
        ),
        channel(
            "cctv5", "CCTV-5 体育", ChannelCategory.CCTV,
            "线路1" to "http://1.24.39.180:9003/hls/5/index.m3u8",
            "线路2" to "http://113.90.154.189:9901/tsfile/live/0005_1.m3u8?key=txiptv&playlive=1&authid=0",
            "线路3" to "http://gmxw.7766.org:808/hls/93/index.m3u8"
        ),
        channel(
            "cctv5p", "CCTV-5+ 体育赛事", ChannelCategory.CCTV,
            "线路1" to "http://124.228.160.40:9901/tsfile/live/0016_1.m3u8?key=txiptv&playlive=1&authid=0",
            "线路2" to "http://219.147.245.238:25480/newlive/live/hls/6/live.m3u8",
            "线路3" to "http://fuxin.yunjifei.top:999/tsfile/live/0016_1.m3u8?key=txiptv"
        ),
        channel(
            "cctv6", "CCTV-6 电影", ChannelCategory.CCTV,
            "线路1" to "http://113.90.154.189:9901/tsfile/live/0006_1.m3u8?key=txiptv&playlive=1&authid=0",
            "线路2" to "http://120.40.39.246:352/newlive/live/hls/7/live.m3u8",
            "线路3" to "http://173.208.212.130:8181/720p/cctv6.m3u8"
        ),
        channel(
            "cctv7", "CCTV-7 国防军事", ChannelCategory.CCTV,
            "线路1" to "http://120.40.39.246:352/newlive/live/hls/8/live.m3u8",
            "线路2" to "http://124.90.45.52:9901/tsfile/live/0007_1.m3u8?key=txiptv&playlive=1&authid=0",
            "线路3" to "http://183.129.255.66:8480/hls/8/index.m3u8"
        ),
        channel(
            "cctv8", "CCTV-8 电视剧", ChannelCategory.CCTV,
            "线路1" to "http://124.228.160.40:9901/tsfile/live/0008_1.m3u8?key=txiptv&playlive=1&authid=0",
            "线路2" to "http://222.223.41.27:8888/hls/8/index.m3u8",
            "线路3" to "http://gmxw.7766.org:808/hls/96/index.m3u8"
        ),
        channel(
            "cctv9", "CCTV-9 纪录", ChannelCategory.CCTV,
            "线路1" to "http://120.40.39.246:352/newlive/live/hls/10/live.m3u8",
            "线路2" to "http://153.0.171.163:9901/tsfile/live/1008_1.m3u8?key=txiptv&playlive=1&authid=0",
            "线路3" to "http://219.147.245.238:25480/newlive/live/hls/10/live.m3u8"
        ),
        channel(
            "cctv10", "CCTV-10 科教", ChannelCategory.CCTV,
            "线路1" to "http://113.90.154.189:9901/tsfile/live/0010_1.m3u8?key=txiptv&playlive=1&authid=0",
            "线路2" to "http://222.223.41.27:8888/hls/10/index.m3u8",
            "线路3" to "http://153.0.171.163:9901/tsfile/live/1009_1.m3u8?key=txiptv&playlive=1&authid=0"
        ),
        channel(
            "cctv11", "CCTV-11 戏曲", ChannelCategory.CCTV,
            "线路1" to "http://1.24.39.180:9003/hls/11/index.m3u8",
            "线路2" to "http://120.40.39.246:352/newlive/live/hls/12/live.m3u8",
            "线路3" to "http://124.90.45.52:9901/tsfile/live/0011_1.m3u8?key=txiptv&playlive=1&authid=0"
        ),
        channel(
            "cctv12", "CCTV-12 社会与法", ChannelCategory.CCTV,
            "线路1" to "http://120.40.39.246:352/newlive/live/hls/13/live.m3u8",
            "线路2" to "http://124.165.251.82:85/tsfile/live/0012_1.m3u8?key=txiptv&playlive=1&authid=0",
            "线路3" to "http://222.223.41.27:8888/hls/12/index.m3u8"
        ),
        channel(
            "cctv13", "CCTV-13 新闻", ChannelCategory.CCTV,
            "线路1" to "http://120.40.39.246:352/newlive/live/hls/14/live.m3u8",
            "线路2" to "http://183.129.255.66:8480/hls/14/index.m3u8",
            "线路3" to "http://222.223.41.27:8888/hls/13/index.m3u8"
        ),
        channel(
            "cctv14", "CCTV-14 少儿", ChannelCategory.CCTV,
            "线路1" to "http://1.24.39.180:9003/hls/14/index.m3u8",
            "线路2" to "http://124.228.160.40:9901/tsfile/live/0014_1.m3u8?key=txiptv&playlive=1&authid=0",
            "线路3" to "http://183.129.255.66:8480/hls/15/index.m3u8"
        ),
        channel(
            "cctv15", "CCTV-15 音乐", ChannelCategory.CCTV,
            "线路1" to "http://1.24.39.180:9003/hls/15/index.m3u8",
            "线路2" to "http://113.90.154.189:9901/tsfile/live/0015_1.m3u8?key=txiptv&playlive=1&authid=0",
            "线路3" to "http://120.40.39.246:352/newlive/live/hls/16/live.m3u8"
        ),
        channel(
            "cctv16", "CCTV-16 奥林匹克", ChannelCategory.CCTV,
            "线路1" to "http://1.24.39.180:9003/hls/60/index.m3u8",
            "线路2" to "http://219.147.245.238:25480/newlive/live/hls/17/live.m3u8",
            "线路3" to "http://gmxw.7766.org:808/hls/169/index.m3u8"
        ),
        channel(
            "cctv17", "CCTV-17 农业农村", ChannelCategory.CCTV,
            "线路1" to "http://1.24.39.180:9003/hls/61/index.m3u8",
            "线路2" to "http://113.90.154.189:9901/tsfile/live/0017_1.m3u8?key=txiptv&playlive=1&authid=0",
            "线路3" to "http://183.129.255.66:8480/hls/18/index.m3u8"
        ),

        channel(
            "dfl", "东方卫视", ChannelCategory.SATELLITE,
            "线路1" to "http://bp-resource-dfl.bestv.cn/148/3/video.m3u8"
        ),
        channel(
            "zj", "浙江卫视", ChannelCategory.SATELLITE,
            "线路1" to "https://ali-m-l.cztv.com/channels/lantian/channel001/1080p.m3u8"
        ),
        channel(
            "hlj", "黑龙江卫视", ChannelCategory.SATELLITE,
            "线路1" to "https://idclive.hljtv.com:4430/live/hljws_own.m3u8"
        ),
        channel(
            "nmg", "内蒙古卫视", ChannelCategory.SATELLITE,
            "线路1" to "http://play1-qk.nmtv.cn/live/1769652018126032.m3u8"
        ),
        channel(
            "sx", "山西卫视", ChannelCategory.SATELLITE,
            "线路1" to "http://m3u8.channel.wsrtv.com.cn/cms/videos/nmip-media/channellive/channel7/playlist.m3u8"
        ),
        channel(
            "jx", "江西卫视", ChannelCategory.SATELLITE,
            "线路1" to "http://112.27.5.218:9901/tsfile/live/faacts/0138_1.m3u8?key=txiptv&playlive=1&authid=0"
        ),
        channel(
            "xz", "西藏卫视", ChannelCategory.SATELLITE,
            "线路1" to "http://112.27.5.218:9901/tsfile/live/0111_1.m3u8?key=txiptv&playlive=1&authid=0"
        ),
        channel(
            "ln", "辽宁卫视", ChannelCategory.SATELLITE,
            "线路1" to "http://61.136.172.236:9901/tsfile/live/0121_1.m3u8?key=txiptv&playlive=1&authid=0"
        ),
        channel(
            "gs", "甘肃卫视", ChannelCategory.SATELLITE,
            "线路1" to "http://218.13.170.98:9901/tsfile/live/0141_1.m3u8"
        ),
        channel(
            "xj", "新疆卫视", ChannelCategory.SATELLITE,
            "线路1" to "http://218.84.12.186:8001/hls/main/playlist.m3u8"
        ),
        channel(
            "dn", "东南卫视", ChannelCategory.SATELLITE,
            "线路1" to "http://live.zohi.tv/video/s10001-fztv-3/index.m3u8"
        ),
        channel(
            "yb", "延边卫视", ChannelCategory.SATELLITE,
            "线路1" to "https://srs.iyb983.cn:443/video/CYS/index.m3u8"
        ),
        channel(
            "ad", "安多卫视", ChannelCategory.SATELLITE,
            "线路1" to "https://liveout.xntv.tv/a65jur/96iln2.m3u8"
        ),
        channel(
            "ah", "安徽卫视", ChannelCategory.SATELLITE,
            "线路1" to "http://gmxw.7766.org:808/hls/40/index.m3u8"
        ),
        channel(
            "sz", "深圳卫视", ChannelCategory.SATELLITE,
            "线路1" to "http://gmxw.7766.org:808/hls/45/index.m3u8"
        ),

        channel(
            "zj_news", "浙江新闻", ChannelCategory.LOCAL,
            "线路1" to "http://ali-m-l.cztv.com/channels/lantian/channel002/1080p.m3u8"
        ),
        channel(
            "zj_qianjiang", "浙江钱江", ChannelCategory.LOCAL,
            "线路1" to "http://ali-m-l.cztv.com/channels/lantian/channel003/1080p.m3u8"
        ),
        channel(
            "zj_jingshi", "浙江经视", ChannelCategory.LOCAL,
            "线路1" to "http://ali-m-l.cztv.com/channels/lantian/channel004/1080p.m3u8"
        ),
        channel(
            "zj_edu", "浙江教育", ChannelCategory.LOCAL,
            "线路1" to "http://ali-m-l.cztv.com/channels/lantian/channel005/1080p.m3u8"
        ),
        channel(
            "zj_kids", "浙江少儿", ChannelCategory.LOCAL,
            "线路1" to "http://ali-m-l.cztv.com/channels/lantian/channel006/1080p.m3u8"
        ),
        channel(
            "js_city", "江苏城市", ChannelCategory.LOCAL,
            "线路1" to "https://tylive.kan0512.com/norecord/csztv4k_4k.m3u8"
        )
    )
}
