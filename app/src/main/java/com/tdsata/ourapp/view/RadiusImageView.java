package com.tdsata.ourapp.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Region;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import com.tdsata.ourapp.R;

/**
 * 圆角ImageView.
 *
 * 可设置的属性：
 *       属性名        值类型             说明          默认值
 *       radius      尺寸值(dp)      图片的圆角半径      10dp
 */
public class RadiusImageView extends AppCompatImageView {
    //private final MyLog myLog = new MyLog("HeadPortraitTAG");
    private final Path path;

    private final float radius;// 圆角半径

    public RadiusImageView(@NonNull Context context) {
        this(context, null);
    }

    public RadiusImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RadiusImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        path = new Path();
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.RadiusImageView);
        try {
            radius = typedArray.getDimension(R.styleable.RadiusImageView_radius,
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, context.getResources().getDisplayMetrics()));
        } finally {
            typedArray.recycle();
        }
        setLayerType(LAYER_TYPE_HARDWARE, null);// 关闭View级别硬件加速
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        path.addRoundRect(0f, 0f, getWidth(), getHeight(), radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(path, Region.Op.DIFFERENCE);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.SRC_IN);
        canvas.restore();
    }
}