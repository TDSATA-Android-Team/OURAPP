package com.tdsata.ourapp.drawable;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 使用方法:  Loading loading = new Loading();
 *           imageView.setImageDrawable(loading);
 *           loading.start();
 */
public class Loading extends Drawable implements Animatable {

    private Shape mCurrentShape;
    private Paint mPaint;
    private int mHeight, mWidth;
    private float translateY;
    private float scale, rotateRect, rotateTriangle;
    private AnimatorSet upAnimatorSet, downAnimatorSet;
    private int rectLength = 100;
    private int radius = rectLength/2;

    public Loading() {
        initData();
    }

    private void initData() {
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mCurrentShape = Shape.SHAPE_CIRCLE;
    }

    @Override
    public void start() {
        startAnimation();
    }

    @Override
    public void stop() {
        stopAnimation();
    }

    @Override
    public boolean isRunning() {
        return upAnimatorSet.isRunning() || downAnimatorSet.isRunning();
    }
    
    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.save();
        drawLoading(canvas);
        canvas.restore();

        canvas.save();
        drawShadow(canvas);
        canvas.restore();

        canvas.save();
        drawText(canvas);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @SuppressLint("WrongConstant")
    @Override
    public int getOpacity() {
        return PixelFormat.RGBA_8888;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mWidth = bounds.width();
        mHeight = bounds.height();
    }

    private void drawLoading(Canvas canvas) {
        canvas.translate(0,translateY);
        if (mCurrentShape == Shape.SHAPE_TRIANGLE)
            canvas.rotate(rotateTriangle, mWidth / 2, mHeight / 2 - rectLength / 2 +(200 / 3));
        else
            canvas.rotate(rotateRect, mWidth / 2, mHeight / 2);

        switch (mCurrentShape) {
            case SHAPE_CIRCLE:
                drawCircle(canvas);
                break;
            case SHAPE_RECT:
                drawRect(canvas);
                break;
            case SHAPE_TRIANGLE:
                drawTriangle(canvas);
                break;
        }
    }

    //画圆
    private void drawCircle(Canvas canvas) {
        mPaint.setColor(Color.parseColor("#aa738ffe"));
        canvas.drawCircle(mWidth / 2, mHeight / 2, radius, mPaint);
    }

    //画正方形
    private void drawRect(Canvas canvas) {
        mPaint.setColor(Color.parseColor("#aae84e49"));
        canvas.drawRect(mWidth / 2 -rectLength/2, mHeight / 2 - rectLength/2, mWidth / 2 + rectLength/2, mHeight / 2 + rectLength/2, mPaint);
    }

    //画三角形（正三角形）
    private void drawTriangle(Canvas canvas) {
        mPaint.setColor(Color.parseColor("#aa72d572"));
        Path path = new Path();
        path.moveTo(mWidth / 2, mHeight / 2 - rectLength/2);
        path.lineTo((float) (mWidth / 2 - Math.sqrt(Math.pow(rectLength,2) / 3)), mHeight / 2 + rectLength/2);
        path.lineTo((float) (mWidth / 2 + Math.sqrt(Math.pow(rectLength,2) / 3)), mHeight / 2 + rectLength/2);
        path.close();
        canvas.drawPath(path, mPaint);
    }

    //画阴影
    private void drawShadow(Canvas canvas) {
        mPaint.setColor(Color.parseColor("#25808080"));
        //椭圆阴影的缩放效果
        canvas.scale(scale, scale, mWidth / 2, mHeight / 2 + 90);
        canvas.drawArc(mWidth / 2 - rectLength / 2, mHeight / 2 + 80, mWidth / 2 + 50, mHeight / 2 + 100, 0, 360, false, mPaint);
    }

    //画文字
    private void drawText(Canvas canvas) {
        mPaint.setTextSize(55);
        mPaint.setColor(Color.DKGRAY);
        mPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("玩命加载中…", mWidth / 2, mHeight / 2 + 170, mPaint);
    }

    //形状
    public enum Shape {
        // 三角
        SHAPE_TRIANGLE,
        // 四边形
        SHAPE_RECT,
        // 圆形
        SHAPE_CIRCLE
    }

    //图形变换效果
    private void exchangeDraw() {
        switch (mCurrentShape) {
            case SHAPE_CIRCLE:
                mCurrentShape = Shape.SHAPE_RECT;
                break;
            case SHAPE_RECT:
                mCurrentShape = Shape.SHAPE_TRIANGLE;
                break;
            case SHAPE_TRIANGLE:
                mCurrentShape = Shape.SHAPE_CIRCLE;
                break;
        }
    }
    /*
    上抛动画
     */
    private void upAnimation() {
        final ValueAnimator upAnimation = ValueAnimator.ofFloat(0, -200);
        upAnimation.setInterpolator(new DecelerateInterpolator(1.2f));
        //为动画设置更新时候的监听
        upAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                translateY = (float) upAnimation.getAnimatedValue();
                //刷新
                invalidateSelf();
            }
        });
        //监听结束，开始下落的动画
        upAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                downAnimation();
            }
        });

        //缩放动画
        final ValueAnimator scaleAnimation  = ValueAnimator.ofFloat(1, 0.3f);
        scaleAnimation.setInterpolator(new DecelerateInterpolator(1.2f));
        scaleAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                scale = (float) scaleAnimation.getAnimatedValue();
            }
        });

        //三角形旋转动画
        final ValueAnimator rotateTriangleAnimation = ValueAnimator.ofFloat(0, 120);
        rotateTriangleAnimation.setInterpolator(new DecelerateInterpolator(1.2f));
        rotateTriangleAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                rotateTriangle = (float) rotateTriangleAnimation.getAnimatedValue();
            }
        });

        //正方形旋转动画
        final ValueAnimator rotateRectAnimation = ValueAnimator.ofFloat(0, 180);
        rotateRectAnimation.setInterpolator(new DecelerateInterpolator(1.2f));
        rotateRectAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                rotateRect = (float) rotateRectAnimation.getAnimatedValue();
            }
        });

        upAnimatorSet = new AnimatorSet();
        upAnimatorSet.setDuration(300);
        upAnimatorSet.playTogether(upAnimation, scaleAnimation, rotateTriangleAnimation ,rotateRectAnimation);
        upAnimatorSet.start();
    }

    /*
    下落动画
     */
    private void downAnimation() {
        final ValueAnimator downAnimation = ValueAnimator.ofFloat(-200, 0);
        downAnimation.setInterpolator(new DecelerateInterpolator(1.2f));
        downAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                translateY = (float) downAnimation.getAnimatedValue();
                invalidateSelf();
            }
        });

        downAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
               upAnimation();
               exchangeDraw();
            }
        });

        final ValueAnimator scaleAnimation = ValueAnimator.ofFloat(0.3f, 1);
        scaleAnimation.setInterpolator(new DecelerateInterpolator(1.2f));
        scaleAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                scale = (float) scaleAnimation.getAnimatedValue();
            }
        });

        downAnimatorSet = new AnimatorSet();
        downAnimatorSet.setDuration(500);
        downAnimatorSet.playTogether(downAnimation, scaleAnimation);
        downAnimatorSet.start();
    }

    private void startAnimation() {
        upAnimation();
    }

    private void stopAnimation() {
        if (upAnimatorSet != null && upAnimatorSet.isStarted()) {
            upAnimatorSet.end();
        }
        if (downAnimatorSet != null && downAnimatorSet.isStarted()) {
            downAnimatorSet.end();
        }
    }
}