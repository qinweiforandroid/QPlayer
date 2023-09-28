package com.qw.exoplayer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;

import com.qw.player.core.IPodPlayer;
import com.qw.player.core.PlayLog;
import com.qw.player.core.PodPlayerTimer;

/**
 * Created by qinwei on 2021/6/9 20:21
 */
public class PodExoplayer implements IPodPlayer {

    private ExoPlayer player;
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
        mTimer.setOnPodPlayerTimerListener(() -> {
            if (isPrepared) {
                handler.post(() -> {
                    listener.onPlayBufferingUpdated(player.getBufferedPercentage());
                    listener.onPlayProgressUpdated((int) player.getCurrentPosition(), getDuring());
                });
            }
        });
        player = new ExoPlayer.Builder(context).build();
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
                        break;
                    case Player.STATE_ENDED:
                        PlayLog.Companion.d("STATE_ENDED");
                        PodExoplayer.this.state = State.IDLE;
                        listener.onPlayCompleted();
                        break;
                }
            }

            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                d("onPlayWhenReadyChanged：" + playWhenReady);
                if (playWhenReady) {
                    listener.onPlayStart();
                    mTimer.start();
                }
            }

            @Override
            public void onIsLoadingChanged(boolean isLoading) {
//                d("onIsLoadingChanged：" + isLoading);
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                d("onIsPlayingChanged：" + isPlaying);
                if (isPlaying) {
                    PodExoplayer.this.state = State.PLAYING;
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                d("onPlayerError：" + error.getMessage());
                state = State.ERROR;
                listener.onPlayError(error.errorCode, error.getMessage());
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                listener.onVideoSizeChanged(videoSize.width, videoSize.height);
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
    public void setVideoScalingMode(int mode) {
        player.setVideoScalingMode(mode);
    }

    @Override
    public void play(String content) {
        isPrepared = false;
        try {
            MediaItem item = MediaItem.fromUri(content);
            player.stop();
            player.clearMediaItems();
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
        player.stop();
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

    public ExoPlayer getExoPlayer() {
        return player;
    }
}