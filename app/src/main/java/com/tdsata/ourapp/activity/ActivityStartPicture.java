package com.tdsata.ourapp.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.tdsata.ourapp.R;
import com.tdsata.ourapp.entity.Member;
import com.tdsata.ourapp.util.FixedValue;
import com.tdsata.ourapp.util.Server;
import com.tdsata.ourapp.util.MyLog;
import com.tdsata.ourapp.util.Tools;

import java.util.Timer;
import java.util.TimerTask;

/**
 * 应用进入后的启动页.
 */
public class ActivityStartPicture extends AppCompatActivity {
    private final MyLog myLog = new MyLog("StartPictureTAG");
    private final AppCompatActivity activity = this;
    private Tools.DepartmentEnum department;
    private String account;
    private String password;
    private boolean autoLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_picture);

        Tools.init();// 初始化
        final boolean hasAllCfg = readCfg();
        Server.initConnection(activity, new Server.InitConnectionInterface() {
            @Override
            public void onSuccess() {
                autoLogin();
            }

            @Override
            public void onFail() {
                showPopups("请检查网络连接");
            }

            @Override
            public void onTimeout() {
                showPopups("网络连接超时");
            }

            @Override
            public void onException() {
                showPopups("连接异常，请稍后重试");
            }

            private void showPopups(final String tipMessage) {
                Server.setNullInterfaceOnInitConnection();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Tools.showDialogWithTwoButtons(activity, tipMessage, Tools.DEFAULT_IS_OK, new Tools.ControlDialog() {
                            @Override
                            public void onDialog(AlertDialog dialog) {
                                Button ok = dialog.findViewById(R.id.dialogOk);
                                Button cancel = dialog.findViewById(R.id.dialogCancel);
                                if (ok != null) {
                                    ok.setText("重试");
                                }
                                if (cancel != null) {
                                    cancel.setText("继续");
                                }
                                dialog.setCancelable(false);
                                dialog.setCanceledOnTouchOutside(false);
                            }

                            @Override
                            public void onOkButton(AlertDialog dialog, View dialogView) {
                                if (Server.initConnectionSuccess()) {
                                    dialog.cancel();
                                    autoLogin();
                                } else {
                                    Toast.makeText(activity, "网络连接不畅", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onCancelButton(AlertDialog dialog, View dialogView) {
                                dialog.cancel();
                                manualLogin(false);
                            }
                        });
                    }
                });
            }
        });
        if (!hasAllCfg) {
            firstLogin();
        } else if (!autoLogin) {
            manualLogin(true);
        } else if ("".equals(password)) {
            manualLogin(true);
        }
    }

    private boolean readCfg() {
        SharedPreferences read = activity.getSharedPreferences(FixedValue.LoginCfg, MODE_PRIVATE);
        String myDepartment = read.getString(FixedValue.myDepartment, null);
        account = read.getString(FixedValue.myAccount, null);
        password = read.getString(FixedValue.myPassword, null);
        if (myDepartment == null || account == null || password == null) {
            return false;
        }
        try {
            department = Tools.DepartmentEnum.valueOf(read.getString(FixedValue.myDepartment, null));
            autoLogin = Boolean.parseBoolean(read.getString(FixedValue.autoLogin, "true"));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 首次登录.
     * 直接跳转到部门选择界面.
     */
    private void firstLogin() {
        Server.setNullInterfaceOnInitConnection();
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                startActivity(new Intent(activity, ActivityDepartmentSelect.class));
                activity.finish();
            }
        }, 500);
    }

    /**
     * 手动登录.
     * 清除存储的密码(置"")，保留部门和账号；
     * 先跳转到部门选择界面，再跳转到登录界面.
     *
     * @param useDelay 是否使登录的核心过程延时执行(延时500ms).
     *                 true表示肯定
     */
    private void manualLogin(boolean useDelay) {
        Server.setNullInterfaceOnInitConnection();
        // 清楚密码项配置
        Tools.saveSharedPreferences(activity, FixedValue.LoginCfg, FixedValue.myPassword, "");
        if (useDelay) {
            // 避免启动页还未加载出来就被finish
            // 造成视觉上的短暂黑屏
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    manualLoginCore();
                }
            }, 500);
        } else {
            manualLoginCore();
        }
    }

    /**
     * 手动登录的核心过程.
     */
    private void manualLoginCore() {
        // 确保从登录页可返回到部门选择页
        Intent[] intents = new Intent[] {
                new Intent(activity, ActivityDepartmentSelect.class),
                new Intent(activity, ActivityLogin.class)
        };
        intents[1].putExtra(FixedValue.myDepartment, department);
        intents[1].putExtra(FixedValue.myAccount, account);
        startActivities(intents);
        activity.finish();
    }

    /**
     * 自动登录.
     * 使用存储的部门、账号、密码自动进行登录；
     * 登录成功则直接跳转到首页.
     */
    private void autoLogin() {
        Server.login(department, account, password, activity, new Server.EndOfRequest() {
            @Override
            public void onSuccess(String result) {
                switch (result) {
                    case "ACCOUNT_NO_EXIST":
                        loginFail("账号不存在");
                        break;
                    case "PASSWORD_ERROR":
                        loginFail("账号或密码错误");
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
                                Server.setNullInterfaceOnInitConnection();
                                Intent toHome = new Intent(activity, ActivityHome.class);
                                if (password.equals(Tools.getKeccak512Encrypt("TD_SATA")) || password.equals(Tools.getKeccak512Encrypt("TD-SATA"))) {
                                    toHome.putExtra("defaultPassword", true);
                                }
                                startActivity(toHome);
                                Toast.makeText(activity, "登录成功", Toast.LENGTH_SHORT).show();
                                activity.finish();
                            } else {
                                loginFail("用户身份异常");
                            }
                        } else {
                            loginFail("登录异常");
                        }
                        break;
                }
            }

            @Override
            public void onException() {
                loginFail("登录异常");
            }

            @Override
            public void onTimeout() {
                Tools.showDialogWithTwoButtons(activity, "登录超时", Tools.DEFAULT_IS_OK, new Tools.ControlDialog() {
                    @Override
                    public void onDialog(AlertDialog dialog) {
                        Button ok = dialog.findViewById(R.id.dialogOk);
                        Button cancel = dialog.findViewById(R.id.dialogCancel);
                        if (ok != null) {
                            ok.setText("重试");
                        }
                        if (cancel != null) {
                            cancel.setText("继续");
                        }
                        dialog.setCancelable(false);
                        dialog.setCanceledOnTouchOutside(false);
                    }

                    @Override
                    public void onOkButton(AlertDialog dialog, View dialogView) {
                        dialog.cancel();
                        new Thread() {
                            @Override
                            public void run() {
                                autoLogin();
                            }
                        }.start();
                    }

                    @Override
                    public void onCancelButton(AlertDialog dialog, View dialogView) {
                        dialog.cancel();
                        manualLogin(false);
                    }
                });
            }

            @Override
            public void noNet() {
                loginFail("网络连接不畅");
            }

            private void loginFail(String message) {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                manualLogin(false);
            }
        });
    }
}