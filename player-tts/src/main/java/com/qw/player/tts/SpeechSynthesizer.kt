package com.qw.player.tts

/**
 * [TextPlayerEngine] 依赖的语音合成抽象。
 *
 * 抽成接口是为了让 engine 的进度 / 状态 / 错误逻辑可以脱离 Android `TextToSpeech`
 * 单独单测，也方便后续替换成在线合成服务。
 */
interface SpeechSynthesizer {

    /** 回调只关心“这一句开始 / 说完了 / 失败了”三种语义。 */
    interface Listener {
        fun onSegmentStarted(segment: TextSegment)
        fun onSegmentCompleted(segment: TextSegment)
        fun onSegmentError(segment: TextSegment, message: String)
    }

    /** 合成引擎是否可用；不可用时 [TextPlayerEngine] 会直接报错而不是静默卡住。 */
    fun isAvailable(): Boolean

    /**
     * 从头开始朗读 [segments]；如果是接续朗读，由调用方传入剩余片段。
     *
     * 实现应先清空当前队列，再依次朗读。
     */
    fun speak(segments: List<TextSegment>)

    /** 停止当前朗读并清空队列。 */
    fun stop()

    /** 设置语速倍率，范围由实现决定，建议 0.5f..3f。 */
    fun setSpeed(speed: Float)

    fun setListener(listener: Listener?)

    /** 释放底层合成资源。 */
    fun release()
}
