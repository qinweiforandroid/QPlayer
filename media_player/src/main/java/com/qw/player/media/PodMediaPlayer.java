package com.qw.player.media;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.PowerManager;

import com.qw.player.core.IPodPlayer;
import com.qw.player.core.PodPlayerTimer;

import java.io.IOException;

public class PodMediaPlayer implements IPodPlayer {
    private final MediaPlayer mMediaPlayer;
    /**
     * 计时器
     */
    private final PodPlayerTimer mTimer;
    private OnPlayListener listener;
    private int mDuring;
    private int state;
    private boolean isPrepared;

    public PodMediaPlayer(Context context) {
        mTimer = new PodPlayerTimer();
        mTimer.setOnPodPlayerTimerListener(new PodPlayerTimer.OnPlayerTimerListener() {

            @Override
            public void onExecute() {
                if (isPrepared) {
                    listener.onPlayProgressUpdated(mMediaPlayer.getCurrentPosition(), mDuring);
                }
            }
        });
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mDuring = mp.getDuration();
                mp.start();
                isPrepared = true;
                state = State.PLAYING;
                listener.onPlayStart();
                mTimer.start();
            }
        });

        mMediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(MediaPlayer mp, int percent) {
                listener.onPlayBufferingUpdated(percent);
            }
        });

        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                listener.onPlayProgressUpdated(0, mDuring);
                state = State.IDLE;
                reset();
                listener.onPlayCompleted();
            }
        });

        mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                reset();
                notifyPlayError(extra, "");
                return true;
            }
        });
    }

    private void reset() {
        if (isPrepared) {
            mMediaPlayer.reset();
            isPrepared = false;
        }
    }


    @Override
    public void play(String content) {
        reset();
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mMediaPlayer.setDataSource(content);
            notifyPlayConnecting();
            mMediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
            notifyPlayError(-1, e.getMessage());
        }
    }

    @Override
    public boolean isPrepared() {
        return isPrepared;
    }

    @Override
    public int getDuring() {
        return mDuring;
    }

    @Override
    public void seekTo(int position) {
        if (isPrepared) {
            if (position > mDuring) {
                position = mDuring;
            }
            mMediaPlayer.seekTo(position);
        }
    }

    @Override
    public void pause() {
        if (isPlaying()) {
            mMediaPlayer.pause();
            state = State.PAUSED;
            listener.onPlayPaused();
            mTimer.stop();
        }
    }

    @Override
    public void resume() {
        if (isPaused()) {
            mMediaPlayer.start();
            mTimer.start();
            state = State.PLAYING;
            listener.onPlayResumed();
        }
    }

    @Override
    public boolean isPaused() {
        return state == State.PAUSED;
    }

    @Override
    public boolean isConnecting() {
        return state == State.CONNECT;
    }

    @Override
    public boolean isPlaying() {
        return state == State.PLAYING;
    }

    @Override
    public void release() {
        if (mMediaPlayer.isPlaying()) {
            mMediaPlayer.stop();
        }
        mMediaPlayer.release();
    }

    @Override
    public void stop() {
        if (mMediaPlayer.isPlaying()) {
            mMediaPlayer.stop();
            mTimer.stop();
            state = State.STOPPED;
            listener.onPlayStopped();
        }
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
        if(isConnecting()){
            return;
        }
        state=State.CONNECT;
        listener.onPlayConnect();
    }

    @Override
    public void notifyPlayError(int code, String msg) {
        state=State.ERROR;
        listener.onPlayError(code,msg);
    }
}