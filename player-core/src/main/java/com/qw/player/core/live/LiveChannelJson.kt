package com.qw.player.core.live

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
/**
 * 直播源 JSON 协议。
 *
 * 结构（见 `data/live/channels.json`）：
 * ```json
 * {
 *   "schemaVersion": 1,
 *   "updatedAt": "2026-11-17T12:00:00+08:00",
 *   "channels": [
 *     {
 *       "id": "cctv1",
 *       "name": "CCTV-1 综合",
 *       "category": "CCTV",
 *       "logoUrl": "",
 *       "lines": [ { "label": "线路1", "url": "http://.../index.m3u8" } ]
 *     }
 *   ]
 * }
 * ```
 *
 * 解析是严格的：频道 id 重复、线路为空、分类未知都会抛 [JSONException]，
 * 让坏数据在启动时暴露，而不是在播放页降级成「没有频道」。
 */
object LiveChannelJson {

    const val SUPPORTED_SCHEMA_VERSION = 1

    /** 解析整份清单；返回按 JSON 顺序排列的频道列表。 */
    @Throws(JSONException::class)
    fun parse(json: String): List<TvChannel> {
        val root = JSONObject(json)
        val version = root.optInt("schemaVersion", 0)
        if (version !in 1..SUPPORTED_SCHEMA_VERSION) {
            throw JSONException("不支持的直播源版本: $version（当前支持 ≤ $SUPPORTED_SCHEMA_VERSION）")
        }
        val array = root.optJSONArray("channels") ?: JSONArray()
        val channels = ArrayList<TvChannel>(array.length())
        val seen = HashSet<String>()
        for (i in 0 until array.length()) {
            val channel = parseChannel(array.getJSONObject(i))
            if (!seen.add(channel.id)) throw JSONException("频道 id 重复: ${channel.id}")
            channels += channel
        }
        return channels
    }

    private fun parseChannel(obj: JSONObject): TvChannel {
        val id = obj.getString("id").trim()
        if (id.isEmpty()) throw JSONException("频道 id 不能为空")
        val name = obj.getString("name").trim()
        if (name.isEmpty()) throw JSONException("频道 $id 缺少 name")

        val rawLines = obj.optJSONArray("lines") ?: JSONArray()
        if (rawLines.length() == 0) throw JSONException("频道 $id 没有任何线路")
        val lines = (0 until rawLines.length()).map { index ->
            val line = rawLines.getJSONObject(index)
            val url = line.getString("url").trim()
            if (!url.startsWith("http")) {
                throw JSONException("频道 $id 第 ${index + 1} 条线路不是 http(s) 地址: $url")
            }
            TvLine(
                id = line.optString("id").takeIf { it.isNotBlank() } ?: "${id}_${index + 1}",
                label = line.optString("label").takeIf { it.isNotBlank() } ?: "线路${index + 1}",
                url = url
            )
        }

        val epgArray = obj.optJSONArray("epg") ?: JSONArray()
        val epg = (0 until epgArray.length()).mapNotNull { index ->
            val entry = epgArray.optJSONObject(index) ?: return@mapNotNull null
            val title = entry.optString("title")
            val start = entry.optLong("startMs", -1L)
            val end = entry.optLong("endMs", -1L)
            if (title.isBlank() || start < 0 || end <= start) null else EpgEntry(title, start, end)
        }

        val categoryKey = obj.optString("category")
        val category = ChannelCategory.entries.firstOrNull {
            it.name.equals(categoryKey.trim(), ignoreCase = true)
        } ?: throw JSONException("频道 $id 的分类未知: $categoryKey")

        return TvChannel(
            id = id,
            name = name,
            category = category,
            logoUrl = obj.optString("logoUrl"),
            lines = lines,
            epg = epg
        )
    }
}
