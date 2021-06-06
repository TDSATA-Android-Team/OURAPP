package com.tdsata.ourapp.activity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.tdsata.ourapp.R;
import com.tdsata.ourapp.util.Server;
import com.tdsata.ourapp.util.Tools;

public class ActivityUploadPersonalInfo extends AppCompatActivity {
    private final AppCompatActivity activity = this;
    private String sex = "";

    private View returnArrow;
    private TextView title;
    private EditText inSubject;
    private RadioGroup inSex;
    private RadioButton sexMale;
    private RadioButton sexFemale;
    private EditText inPhone;
    private EditText inQQ;
    private EditText inTeacher;
    private EditText inEmail;
    private View verifiedTip;
    private View unverifiedTip;
    private Button ok;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_personal_info);

        initView();
        initInfo();
        myListener();
        Server.refreshMembers(activity, new Server.EndOfRequest() {
            @Override
            public void onSuccess(String result) {
                switch (result) {
                    case "AES_KEY_ERROR":
                        Server.startInitConnectionThread();
                    case "ERROR":
                        onFail();
                        break;
                    default:
                        if (Tools.refreshMemberList(activity, Server.aesDecryptData(result))) {
                            initInfo();
                            new Server.RefreshLocalPhotoThread(activity, null).start();
                        } else {
                            onFail();
                        }
                }
            }

            @Override
            public void onException() {
                onFail();
            }

            @Override
            public void onTimeout() {
                onFail();
            }

            @Override
            public void noNet() {
                onFail();
            }

            private void onFail() {
                Toast.makeText(activity, "刷新失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initView() {
        returnArrow = findViewById(R.id.returnArrow);
        title = findViewById(R.id.title);
        inSubject = findViewById(R.id.inSubject);
        inSex = findViewById(R.id.inSex);
        sexMale = findViewById(R.id.sexMale);
        sexFemale = findViewById(R.id.sexFemale);
        inPhone = findViewById(R.id.inPhone);
        inQQ = findViewById(R.id.inQQ);
        inTeacher = findViewById(R.id.inTeacher);
        inEmail = findViewById(R.id.inEmail);
        verifiedTip = findViewById(R.id.verifiedTip);
        unverifiedTip = findViewById(R.id.unverifiedTip);
        ok = findViewById(R.id.ok);
    }

    private void initInfo() {
        if (Tools.my.infoEnable) {
            title.setText("修改个人信息");
            inSubject.setText(Tools.my.getSubject());
            switch (Tools.my.getSex()) {
                case "男":
                    inSex.check(R.id.sexMale);
                    break;
                case "女":
                    inSex.check(R.id.sexFemale);
                    break;
            }
            inPhone.setText(Tools.my.getPhone());
            inQQ.setText(Tools.my.getQQ());
            inTeacher.setText(Tools.my.getTeacher());
            inEmail.setText(Tools.my.mail);
            if (Tools.my.mailEnable) {
                verifiedTip.setVisibility(View.VISIBLE);
                unverifiedTip.setVisibility(View.GONE);
            } else if (!"".equals(Tools.my.mail)) {
                verifiedTip.setVisibility(View.GONE);
                unverifiedTip.setVisibility(View.VISIBLE);
            }
        }
    }

    private void myListener() {
        returnArrow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        inSex.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.sexMale) {
                    sex = "男";
                } else if (checkedId == R.id.sexFemale) {
                    sex = "女";
                }
            }
        });

        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String subject = String.valueOf(inSubject.getText());
                if (sexMale.isChecked()) {
                    sex = "男";
                } else if (sexFemale.isChecked()) {
                    sex = "女";
                }
                String phone = String.valueOf(inPhone.getText());
                String qq = String.valueOf(inQQ.getText());
                String teacher = String.valueOf(inTeacher.getText());
                String email = String.valueOf(inEmail.getText());
                if (TextUtils.isEmpty(subject) || "".equals(sex) || TextUtils.isEmpty(phone)
                        || TextUtils.isEmpty(qq) || TextUtils.isEmpty(teacher)) {
                    Toast.makeText(activity, "输入不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                final AlertDialog waitDialog = Tools.showPleaseWait(activity);
                Server.uploadPersonalInfo(subject, sex, phone, qq, teacher, email, activity, new Server.EndOfRequest() {
                    @Override
                    public void onSuccess(String result) {
                        switch (result) {
                            case "OK":
                                Server.refreshMembers(activity, new Server.EndOfRequest() {
                                    @Override
                                    public void onSuccess(String result) {
                                        switch (result) {
                                            case "AES_KEY_ERROR":
                                            case "ERROR":
                                                onFail();
                                                break;
                                            default:
                                                if (Tools.refreshMemberList(activity, Server.aesDecryptData(result))) {
                                                    waitDialog.dismiss();
                                                    Toast.makeText(activity, "已更新", Toast.LENGTH_SHORT).show();
                                                    activity.finish();
                                                } else {
                                                    onFail();
                                                }
                                        }
                                    }

                                    @Override
                                    public void onException() {
                                        onFail();
                                    }

                                    @Override
                                    public void onTimeout() {
                                        onFail();
                                    }

                                    @Override
                                    public void noNet() {
                                        onFail();
                                    }

                                    private void onFail() {
                                        waitDialog.dismiss();
                                        Toast.makeText(activity, "刷新失败", Toast.LENGTH_SHORT).show();
                                    }
                                });
                                break;
                            case "AES_KEY_ERROR":
                                Server.startInitConnectionThread();
                            case "ERROR":
                                onException();
                                break;
                        }
                    }

                    @Override
                    public void onException() {
                        onFail("更新个人信息失败");
                    }

                    @Override
                    public void onTimeout() {
                        onFail("连接超时");
                    }

                    @Override
                    public void noNet() {
                        onFail("网络连接不畅");
                    }

                    private void onFail(String message) {
                        waitDialog.dismiss();
                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        unverifiedTip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AlertDialog waitDialog = Tools.showPleaseWait(activity);
                Server.sendVerifyEmailMail(activity, new Server.EndOfRequest() {
                    @Override
                    public void onSuccess(String result) {
                        switch (result) {
                            case "OK":
                                waitDialog.dismiss();
                                Toast.makeText(activity, "验证邮件已发送，请查收", Toast.LENGTH_SHORT).show();
                                activity.finish();
                                break;
                            case "AES_KEY_ERROR":
                            case "ERROR":
                                onException();
                                break;
                        }
                    }

                    @Override
                    public void onException() {
                        onFail("请求失败，请重试");
                    }

                    @Override
                    public void onTimeout() {
                        onFail("连接超时");
                    }

                    @Override
                    public void noNet() {
                        onFail("请检查网络连接");
                    }

                    private void onFail(String message) {
                        waitDialog.dismiss();
                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}