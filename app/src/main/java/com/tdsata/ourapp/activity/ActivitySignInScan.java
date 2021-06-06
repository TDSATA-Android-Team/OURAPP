package com.tdsata.ourapp.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.tdsata.ourapp.R;
import com.tdsata.ourapp.entity.Member;
import com.tdsata.ourapp.entity.SignInActivity;
import com.tdsata.ourapp.entity.SignInInfo;
import com.tdsata.ourapp.util.FixedValue;
import com.tdsata.ourapp.util.MyLog;
import com.tdsata.ourapp.util.Server;
import com.tdsata.ourapp.util.Tools;
import com.tdsata.ourapp.view.ScanShadowView;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 签到扫码的页面.
 */
public class ActivitySignInScan extends AppCompatActivity {
    private final MyLog myLog = new MyLog("SignInScanTAG");
    private final AppCompatActivity activity = this;
    private final int permissionsRequestCode = Tools.getRequestCode();
    private Handler uiHandler;
    private CaptureRequest.Builder previewBuilder;
    private CameraManager cameraManager = null;
    private CameraDevice.StateCallback stateCallback = null;
    private boolean isStop = false;// 标记是否通过Home键、熄屏等使stop过
    private CameraDevice cameraDevice = null;
    private CameraCaptureSession cameraCaptureSession = null;
    private Size previewSize;
    private ImageReader imageReader;
    private SignInActivity currentSignIn = null;
    private final List<String> signInNumber = new LinkedList<>();
    private final List<String> signInName = new LinkedList<>();
    private final List<Integer> signInStatus = new LinkedList<>();
    private volatile boolean needUploadData = true;

