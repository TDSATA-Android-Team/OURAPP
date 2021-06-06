package com.tdsata.ourapp.activity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.tdsata.ourapp.R;
import com.tdsata.ourapp.util.FixedValue;
import com.tdsata.ourapp.util.Server;
import com.tdsata.ourapp.util.Tools;

public class ActivityAlterPassword extends AppCompatActivity {
    private final AppCompatActivity activity = this;

    private Toolbar toolbar;
    private EditText inOldPassword;
    private EditText inNewPassword;
    private EditText inConfirmPassword;
    private Button ok;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alter_password);

        initView();
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String oldPassword = String.valueOf(inOldPassword.getText());
                String newPassword = String.valueOf(inNewPassword.getText());
                String confirmPassword = String.valueOf(inConfirmPassword.getText());
                if (TextUtils.isEmpty(oldPassword) || TextUtils.isEmpty(newPassword) || TextUtils.isEmpty(confirmPassword)) {
                    Toast.makeText(activity, "输入不能为空", Toast.LENGTH_SHORT).show();
                } else if (!newPassword.equals(confirmPassword)) {
                    Toast.makeText(activity, "您的输入不一致", Toast.LENGTH_SHORT).show();
                } else if (newPassword.equals(oldPassword)) {
                    Toast.makeText(activity, "新密码不能与旧密码相同", Toast.LENGTH_SHORT).show();
                } else if (Tools.availablePassword(newPassword, activity)) {
                    oldPassword = Tools.getKeccak512Encrypt(oldPassword);
                    newPassword = Tools.getKeccak512Encrypt(newPassword);
                    final AlertDialog waitDialog = Tools.showPleaseWait(activity);
                    Server.alterPassword(oldPassword, newPassword, activity, new Server.EndOfRequest() {
                        @Override
                        public void onSuccess(String result) {
                            waitDialog.dismiss();
                            switch (result) {
                                case "PASSWORD_ERROR":
                                    Tools.showDialogOnlyOkButton(activity, Tools.TYPE_TIP, "密码错误", null);
                                    break;
                                case "OK":
                                    Tools.saveSharedPreferences(activity, FixedValue.LoginCfg, FixedValue.autoLogin, "false");
                                    Tools.showDialogOnlyOkButton(activity, Tools.TYPE_TIP, "修改成功", new Tools.ControlDialog() {
                                        @Override
                                        public void onDialog(AlertDialog dialog) {
                                            dialog.setCancelable(false);
                                            dialog.setCanceledOnTouchOutside(false);
                                        }

                                        @Override
                                        public void onOkButton(AlertDialog dialog, View dialogView) {
                                            dialog.dismiss();
                                            Tools.showDialogOnlyOkButton(activity, Tools.TYPE_TIP, "密码已修改，请重新登录", new Tools.ControlDialog() {
                                                @Override
                                                public void onDialog(AlertDialog dialog) {
                                                    dialog.setCancelable(false);
                                                    dialog.setCanceledOnTouchOutside(false);
                                                }

                                                @Override
                                                public void onOkButton(AlertDialog dialog, View dialogView) {
                                                    dialog.dismiss();
                                                    Intent intent = new Intent(activity, ActivityStartPicture.class);
                                                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                    startActivity(intent);
                                                }

                                                @Override
                                                public void onCancelButton(AlertDialog dialog, View dialogView) {}
                                            });
                                        }

                                        @Override
                                        public void onCancelButton(AlertDialog dialog, View dialogView) {}
                                    });
                                    break;
                                case "AES_KEY_ERROR":
                                    Server.startInitConnectionThread();
                                    Toast.makeText(activity, "请求失败，请重试", Toast.LENGTH_SHORT).show();
                                    break;
                                case "ERROR":
                                default:
                                    onException();
                                    break;
                            }
                        }

                        @Override
                        public void onException() {
                            if (waitDialog.isShowing()) {
                                waitDialog.dismiss();
                            }
                            Toast.makeText(activity, "未知错误，修改失败", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onTimeout() {
                            waitDialog.dismiss();
                            Toast.makeText(activity, "连接超时", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void noNet() {
                            waitDialog.dismiss();
                            Toast.makeText(activity, "请检查网络连接", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void initView() {
        toolbar = findViewById(R.id.toolbar);
        inOldPassword = findViewById(R.id.inOldPassword);
        inNewPassword = findViewById(R.id.inNewPassword);
        inConfirmPassword = findViewById(R.id.inConfirmPassword);
        ok = findViewById(R.id.ok);
    }
}