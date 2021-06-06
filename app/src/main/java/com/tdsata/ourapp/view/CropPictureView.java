package com.tdsata.ourapp.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.tdsata.ourapp.R;
import com.tdsata.ourapp.util.MyLog;

import java.io.FileNotFoundException;

/**
 * 裁剪图片视图.
 *
 * 可设置的属性：
 *              属性名           值类型                      说明                                   默认值
 *          backgroundColor      颜色值               整个视图的背景基色                            #424242
 *         transparentScale      浮点值      非裁剪框区域覆盖的黑色(#000000)幕布透明度[0.1, 1.0]      0.5
 *         cropFrameColor        颜色值               裁剪框框线的颜色                            Color.WHITE
 *       cropFrameStrokeWidth  尺寸值(dp)               裁剪框正方形框线宽度                          2dp
 *         cropFrameWeight     尺寸值(px)                正方形裁剪框的边长                          500px
 *
 * @see #setSrcPic(Drawable)
 * @see #setSrcPic(Uri)
 * @see #setSrcPic(Bitmap)
 * @see #getCropPicBitmap()
 * @version 1
 */
public class CropPictureView extends View {
    private final MyLog myLog = new MyLog("CropPictureViewTAG");
    private final Context context;
    private final Paint paint;
    private final Path path;
    private final DashPathEffect dashPathEffect;
    private final Rect picRect;// 图片区域的矩阵
    private final RectF cropFrameRectF;// 裁剪框区域的矩阵
    private Drawable srcPic;// 被操作的图片
    private Bitmap thePic;// 图片的Bitmap形式
    private boolean initPicRect = true;// 标记是否初始化图片所在位置（初始化picRect）
    private float drawFrameStartX = -1.0f;// 记录裁剪框绘制的left坐标（-1表示使用初始值）
    private float drawFrameStartY = -1.0f;// 记录裁剪框绘制的top坐标（-1表示使用初始值）
    private float xAtFrame = 0.0f;// 记录移动裁剪框时的首次触摸点在裁剪框中的相对x坐标
    private float yAtFrame = 0.0f;// 记录移动裁剪框时的首次触摸点在裁剪框中的相对y坐标
    private boolean frameMoving = false;// 标记是否要移动裁剪框
    private boolean doubleFingerMoving = false;// 标记是否要放缩图片
    private final FingerPoint[] fingerPoints = new FingerPoint[] {new FingerPoint(), new FingerPoint()};// 存储双指的坐标
    private double lastTwoPointDistance = 0;// 记录双指的上一个坐标间距
    private final Point oldFingerMidPoint = new Point();// 记录双指的上一个坐标中点
    private final Point fingerMidPoint = new Point();// 记录当前双指的坐标中点
    private final Point zoomMidPoint = new Point();// 缩放中心点
    private final PointF zoomPointScale = new PointF();// x、y坐标分别表示缩放中心点分别在picRect宽度上和高度上的比例

    private final int backgroundColor;// 整个视图的背景基色
    private float transparentScale;// 黑色幕布（阴影层）透明度
    private final int cropFrameColor;// 裁剪框线的颜色
    private final float cropFrameStrokeWidth;// 裁剪框四角的描线宽度
    private final float cropFrameWeight;// 正方形裁剪框的边长

    public CropPictureView(Context context) {
        this(context, null, 0);
    }

