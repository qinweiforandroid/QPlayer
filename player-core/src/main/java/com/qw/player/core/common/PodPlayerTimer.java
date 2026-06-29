package com.qw.player.core.common;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PodPlayerTimer {
    private static final long REFRESH_INTERVAL_MS = 1000;
    private ScheduledExecutorService mExecutor;
    private Runnable mTimerTask;
    private OnPlayerTimerListener listener;

    public void start() {
        start(REFRESH_INTERVAL_MS);
    }

    /**
     * Syncs the mMediaPlayer position with mPlaybackProgressCallback via recurring task.
     */
    public void start(long interval_ms) {
        stop();
        if (mExecutor != null) {
            return;
        }
        mExecutor = Executors.newSingleThreadScheduledExecutor();
        mTimerTask = () -> {
            listener.onExecute();
        };
        mExecutor.scheduleAtFixedRate(
                mTimerTask,
                0,
                interval_ms,
                TimeUnit.MILLISECONDS
        );
    }

    public void stop() {
        if (mExecutor != null) {
            mExecutor.shutdownNow();
            mExecutor = null;
            mTimerTask = null;
        }
    }

    public void setOnPodPlayerTimerListener(OnPlayerTimerListener listener) {
        this.listener = listener;
    }

    public interface OnPlayerTimerListener {
        void onExecute();
    }
}
