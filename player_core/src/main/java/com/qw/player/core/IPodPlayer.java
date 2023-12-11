package com.qw.player.core;

import android.view.Surface;

/**
 * 播放接口
 *
 * @author qinwei
 */
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

    /**
     * 设置播放配置
     *
     * @param playerConfig
     */
    void setPlayerConfig(PodPlayerConfig playerConfig);

    /**
     * 预加载内容
     *
     * @param content 内容链接
     */
    void preload(String content);

    /**
     * 播放内容
     *
     * @param content 内容链接
     */
    void play(String content);

    /**
     * 是否连接中
     *
     * @return
     */
    boolean isConnecting();

    /**
     * 资源是否准备好
     *
     * @return
     */
    boolean isPrepared();

    /**
     * @return
     */
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

    default void registerVideoListener(OnVideoListener listener) {

    }

    void unregisterListener();

    void notifyPlayConnecting();

    void notifyPlayError(int code, String msg);

    interface OnVideoListener {
        default void onVideoSizeChanged(int width, int height) {
        }
    }

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