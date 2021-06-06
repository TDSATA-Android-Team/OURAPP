package com.tdsata.ourapp.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.Toolbar;

import com.tdsata.ourapp.R;
import com.tdsata.ourapp.entity.Member;
import com.tdsata.ourapp.util.FixedValue;
import com.tdsata.ourapp.util.Server;
import com.tdsata.ourapp.util.MyLog;
import com.tdsata.ourapp.util.Tools;
import com.tdsata.ourapp.view.HeadPortraitView;

import java.io.File;

/**
 * 登录页面.
 */
public class ActivityLogin extends AppCompatActivity {
    private final MyLog myLog = new MyLog("LoginTAG");
    private final AppCompatActivity activity = this;
    private Tools.DepartmentEnum department;
    private String account;
    private String password;

    private Toolbar toolbar;
    private HeadPortraitView headPhoto;
    private ImageView departmentIcon;
    private EditText inputAccount;
    private EditText inputPassword;
    private AppCompatCheckBox autoLogin;
    private TextView forgetPassword;
    private Button login;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initView();
        department = (Tools.DepartmentEnum) getIntent().getSerializableExtra(FixedValue.myDepartment);
        if (department == null) {
            myLog.d("myDepartment为空");
            department = Tools.DepartmentEnum.SOFTWARE;
        }
        departmentIcon.setImageResource(department.getIconId());
        account = getIntent().getStringExtra(FixedValue.myAccount);
        if (account != null) {
            inputAccount.setText(account);
        }
        toolbar.setTitle(department.getSimpleName());
        myListener();
        final String photoName = getSharedPreferences(FixedValue.LoginCfg, MODE_PRIVATE).getString(FixedValue.headPhoto, null);
        if (photoName != null) {
            Tools.threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    File photo = Tools.generateFileAtCache(activity, FixedValue.photoDirectory, photoName, true);
                    final Bitmap pic = BitmapFactory.decodeFile(photo.getAbsolutePath());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (pic != null) {
                                headPhoto.setImageBitmap(pic);
                            }
                        }
                    });
                }
            });
        }
    }

    private void initView() {
        toolbar = findViewById(R.id.toolbar);
        headPhoto = findViewById(R.id.headPhoto);
        departmentIcon = findViewById(R.id.departmentIcon);
        inputAccount = findViewById(R.id.inputAccount);
        inputPassword = findViewById(R.id.inputPassword);
        autoLogin = findViewById(R.id.autoLogin);
        forgetPassword = findViewById(R.id.forgetPassword);
        login = findViewById(R.id.login);
    }

    private void myListener() {
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        login.setOnClickListener(new View.OnClickListener() {
            private AlertDialog waitDialog;

            @Override
            public void onClick(View v) {
                account = String.valueOf(inputAccount.getText());
                password = String.valueOf(inputPassword.getText());
                if (TextUtils.isEmpty(account) || TextUtils.isEmpty(password)) {
                    Toast.makeText(activity, "账号或密码不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                final boolean defaultPassword = password.equals("TD_SATA") || password.equals("TD-SATA");
                waitDialog = Tools.showPleaseWait(activity);
                password = Tools.getKeccak512Encrypt(password);
                Server.login(department, account, password, activity, new Server.EndOfRequest() {
                    @Override
                    public void onSuccess(String result) {
                        waitDialog.cancel();
                        switch (result) {
                            case "ACCOUNT_NO_EXIST":
                                Tools.showDialogOnlyOkButton(activity, Tools.TYPE_TIP, "账号不存在", null);
                                break;
                            case "PASSWORD_ERROR":
                                Tools.showDialogOnlyOkButton(activity, Tools.TYPE_TIP, "账号或密码错误", null);
                                break;
                            case "AES_KEY_ERROR":
                                Tools.showDialogOnlyOkButton(activity, Tools.TYPE_ERROR, "登录失败", null);
                                Server.startInitConnectionThread();
                                break;
                            case "ERROR":
                                onException();
                                break;
                            default:
                                Tools.memberList = Tools.getListFromJson(Server.aesDecryptData(result), Member.class);
                                if (Tools.memberList != null) {
                                    Tools.sortMemberList();
                                    if (Tools.setMy(activity, department, account)) {
                                        Tools.setAdministrators();
                                        Tools.saveSharedPreferences(activity, FixedValue.LoginCfg,
                                                FixedValue.myDepartment, department.name(),
                                                FixedValue.myAccount, account,
                                                FixedValue.myPassword, password,
                                                FixedValue.autoLogin, String.valueOf(autoLogin.isChecked())
                                        );
                                        Toast.makeText(activity, "登录成功", Toast.LENGTH_SHORT).show();
                                        Intent skipHome = new Intent(activity, ActivityHome.class);
                                        skipHome.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        skipHome.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        skipHome.putExtra("defaultPassword", defaultPassword);
                                        startActivity(skipHome);
                                    } else {
                                        Toast.makeText(activity, "用户身份异常", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    Tools.showDialogOnlyOkButton(activity, Tools.TYPE_ERROR, "登录异常", null);
                                }
                                break;
                        }
                    }

                    @Override
                    public void onException() {
                        if (waitDialog.isShowing()) {
                            waitDialog.cancel();
                        }
                        Tools.showDialogOnlyOkButton(activity, Tools.TYPE_ERROR, "登录异常", null);
                    }

                    @Override
                    public void onTimeout() {
                        waitDialog.cancel();
                        Tools.showDialogOnlyOkButton(activity, Tools.TYPE_TIP, "登录超时", null);
                    }

                    @Override
                    public void noNet() {
                        waitDialog.cancel();
                        Tools.showDialogOnlyOkButton(activity, Tools.TYPE_TIP, "网络连接不可用", null);
                    }
                });
            }
        });

        forgetPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(activity, ActivityForgetPassword.class);
                intent.putExtra(FixedValue.myDepartment, department);
                intent.putExtra(FixedValue.myAccount, String.valueOf(inputAccount.getText()));
                startActivity(intent);
            }
        });
    }
}