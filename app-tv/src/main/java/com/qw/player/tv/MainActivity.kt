package com.qw.player.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.qw.player.core.live.LiveChannelRepository
import com.qw.player.tv.playback.TvPlaybackRuntime
import com.qw.player.tv.ui.QPlayerTvApp
import com.qw.player.tv.ui.theme.QPlayerTvTheme

/**
 * TV 唯一入口。
 *
 * 一个 Activity 承载全部页面，runtime 随 Activity 创建、销毁时释放，
 * 避免遥控器快速退出时解码资源残留。
 */
class MainActivity : ComponentActivity() {

    private val runtime = TvPlaybackRuntime()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LiveChannelRepository.initialize(this)
        runtime.initialize(this)

        setContent {
            QPlayerTvTheme {
                QPlayerTvApp(runtime = runtime)
            }
        }
    }

    override fun onDestroy() {
        runtime.release()
        super.onDestroy()
    }
}
