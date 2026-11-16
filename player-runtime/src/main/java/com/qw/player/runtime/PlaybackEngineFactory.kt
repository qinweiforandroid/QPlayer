package com.qw.player.runtime

import android.content.Context
import com.qw.player.core.engine.PlayerConfig
import com.qw.player.core.engine.RoutingPlayerEngine

/**
 * 装配 engine 的协议。
 *
 * 「有哪些 engine、mediaType 怎么映射」属于接入方的装配策略，
 * runtime 只要求拿到一个已经 initialize 好的路由 engine。
 */
fun interface PlaybackEngineFactory {
    fun create(context: Context, config: PlayerConfig): RoutingPlayerEngine
}