    private Toolbar toolbar;
    private SurfaceView surfaceView;// 整个摄像预览界面
    private ScanShadowView scanShadow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in_scan);

        currentSignIn = (SignInActivity) getIntent().getSerializableExtra(FixedValue.currentSignIn);
        if (currentSignIn == null) {
            Toast.makeText(activity, "未知错误", Toast.LENGTH_SHORT).show();
            finish();
            return;
        } else if (!currentSignIn.isStart()) {
            Toast.makeText(activity, "请等待签到开始", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, permissionsRequestCode);
        } else {
            openPreview();
        }
    }

    private void initView() {
        toolbar = findViewById(R.id.toolbar);
        surfaceView = findViewById(R.id.surfaceView);
        scanShadow = findViewById(R.id.scanShadow);
    }

    /**
     * 通过Home等方式使页面不可见时，关闭相机.
     */
    @Override
    protected void onStop() {
        isStop = true;
        closeCamera();
        if (needUploadData) {
            uploadSignInData();
        }
        super.onStop();
    }

    @Override
    public void finish() {
        needUploadData = false;
        super.finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isStop) {
            isStop = false;
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try {
                    cameraManager.openCamera(String.valueOf(CameraCharacteristics.LENS_FACING_FRONT), stateCallback, uiHandler);
                } catch (CameraAccessException e) {
                    Toast.makeText(activity, "启用相机失败", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (signInNumber.size() > 0 && signInName.size() > 0 && signInStatus.size() > 0) {
            uploadSignInData();
        } else {
            super.onBackPressed();
        }
    }

    private void uploadSignInData() {
        if (signInNumber.size() > 0 && signInName.size() > 0 && signInStatus.size() > 0) {
            String[] multipleSignInNumber = new String[signInNumber.size()];
            String[] multipleSignInName = new String[signInName.size()];
            Integer[] multipleSignInStatus = new Integer[signInStatus.size()];
            signInNumber.toArray(multipleSignInNumber);
            signInName.toArray(multipleSignInName);
            signInStatus.toArray(multipleSignInStatus);
            List<SignInInfo> signInInfoList = new ArrayList<>();
            for (int i = 0; i < multipleSignInNumber.length; i++) {
                signInInfoList.add(new SignInInfo(multipleSignInNumber[i], multipleSignInName[i], multipleSignInStatus[i]));
            }
            needUploadData = false;
            new UploadSignInDataThread(activity, currentSignIn, multipleSignInNumber, multipleSignInStatus, signInInfoList, !isStop).start();
        }
    }

    private void closeCamera() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == permissionsRequestCode) {
            boolean noAllPermissions = false;
            for (int requestResult : grantResults) {
                if (requestResult != PackageManager.PERMISSION_GRANTED) {
                    noAllPermissions = true;
                    break;
                }
            }
            if (noAllPermissions) {
                Toast.makeText(activity, "权限不足", Toast.LENGTH_SHORT).show();
                activity.finish();
            } else {
                openPreview();// 此时已有权限
            }
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    private void openPreview() {
        initView();
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        scanShadow.startScanAnimation();
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        uiHandler = new Handler(getMainLooper());
        previewSize = getBestPreviewSize(cameraManager);
        surfaceView.setLayoutParams(new LinearLayout.LayoutParams(previewSize.getWidth(), previewSize.getHeight()));
        final SurfaceHolder surfaceHolder = surfaceView.getHolder();
        surfaceHolder.setKeepScreenOn(true);// 保持屏幕常开
        imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.JPEG, 2);
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            private final Timer timer = new Timer();

            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireNextImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                image.close();
                new DecodeQRCodeThread(bytes, new DecodeQRCodeObserver() {
                    private final Pattern pattern = Pattern.compile("^http[s]?://.*num=\\d{8}$");
                    private String title;
                    private String number;
                    private String name;

                    @Override
                    public void decodeResult(String result) {
                        String statusDescribe;
                        if (verifyQRCode(result)) {
                            if (currentSignIn.getTitle().equals(title)) {
                                signInNumber.add(number);
                                signInName.add(name);
                                // 1 - 已签到；2 - 已签到但迟到
                                if (currentSignIn.isEnd()) {
                                    signInStatus.add(2);
                                    statusDescribe = "已迟到";
                                } else {
                                    signInStatus.add(1);
                                    statusDescribe = "已签到";
                                }
                            } else {
                                Toast.makeText(activity, "请检查部员展示的二维码是否属于当前签到活动", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        } else {
                            Toast.makeText(activity, "请扫描部员的签到二维码\n" + result, Toast.LENGTH_SHORT).show();
                            timer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    decodeScanImage();
                                }
                            }, 1000);
                            return;
                        }
                        try {
                            cameraCaptureSession.stopRepeating();
                            Tools.showDialogOnlyOkButton(activity, Tools.TYPE_TIP, "姓名：" + name + "\n学号：" + number + "\n" + statusDescribe + "（若已有签到记录，将不会改变）", new Tools.ControlDialog() {
                                @Override
                                public void onDialog(AlertDialog dialog) {
                                    dialog.setCancelable(false);
                                    dialog.setCanceledOnTouchOutside(false);
                                }

                                @Override
                                public void onOkButton(AlertDialog dialog, View dialogView) {
                                    dialog.cancel();
                                    try {
                                        cameraCaptureSession.setRepeatingRequest(previewBuilder.build(), null, uiHandler);
                                        decodeScanImage();
                                    } catch (CameraAccessException e) {
                                        Toast.makeText(activity, "相机使用异常", Toast.LENGTH_SHORT).show();
                                        closeCamera();
                                    }
                                }

                                @Override
                                public void onCancelButton(AlertDialog dialog, View dialogView) {}
                            });
                        } catch (CameraAccessException e) {
                            Toast.makeText(activity, "相机使用异常", Toast.LENGTH_SHORT).show();
                            closeCamera();
                        }
                    }

                    @Override
                    public void onFail() {
                        decodeScanImage();
                    }

                    private boolean verifyQRCode(String result) {
                        Matcher matcher = pattern.matcher(result);
                        if (matcher.find()) {
                            String str = matcher.group();
                            title = currentSignIn.getTitle();
                            number = str.substring(str.length() - 8);
                            myLog.v("学号：" + number);
                            for (Member member : Tools.memberList) {
                                if (member.getNumber().equals(number)) {
                                    name = member.getName();
                                    return true;
                                }
                            }
                        }
                        try {
                            int startIndex = result.indexOf("$");
                            int endIndex = result.indexOf("$");
                            if (startIndex < 1 || endIndex > result.length() - 2) {
                                return false;
                            }
                            number = result.substring(startIndex + 1, endIndex);
                            Long.parseLong(number);
                            title = result.substring(0, startIndex);
                            name = result.substring(endIndex + 1);
                            return !"".equals(name);
                        } catch (Exception e) {
                            return false;
                        }
                    }
                }).start();
            }
        }, uiHandler);
        stateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                cameraDevice = camera;
                try {
                    previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    previewBuilder.addTarget(surfaceHolder.getSurface());
                    previewBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                    // 自动曝光
                    previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO);
                    // 自动白平衡
                    previewBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
                    // 自动对焦
                    previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    //光学防抖（OIS）。
                    previewBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
                    camera.createCaptureSession(Arrays.asList(surfaceHolder.getSurface(), imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            cameraCaptureSession = session;
                            try {
                                session.setRepeatingRequest(previewBuilder.build(), null, uiHandler);
                                decodeScanImage();
                            } catch (CameraAccessException e) {
                                myLog.e("扫码请求持续更新预览失败", e);
                                Toast.makeText(activity, "启用相机失败", Toast.LENGTH_SHORT).show();
                                closeCamera();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            myLog.d("扫码配置相机会话失败");
                            Toast.makeText(activity, "启用相机失败", Toast.LENGTH_SHORT).show();
                            closeCamera();
                        }
                    }, uiHandler);
                } catch (CameraAccessException e) {
                    myLog.e("扫码建立预览配置失败", e);
                    Toast.makeText(activity, "启用相机失败", Toast.LENGTH_SHORT).show();
                    closeCamera();
                }
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                closeCamera();
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                Toast.makeText(activity, "启用相机失败", Toast.LENGTH_SHORT).show();
                closeCamera();
            }

            @Override
            public void onClosed(@NonNull CameraDevice camera) {
                super.onClosed(camera);
            }
        };
        try {
            cameraManager.openCamera(String.valueOf(CameraCharacteristics.LENS_FACING_FRONT), stateCallback, uiHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Size getBestPreviewSize(CameraManager cameraManager) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();// 获取屏幕分辨率
        int deviceWidth = displayMetrics.widthPixels; //屏幕分辨率宽
        int deviceHeight = displayMetrics.heightPixels; //屏幕分辨率高
        Size result = null;
        try {
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(String.valueOf(CameraCharacteristics.LENS_FACING_FRONT));
            StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (streamConfigurationMap != null) {
                // 尺寸宽度 ==> 屏幕高度      尺寸高度 ==> 屏幕宽度
                Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
                Arrays.sort(sizes, new Comparator<Size>() {
                    @Override
                    public int compare(Size o1, Size o2) {
                        // 以屏幕宽度优先，降序排序
                        if (o1.getHeight() < o2.getHeight()) {
                            return 1;
                        } else if (o1.getHeight() > o2.getHeight()) {
                            return -1;
                        } else {
                            return Integer.compare(o2.getWidth(), o1.getWidth());
                        }
                    }
                });
                myLog.v("可用尺寸：" + Arrays.toString(sizes));
                for (Size size : sizes) {
                    float scale = (float) deviceWidth / size.getHeight();
                    int newHeight;
                    if ((newHeight = (int) (size.getWidth() * scale)) <= deviceHeight) {
                        result = new Size(deviceWidth, newHeight);
                        break;
                    }
                    /*if (size.getHeight() <= deviceWidth && size.getWidth() <= deviceHeight) {
                        result = new Size(size.getHeight(), size.getWidth());
                        break;
                    }*/
                }
                if (result == null || result.getWidth() < 512 || result.getHeight() < 512) {
                    myLog.v("没有合适的尺寸");
                    result = new Size(512, 512);
                }
            } else {
                myLog.w("不能获取支持的尺寸信息");
                result = new Size(512, 512);
            }
        } catch (CameraAccessException e) {
            myLog.e("获取尺寸时，访问后置相机失败", e);
            result = new Size(512, 512);
        } catch (Exception e) {
            result = new Size(512, 512);
        }
        if (result.getWidth() != deviceWidth) {
            result = new Size(deviceWidth, deviceWidth * result.getHeight() / result.getWidth());
        }
        return result;
        //return new Size(deviceWidth, deviceHeight);
    }

    private void decodeScanImage() {
        new Thread() {
            @Override
            public void run() {
                if (cameraDevice != null) {
                    try {
                        CaptureRequest.Builder photoBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                        photoBuilder.addTarget(imageReader.getSurface());
                        cameraCaptureSession.capture(photoBuilder.build(), null, uiHandler);
                    } catch (CameraAccessException e) {
                        myLog.e("解析QR码中相机使用异常", e);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(activity, "相机使用异常", Toast.LENGTH_SHORT).show();
                                closeCamera();
                            }
                        });
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }.start();
    }

    private interface DecodeQRCodeObserver {
        void decodeResult(String result);

        void onFail();
    }

    private class DecodeQRCodeThread extends Thread {
        private final byte[] picData;
        private final DecodeQRCodeObserver observer;

        DecodeQRCodeThread(byte[] picData, DecodeQRCodeObserver observer) {
            this.picData = picData;
            this.observer = observer;
        }

        @Override
        public void run() {
            if (cameraDevice != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(picData, 0, picData.length);
                // 计算出扫描框内图像范围
                float scanWidth = scanShadow.getScanWidth();
                int cutWidth = (int) (bitmap.getWidth() * scanWidth / previewSize.getWidth());
                int cutHeight = (int) (bitmap.getHeight() * scanWidth / previewSize.getHeight());
                int cutStartX = (int) (bitmap.getWidth() * ((previewSize.getWidth() - scanWidth) / 2.0f) / previewSize.getWidth());
                int cutStartY = (int) (bitmap.getHeight() * ((previewSize.getHeight() - scanWidth) / 2.0f) / previewSize.getHeight());
                /*// 适当扩大扫描范围（保持中心点，横纵方向各扩大1/3）
                float widthIncrement = cutWidth / 3.0f;
                float heightIncrement = cutHeight / 3.0f;
                cutStartX -= (widthIncrement / 2.0f);
                cutStartY -= (heightIncrement / 2.0f);
                cutWidth += widthIncrement;
                cutHeight += heightIncrement;*/
                // 计算缩放
                int bestWidth = 512;
                //int bestHeight = 512;
                Matrix matrix = new Matrix();
                /*float scale;
                if (cutWidth > cutHeight) {
                    scale = (float) bestWidth / cutWidth;
                } else {
                    scale = (float) bestHeight / cutHeight;
                }*/
                final float scale = (float) bestWidth / cutWidth;
                matrix.setScale(scale, scale);
                bitmap = getBinaryzationBitmap(Bitmap.createBitmap(bitmap, cutStartX, cutStartY, cutWidth, cutHeight, matrix, true));
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                int[] pixels = new int[width * height];
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                BinaryBitmap image = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
                Map<DecodeHintType, Object> hints = new HashMap<>();
                hints.put(DecodeHintType.CHARACTER_SET, "UTF8");
                try {
                    final Result result = new QRCodeReader().decode(image, hints);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (cameraDevice != null) {
                                observer.decodeResult(result.getText());
                            }
                        }
                    });
                } catch (NotFoundException | FormatException | ChecksumException e) {
                    /*try {
                        sleep(500);
                    } catch (InterruptedException interruptedException) {
                        // ignore
                    }*/
                    observer.onFail();
                } finally {
                    bitmap.recycle();
                }
            }
        }

        /**
         * 对图片进行二值化处理.
         * 原作者：https://blog.csdn.net/xuwenneng
         * 原博客文章：https://blog.csdn.net/xuwenneng/article/details/52635945
         *
         * @param srcPic 原始图片
         * @return 二值化处理后的图片
         */
        private Bitmap getBinaryzationBitmap(Bitmap srcPic) {
            Bitmap bitmap;
            // 获取图片的宽和高
            int width = srcPic.getWidth();
            int height = srcPic.getHeight();
            // 创建二值化图像
            bitmap = srcPic.copy(Bitmap.Config.ARGB_8888, true);
            srcPic.recycle();
            // 遍历原始图像像素,并进行二值化处理
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    // 得到当前的像素值
                    int pixel = bitmap.getPixel(i, j);
                    // 得到Alpha通道的值
                    int alpha = pixel & 0xFF000000;
                    // 得到Red的值
                    int red = (pixel & 0x00FF0000) >> 16;
                    // 得到Green的值
                    int green = (pixel & 0x0000FF00) >> 8;
                    // 得到Blue的值
                    int blue = pixel & 0x000000FF;
                    // 通过加权平均算法,计算出最佳像素值
                    int gray = (int) ((float) red * 0.3 + (float) green * 0.59 + (float) blue * 0.11);
                    // 对图像设置黑白图
                    if (gray <= 95) {
                        gray = 0;
                    } else {
                        gray = 255;
                    }
                    // 得到新的像素值
                    int newPiexl = alpha | (gray << 16) | (gray << 8) | gray;
                    // 赋予新图像的像素
                    bitmap.setPixel(i, j, newPiexl);
                }
            }
            return bitmap;
        }
    }

    private static class UploadSignInDataThread extends Thread {
        private final MyLog myLog = new MyLog("ScanUploadThreadTAG");
        private final AppCompatActivity activity;
        private final SignInActivity currentSignIn;
        private final List<SignInInfo> signInInfoList;
        private final String[] multipleSignInNumber;
        private final Integer[] multipleSignInStatus;
        private final boolean finishOnOver;
        private volatile static File cacheFile = null;

        private UploadSignInDataThread(final AppCompatActivity activity, SignInActivity currentSignIn, String[] multipleSignInNumber, Integer[] multipleSignInStatus, List<SignInInfo> signInInfoList, boolean finishOnOver) {
            this.activity = activity;
            this.currentSignIn = currentSignIn;
            this.signInInfoList = signInInfoList;
            this.multipleSignInNumber = multipleSignInNumber;
            this.multipleSignInStatus = multipleSignInStatus;
            this.finishOnOver = finishOnOver;
            if (cacheFile == null) {
                cacheFile = Tools.saveSignInData(activity, currentSignIn, signInInfoList);
                if (cacheFile == null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity, "签到数据暂存至本地失败，正在同步数据到服务器", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }

        @Override
        public void run() {
            Server.setMultipleSignInStatus(currentSignIn.getTitle(), multipleSignInNumber, multipleSignInStatus, activity, new Server.EndOfRequest() {
                @Override
                public void onSuccess(String result) {
                    switch (result) {
                        case "OK":
                            if (cacheFile != null && !cacheFile.delete()) {
                                myLog.w("缓存文件删除失败：" + cacheFile.getPath());
                            }
                            cacheFile = null;
                            Toast.makeText(activity, "签到数据已同步至服务器", Toast.LENGTH_SHORT).show();
                            if (finishOnOver) {
                                activity.finish();
                            }
                            break;
                        case "ACTIVITY_NOT_EXIST":
                            Toast.makeText(activity, "签到活动不存在，可能已被删除", Toast.LENGTH_SHORT).show();
                            if (finishOnOver) {
                                activity.finish();
                            }
                            break;
                        case "AES_KEY_ERROR":
                            Server.startInitConnectionThread();
                        case "ERROR":
                        default:
                            onException();
                            break;
                    }
                }

                @Override
                public void onException() {
                    Tools.showDialogOnlyOkButton(activity, Tools.TYPE_ERROR, "同步至服务器发生异常，将导出到文件", new Tools.ControlDialog() {
                        @Override
                        public void onDialog(AlertDialog dialog) {
                            dialog.setCancelable(false);
                            dialog.setCanceledOnTouchOutside(false);
                        }

                        @Override
                        public void onOkButton(AlertDialog dialog, View dialogView) {
                            dialog.dismiss();
                            File file = Tools.exportSignInData(activity, currentSignIn.getTitle(), signInInfoList);
                            if (file != null) {
                                Toast.makeText(activity, "已导出至" + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
                            } else {// 没办法了，救不了了
                                Toast.makeText(activity, "导出失败", Toast.LENGTH_SHORT).show();
                            }
                            if (finishOnOver) {
                                activity.finish();
                            }
                        }

                        @Override
                        public void onCancelButton(AlertDialog dialog, View dialogView) {}
                    });
                }

                @Override
                public void onTimeout() {
                    Toast.makeText(activity, "连接超时", Toast.LENGTH_SHORT).show();
                    onNetException();
                }

                @Override
                public void noNet() {
                    Toast.makeText(activity, "请检查网络连接", Toast.LENGTH_SHORT).show();
                    onNetException();
                }

                private void onNetException() {
                    Tools.showDialogOnlyOkButton(activity, Tools.TYPE_WARNING, "为避免数据丢失，请重新上传数据", new Tools.ControlDialog() {
                        @Override
                        public void onDialog(AlertDialog dialog) {
                            dialog.setCancelable(false);
                            dialog.setCanceledOnTouchOutside(false);
                        }

                        @Override
                        public void onOkButton(AlertDialog dialog, View dialogView) {
                            dialog.dismiss();
                            new UploadSignInDataThread(activity, currentSignIn, multipleSignInNumber, multipleSignInStatus, signInInfoList, finishOnOver).start();
                        }

                        @Override
                        public void onCancelButton(AlertDialog dialog, View dialogView) {}
                    });
                }
            });
        }
    }
}