package com.tdsata.ourapp.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.tdsata.ourapp.R;
import com.tdsata.ourapp.entity.SignInActivity;
import com.tdsata.ourapp.util.FixedValue;
import com.tdsata.ourapp.util.Tools;

import java.util.HashMap;
import java.util.Map;

/**
 * 作为部员展示二维码的页面.
 */
public class ActivitySignInCode extends AppCompatActivity {
    private final AppCompatActivity activity = this;
    private SignInActivity currentSignIn;
    private Tools.OnTimeChangeObserver timeChangeObserver = null;

    private Toolbar toolbar;
    private TextView activityTitle;
    private TextView endTime;
    private TextView myName;
    private TextView myNumber;
    private ImageView myQRCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in_code);

        initView();
        currentSignIn = (SignInActivity) getIntent().getSerializableExtra(FixedValue.currentSignIn);
        if (currentSignIn == null) {
            Toast.makeText(activity, "未知错误", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            setQRCode();
            activityTitle.setText(currentSignIn.getTitle());
            myName.setText(Tools.my.getName());
            myNumber.setText(Tools.my.getAccount());
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBackPressed();
                }
            });
        }
    }

    private void initView() {
        toolbar = findViewById(R.id.toolbar);
        activityTitle = findViewById(R.id.activityTitle);
        endTime = findViewById(R.id.endTime);
        myName = findViewById(R.id.myName);
        myNumber = findViewById(R.id.myNumber);
        myQRCode = findViewById(R.id.myQRCode);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (timeChangeObserver == null) {
            setEndTime();
            if (!currentSignIn.isEnd()) {
                timeChangeObserver = Tools.addTimeChangeObserver(new Tools.OnTimeChangeObserver() {
                    @Override
                    public void onMinuteChange() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setEndTime();
                            }
                        });
                    }
                });
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (timeChangeObserver != null) {
            Tools.removeTimeChangeObserver(timeChangeObserver);
            timeChangeObserver = null;
        }
    }

    @SuppressLint("SetTextI18n")
    private void setEndTime() {
        int endMinute = currentSignIn.getEndTime();
        if (endMinute > 0) {
            endTime.setText("距离签到结束还有" + endMinute + "分钟");
        } else if (endMinute == 0) {
            endTime.setText("距离签到结束在1分钟以内");
        } else {
            endTime.setText("签到已结束");
        }
    }

    private void setQRCode() {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF8");
        // 纠错等级
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        // 二维码边距
        hints.put(EncodeHintType.MARGIN, 2);
        try {
            BitMatrix bitMatrix = new QRCodeWriter().encode(currentSignIn.getTitle() + "$" + Tools.my.getAccount() + "$" + Tools.my.getName(), BarcodeFormat.QR_CODE, 512, 512, hints);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if(bitMatrix.get(x, y)) {
                        pixels[y * width + x] = 0xff000000;
                    }
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            //通过像素数组生成bitmap,具体参考api
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            myQRCode.setImageBitmap(bitmap);
        } catch (WriterException e) {
            Toast.makeText(activity, "未知错误", Toast.LENGTH_SHORT).show();
            activity.finish();
        }
    }
}