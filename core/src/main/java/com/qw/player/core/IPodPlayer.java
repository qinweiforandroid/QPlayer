package com.qw.player.core;

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

    boolean isPrepared();

    int getDuring();

    void seekTo(int position);

    void pause();

    void resume();

    boolean isPaused();

    boolean isPlaying();

    void release();

    void stop();

    int getState();

    void registerListener(OnPlayListener listener);

    void unregisterListener();

    interface OnPlayListener {
        void onPlayConnect();

        void onPlayStart();

        void onPlayPaused();

        void onPlayResumed();

        void onPlayStopped();

        void onPlayBufferingUpdated(int percent);

        void onPlayProgressUpdated(int cur, int total);

        void onPlayCompleted();

        void onPlayError(int code, String message);

    }
}