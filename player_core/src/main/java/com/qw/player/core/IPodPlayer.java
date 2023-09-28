package com.qw.player.core;

import android.view.Surface;

public interface IPodPlayer {


    class State {
        /**
         * 空闲
         */
        public static final int IDLE = 1;
        /**
         * 连接中
         */
        public static final int CONNECT = 2;
        /**
         * 播放中
         */
        public static final int PLAYING = 3;
        /**
         * 已暂停
         */
        public static final int PAUSED = 4;
        /**
         * 已停止
         */
        public static final int STOPPED = 5;
        /**
         * 失败
         */
        public static final int ERROR = 6;
    }

    void play(String content);

    boolean isConnecting();

    boolean isPrepared();

    int getDuring();

    void seekTo(int position);

    void pause();

    void resume();

    boolean isPaused();

    boolean isPlaying();

    void setSpeed(float speed);

    void setSurface(Surface surface);

    void release();

    void stop();

    int getState();

    void registerListener(OnPlayListener listener);

    void unregisterListener();

    void notifyPlayConnecting();

    void notifyPlayError(int code, String msg);

    interface OnPlayListener {
        default void onPlayConnect() {
        }

        default void onPlayStart() {
        }

        default void onPlayPaused() {
        }

        default void onPlayResumed() {
        }

        default void onPlayStopped() {
        }

        default void onPlayBufferingUpdated(int percent) {
        }

        default void onPlayProgressUpdated(int cur, int total) {
        }

        default void onPlayCompleted() {
        }

        default void onPlayError(int code, String message) {
        }
    }
}