package com.qw.player.demo;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;

import com.qw.player.core.IPodPlayer;
import com.qw.player.media.PodMediaPlayer;


public class MainActivity extends AppCompatActivity {
    String url = "http://fs.pc.kugou.com/202104161533/e2c74980e0b3eee70fb993ab45e6faf2/G189/M00/17/03/XYcBAF4NnfOAdKH5ADn3W1bs4L0215.mp3";
    private IPodPlayer podPlayer;
    private Button mPlayerBtn;
    private SeekBar mPlayerSeekBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPlayerBtn = findViewById(R.id.mPlayerBtn);
        mPlayerSeekBar = findViewById(R.id.mPlayerSeekBar);
        mPlayerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (podPlayer.isPrepared()) {
                    podPlayer.seekTo(seekBar.getProgress());
                }
            }
        });
        mPlayerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (podPlayer.isPlaying()) {
                    podPlayer.pause();
                } else if (podPlayer.isPaused()) {
                    podPlayer.resume();
                } else {
                    podPlayer.play(url);
                }
            }
        });
        podPlayer = new PodMediaPlayer(this);
        podPlayer.registerListener(new IPodPlayer.OnPlayListener() {
            @Override
            public void onPlayConnect() {
                mPlayerBtn.setText("加载中");
                mPlayerBtn.setEnabled(false);
            }

            @Override
            public void onPlayStart() {
                mPlayerBtn.setEnabled(true);
                setButtonText();
            }

            @Override
            public void onPlayPaused() {
                setButtonText();
            }

            @Override
            public void onPlayResumed() {
                setButtonText();
            }

            @Override
            public void onPlayStopped() {
                setButtonText();
            }

            @Override
            public void onPlayProgressUpdated(int cur, int total) {
                mPlayerSeekBar.post(new Runnable() {
                    @Override
                    public void run() {
                        mPlayerSeekBar.setMax(total);
                        mPlayerSeekBar.setProgress(cur);
                    }
                });
            }


            @Override
            public void onPlayCompleted() {
                setButtonText();
            }

            @Override
            public void onPlayError(int code, String message) {

            }

            @Override
            public void onPlayBufferingUpdate(int percent) {
                mPlayerSeekBar.setSecondaryProgress((int) (podPlayer.getDuring() * percent * 0.01));
            }
        });
    }

    private void setButtonText() {
        if (podPlayer.isPlaying()) {
            mPlayerBtn.setText("暂停");
        } else {
            mPlayerBtn.setText("播放");
        }
    }
}