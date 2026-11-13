package com.qw.player.demo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.qw.player.demo.audio.AudioPlayerFragment
import com.qw.player.demo.cctv.CctvChannelListActivity
import com.qw.player.demo.databinding.ActivityMainBinding
import com.qw.player.demo.video.VideoPlayerFragment
import com.qw.player.demo.vod.VodPlayerActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bind = ActivityMainBinding.inflate(layoutInflater)
        setContentView(bind.root)

        bind.audioCard.setOnClickListener {
            openDemo("AudioPlayer", AudioPlayerFragment::class.java.name)
        }
        bind.videoCard.setOnClickListener {
            openDemo("VideoPlayer", VideoPlayerFragment::class.java.name)
        }
        bind.cctvCard.setOnClickListener {
            startActivity(CctvChannelListActivity.intent(this))
        }
        bind.vodCard.setOnClickListener {
            startActivity(Intent(this, VodPlayerActivity::class.java))
        }
    }

    private fun openDemo(title: String, fragmentClassName: String) {
        startActivity(
            Intent(this, FragmentHostActivity::class.java)
                .putExtra(FragmentHostActivity.EXTRA_TITLE, title)
                .putExtra(FragmentHostActivity.EXTRA_FRAGMENT_CLASS_NAME, fragmentClassName)
        )
    }
}
