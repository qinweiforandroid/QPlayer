package com.qw.player.demo.widget;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.qw.player.core.IPodPlayer;

import org.jetbrains.annotations.NotNull;

/**
 * Created by qinwei on 2023/10/10 14:36
 * email: qinwei_it@163.com
 */
public class VideoTextureView extends AspectRatioFrameLayout {
    private final TextureView mTextureView;

    public VideoTextureView(Context context) {
        super(context);
    }

    public VideoTextureView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }


    {
        mTextureView = new TextureView(getContext());
        mTextureView.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(mTextureView);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (getResizeMode() == RESIZE_MODE_ASPECT_FILL) {
            int size = getWidth() - maxFillWidth;
            if (size > 0) {
                setTranslationX(-size / 2F);
            } else {
                setTranslationX(0);
            }
        }
    }

    /**
     * 最大可显示宽度
     */
    private int maxFillWidth;
    /**
     * 最大可显示高度
     */
    private int maxFillHeight;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        maxFillWidth = MeasureSpec.getSize(widthMeasureSpec);
        maxFillHeight = MeasureSpec.getSize(heightMeasureSpec);
    }

    public void setOnSurfaceTextureListener(AbsSurfaceTextureListener listener) {
        mTextureView.setSurfaceTextureListener(listener);
    }

    public void bindPodPlayer(@NotNull IPodPlayer podPlayer) {
        setKeepScreenOn(true);
        podPlayer.registerVideoListener(new IPodPlayer.OnVideoListener() {
            @Override
            public void onVideoSizeChanged(int width, int height) {
                setAspectRatio(width / new Float(height));
            }
        });
        setOnSurfaceTextureListener(new AbsSurfaceTextureListener() {

            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                podPlayer.setSurface(new Surface(surface));
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                podPlayer.setSurface(null);
                return false;
            }
        });
    }

    public static abstract class AbsSurfaceTextureListener implements TextureView.SurfaceTextureListener {

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

        }


        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    }
}
