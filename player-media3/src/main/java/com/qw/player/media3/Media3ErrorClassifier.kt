package com.qw.player.media3

import androidx.media3.common.PlaybackException
import com.qw.player.core.engine.PlaybackErrorCategory

/**
 * 把 Media3 的原始错误码归一为内核错误分类，并给出重试策略。
 *
 * 抽成独立对象是为了让这段判定逻辑可以脱离 ExoPlayer 实例做纯逻辑单测。
 */
internal object Media3ErrorClassifier {

    /** 网络连通类错误。 */
    const val ERROR_KIND_NETWORK = "network"

    /** 资源 / 容器 / 清单格式错误，通常等同于“视频加载失败”。 */
    const val ERROR_KIND_SOURCE = "source"

    /** 解码失败。 */
    const val ERROR_KIND_DECODE = "decode"

    /** 无法归类的错误。 */
    const val ERROR_KIND_UNKNOWN = "unknown"

    /** 可恢复错误最多自动重试次数。 */
    const val MAX_RETRY_ATTEMPTS = 3

    /** 重试退避基准间隔，按 2^(attempt-1) 递增。 */
    const val RETRY_BASE_DELAY_MS = 1_000L

    fun classify(errorCode: Int): String {
        return when {
            errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION ||
                errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> ERROR_KIND_NETWORK

            errorCode >= 2000 && errorCode < 4000 -> ERROR_KIND_SOURCE
            errorCode >= 4000 && errorCode < 5000 -> ERROR_KIND_DECODE

            else -> ERROR_KIND_UNKNOWN
        }
    }

    /**
     * 只有网络和资源类错误值得原地重试一次；
     * 解码失败重试不会自愈，直接交给上层。
     */
    fun isRecoverable(kind: String): Boolean {
        return kind == ERROR_KIND_NETWORK || kind == ERROR_KIND_SOURCE
    }

    /** 把内部字符串分类映射成 core 的类型化分类，供上层做文案决策。 */
    fun toCategory(kind: String): PlaybackErrorCategory {
        return when (kind) {
            ERROR_KIND_NETWORK -> PlaybackErrorCategory.NETWORK
            ERROR_KIND_SOURCE -> PlaybackErrorCategory.SOURCE
            ERROR_KIND_DECODE -> PlaybackErrorCategory.DECODE
            else -> PlaybackErrorCategory.UNKNOWN
        }
    }

    /** 第 [attempt] 次重试（从 1 开始）的退避时长。 */
    fun retryDelayMs(attempt: Int): Long {
        val safeAttempt = attempt.coerceAtLeast(1)
        return RETRY_BASE_DELAY_MS shl (safeAttempt - 1)
    }
}
