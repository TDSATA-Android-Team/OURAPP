package com.tdsata.ourapp.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

import com.tdsata.ourapp.R;

/**
 * 页面进度条.
 *
 * 可设置的属性：
 *         属性名            值类型                       说明                                默认值
 *       baseColor           颜色值       设置底色（未进行的页面进度的颜色）                  #CCCCCC
 *       fillColor           颜色值       设置填充色（已完成的和正在进行的页面进度的颜色）     #F57c00
 *       nodeAmount          整型值       设置结点数量（页面进度的页面数量）                     3
 *       nodeDiameter      尺寸值(dp)     设置结点直径                                         30dp
 *       nodeTextSize      尺寸值(sp)     设置结点内数字序号的字体大小                          20sp
 *       nodeTextColor       颜色值       设置结点内数字序号的字体颜色                       Color.WHITE
 *       lineHeight        尺寸值(dp)     设置结点间连接线的高度（粗细程度）                    5dp
 *
 * @see #setNowSerialNumber(int)
 * @version 1
 */
public class PageProgressBar extends View {
    private final Paint paint;
    private final RectF rectF;
    private final float oneDp;// 1dp的大小
    private int nowSerialNumber;// 当前页面进度所在序号
    private float textOffset;// 结点内数字序号居中的高度偏移量
    private final Bitmap complete;// 图片(勾)
    private final Rect completeRect;// 图片(勾)所构成的矩阵

    private final int baseColor;// 页面进度条的底色
    private final int fillColor;// 完成的页面进度及当前的页面进度的填充颜色
    private int nodeAmount;// 页面进度结点的数量
    private final float nodeRadius;// 每个结点的半径（由直径获得）
    private final float nodeTextSize;// 结点内数字序号的字体大小
    private final int nodeTextColor;// 结点内数字序号的颜色
    private final float lineHeight;// 结点间的连线的高度

    public PageProgressBar(Context context) {
        this(context, null);
    }

