package com.qw.player.demo.widget

import android.content.Context
import android.util.AttributeSet

/**
 * 兼容 CCTV 场景的旧命名，底层已切到通用视频控制实现。
 */
class CctvPlayerControlView : VideoPlayerControlView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )
}
