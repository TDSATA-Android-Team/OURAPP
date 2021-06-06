package com.tdsata.ourapp.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

import com.tdsata.ourapp.R;

/**
 * 扫码阴影层遮盖视图.
 *
 * 可设置的属性：
 *             属性名          值类型                     说明                                   默认值
 *           scanWidth       尺寸值(px)           正方形扫描区域的边长                            500px
 *          scanLineColor      颜色值                 扫描线的颜色                            Color.BLACK
 *        fourCornersColor     颜色值             扫描区域四个角的颜色                        Color.BLACK
 *       shadowTransparency    浮点值    非扫描区域颜色为#000000，该值为其透明度[0.1f, 1.0f]      0.5f
 *          tipMessage         字符串          位于扫描区域下的白色提示文字                       null
 *        tipMessageSize     尺寸值(sp)            提示文字的字体大小                            17sp
 *           distance        尺寸值(dp)    提示文字基线(大致为文字底部)与扫描区域底边的距离       30dp
 *
 * @see #startScanAnimation()
 * @see #getScanWidth()
 * @version 1
 */
public class ScanShadowView extends View {
    private final Paint paint;
    private final Path path;
    private final float cornerStrokeWidth;// 四个角的描边宽度（2dp）
    private final float scanLineStrokeWidth;// 扫描线的描边宽度（1dp）
    private float scanLineOffset = 0.0f;// 扫描线的平移量

    private final float scanWidth;// 扫描区域宽度(px)
    private final int scanLineColor;// 扫描线颜色
    private final int fourCornersColor;// 四个角颜色
    private float shadowTransparency;// 阴影遮盖的透明度
    private final String tipMessage;
    private final float tipMessageSize;
    private final float distance;

    public ScanShadowView(Context context) {
        this(context, null);
    }

    public ScanShadowView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScanShadowView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float oneDp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.0f, dm);
        float oneSp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1.0f, dm);
        cornerStrokeWidth = 4 * oneDp;
        scanLineStrokeWidth = oneDp;
        paint = new Paint();
        path = new Path();
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.ScanShadowView);
        try {
            scanWidth = typedArray.getDimensionPixelSize(R.styleable.ScanShadowView_scanWidth, 500);
            scanLineColor = typedArray.getColor(R.styleable.ScanShadowView_scanLineColor, Color.BLACK);
            fourCornersColor = typedArray.getColor(R.styleable.ScanShadowView_fourCornersColor, Color.BLACK);
            shadowTransparency = typedArray.getFloat(R.styleable.ScanShadowView_shadowTransparency, 0.5f);
            if (shadowTransparency < 0.1f) {
                shadowTransparency = 0.1f;
            } else if (shadowTransparency > 1.0f) {
                shadowTransparency = 1.0f;
            }
            tipMessage = typedArray.getString(R.styleable.ScanShadowView_tipMessage);
            tipMessageSize = typedArray.getDimension(R.styleable.ScanShadowView_tipMessageSize, 17 * oneSp);
            distance = typedArray.getDimension(R.styleable.ScanShadowView_distance, 30 * oneDp);
        } finally {
            typedArray.recycle();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float widthHalf = getWidth() / 2.0f;
        float heightHalf = getHeight() / 2.0f;
        float scanWidthHalf = scanWidth / 2.0f;
        // 绘制阴影层
        canvas.drawARGB((int) (255 * shadowTransparency), 0, 0, 0);
        canvas.save();
        canvas.clipRect(widthHalf - scanWidthHalf, heightHalf - scanWidthHalf, widthHalf + scanWidthHalf, heightHalf + scanWidthHalf);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.SRC_IN);
        canvas.restore();
        // 绘制四个角
        float drawLength = scanWidth / 10.0f;
        float cornerStrokeWidthHalf = cornerStrokeWidth / 2.0f;
        resetDrawTools();
        paint.setColor(fourCornersColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(cornerStrokeWidth);
        // --左上角
        path.moveTo(widthHalf - scanWidthHalf + cornerStrokeWidthHalf, heightHalf - scanWidthHalf + drawLength);
        path.lineTo(widthHalf - scanWidthHalf + cornerStrokeWidthHalf, heightHalf - scanWidthHalf + cornerStrokeWidthHalf);
        path.lineTo(widthHalf - scanWidthHalf + drawLength, heightHalf - scanWidthHalf + cornerStrokeWidthHalf);
        // --右上角
        path.moveTo(widthHalf + scanWidthHalf - drawLength, heightHalf - scanWidthHalf + cornerStrokeWidthHalf);
        path.lineTo(widthHalf + scanWidthHalf - cornerStrokeWidthHalf, heightHalf - scanWidthHalf + cornerStrokeWidthHalf);
        path.lineTo(widthHalf + scanWidthHalf - cornerStrokeWidthHalf, heightHalf - scanWidthHalf + drawLength);
        // --右下角
        path.moveTo(widthHalf + scanWidthHalf - cornerStrokeWidthHalf, heightHalf + scanWidthHalf - drawLength);
        path.lineTo(widthHalf + scanWidthHalf - cornerStrokeWidthHalf, heightHalf + scanWidthHalf - cornerStrokeWidthHalf);
        path.lineTo(widthHalf + scanWidthHalf -drawLength, heightHalf + scanWidthHalf - cornerStrokeWidthHalf);
        // --左下角
        path.moveTo(widthHalf - scanWidthHalf + drawLength, heightHalf + scanWidthHalf - cornerStrokeWidthHalf);
        path.lineTo(widthHalf - scanWidthHalf + cornerStrokeWidthHalf, heightHalf + scanWidthHalf - cornerStrokeWidthHalf);
        path.lineTo(widthHalf - scanWidthHalf + cornerStrokeWidthHalf, heightHalf + scanWidthHalf - drawLength);
        canvas.drawPath(path, paint);
        // 绘制扫描线
        float drawScanLineY = heightHalf - scanWidthHalf + scanLineStrokeWidth / 2.0f + scanLineOffset;
        paint.setColor(scanLineColor);
        paint.setStrokeWidth(scanLineStrokeWidth);
        canvas.drawLine(widthHalf - scanWidthHalf + cornerStrokeWidth, drawScanLineY, widthHalf + scanWidthHalf - cornerStrokeWidth, drawScanLineY, paint);
        // 绘制提示语
        if (tipMessage != null) {
            paint.reset();
            paint.setColor(Color.WHITE);
            paint.setTextSize(tipMessageSize);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(tipMessage, widthHalf, heightHalf + scanWidthHalf + distance, paint);
        }
    }

    private void resetDrawTools() {
        paint.reset();
        path.reset();
    }

    /**
     * 启动扫描线的平移动画.
     */
    public void startScanAnimation() {
        ValueAnimator scanLineAnimator = ValueAnimator.ofFloat(0.0f, scanWidth - scanLineStrokeWidth);
        scanLineAnimator.setDuration(5000);
        scanLineAnimator.setRepeatCount(ValueAnimator.INFINITE);
        scanLineAnimator.setRepeatMode(ValueAnimator.RESTART);
        scanLineAnimator.start();
        scanLineAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                scanLineOffset = (float) animation.getAnimatedValue();
                invalidate();
                requestLayout();
            }
        });
    }

    /**
     * 获得扫描区域的宽度，以像素为单位.
     *
     * @return 扫描区域的宽度
     */
    public float getScanWidth() {
        return scanWidth;
    }
}
