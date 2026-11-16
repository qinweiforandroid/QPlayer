package com.qw.player.runtime

import android.content.Context
import android.media.AudioManager
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat

/**
 * 音频焦点持��方。
 *
 * 把“抢焦点”和“失焦后怎么响应”收在一个协议后面，runtime 只表达意图：
 * 起播前 [acquire]，业务侧暂停时 [onPlaybackPaused]，停止时 [release]。
 * [AudioManager] 的请求参数、焦点回调、以及“下次 GAIN 要不要续播”这个状态
 * 都由实现类持有，不再散落在 runtime 里。
 */
public interface AudioFocusController {
    /** 申请焦点；返回 false 表示焦点未拿到，调用方不应起播。 */
    fun acquire(): Boolean

    /**
     * 业务侧主动暂停，[resumeOnGain] 决定下一次 GAIN 是否自动续播。
     *
     * 视频页传 false（被占用即暂停且不恢复），音频页跟随构造配置。
     */
    fun onPlaybackPaused(resumeOnGain: Boolean)

    /** 放弃焦点；之后可再次 [acquire]。 */
    fun release()
}

public fun interface AudioFocusControllerFactory {
    fun create(
        context: Context,
        onResumePlayback: () -> Unit,
        onPausePlayback: () -> Unit
    ): AudioFocusController
}

/**
 * 基于 [AudioManagerCompat] 的实现。
 *
 * [AudioFocusRequestCompat] 只在首次 [acquire] 时构建并复用：复用同一个 request 不会
 * 触发“自我顶替”，否则新请求入栈会让系统向被顶掉的旧请求派发 [AudioManager.AUDIOFOCUS_LOSS]，
 * 而旧请求绑的是同一个监听器，最终把自己刚起的播放暂停掉。
 *
 * @param defaultResumeOnGain 焦点被短暂占用（可 duck）后是否自动续播。
 */
public class AndroidAudioFocusController(
    private val context: Context,
    private val defaultResumeOnGain: Boolean,
    private val onResumePlayback: () -> Unit,
    private val onPausePlayback: () -> Unit
) : AudioFocusController {

    private var request: AudioFocusRequestCompat? = null

    /** 当前是否已持有焦点；持有期间重复 [acquire] 是幂等的。 */
    private var hasFocus = false

    /** 下一次 AUDIOFOCUS_GAIN 到达时是否续播；由最近一次暂停意图决定。 */
    private var resumeOnGain = defaultResumeOnGain

    override fun acquire(): Boolean {
        if (hasFocus) {
            return true
        }
        val audioManager = audioManager() ?: return true
        val currentRequest = request ?: buildRequest().also { request = it }
        val granted = AudioManagerCompat.requestAudioFocus(audioManager, currentRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        hasFocus = granted
        return granted
    }

    override fun onPlaybackPaused(resumeOnGain: Boolean) {
        this.resumeOnGain = resumeOnGain
    }

    override fun release() {
        hasFocus = false
        val audioManager = audioManager() ?: return
        val current = request ?: return
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, current)
    }

    private fun buildRequest(): AudioFocusRequestCompat {
        return AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributesCompat.Builder()
                    .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(::onFocusChange)
            .build()
    }

    private fun onFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                if (resumeOnGain) {
                    onResumePlayback()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                resumeOnGain = false
                onPausePlayback()
            }

            // 短暂占用：暂停后按构造配置决定是否续播，视频页固定不续播。
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                resumeOnGain = defaultResumeOnGain
                onPausePlayback()
            }
        }
    }

    private fun audioManager(): AudioManager? {
        return context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
}