    public CropPictureView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CropPictureView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        paint = new Paint();
        path = new Path();
        cropFrameRectF = new RectF();
        picRect = new Rect();
        dashPathEffect = new DashPathEffect(new float[] {10, 5}, 0);
        float oneDp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.0f, context.getResources().getDisplayMetrics());
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.CropPictureView);
        try {
            backgroundColor = typedArray.getColor(R.styleable.CropPictureView_backgroundColor, Color.rgb(66, 66, 66));
            transparentScale = typedArray.getFloat(R.styleable.CropPictureView_transparentScale, 0.5f);
            if (transparentScale < 0.1f) {
                transparentScale = 0.1f;
            } else if (transparentScale > 1.0f) {
                transparentScale = 1.0f;
            }
            cropFrameColor = typedArray.getColor(R.styleable.CropPictureView_cropFrameColor, Color.WHITE);
            cropFrameStrokeWidth = typedArray.getDimension(R.styleable.CropPictureView_cropFrameStrokeWidth, 2 * oneDp);
            cropFrameWeight = typedArray.getDimension(R.styleable.CropPictureView_cropFrameWeight, 500);
        } finally {
            typedArray.recycle();
        }
    }

    private void resetDrawTools() {
        paint.reset();
        path.reset();
    }

    private void refresh() {
        invalidate();
        requestLayout();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(backgroundColor);
        int width = getWidth();
        int height = getHeight();
        float widthHalf = width / 2.0f;
        float heightHalf = height / 2.0f;
        // 绘制图片
        if (srcPic != null) {
            if (initPicRect) {
                int srcPicWidth = srcPic.getIntrinsicWidth();
                int srcPicHeight = srcPic.getIntrinsicHeight();
                int drawPicStartX, drawPicStartY, drawPicWidth, drawPicHeight;
                if (srcPicWidth > srcPicHeight) {// 宽图
                    if (srcPicHeight >= 0.25f * cropFrameWeight) {// 图高大于裁剪框的 1/4 则将宽度匹配为裁剪框的宽度
                        drawPicHeight = (int) cropFrameWeight;
                        drawPicWidth = drawPicHeight * srcPicWidth / srcPicHeight;
                    } else {// 图高小于裁剪框的 1/4 则将宽度匹配为控件宽度
                        drawPicWidth = width;
                        drawPicHeight = width * srcPicHeight / srcPicWidth;
                    }
                } else {// 长图
                    if (srcPicWidth >= 0.25f * cropFrameWeight) {// 图宽大于裁剪框的 1/4 则将高度匹配为裁剪框的高度
                        drawPicWidth = (int) cropFrameWeight;
                        drawPicHeight = drawPicWidth * srcPicHeight / srcPicWidth;
                    } else {// 图宽小于裁剪框的 1/4 则将高度匹配为控件高度
                        drawPicHeight = height;
                        drawPicWidth = height * srcPicWidth / srcPicHeight;
                    }
                }
                drawPicStartX = (int) (widthHalf - drawPicWidth / 2.0f);
                drawPicStartY = (int) (heightHalf - drawPicHeight / 2.0f);
                picRect.set(drawPicStartX, drawPicStartY,
                        drawPicStartX + drawPicWidth, drawPicStartY + drawPicHeight);
            }
            srcPic.setBounds(picRect);
            srcPic.draw(canvas);
        }
        // 绘制裁剪框
        if (drawFrameStartX < 0 || drawFrameStartY < 0) {
            drawFrameStartX = widthHalf - cropFrameWeight / 2.0f;
            drawFrameStartY = heightHalf - cropFrameWeight / 2.0f;
        }
        drawCropFrame(canvas, drawFrameStartX, drawFrameStartY);
    }

    /**
     * 绘制裁剪框.
     *
     * @param canvas 控件画布
     * @param drawFrameStartX 绘制裁剪框的startX（left）
     * @param drawFrameStartY 绘制裁剪框的startY（top）
     */
    private void drawCropFrame(Canvas canvas, float drawFrameStartX, float drawFrameStartY) {
        resetDrawTools();
        int width = getWidth();
        int height = getHeight();
        // 绘制未选中的阴影层
        float drawFrameEndX = drawFrameStartX + cropFrameWeight;// 绘制裁剪框的endX（right）
        float drawFrameEndY = drawFrameStartY + cropFrameWeight;// 绘制裁剪框的endY（bottom）
        cropFrameRectF.set(drawFrameStartX, drawFrameStartY, drawFrameEndX, drawFrameEndY);
        paint.setColor(Color.argb((int) (255 * transparentScale), 0, 0, 0));
        paint.setStyle(Paint.Style.FILL);
        path.addRect(0, 0, width, drawFrameStartY, Path.Direction.CW);
        path.addRect(0, drawFrameStartY, drawFrameStartX, drawFrameEndY, Path.Direction.CW);
        path.addRect(drawFrameEndX, drawFrameStartY, width, drawFrameEndY, Path.Direction.CW);
        path.addRect(0, drawFrameEndY, width, height, Path.Direction.CW);
        canvas.drawPath(path, paint);
        // 绘制裁剪框的四个角
        float drawLength = cropFrameWeight * 0.1f;
        float strokeWidthHalf = cropFrameStrokeWidth / 2.0f;// 裁剪框框线宽度的一半
        resetDrawTools();
        paint.setColor(cropFrameColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(cropFrameStrokeWidth);
        // 左上角
        path.moveTo(drawFrameStartX - strokeWidthHalf, drawFrameStartY + drawLength);
        path.lineTo(drawFrameStartX - strokeWidthHalf, drawFrameStartY - strokeWidthHalf);
        path.lineTo(drawFrameStartX + drawLength, drawFrameStartY - strokeWidthHalf);
        // 右上角
        path.moveTo(drawFrameEndX - drawLength, drawFrameStartY - strokeWidthHalf);
        path.lineTo(drawFrameEndX + strokeWidthHalf, drawFrameStartY - strokeWidthHalf);
        path.lineTo(drawFrameEndX + strokeWidthHalf, drawFrameStartY + drawLength);
        // 右下角
        path.moveTo(drawFrameEndX + strokeWidthHalf, drawFrameEndY - drawLength);
        path.lineTo(drawFrameEndX + strokeWidthHalf, drawFrameEndY + strokeWidthHalf);
        path.lineTo(drawFrameEndX - drawLength, drawFrameEndY + strokeWidthHalf);
        // 左下角
        path.moveTo(drawFrameStartX + drawLength, drawFrameEndY + strokeWidthHalf);
        path.lineTo(drawFrameStartX - strokeWidthHalf, drawFrameEndY + strokeWidthHalf);
        path.lineTo(drawFrameStartX - strokeWidthHalf, drawFrameEndY - drawLength);
        canvas.drawPath(path, paint);
        // 绘制裁剪框内的虚线圆
        paint.setStrokeWidth(strokeWidthHalf);// 描边宽度为四角描边宽度的一半
        paint.setPathEffect(dashPathEffect);// 虚线
        paint.setAntiAlias(true);// 绘制曲线采用抗锯齿
        canvas.drawCircle(drawFrameStartX + cropFrameWeight / 2.0f, drawFrameStartY + cropFrameWeight / 2.0f,
                cropFrameWeight / 2.0f - strokeWidthHalf / 2.0f, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (thePic != null) {
            float x, y;
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    x = event.getX(0);
                    y = event.getY(0);
                    doubleFingerMoving = false;
                    fingerPoints[0].setFloat(x, y);
                    frameMoving = cropFrameRectF.contains(x, y);
                    if (frameMoving) {
                        xAtFrame = x - cropFrameRectF.left;
                        yAtFrame = y - cropFrameRectF.top;
                    }
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    frameMoving = false;
                    fingerPoints[1].setFloat(event.getX(1), event.getY(1));
                    doubleFingerMoving = event.getPointerCount() == 2;
                    if (doubleFingerMoving) {
                        setFingerMidPoint();
                        setZoomMidPoint();
                        lastTwoPointDistance = getTwoPointDistance();
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (frameMoving) {
                        drawFrameStartX = event.getX(0) - xAtFrame;
                        drawFrameStartY = event.getY(0) - yAtFrame;
                        if (drawFrameStartX < 0) {
                            drawFrameStartX = 0;
                        } else if (drawFrameStartX + cropFrameWeight > getWidth()) {
                            drawFrameStartX = getWidth() - cropFrameWeight;
                        } else if (Math.abs(drawFrameStartX - picRect.left) <= 10) {
                            drawFrameStartX = picRect.left;
                        } else if (Math.abs(drawFrameStartX + cropFrameWeight - picRect.right) <= 10) {
                            drawFrameStartX = picRect.right - cropFrameWeight;
                        }
                        if (drawFrameStartY < 0) {
                            drawFrameStartY = 0;
                        } else if (drawFrameStartY + cropFrameWeight > getHeight()) {
                            drawFrameStartY = getHeight() - cropFrameWeight;
                        } else if (Math.abs(drawFrameStartY - picRect.top) <= 10) {
                            drawFrameStartY = picRect.top;
                        } else if (Math.abs(drawFrameStartY + cropFrameWeight - picRect.bottom) <= 10) {
                            drawFrameStartY = picRect.bottom - cropFrameWeight;
                        }
                        refresh();
                    }
                    if (doubleFingerMoving) {
                        oldFingerMidPoint.set(fingerMidPoint.x, fingerMidPoint.y);
                        fingerPoints[0].setFloat(event.getX(0), event.getY(0));
                        fingerPoints[1].setFloat(event.getX(1), event.getY(1));
                        setFingerMidPoint();
                        double distance = getTwoPointDistance();
                        double diff = distance - lastTwoPointDistance;
                        if (Math.abs(diff) <= 30) {// 双指平行移动
                            int xDiff = oldFingerMidPoint.x - fingerMidPoint.x;
                            int yDiff = oldFingerMidPoint.y - fingerMidPoint.y;
                            picRect.left -= xDiff;
                            picRect.top -= yDiff;
                            picRect.right -= xDiff;
                            picRect.bottom -= yDiff;
                        } else {// 缩放手势
                            double scale = distance / lastTwoPointDistance;
                            int width = (int) (picRect.width() * scale);
                            int height = (int) (picRect.height() * scale);
                            if (width < cropFrameWeight) {
                                width = (int) cropFrameWeight;
                                height = width * picRect.height() / picRect.width();
                            }
                            if (height < cropFrameWeight) {
                                height = (int) cropFrameWeight;
                                width = height * picRect.width() / picRect.height();
                            }
                            picRect.left = (int) (zoomMidPoint.x - width * zoomPointScale.x);
                            picRect.right = picRect.left + width;
                            picRect.top = (int) (zoomMidPoint.y - height * zoomPointScale.y);
                            picRect.bottom = picRect.top + height;
                        }
                        setZoomMidPoint();
                        lastTwoPointDistance = distance;
                        if (initPicRect) {
                            initPicRect = false;
                        }
                        refresh();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    performClick();
                default:
                    doubleFingerMoving = false;
                    frameMoving = false;
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private double getTwoPointDistance() {
        return Math.sqrt(Math.pow(fingerPoints[0].x - fingerPoints[1].x, 2) +
                Math.pow(fingerPoints[0].y - fingerPoints[1].y, 2));
    }

    private void setFingerMidPoint() {
        fingerMidPoint.set((fingerPoints[0].x + fingerPoints[1].x) / 2, (fingerPoints[0].y + fingerPoints[1].y) / 2);
    }

    private void setZoomMidPoint() {
        int centerX = (fingerPoints[0].x + fingerPoints[1].x) / 2;
        int centerY = (fingerPoints[0].y + fingerPoints[1].y) / 2;
        if (centerX < picRect.left) {
            centerX = picRect.left;
        } else if (centerX > picRect.right) {
            centerX = picRect.right;
        }
        if (centerY < picRect.top) {
            centerY = picRect.top;
        } else if (centerY > picRect.bottom) {
            centerY = picRect.bottom;
        }
        zoomPointScale.set((centerX - picRect.left) / (float) picRect.width(), (centerY - picRect.top) / (float) picRect.height());
        zoomMidPoint.set(centerX, centerY);
    }

    private static class FingerPoint extends Point {
        void setFloat(float x, float y) {
            this.x = (int) x;
            this.y = (int) y;
        }
    }

    /**
     * 设置被裁剪的图片.
     *
     * @param pic 图片的Drawable对象
     */
    public void setSrcPic(final Drawable pic) {
        if (pic == null) {
            throw new NullPointerException("Drawable图片为null");
        }
        this.srcPic = pic;
        // 避免大图片阻塞线程
        new Thread() {
            @Override
            public void run() {
                int width = pic.getIntrinsicWidth();
                int height = pic.getIntrinsicHeight();
                thePic = Bitmap.createBitmap(width, height, pic.getOpacity() != PixelFormat.OPAQUE ? Config.ARGB_8888 : Config.RGB_565);
                Canvas canvas = new Canvas(thePic);
                pic.setBounds(0, 0, width, height);
                pic.draw(canvas);
            }
        }.start();
        drawFrameStartX = -1.0f;
        drawFrameStartY = -1.0f;
        initPicRect = true;
        refresh();
    }

    /**
     * 设置被裁剪的图片.
     *
     * @param pic 图片的Uri路径
     * @throws FileNotFoundException 当无法打开Uri路径指向的文件时抛出此异常
     */
    public void setSrcPic(Uri pic) throws FileNotFoundException {
        if (pic == null) {
            throw new NullPointerException("图片的uri为null");
        }
        Drawable drawable = Drawable.createFromStream(context.getContentResolver().openInputStream(pic), null);
        setSrcPic(drawable);
    }

    /**
     * 设置被裁剪的图片.
     *
     * @param pic 图片的Bitmap对象
     */
    public void setSrcPic(Bitmap pic) {
        if (pic == null) {
            throw new NullPointerException("Bitmap图片为null");
        }
        thePic = pic;
        srcPic = new BitmapDrawable(context.getResources(), pic);
        drawFrameStartX = -1.0f;
        drawFrameStartY = -1.0f;
        initPicRect = true;
        refresh();
    }

    /**
     * 获得裁剪框区域的图片.
     *
     * @return 裁剪框区域图片的Bitmap对象
     */
    public Bitmap getCropPicBitmap() {
        if (thePic != null) {
            Bitmap bitmap = Bitmap.createBitmap((int) cropFrameWeight, (int) cropFrameWeight, thePic.getConfig());
            Canvas canvas = new Canvas(bitmap);
            Region cropFrame = new Region((int) cropFrameRectF.left, (int) cropFrameRectF.top,
                    (int) cropFrameRectF.right, (int) cropFrameRectF.bottom);// 裁剪框区域
            Region pic = new Region(picRect);// 图片区域
            cropFrame.op(pic, Region.Op.INTERSECT);// 取交集
            Rect cutPicRect = cropFrame.getBounds();
            if (!cutPicRect.isEmpty()) {
                // 交集左上角相对于取景框的坐标
                Point atFrame = new Point((int) (cutPicRect.left - cropFrameRectF.left), (int) (cutPicRect.top - cropFrameRectF.top));
                // 交集左上角相对于图片的坐标比例
                PointF atPic = new PointF((cutPicRect.left - picRect.left) / (float) picRect.width(),
                        (cutPicRect.top - picRect.top) / (float) picRect.height());
                int cutWidth = thePic.getWidth() * cutPicRect.width() / picRect.width();// 按比例在图片的Bitmap形式上裁剪的宽度
                int cutHeight = thePic.getHeight() * cutPicRect.height() / picRect.height();// 按比例在图片的Bitmap形式上裁剪的高度
                // 裁剪图片
                Bitmap cutPic = Bitmap.createBitmap(thePic, (int) (thePic.getWidth() * atPic.x),
                        (int) (thePic.getHeight() * atPic.y), cutWidth, cutHeight);
                Drawable cutPicD = new BitmapDrawable(context.getResources(), cutPic);
                cutPicD.setBounds(atFrame.x, atFrame.y, atFrame.x + cutPicRect.width(), atFrame.y + cutPicRect.height());
                cutPicD.draw(canvas);
                cutPic.recycle();
            }
            canvas.save();
            return bitmap;
        }
        myLog.d("thePic为空");
        return null;
    }
}