    public PageProgressBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PageProgressBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint = new Paint();
        rectF = new RectF();
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        oneDp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, dm);
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.PageProgressBar);
        try {
            baseColor = typedArray.getColor(R.styleable.PageProgressBar_baseColor, Color.parseColor("#CCCCCC"));
            fillColor = typedArray.getColor(R.styleable.PageProgressBar_fillColor, Color.parseColor("#F57C00"));
            nodeAmount = typedArray.getInt(R.styleable.PageProgressBar_nodeAmount, 3);
            if (nodeAmount <= 0) {
                nodeAmount = 1;
            }
            nowSerialNumber = nodeAmount / 2;
            if (nowSerialNumber <= 0) {
                nowSerialNumber = 1;
            }
            nodeRadius = typedArray.getDimension(R.styleable.PageProgressBar_nodeDiameter, 30f * oneDp) / 2.0f;
            nodeTextSize = typedArray.getDimension(R.styleable.PageProgressBar_nodeTextSize, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20, dm));
            nodeTextColor = typedArray.getColor(R.styleable.PageProgressBar_nodeTextColor, Color.WHITE);
            lineHeight = typedArray.getDimension(R.styleable.PageProgressBar_lineHeight, 5f * oneDp);
        } finally {
            typedArray.recycle();
        }
        measureTextOffset();
        complete = BitmapFactory.decodeResource(context.getResources(), R.drawable.progress_bar_complete);
        completeRect = new Rect(0, 0, complete.getWidth(), complete.getHeight());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (widthMode != MeasureSpec.EXACTLY) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec((int) (270 * oneDp), MeasureSpec.EXACTLY);
        }
        if (heightMode != MeasureSpec.EXACTLY) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec((int) (30 * oneDp), MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 绘制顺序：底色圆 -> 底色连接线 -> 填充色圆 -> 填充色连接线 -> 数字序号 -> 图片(勾)
        // 从最后的圆(底色圆)开始画
        // 原因：画连接线时，线两端故意多画一些长度，以与圆相覆盖，使圆与线
        //       连接更紧密，避免因切线而留出一小部分空白。因此，若从前面的
        //       圆开始画，则会出现底色线覆盖填充色圆，而从后面的圆开始画，
        //       就是填充色圆覆盖底色线了。
        float heightHalf = getHeight() / 2.0f;
        float width = getWidth();
        float lineWidth;
        if (nodeAmount > 1) {
            lineWidth = (width - nodeAmount * 2.0f * nodeRadius) / (nodeAmount - 1);
            if (lineWidth < 0) {
                lineWidth = 0;
            }
        } else {
            lineWidth = 0;
        }
        float nodeStartX = width - nodeRadius;
        float lineStartX = width - 2.0f * nodeRadius - lineWidth;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        // 底色圆
        paint.setColor(baseColor);
        for (int i = 0; i < nodeAmount - nowSerialNumber; i++) {
            canvas.drawCircle(nodeStartX, heightHalf, nodeRadius, paint);
            nodeStartX -= (lineWidth + 2.0f * nodeRadius);
        }
        // 底色线
        for (int i = 0; i < nodeAmount - nowSerialNumber; i++) {// 总线数 - 现在序号经过的线数：(nodeAmount - 1) - (nowSerialNumber - 1)
            canvas.drawRect(lineStartX - nodeRadius / 2.0f, heightHalf - lineHeight / 2.0f,
                    lineStartX + lineWidth + nodeRadius / 2.0f, heightHalf + lineHeight / 2.0f,
                    paint);
            lineStartX -= (lineWidth + 2.0f * nodeRadius);
        }
        // 填充色圆
        paint.setColor(fillColor);
        for (int i = 0; i < nowSerialNumber; i++) {
            canvas.drawCircle(nodeStartX, heightHalf, nodeRadius, paint);
            nodeStartX -= (lineWidth + 2.0f * nodeRadius);
        }
        // 填充色线
        for (int i = 0; i < nowSerialNumber - 1; i++) {
            canvas.drawRect(lineStartX - nodeRadius / 2.0f, heightHalf - lineHeight / 2.0f,
                    lineStartX + lineWidth + nodeRadius / 2.0f, heightHalf + lineHeight / 2.0f,
                    paint);
            lineStartX -= (lineWidth + 2.0f * nodeRadius);
        }
        // 数字序号
        paint.setColor(nodeTextColor);
        paint.setTextSize(nodeTextSize);
        paint.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        float textStart = nodeRadius;
        for (int i = 1; i <= nodeAmount - 1; i++) {
            canvas.drawText(String.valueOf(i), textStart, heightHalf + textOffset, paint);
            textStart += (lineWidth + 2.0f * nodeRadius);
        }
        // 图片(勾)
        float picWidthHalf = completeRect.width() / 2.0f;
        float picHeightHalf = completeRect.height() / 2.0f;
        rectF.set(textStart - picWidthHalf, heightHalf - picHeightHalf,
                textStart + picWidthHalf, heightHalf + picHeightHalf);
        canvas.drawBitmap(complete, completeRect, rectF, paint);
    }

    /**
     * 测量数字序号的高度以计算高度中心的偏移量.
     */
    private void measureTextOffset() {
        paint.setTextSize(nodeTextSize);
        paint.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        Rect rect = new Rect();
        paint.getTextBounds("8", 0, 1, rect);
        textOffset = rect.height() / 2.0f;
    }

    /**
     * 设置当前页面所在序号.
     * 同时会刷新视图（默认为总进度的一半，向下取整）.
     *
     * @param nowSerialNumber 当前页面进度所在序号(从1开始)
     */
    public void setNowSerialNumber(int nowSerialNumber) {
        if (nowSerialNumber <= 0) {
            this.nowSerialNumber = 1;
        } else {
            this.nowSerialNumber = Math.min(nowSerialNumber, nodeAmount);
        }
        invalidate();
        forceLayout();
        requestLayout();
    }
}
