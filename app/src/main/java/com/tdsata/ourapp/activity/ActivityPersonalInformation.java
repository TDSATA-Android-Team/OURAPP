package com.tdsata.ourapp.activity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.tdsata.ourapp.R;
import com.tdsata.ourapp.entity.Member;
import com.tdsata.ourapp.util.FixedValue;
import com.tdsata.ourapp.util.Server;
import com.tdsata.ourapp.util.Tools;
import com.tdsata.ourapp.view.HeadPortraitView;

public class ActivityPersonalInformation extends AppCompatActivity {
    private final AppCompatActivity activity = this;
    private Member currentMember;

    private Toolbar toolbar;
    private MenuItem alterCount;
    private HeadPortraitView headPhoto;
    private View toSettingInfo;
    private ImageView arrow;
    private TextView name;
    private TextView number;
    private TextView count;
    private TextView department;
    private TextView flag;
    private TextView subject;
    private TextView phone;
    private TextView qq;
    private TextView teacher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_information);

        currentMember = (Member) getIntent().getSerializableExtra(FixedValue.currentMember);
        if (currentMember == null) {
            Toast.makeText(activity, "未知部员信息", Toast.LENGTH_SHORT).show();
            finish();
        }
        initView();
        myListener();
        if (Tools.my.hasPermission()) {
            alterCount.setVisible(true);
        }
        if (currentMember.getNumber().equals(Tools.my.getAccount())) {
            arrow.setVisibility(View.VISIBLE);
        }
        currentMember.settingHeadPhoto(activity, headPhoto);
        name.setText(currentMember.getName());
        number.setText(currentMember.getNumber());
        count.setText(String.valueOf(currentMember.getCount()));
        department.setText(Tools.my.getDepartment().getWholeName());
        flag.setText(currentMember.getIdentity());
        subject.setText(currentMember.getSubject());
        phone.setText(currentMember.getPhone());
        qq.setText(currentMember.getQQ());
        teacher.setText(currentMember.getTeacher());
    }

    private void initView() {
        toolbar = findViewById(R.id.toolbar);
        alterCount = toolbar.getMenu().findItem(R.id.alterCount);
        headPhoto = findViewById(R.id.headPhoto);
        toSettingInfo = findViewById(R.id.toSettingInfo);
        arrow = findViewById(R.id.arrow);
        name = findViewById(R.id.name);
        number = findViewById(R.id.number);
        count = findViewById(R.id.count);
        department = findViewById(R.id.department);
        flag = findViewById(R.id.flag);
        subject = findViewById(R.id.subject);
        phone = findViewById(R.id.phone);
        qq = findViewById(R.id.qq);
        teacher = findViewById(R.id.teacher);
    }

    private void myListener() {
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            private int changeValue = 0;

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.alterCount) {
                    changeValue = 0;
                    View changeView = View.inflate(activity, R.layout.dialog_change_integral, null);
                    final AlertDialog changeDialog = new AlertDialog.Builder(activity, R.style.AlertDialogCornerRadius)
                            .setView(changeView)
                            .create();
                    changeDialog.show();
                    changeDialog.setCanceledOnTouchOutside(false);
                    final View addCount = changeView.findViewById(R.id.addCount);
                    final View minusCount = changeView.findViewById(R.id.minusCount);
                    final TextView changeValueText = changeView.findViewById(R.id.changeValueText);
                    final EditText inputReason = changeView.findViewById(R.id.inputReason);
                    final View ok = changeView.findViewById(R.id.ok);
                    addCount.setOnClickListener(new View.OnClickListener() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void onClick(View v) {
                            changeValue += 1;
                            if (changeValue > 0) {
                                changeValueText.setText("+" + changeValue);
                            } else {
                                changeValueText.setText(String.valueOf(changeValue));
                            }
                        }
                    });
                    minusCount.setOnClickListener(new View.OnClickListener() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void onClick(View v) {
                            changeValue -= 1;
                            if (changeValue > 0) {
                                changeValueText.setText("+" + changeValue);
                            } else {
                                changeValueText.setText(String.valueOf(changeValue));
                            }
                        }
                    });
                    ok.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String description = String.valueOf(inputReason.getText());
                            changeDialog.setCancelable(false);
                            ok.setEnabled(false);
                            Server.changeCount(currentMember.getNumber(), changeValue, description, activity, new Server.EndOfRequest() {
                                @Override
                                public void onSuccess(String result) {
                                    switch (result) {
                                        case "OK":
                                            changeDialog.dismiss();
                                            Toast.makeText(activity, "已修改", Toast.LENGTH_SHORT).show();
                                            count.setText(String.valueOf(currentMember.getCount() + changeValue));
                                            Server.refreshMembers(activity, new Server.EndOfRequest() {
                                                @Override
                                                public void onSuccess(String result) {
                                                    if (Tools.refreshMemberList(activity, Server.aesDecryptData(result))) {
                                                        new Server.RefreshLocalPhotoThread(activity, null).start();
                                                    }
                                                }

                                                @Override
                                                public void onException() {}

                                                @Override
                                                public void onTimeout() {}

                                                @Override
                                                public void noNet() {}
                                            });
                                            break;
                                        case "AES_KEY_ERROR":
                                            Server.startInitConnectionThread();
                                        case "ERROR":
                                            onFail("修改失败，请重试");
                                            break;
                                    }
                                }

                                @Override
                                public void onException() {
                                    onFail("修改失败");
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
                                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                                    changeDialog.setCancelable(true);
                                    ok.setEnabled(true);
                                }
                            });
                        }
                    });
                    return true;
                }
                return false;
            }
        });

        toSettingInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentMember.getNumber().equals(Tools.my.getAccount())) {
                    startActivity(new Intent(activity, ActivityUploadPersonalInfo.class));
                }
            }
        });
    }
}