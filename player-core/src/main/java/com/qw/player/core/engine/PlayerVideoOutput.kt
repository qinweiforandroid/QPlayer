package com.qw.player.core.engine

import android.view.Surface

/**
 * 当前视频输出目标的协议层描述。
 *
 * core 层只暴露 engine 真正需要的最小 surface 能力，
 * 更丰富的 UI 语义不放在这里。
 */
data class PlayerVideoOutput(
    val surface: Surface?,
    val scaleMode: VideoScaleMode = VideoScaleMode.FIT
)
