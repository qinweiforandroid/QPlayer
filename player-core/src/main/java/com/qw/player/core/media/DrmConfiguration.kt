package com.qw.player.core.media

/**
 * 某个可播放 source 对应的 DRM 配置。
 *
 * 之所以挂在 source 层，是因为不同清晰度、不同 CDN、不同分发链路可能会有不同的授权信息。
 */
data class DrmConfiguration(
    val scheme: String,
    val licenseUrl: String,
    val requestHeaders: Map<String, String> = emptyMap(),
    val multiSession: Boolean = false
)
