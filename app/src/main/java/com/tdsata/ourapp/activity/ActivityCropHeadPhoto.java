package com.tdsata.ourapp.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.tdsata.ourapp.R;
import com.tdsata.ourapp.util.FixedValue;
import com.tdsata.ourapp.util.Server;
import com.tdsata.ourapp.util.Tools;
import com.tdsata.ourapp.view.CropPictureView;

import java.io.FileNotFoundException;

public class ActivityCropHeadPhoto extends AppCompatActivity {
    private final AppCompatActivity activity = this;

    private Toolbar toolbar;
    private CropPictureView cropPhoto;
    private Button ok;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop_head_photo);

        Bitmap srcPic = getIntent().getParcelableExtra(FixedValue.cropBitmap);
        Uri srcUri = getIntent().getParcelableExtra(FixedValue.cropUri);
        if (srcPic == null && srcUri == null) {
            Toast.makeText(activity, "解析图片失败", Toast.LENGTH_SHORT).show();
            finish();
        }
        initView();
        myListener();
        if (srcPic != null) {
            cropPhoto.setSrcPic(srcPic);
        } else if (srcUri != null) {
            try {
                cropPhoto.setSrcPic(srcUri);
            } catch (FileNotFoundException e) {
                Toast.makeText(activity, "解析图片失败", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void initView() {
        toolbar = findViewById(R.id.toolbar);
        cropPhoto = findViewById(R.id.cropPhoto);
        ok = findViewById(R.id.ok);
    }

    private void myListener() {
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bitmap crop = cropPhoto.getCropPicBitmap();
                final byte[] cropBytes = Tools.getRightDataFromBitmap(crop);
                if (cropBytes == null) {
                    Toast.makeText(activity, "图片过大，请重新裁剪", Toast.LENGTH_SHORT).show();
                    return;
                }
                final AlertDialog waitDialog = Tools.showPleaseWait(activity);
                Server.uploadHeadPhoto(cropBytes, activity, new Server.EndOfRequest() {
                    @Override
                    public void onSuccess(String result) {
                        waitDialog.dismiss();
                        switch (result) {
                            case "PIC_TOO_BIG":
                                Toast.makeText(activity, "图片过大，请重新裁剪", Toast.LENGTH_SHORT).show();
                                break;
                            case "AES_KEY_ERROR":
                                Server.startInitConnectionThread();
                            case "ERROR":
                                Toast.makeText(activity, "上传失败，请重试", Toast.LENGTH_SHORT).show();
                                break;
                            default:
                                String newPhotoName = Server.aesDecryptData(result);
                                if (newPhotoName != null && newPhotoName.startsWith(Tools.getMD5(Tools.my.getAccount()).substring(0, 8))) {
                                    Tools.deleteFile(Tools.generateFileAtCache(activity, FixedValue.photoDirectory, Tools.my.getPhotoName(), true));
                                    Tools.my.getMemberInfo().setPhotoName(newPhotoName);
                                    Tools.savePhotoFromBytes(activity, cropBytes, Tools.my.getMemberInfo());
                                    Intent back = new Intent();
                                    back.putExtra(FixedValue.cropResult, newPhotoName);
                                    setResult(RESULT_OK, back);
                                    activity.finish();
                                } else {
                                    Toast.makeText(activity, "上传失败，请重试", Toast.LENGTH_SHORT).show();
                                }
                                break;
                        }
                    }

                    @Override
                    public void onException() {
                        waitDialog.dismiss();
                        Toast.makeText(activity, "上传失败，请重试", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onTimeout() {
                        waitDialog.dismiss();
                        Toast.makeText(activity, "连接超时", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void noNet() {
                        waitDialog.dismiss();
                        Toast.makeText(activity, "网络连接不畅", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}