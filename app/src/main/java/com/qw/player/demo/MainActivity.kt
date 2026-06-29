package com.qw.player.demo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.qw.player.demo.audio.AudioPlayerFragment
import com.qw.player.demo.cctv.CctvPlayerActivity
import com.qw.player.demo.video.VideoPlayerFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.playListButton).setOnClickListener {
            openDemo("AudioPlayer", AudioPlayerFragment::class.java.name)
        }
        findViewById<Button>(R.id.videoDemoButton).setOnClickListener {
            openDemo("VideoPlayer", VideoPlayerFragment::class.java.name)
        }
        findViewById<Button>(R.id.cctvButton).setOnClickListener {
            startActivity(Intent(this, CctvPlayerActivity::class.java))
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
