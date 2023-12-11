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
import com.qw.player.core.PodPlayerConfig;
import com.qw.player.core.PodPlayerTimer;

/**
 * Created by qinwei on 2021/6/9 20:21
 */
public class PodExoplayer implements IPodPlayer {

    private ExoPlayer player;
    private OnPlayListener listener;
    private OnVideoListener videoListener;
    private int state;
    private boolean isPrepared;
    /**
     * 计时器
     */
    private PodPlayerTimer mTimer;
    private Handler handler = new Handler(Looper.getMainLooper());

    private PodPlayerConfig playerConfig;


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
//        player.addAnalyticsListener(new EventLogger());
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
                        PlayLog.Companion.d("STATE_READY " + player.getPlayWhenReady());
                        isPrepared = true;
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
            }

            @Override
            public void onIsLoadingChanged(boolean isLoading) {
                d("onIsLoadingChanged：" + isLoading);
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                d("onIsPlayingChanged：" + isPlaying);
                if (isPlaying) {
                    if (isConnecting()) {
                        PodExoplayer.this.state = State.PLAYING;
                        mTimer.start();
                        listener.onPlayStart();
                    } else if (isPlaying()) {
                        listener.onPlayResumed();
                    }

                } else {
                    mTimer.stop();
                    if (isPaused()) {
                        listener.onPlayPaused();
                    } else if (state == State.STOPPED) {
                        listener.onPlayStopped();
                    }
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                error.printStackTrace();
                d("onPlayerError：" + error.getMessage());
                state = State.ERROR;
                listener.onPlayError(error.errorCode, error.getMessage());
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                d("onVideoSizeChanged：" + videoSize.width + ":" + videoSize.height);
                if (videoSize.equals(VideoSize.UNKNOWN)
                        || player == null
                        || player.getPlaybackState() == Player.STATE_IDLE) {
                    return;
                }
                if (videoListener != null) {
                    videoListener.onVideoSizeChanged(videoSize.width, videoSize.height);
                }
            }
        });
    }

    @Override
    public void registerVideoListener(OnVideoListener listener) {
        this.videoListener = listener;
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
        d("setSurface " + surface.toString());
        player.clearVideoSurface();
        player.setVideoSurface(surface);
    }


    @Override
    public void setPlayerConfig(PodPlayerConfig playerConfig) {
        this.playerConfig = playerConfig;
    }

    @Override
    public void preload(String content) {
        
    }

    @Override
    public void play(String content) {
        isPrepared = false;
        try {
            d("play:" + content);
            MediaItem item = MediaItem.fromUri(content);
            player.stop();
            player.clearMediaItems();
            player.addMediaItem(item);
            notifyPlayConnecting();
            player.prepare();
            player.setPlayWhenReady(true);
        } catch (Exception e) {
            e.printStackTrace();
            state = State.ERROR;
            d("play：" + e.getMessage());
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
        player.seekTo(position);
    }

    @Override
    public void pause() {
        d("pause");
        if (isPlaying()) {
            state = State.PAUSED;
            player.pause();
        }
    }

    @Override
    public void resume() {
        d("resume");
        if (isPaused()) {
            state = State.PLAYING;
            player.play();
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
        unregisterListener();
    }

    @Override
    public void stop() {
        d("stop");
        state = State.STOPPED;
        player.stop();
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
        if (state != State.CONNECT) {
            state = State.CONNECT;
            this.listener.onPlayConnect();
        }
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