package com.qw.player.demo.runtime

import com.qw.player.core.common.PlayLog
import com.qw.player.core.telemetry.PlaybackTelemetry

/**
 * 演示用的可观测性实现：把内核事件打到 Logcat。
 *
 * 真实项目里替换成友盟 / 自建上报即可，
 * 只需要实现 [PlaybackTelemetry]，core 与 media3 都不需要改。
 */
class LogcatPlaybackTelemetry : PlaybackTelemetry {

    override fun onPlaybackResult(event: PlaybackTelemetry.PlaybackResultEvent) {
        PlayLog.d(
            "Telemetry > result outcome=${event.outcome}" +
                " media=${event.context.mediaId} source=${event.context.sourceId}" +
                " playedMs=${event.playedDurationMs} error=${event.error?.code}"
        )
    }

    override fun onFirstFrame(event: PlaybackTelemetry.FirstFrameEvent) {
        PlayLog.d(
            "Telemetry > firstFrame media=${event.context.mediaId}" +
                " ttffMs=${event.timeToFirstFrameMs}"
        )
    }

    override fun onStall(event: PlaybackTelemetry.StallEvent) {
        PlayLog.d(
            "Telemetry > stall media=${event.context.mediaId}" +
                " positionMs=${event.positionMs} stallMs=${event.stallDurationMs}"
        )
    }

    override fun onError(event: PlaybackTelemetry.ErrorEvent) {
        PlayLog.d(
            "Telemetry > error media=${event.context.mediaId}" +
                " code=${event.error.code} domain=${event.error.domain}" +
                " kind=${event.error.extras["errorKind"]}"
        )
    }
}
