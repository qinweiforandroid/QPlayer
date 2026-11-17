package com.qw.player.core.live

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveChannelJsonTest {

    private fun channelJson(
        id: String = "cctv1",
        name: String = "CCTV-1 综合",
        category: String = "CCTV",
        lines: String = """[{"label":"线路1","url":"http://a.test/1.m3u8"}]"""
    ) = """{"id":"$id","name":"$name","category":"$category","lines":$lines}"""

    private fun manifest(vararg channels: String) =
        """{"schemaVersion":1,"channels":[${channels.joinToString(",")}]}"""

    @Test
    fun `parses channels and derives line ids`() {
        val channels = LiveChannelJson.parse(
            manifest(
                channelJson(
                    lines = """[{"label":"线路1","url":"http://a.test/1.m3u8"},""" +
                        """{"label":"线路2","url":"http://a.test/2.m3u8"}]"""
                )
            )
        )

        assertEquals(1, channels.size)
        val channel = channels.first()
        assertEquals("cctv1", channel.id)
        assertEquals(ChannelCategory.CCTV, channel.category)
        assertEquals(listOf("cctv1_1", "cctv1_2"), channel.lines.map { it.id })
    }

    @Test
    fun `keeps declaration order`() {
        val channels = LiveChannelJson.parse(
            manifest(channelJson(id = "b"), channelJson(id = "a"))
        )
        assertEquals(listOf("b", "a"), channels.map { it.id })
    }

    @Test
    fun `accepts legacy manifests without logoUrl or lines labels`() {
        val channels = LiveChannelJson.parse(
            manifest(channelJson(lines = """[{"url":"https://a.test/x.m3u8"}]"""))
        )
        assertEquals("线路1", channels.first().lines.first().label)
        assertEquals("", channels.first().logoUrl)
    }

    @Test(expected = JSONException::class)
    fun `rejects duplicate channel ids`() {
        LiveChannelJson.parse(manifest(channelJson(id = "cctv1"), channelJson(id = "cctv1")))
    }

    @Test(expected = JSONException::class)
    fun `rejects channel without lines`() {
        LiveChannelJson.parse(manifest(channelJson(lines = "[]")))
    }

    @Test(expected = JSONException::class)
    fun `rejects non http line urls`() {
        LiveChannelJson.parse(
            manifest(channelJson(lines = """[{"label":"线路1","url":"rtmp://a.test/1"}]"""))
        )
    }

    @Test(expected = JSONException::class)
    fun `rejects unknown category`() {
        LiveChannelJson.parse(manifest(channelJson(category = "MOVIE")))
    }

    @Test(expected = JSONException::class)
    fun `rejects unsupported schema version`() {
        LiveChannelJson.parse("""{"schemaVersion":2,"channels":[${channelJson()}]}""")
    }

    @Test
    fun `repository keeps previous data when replace fails`() {
        assertTrue(LiveChannelRepository.replace(manifest(channelJson(id = "keep"))))
        assertEquals(listOf("keep"), LiveChannelRepository.loadChannels().map { it.id })

        // 坏数据不应该把已有清单清空
        LiveChannelRepository.replace("{not json")
        assertEquals(listOf("keep"), LiveChannelRepository.loadChannels().map { it.id })
    }
}
