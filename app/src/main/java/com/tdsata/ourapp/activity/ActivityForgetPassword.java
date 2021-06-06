package com.tdsata.ourapp.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.tdsata.ourapp.R;
import com.tdsata.ourapp.util.FixedValue;
import com.tdsata.ourapp.util.Server;
import com.tdsata.ourapp.util.MyLog;
import com.tdsata.ourapp.util.Tools;
import com.tdsata.ourapp.view.ImageCaptcha;
import com.tdsata.ourapp.view.PageProgressBar;

public class ActivityForgetPassword extends AppCompatActivity {
    private static final MyLog myLog = new MyLog("ForgetPasswordTAG");
    private final AppCompatActivity activity = this;
    private static int lastSerialNumber = 0;
    private static Tools.DepartmentEnum department;
    private static String account;
    private static boolean successAlter = false;

    private Toolbar toolbar;
    private static PageProgressBar pageProgressBar;
    private static TextView[] texts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forget_password);

        initView();
        department = (Tools.DepartmentEnum) getIntent().getSerializableExtra(FixedValue.myDepartment);
        account = getIntent().getStringExtra(FixedValue.myAccount);
        if (department == null) {
            myLog.d("myDepartment为空");
            department = Tools.DepartmentEnum.SOFTWARE;
        }
        setSerialNumber(1, activity);
        myListener();
    }

    private static void resetStatic() {
        lastSerialNumber = 0;
        department = null;
        account = null;
        successAlter = false;
    }

    @Override
    public void onBackPressed() {
        if (successAlter) {
            super.onBackPressed();
        } else {
            Tools.showDialogWithTwoButtons(activity, "确定要返回吗，当前数据将不被保存?", Tools.DEFAULT_IS_CANCEL, new Tools.ControlDialog() {
                @Override
                public void onDialog(AlertDialog dialog) {
                    dialog.setCanceledOnTouchOutside(false);
                }

                @Override
                public void onOkButton(AlertDialog dialog, View dialogView) {
                    dialog.cancel();
                    resetStatic();
                    activity.finish();
                }

                @Override
                public void onCancelButton(AlertDialog dialog, View dialogView) {
                    dialog.cancel();
                }
            });
        }
    }

    private void initView() {
        toolbar = findViewById(R.id.toolbar);
        pageProgressBar = findViewById(R.id.pagerProgressBar);
        texts = new TextView[] {
                findViewById(R.id.textVerify), findViewById(R.id.textSetting), findViewById(R.id.textSuccess)
        };
    }

    private void myListener() {
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
    }

    /**
     * 设置当前页面所在进度的序号.
     *
     * @param serialNumber 当前页面所在进度的序号，从1开始
     */
    private static void setSerialNumber(int serialNumber, AppCompatActivity activity) {
        if (serialNumber == lastSerialNumber) {
            return;
        }
        pageProgressBar.setNowSerialNumber(serialNumber);
        if (lastSerialNumber > 0) {
            texts[lastSerialNumber - 1].setEnabled(true);
        } else {
            for (TextView text : texts) {
                text.setEnabled(true);
            }
        }
        texts[serialNumber - 1].setEnabled(false);
        lastSerialNumber = serialNumber;
        FragmentTransaction fragmentTransaction = activity.getSupportFragmentManager().beginTransaction();
        switch (serialNumber) {
            case 1:
                fragmentTransaction.add(R.id.fragment, new SafetyVerifyFragment()).commit();
                break;
            case 2:
                fragmentTransaction.replace(R.id.fragment, new SettingPasswordFragment()).commit();
                break;
            case 3:
                successAlter = true;
                fragmentTransaction.replace(R.id.fragment, new AlterSuccessFragment()).commit();
                break;
        }
    }

    public static class SafetyVerifyFragment extends Fragment {
        private Context context;
        private String account;
        private CountDownTimer countDownTimer;

        private EditText inputAccount;
        private EditText inputEmail;
        private EditText inputMailCode;
        private TextView getMailCode;
        private Button ok;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_safety_verify, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            context = getContext();
            if (context == null) {
                throw new NullPointerException("SafetyVerifyFragment获得的context为空");
            }
            initView(view);
            inputMailCode.setEnabled(false);
            ok.setEnabled(false);
            if (!TextUtils.isEmpty(ActivityForgetPassword.account)) {
                this.account = ActivityForgetPassword.account;
                inputAccount.setText(account);
            }
            myListener();
        }

        private void initView(View view) {
            inputAccount = view.findViewById(R.id.inputAccount);
            inputEmail = view.findViewById(R.id.inputEmail);
            inputMailCode = view.findViewById(R.id.inputMailCode);
            getMailCode = view.findViewById(R.id.getMailCode);
            ok = view.findViewById(R.id.ok);
        }

        private void myListener() {
            getMailCode.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    account = String.valueOf(inputAccount.getText());
                    ActivityForgetPassword.account = account;
                    final String email = String.valueOf(inputEmail.getText());
                    if (TextUtils.isEmpty(account) || TextUtils.isEmpty(email)) {
                        Toast.makeText(context, "账号或邮箱不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    View dialogView = View.inflate(context, R.layout.dialog_input_captcha, null);
                    final AlertDialog dialog = new AlertDialog.Builder(context, R.style.AlertDialogCornerRadius)
                            .setView(dialogView)
                            .create();
                    dialog.show();
                    dialog.setCancelable(false);
                    dialog.setCanceledOnTouchOutside(false);
                    final ImageCaptcha imageCaptcha = dialogView.findViewById(R.id.imageCaptcha);
                    final EditText inputCaptcha = dialogView.findViewById(R.id.inputCaptcha);
                    dialogView.findViewById(R.id.refreshCaptcha).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            imageCaptcha.refresh();
                        }
                    });
                    dialogView.findViewById(R.id.dialogCancel).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.cancel();
                        }
                    });
                    final Button dialogOk = dialogView.findViewById(R.id.dialogOk);
                    dialogOk.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String captcha = String.valueOf(inputCaptcha.getText());
                            if (TextUtils.isEmpty(captcha)) {
                                Toast.makeText(context, "请输入图形验证码", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (imageCaptcha.verificationInput(captcha)) {
                                dialogOk.setEnabled(false);
                                Server.requestMailCode(department, account, email, (AppCompatActivity) context, new Server.EndOfRequest() {
                                    @Override
                                    public void onSuccess(String result) {
                                        switch (result) {
                                            case "OK":
                                                closePopUps("邮件已发送");
                                                inputMailCode.setEnabled(true);
                                                ok.setEnabled(true);
                                                countdown();
                                                break;
                                            case "ACCOUNT_NO_EXIST":
                                                closePopUps("账号不存在");
                                                break;
                                            case "MAIL_ENABLE_FALSE":
                                                closePopUps("您的邮箱地址输入有误或未绑定该邮箱");
                                                break;
                                            case "MAIL_NO_VERIFY":
                                                closePopUps("您的邮箱未验证，请联系部门部长");
                                                break;
                                            case "AES_KEY_ERROR":
                                                Server.startInitConnectionThread();
                                            case "ERROR":
                                            default:
                                                onException();
                                                break;
                                        }
                                    }

                                    private void closePopUps(String message) {
                                        dialog.cancel();
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onException() {
                                        resetCaptcha("网络请求异常");
                                    }

                                    @Override
                                    public void onTimeout() {
                                        dialogOk.setEnabled(true);
                                        Toast.makeText(context, "连接超时", Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void noNet() {
                                        dialogOk.setEnabled(true);
                                        Toast.makeText(context, "网络连接不可用", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            } else {
                                resetCaptcha("验证码错误");
                            }
                        }

                        private void resetCaptcha(String message) {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                            inputCaptcha.setText("");
                            imageCaptcha.refresh();
                            dialogOk.setEnabled(true);
                        }
                    });
                }
            });

            ok.setOnClickListener(new View.OnClickListener() {
                private AlertDialog waitDialog;

                @Override
                public void onClick(View v) {
                    final String inputCode = String.valueOf(inputMailCode.getText());
                    if (TextUtils.isEmpty(inputCode)) {
                        Toast.makeText(context, "请输入接收到的验证码", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    waitDialog = Tools.showPleaseWait(context);
                    Server.verifyMailCode(department, account, inputCode, (AppCompatActivity) context, new Server.EndOfRequest() {
                        @Override
                        public void onSuccess(String result) {
                            waitDialog.cancel();
                            switch (result) {
                                case "true":
                                    if (countDownTimer != null) {
                                        countDownTimer.cancel();
                                    }
                                    getMailCode.setText("获取");
                                    getMailCode.setEnabled(true);
                                    setSerialNumber(2, (AppCompatActivity) context);
                                    break;
                                case "false":
                                    Toast.makeText(context, "验证码错误", Toast.LENGTH_SHORT).show();
                                    inputMailCode.setText("");
                                    break;
                                case "AES_KEY_ERROR":
                                    Server.startInitConnectionThread();
                                default:
                                    Toast.makeText(context, "请求失败", Toast.LENGTH_SHORT).show();
                                    break;
                            }
                        }

                        @Override
                        public void onException() {
                            waitDialog.cancel();
                            Toast.makeText(context, "网络请求异常", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onTimeout() {
                            waitDialog.cancel();
                            Toast.makeText(context, "连接超时", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void noNet() {
                            waitDialog.cancel();
                            Toast.makeText(context, "网络连接不可用", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }

        private void countdown() {
            getMailCode.setEnabled(false);
            inputAccount.setEnabled(false);
            inputEmail.setEnabled(false);
            countDownTimer = new CountDownTimer(60200/*1分钟*/, 1000) {

                @SuppressLint("SetTextI18n")
                @Override
                public void onTick(long millisUntilFinished) {
                    if (getMailCode == null) {
                        this.cancel();
                    } else {
                        getMailCode.setText("重新获取(" + millisUntilFinished / 1000 + "s)");
                    }
                }

                @Override
                public void onFinish() {
                    getMailCode.setText("获取");
                    getMailCode.setEnabled(true);
                }
            };
            countDownTimer.start();
        }
    }

    public static class SettingPasswordFragment extends Fragment {
        private Context context;

        private EditText inputNewPassword;
        private EditText confirmNewPassword;
        private Button ok;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_setting_password, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            context = getContext();
            if (context == null) {
                throw new NullPointerException("SettingPasswordFragment获得的context为空");
            }
            initView(view);
            ok.setOnClickListener(new View.OnClickListener() {
                private AlertDialog waitDialog;

                @Override
                public void onClick(View v) {
                    String newPassword = String.valueOf(inputNewPassword.getText());
                    String confirmPassword = String.valueOf(confirmNewPassword.getText());
                    if (TextUtils.isEmpty(newPassword) || TextUtils.isEmpty(confirmPassword)) {
                        Toast.makeText(context, "输入不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newPassword.equals(confirmPassword)) {
                        if (Tools.availablePassword(newPassword, context)) {
                            waitDialog = Tools.showPleaseWait(context);
                            newPassword = Tools.getKeccak512Encrypt(newPassword);
                            Server.alterPasswordUseMail(department, account, newPassword, (AppCompatActivity) context, new Server.EndOfRequest() {
                                @Override
                                public void onSuccess(String result) {
                                    waitDialog.cancel();
                                    switch (result) {
                                        case "OK":
                                            setSerialNumber(3, (AppCompatActivity) context);
                                            break;
                                        case "ILLEGAL":
                                            Tools.showDialogOnlyOkButton(context, Tools.TYPE_WARNING, "非法的改密行为", null);
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
                                    if (waitDialog.isShowing()) {
                                        waitDialog.cancel();
                                    }
                                    Tools.showDialogOnlyOkButton(context, Tools.TYPE_ERROR, "网络请求异常", null);
                                }

                                @Override
                                public void onTimeout() {
                                    waitDialog.cancel();
                                    Tools.showDialogOnlyOkButton(context, Tools.TYPE_TIP, "连接超时", null);
                                }

                                @Override
                                public void noNet() {
                                    waitDialog.cancel();
                                    Tools.showDialogOnlyOkButton(context, Tools.TYPE_TIP, "网络连接不可用", null);
                                }
                            });
                        }
                    } else {
                        Toast.makeText(context, "您的输入不一致", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        private void initView(View view) {
            inputNewPassword = view.findViewById(R.id.inputNewPassword);
            confirmNewPassword = view.findViewById(R.id.confirmNewPassword);
            ok = view.findViewById(R.id.ok);
        }
    }

    public static class AlterSuccessFragment extends Fragment {
        private Button toLogin;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_alter_success, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            initView(view);
            toLogin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requireActivity().finish();
                }
            });
        }

        private void initView(View view) {
            toLogin = view.findViewById(R.id.toLogin);
        }
    }
}