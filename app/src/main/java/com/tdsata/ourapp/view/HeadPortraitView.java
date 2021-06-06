package com.tdsata.ourapp.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PorterDuff.Mode;
import android.graphics.Region;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * 展示圆形图片的ImageView.
 * 作为头像展示使用.
 *
 * @version 1
 */
public class HeadPortraitView extends AppCompatImageView {
    //private final MyLog myLog = new MyLog("HeadPortraitTAG");
    private final Path path;

    public HeadPortraitView(@NonNull Context context) {
        this(context, null);
    }

    public HeadPortraitView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HeadPortraitView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        path = new Path();
        setLayerType(LAYER_TYPE_HARDWARE,null);// 关闭View级别硬件加速
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int height = getHeight();
        int width = getWidth();
        path.reset();
        path.addCircle(width / 2.0f, height / 2.0f, width / 2.0f, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(path, Region.Op.DIFFERENCE);
        canvas.drawColor(Color.TRANSPARENT, Mode.SRC_IN);
        canvas.restore();
    }
}
