package com.qw.player.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * 基于系统 [TextToSpeech] 的 [SpeechSynthesizer] 实现。
 *
 * 逐句朗读的做法是：把每个 [TextSegment] 作为一条独立 utterance 依次入队，
 * 通过 [UtteranceProgressListener] 判断单句开始 / 完成，从而推进进度。
 *
 * 初始化是异步的：构造后需要等待 [isAvailable] 变为 true。
 * 初始化完成前调用 [speak] 会缓存片段，回调到达后自动补发。
 *
 * [UtteranceProgressListener] 的回调在 Android 上跑在 TTS 自己的后台线程，
 * 而 [speak] / [stop] 这些指令都来自主线程（[TextPlayerEngine] 的调用方）。
 * 如果不把回调切回主线程，`utteranceSegments` 会被两个线程同时读写，
 * 并且 [SpeechSynthesizer.Listener] 往下传导到 core/session/UI 的整条链路
 * （包括 ExoPlayer 的 API 调用）都要求跑在主线程上，会直接出问题。
 * 所以这里统一用 [mainHandler] 把回调 post 回主线程再处理。
 */
class AndroidTtsSynthesizer(
    context: Context,
    locale: Locale = Locale.getDefault()
) : SpeechSynthesizer {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var listener: SpeechSynthesizer.Listener? = null
    private var tts: TextToSpeech? = null
    private var ready = false
    private var released = false
    private var speed: Float = 1f
    private var pendingSegments: List<TextSegment> = emptyList()
    private val utteranceSegments = mutableMapOf<String, TextSegment>()
    private var currentLocale: Locale = locale

    init {
        createEngine()
    }

    private fun createEngine() {
        @Suppress("DEPRECATION")
        tts = TextToSpeech(appContext) { status ->
            if (released) {
                return@TextToSpeech
            }
            val engine = tts
            ready = status == TextToSpeech.SUCCESS && engine != null &&
                    engine.setLanguage(currentLocale) >= TextToSpeech.LANG_AVAILABLE
            if (!ready) {
                return@TextToSpeech
            }
            engine?.setSpeechRate(speed)
            engine?.setOnUtteranceProgressListener(progressListener)
            if (pendingSegments.isNotEmpty()) {
                val segments = pendingSegments
                pendingSegments = emptyList()
                speak(segments)
            }
        }
    }

    override fun isAvailable(): Boolean = ready && !released

    override fun speak(segments: List<TextSegment>) {
        val engine = tts
        if (!ready || engine == null || released) {
            pendingSegments = segments
            return
        }
        engine.stop()
        utteranceSegments.clear()
        segments.forEachIndexed { position, segment ->
            val utteranceId = "$UTTERANCE_PREFIX${segment.index}-$position-${UUID.randomUUID()}"
            utteranceSegments[utteranceId] = segment
            val result = engine.speak(segment.text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                utteranceSegments.remove(utteranceId)
                listener?.onSegmentError(segment, "TextToSpeech.speak 返回失败")
                return
            }
        }
    }

    override fun stop() {
        tts?.stop()
        utteranceSegments.clear()
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.5f, 3f)
        tts?.setSpeechRate(this.speed)
    }

    override fun setListener(listener: SpeechSynthesizer.Listener?) {
        this.listener = listener
    }

    override fun release() {
        if (released) {
            return
        }
        released = true
        ready = false
        utteranceSegments.clear()
        pendingSegments = emptyList()
        tts?.stop()
        tts?.shutdown()
        tts = null
        listener = null
    }

    /** 切换朗读语言，例如中英文混排的阅读场景。 */
    fun setLanguage(locale: Locale) {
        currentLocale = locale
        tts?.setLanguage(locale)
    }

    /** 透出底层引擎，供“跳转系统 TTS 设置页”这类宿主需求使用。 */
    fun getTextToSpeech(): TextToSpeech? = tts

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            mainHandler.post {
                utteranceSegments[utteranceId]?.let { listener?.onSegmentStarted(it) }
            }
        }

        override fun onDone(utteranceId: String?) {
            mainHandler.post {
                val segment = utteranceSegments.remove(utteranceId) ?: return@post
                listener?.onSegmentCompleted(segment)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            fail(utteranceId, "TextToSpeech 朗读失败")
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            fail(utteranceId, "TextToSpeech 朗读失败: $errorCode")
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            mainHandler.post {
                utteranceSegments.remove(utteranceId)
            }
        }

        private fun fail(utteranceId: String?, message: String) {
            mainHandler.post {
                val segment = utteranceSegments.remove(utteranceId) ?: return@post
                listener?.onSegmentError(segment, message)
            }
        }
    }

    private companion object {
        const val UTTERANCE_PREFIX = "qw-player-tts-"
    }
}
