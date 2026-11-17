package com.qw.player.tv.data

/**
 * 点播演示数据。
 *
 * 当前为本地 mock，接入真实接口时替换这里即可，页面只依赖 [VodPlaylist]。
 */
object VodRepository {

    private const val SINTEL = "https://media.w3.org/2010/05/sintel/trailer.mp4"

    fun loadPlaylist(): VodPlaylist {
        return VodPlaylist(
            title = "演示点播 · 多P与清晰度切换",
            coverUrl = "",
            author = "QPlayer Demo",
            episodes = listOf(
                episode("p1", "P1 · 预告片"),
                episode("p2", "P2 · 第二集"),
                episode("p3", "P3 · 第三集")
            )
        )
    }

    private fun episode(id: String, title: String): VodEpisode {
        return VodEpisode(
            id = id,
            title = title,
            qualities = listOf(
                VodQuality(id = "hd", label = "高清 1080P", url = SINTEL),
                VodQuality(id = "sd", label = "标清 720P", url = SINTEL),
                VodQuality(id = "ld", label = "流畅 480P", url = SINTEL)
            )
        )
    }
}
