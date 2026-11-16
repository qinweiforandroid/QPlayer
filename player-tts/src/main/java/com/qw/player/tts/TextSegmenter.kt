package com.qw.player.tts

/**
 * 一个可朗读的文本片段。
 *
 * [startOffset] / [endOffset] 是它在整篇正文里的字符区间（左闭右开），
 * 用于把“朗读到第几句”换算回进度，也用于上层做逐句高亮。
 */
data class TextSegment(
    val index: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int
)

/**
 * 把整篇正文切成 TTS 可接受的朗读片段。
 *
 * 切分规则：
 * - 优先在句末标点（。！？；!?;）和换行处断开
 * - 没有标点的长文本按 [maxSegmentChars] 硬切，避免单次合成过长导致失败
 * - 空片段被丢弃，但 [TextSegment.startOffset] 仍指向原文位置
 */
class TextSegmenter(
    private val maxSegmentChars: Int = 120
) {
    private val sentenceEnds = charArrayOf('。', '！', '？', '；', '!', '?', ';', '\n')

    fun split(text: String): List<TextSegment> {
        val segments = mutableListOf<TextSegment>()
        var bufferStart = -1
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (bufferStart == -1 && !ch.isWhitespace()) {
                bufferStart = i
            }
            val isSentenceEnd = sentenceEnds.contains(ch)
            val bufferLength = if (bufferStart == -1) 0 else i - bufferStart + 1
            if (isSentenceEnd || (bufferStart != -1 && bufferLength >= maxSegmentChars)) {
                appendSegment(text, bufferStart, i + 1, segments)
                bufferStart = -1
            }
            i++
        }
        appendSegment(text, bufferStart, text.length, segments)
        return segments
    }

    private fun appendSegment(
        text: String,
        start: Int,
        end: Int,
        out: MutableList<TextSegment>
    ) {
        if (start < 0 || end <= start) {
            return
        }
        val raw = text.substring(start, end)
        if (raw.isBlank()) {
            return
        }
        out.add(
            TextSegment(
                index = out.size,
                text = raw.trim(),
                startOffset = start,
                endOffset = end
            )
        )
    }
}
