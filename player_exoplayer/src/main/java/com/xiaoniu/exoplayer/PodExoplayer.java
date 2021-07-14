package com.xiaoniu.exoplayer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.qw.player.core.IPodPlayer;
import com.qw.player.core.PlayLog;
import com.qw.player.core.PodPlayerTimer;

import java.io.IOException;

/**
 * Created by qinwei on 2021/6/9 20:21
 */
public class PodExoplayer implements IPodPlayer {

    private SimpleExoPlayer player;
    private OnPlayListener listener;
    private int state;
    private boolean isPrepared;
    /**
     * 计时器
     */
    private PodPlayerTimer mTimer;
    private Handler handler = new Handler(Looper.getMainLooper());

    public PodExoplayer(Context context) {
        mTimer = new PodPlayerTimer();
        mTimer.setOnPodPlayerTimerListener(new PodPlayerTimer.OnPlayerTimerListener() {

            @Override
            public void onExecute() {
                if (isPrepared) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onPlayBufferingUpdated(player.getBufferedPercentage());
                            listener.onPlayProgressUpdated((int) player.getCurrentPosition(), getDuring());
                        }
                    });
                }
            }
        });
        player = new SimpleExoPlayer.Builder(context)
                .setAudioAttributes(new AudioAttributes.Builder()
                                .setContentType(C.CONTENT_TYPE_MUSIC)
                                .setUsage(C.USAGE_MEDIA)
                                .build(),
                        false)
                .build();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                switch (state) {
                    case Player.STATE_IDLE:
                        PlayLog.Companion.d("STATE_IDLE");
                        break;
                    case Player.STATE_BUFFERING:
                        PlayLog.Companion.d("STATE_BUFFERING");
                        break;
                    case Player.STATE_READY:
                        PlayLog.Companion.d("STATE_READY");
                        isPrepared = true;
                        player.play();
                        PodExoplayer.this.state = State.PLAYING;
                        listener.onPlayStart();
                        mTimer.start();
                        break;
                    case Player.STATE_ENDED:
                        PlayLog.Companion.d("STATE_ENDED");
                        PodExoplayer.this.state = State.IDLE;
                        listener.onPlayCompleted();
                        break;
                }
            }


            @Override
            public void onIsLoadingChanged(boolean isLoading) {
                d("onIsLoadingChanged：" + isLoading);
            }


            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                d("onIsPlayingChanged：" + isPlaying);
            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                d("onPlayerError：" + error.getMessage());
                if (error.type == ExoPlaybackException.TYPE_SOURCE) {
                    IOException cause = error.getSourceException();
                    state = State.ERROR;
                    listener.onPlayError(-1, cause.getMessage());
//                    if (cause instanceof HttpDataSource.HttpDataSourceException) {
//                        // An HTTP error occurred.
//                        HttpDataSource.HttpDataSourceException httpError = (HttpDataSource.HttpDataSourceException) cause;
//                        // This is the request for which the error occurred.
//                        DataSpec requestDataSpec = httpError.dataSpec;
//                        // It's possible to find out more about the error both by casting and by
//                        // querying the cause.
//                        if (httpError instanceof HttpDataSource.InvalidResponseCodeException) {
//                            // Cast to InvalidResponseCodeException and retrieve the response code,
//                            // message and headers.
//                        } else {
//                            // Try calling httpError.getCause() to retrieve the underlying cause,
//                            // although note that it may be null.
//                        }
//                    }
                }
            }
        });
    }

    @Override
    public void setSpeed(float speed) {
        if (player != null) {
            PlaybackParameters playbackParameters = new PlaybackParameters(speed);
            player.setPlaybackParameters(playbackParameters);
        }
    }

    @Override
    public void setSurface(Surface surface) {
        player.setVideoSurface(surface);
    }

    @Override
    public void play(String content) {
        isPrepared = false;
        try {
            MediaItem item = MediaItem.fromUri(content);
            player.stop(true);


            player.addMediaItem(item);
            player.prepare();
            state = State.CONNECT;
            listener.onPlayConnect();
        } catch (Exception e) {
            e.printStackTrace();
            state = State.ERROR;
            listener.onPlayError(-1, e.getMessage());
        }
    }

    @Override
    public boolean isConnecting() {
        return state == State.CONNECT;
    }

    @Override
    public boolean isPrepared() {
        return isPrepared;
    }

    @Override
    public int getDuring() {
        return (int) player.getDuration();
    }

    @Override
    public void seekTo(int position) {
        if (isPrepared()) {
            player.seekTo(position);
        }
    }

    @Override
    public void pause() {
        d("pause");
        if (isPlaying()) {
            player.pause();
            state = State.PAUSED;
            mTimer.stop();
            listener.onPlayPaused();
        }
    }

    @Override
    public void resume() {
        d("resume");
        if (isPaused()) {
            player.play();
            state = State.PLAYING;
            mTimer.start();
            listener.onPlayResumed();
        }
    }

    @Override
    public boolean isPaused() {
        return state == State.PAUSED;
    }

    @Override
    public boolean isPlaying() {
        return state == State.PLAYING;
    }


    @Override
    public void release() {
        d("release");
        stop();
        player.release();
    }

    @Override
    public void stop() {
        d("stop");
        player.stop(true);
        state = State.STOPPED;
        mTimer.stop();
        listener.onPlayStopped();
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public void registerListener(OnPlayListener listener) {
        this.listener = listener;
    }

    @Override
    public void unregisterListener() {
        this.listener = null;
    }

    @Override
    public void notifyPlayConnecting() {
        state = State.CONNECT;
        this.listener.onPlayConnect();
    }

    @Override
    public void notifyPlayError(int code, String msg) {
        state = State.ERROR;
        this.listener.onPlayError(code, msg);
    }

    private void d(String msg) {
        PlayLog.Companion.d("PodExoplayer > " + msg);
    }
}