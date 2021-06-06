package com.tdsata.ourapp.activity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Base64;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.tdsata.ourapp.R;
import com.tdsata.ourapp.entity.SignInActivity;
import com.tdsata.ourapp.entity.SignInInfo;
import com.tdsata.ourapp.util.FixedValue;
import com.tdsata.ourapp.util.MyLog;
import com.tdsata.ourapp.util.Server;
import com.tdsata.ourapp.util.Tools;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * 查看签到活动及增添签到活动的页面.
 */
public class ActivitySignIn extends AppCompatActivity {
    private final MyLog myLog = new MyLog("SignInTAG");
    private final AppCompatActivity activity = this;
    private List<SignInActivity> signInActivities = null;
    private SignInListAdapter signInListAdapter;
    private boolean delAllMode = true;// 标记删除图标的工作模式
    private Tools.OnTimeChangeObserver timeChangeObserver = null;

    private ViewGroup parent;
    private View tipBlank;
    private Toolbar toolbar;
    private TextView showNowTime;
    private SwipeRefreshLayout refresh;
    private ListView signInList;
    private View addSignIn;
    private MenuItem delSignIn;

    // 添加签到活动的弹窗
    private AlertDialog addSignInDialog;
    private EditText inputSignInTitle;
    private EditText inputContinueTime;
    private DatePicker datePicker;
    private TimePicker timePicker;
    private TextView selectDate;
    private TextView selectTime;
    private final int[] hourMinute = new int[2];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);
        Tools.setBlackWordOnStatus(activity);

        initView();
        initDialog();
        myListener();
        parent.addView(tipBlank);
        if (Tools.my.hasPermission()) {
            addSignIn.setVisibility(View.VISIBLE);
            delSignIn.setVisible(true);
        }
        setShowNowTime();
        requestSignInActivities();
    }

    private void initView() {
        parent = findViewById(R.id.parent);
        tipBlank = View.inflate(activity, R.layout.layout_tip_blank, null);
        tipBlank.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        toolbar = findViewById(R.id.toolbar);
        showNowTime = findViewById(R.id.showNowTime);
        refresh = findViewById(R.id.refresh);
        signInList = findViewById(R.id.signInList);
        addSignIn = findViewById(R.id.addSignIn);
        delSignIn = toolbar.getMenu().findItem(R.id.delSignIn);
    }

    @SuppressLint("SetTextI18n")
    private void initDialog() {
        View addSignInView = View.inflate(activity, R.layout.dialog_add_sign_in, null);
        addSignInDialog = new AlertDialog.Builder(activity, R.style.AlertDialogCornerRadius)
                .setView(addSignInView)
                .create();
        addSignInDialog.setCanceledOnTouchOutside(false);
        inputSignInTitle = addSignInView.findViewById(R.id.inputSignInTitle);
        inputContinueTime = addSignInView.findViewById(R.id.inputContinueTime);
        datePicker = addSignInView.findViewById(R.id.datePicker);
        timePicker = addSignInView.findViewById(R.id.timePicker);
        selectDate = addSignInView.findViewById(R.id.selectDate);
        selectTime = addSignInView.findViewById(R.id.selectTime);
        datePicker.setMinDate(Tools.getNowTime());
        timePicker.setIs24HourView(true);
        // 项目最低版本：API 21
        // DatePicker ==> setOnDateChangedListener监听回调（API 26）
        // TimePicker ==> getXXX()方法（API 23）
        addSignInView.findViewById(R.id.selectDateParent).setOnClickListener(new View.OnClickListener() {
            private boolean hasNotShowTip = true;

            @Override
            public void onClick(View v) {
                datePicker.setVisibility(View.VISIBLE);
                timePicker.setVisibility(View.GONE);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    if (hasNotShowTip) {
                        hasNotShowTip = false;
                        Toast.makeText(activity, "系统版本较低，请点击日期选择结果框以更新选择的日期", Toast.LENGTH_SHORT).show();
                    }
                    selectDate.setText(datePicker.getYear() + "-" + (datePicker.getMonth() + 1) + "-" + datePicker.getDayOfMonth());
                }
            }
        });
        addSignInView.findViewById(R.id.selectTimeParent).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                datePicker.setVisibility(View.GONE);
                timePicker.setVisibility(View.VISIBLE);
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            datePicker.setOnDateChangedListener(new DatePicker.OnDateChangedListener() {
                @Override
                public void onDateChanged(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                    selectDate.setText(year + "-" + (monthOfYear + 1) + "-" + dayOfMonth);
                }
            });
        }
        timePicker.setOnTimeChangedListener(new TimePicker.OnTimeChangedListener() {
            @Override
            public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
                hourMinute[0] = hourOfDay;
                hourMinute[1] = minute;
                if (minute >= 10) {
                    selectTime.setText(hourOfDay + ":" + minute);
                } else {
                    selectTime.setText(hourOfDay + ":0" + minute);
                }
            }
        });
        addSignInView.findViewById(R.id.releaseSignIn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    selectDate.setText(datePicker.getYear() + "-" + (datePicker.getMonth() + 1) + "-" + datePicker.getDayOfMonth());
                }
                String activityTitle = String.valueOf(inputSignInTitle.getText());
                String continueTime = String.valueOf(inputContinueTime.getText());
                if (TextUtils.isEmpty(activityTitle) || TextUtils.isEmpty(continueTime)) {
                    Toast.makeText(activity, "输入不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (activityTitle.length() > 30) {
                    Toast.makeText(activity, "签到活动标题字数不能超过30个字", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (activityTitle.contains("$")) {
                    Toast.makeText(activity, "请勿使用特殊符号“$”", Toast.LENGTH_SHORT).show();
                }
                if (Integer.parseInt(continueTime) <= 0) {
                    Toast.makeText(activity, "签到有效时限至少为1分钟", Toast.LENGTH_SHORT).show();
                    return;
                }
                // 获取选择的日期和时间
                int month = datePicker.getMonth() + 1;
                int day = datePicker.getDayOfMonth();
                // yyyy-MM-dd HH:mm
                String signInTime = datePicker.getYear() + "-" + (month < 10 ? ("0" + month) : month)
                        + "-" + (day < 10 ? ("0" + day) : day) + " " + (hourMinute[0] < 10 ? ("0" + hourMinute[0]) : hourMinute[0])
                        + ":" + (hourMinute[1] < 10 ? ("0" + hourMinute[1]) : hourMinute[1]);
                addSignInActivity(activityTitle, continueTime, signInTime);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (delAllMode) {
            super.onBackPressed();
        } else {
            delAllMode = true;
            refresh.setEnabled(true);
            signInListAdapter = new SignInListAdapter(activity, signInActivities, false);
            signInList.setAdapter(signInListAdapter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setShowNowTime();
        if (Tools.my.hasPermission()) {
            checkNeedUpload();
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

    private void setShowNowTime() {
        if (timeChangeObserver == null) {
            showNowTime.setText(Tools.dateFormat.format(Tools.getNowTime()));
            timeChangeObserver = Tools.addTimeChangeObserver(new Tools.OnTimeChangeObserver() {
                @Override
                public void onMinuteChange() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showNowTime.setText(Tools.dateFormat.format(Tools.getNowTime()));
                        }
                    });
                }
            });
        }
    }

    private void myListener() {
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.finish();
            }
        });

        refresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                requestSignInActivities();
            }
        });

        signInList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            private SignInActivity selectSignIn;

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!refresh.isRefreshing()) {
                    selectSignIn = signInActivities.get(position);
                    if (Tools.my.hasPermission()) {
                        Intent next = new Intent(activity, ActivitySignInProgress.class);
                        next.putExtra(FixedValue.currentSignIn, selectSignIn);
                        startActivity(next);
                    } else {
                        switch (selectSignIn.getActivityStatus()) {
                            case SignInActivity.NO_STATUS:
                            case SignInActivity.COMING_SOON:
                                Toast.makeText(activity, "请等待签到开始", Toast.LENGTH_SHORT).show();
                                return;
                            case SignInActivity.IS_RUNNING:
                            case SignInActivity.ALREADY_END:
                                final AlertDialog waitDialog = Tools.showPleaseWait(activity);
                                Server.getSignInStatus(selectSignIn.getTitle(), activity, new Server.EndOfRequest() {
                                    @Override
                                    public void onSuccess(String result) {
                                        waitDialog.dismiss();
                                        switch (result) {
                                            case "ACTIVITY_NOT_EXIST":
                                                Toast.makeText(activity, "签到活动不存在，可能已被删除", Toast.LENGTH_SHORT).show();
                                                break;
                                            case "0":
                                                Intent next = new Intent(activity, ActivitySignInCode.class);
                                                next.putExtra(FixedValue.currentSignIn, selectSignIn);
                                                startActivity(next);
                                                break;
                                            case "1":
                                                Toast.makeText(activity, "已在规定时间内签到", Toast.LENGTH_SHORT).show();
                                                break;
                                            case "2":
                                                Toast.makeText(activity, "已签到，签到状态为已迟到", Toast.LENGTH_SHORT).show();
                                                break;
                                            case "AES_KEY_ERROR":
                                                Server.startInitConnectionThread();
                                            case "ERROR":
                                            default:
                                                Toast.makeText(activity, "获取签到状态失败", Toast.LENGTH_SHORT).show();
                                                break;
                                        }
                                    }

                                    @Override
                                    public void onException() {
                                        onFail("获取签到状态失败");
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
                                break;
                        }
                    }
                }
            }
        });

        signInList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (!Tools.my.hasPermission() || refresh.isRefreshing()) {
                    return false;
                }
                if (delAllMode) {
                    delAllMode = false;
                    refresh.setEnabled(false);
                    signInListAdapter = new SignInListAdapter(activity, signInActivities, true);
                    signInListAdapter.initLocate = position;
                    signInList.setAdapter(signInListAdapter);
                }
                return true;
            }
        });

        signInList.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                if (!delAllMode) {
                    return;
                }
                View topView = signInList.getChildAt(0);
                int scrollY = topView == null ? 0 : signInList.getFirstVisiblePosition() * topView.getHeight() - topView.getTop();
                refresh.setEnabled(scrollY == 0);
            }
        });

        addSignIn.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                if (Tools.my.hasPermission()) {
                    if (!delAllMode) {
                        return;
                    }
                    String[] nowTime = Tools.dateFormat.format(Tools.getNowTime()).split(" ");
                    datePicker.updateDate(Integer.parseInt(nowTime[0].substring(0, 4)),
                            Integer.parseInt(nowTime[0].substring(5, 7)) - 1,
                            Integer.parseInt(nowTime[0].substring(8)));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        timePicker.setHour(Integer.parseInt(nowTime[1].substring(0, 2)));
                        timePicker.setMinute(Integer.parseInt(nowTime[1].substring(3)));
                    }
                    datePicker.setVisibility(View.GONE);
                    timePicker.setVisibility(View.VISIBLE);
                    addSignInDialog.show();
                    selectDate.setText(nowTime[0].replace("-0", "-"));
                    if (nowTime[1].charAt(0) == '0') {
                        selectTime.setText(nowTime[1].substring(1));
                    } else {
                        selectTime.setText(nowTime[1]);
                    }
                    hourMinute[0] = Integer.parseInt(nowTime[1].substring(0, 2));
                    hourMinute[1] = Integer.parseInt(nowTime[1].substring(3));
                } else {
                    Tools.showDialogOnlyOkButton(activity, Tools.TYPE_WARNING, "没有操作权限", null);
                }
            }
        });

        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.delSignIn) {
                    if (signInActivities != null && signInActivities.size() > 0) {
                        if (delAllMode) {
                            Tools.showDialogWithTwoButtons(activity, "确定要删除所有签到活动吗?", Tools.DEFAULT_IS_CANCEL, new Tools.ControlDialog() {
                                @Override
                                public void onDialog(AlertDialog dialog) {
                                }

                                @Override
                                public void onOkButton(AlertDialog dialog, View dialogView) {
                                    dialog.cancel();
                                    delSignInActivities(signInActivities);
                                }

                                @Override
                                public void onCancelButton(AlertDialog dialog, View dialogView) {
                                    dialog.cancel();
                                }
                            });
                        } else {
                            myLog.d("needDelete: " + signInListAdapter.needDelete);
                            if (signInListAdapter.needDelete.size() > 0) {
                                Tools.showDialogWithTwoButtons(activity, "确定要删除吗?", Tools.DEFAULT_IS_CANCEL, new Tools.ControlDialog() {
                                    @Override
                                    public void onDialog(AlertDialog dialog) {
                                    }

                                    @Override
                                    public void onOkButton(AlertDialog dialog, View dialogView) {
                                        dialog.cancel();
                                        delSignInActivities(signInListAdapter.needDelete);
                                    }

                                    @Override
                                    public void onCancelButton(AlertDialog dialog, View dialogView) {
                                        dialog.cancel();
                                    }
                                });
                            } else {
                                Toast.makeText(activity, "请选择要删除的签到活动", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } else {
                        Toast.makeText(activity, "没有签到活动", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return false;
            }

            private void delSignInActivities(List<SignInActivity> activities) {
                int length = activities.size();
                String[] activityTitles = new String[length];
                for (int i = 0; i < length; i++) {
                    activityTitles[i] = activities.get(i).getTitle();
                }
                final AlertDialog waitDialog = Tools.showPleaseWait(activity);
                Server.delSignInActivities(activityTitles, activity, new Server.EndOfRequest() {
                    @Override
                    public void onSuccess(String result) {
                        waitDialog.dismiss();
                        switch (result) {
                            case "AES_KEY_ERROR":
                                Server.startInitConnectionThread();
                            case "DEL_FAIL":
                                onException();
                                break;
                            default:
                                boolean[] delResults = Tools.getObjectFromJson(result, boolean[].class);
                                if (delResults != null) {
                                    allSuccess: {
                                        for (boolean delResult : delResults) {
                                            if (!delResult) {
                                                Toast.makeText(activity, "部分签到活动删除失败，可能已被删除", Toast.LENGTH_SHORT).show();
                                                break allSuccess;
                                            }
                                        }
                                        Toast.makeText(activity, "已删除", Toast.LENGTH_SHORT).show();
                                    }
                                    if (delAllMode) {
                                        signInActivities = null;
                                    } else {
                                        for (SignInActivity signIn : signInListAdapter.needDelete) {
                                            signInActivities.remove(signIn);
                                        }
                                    }
                                    signInListAdapter = new SignInListAdapter(activity, signInActivities, false);
                                    signInList.setAdapter(signInListAdapter);
                                    delAllMode = true;
                                    requestSignInActivities();
                                } else {
                                    onException();
                                }
                        }
                    }

                    @Override
                    public void onException() {
                        if (waitDialog.isShowing()) {
                            waitDialog.dismiss();
                        }
                        Tools.showDialogOnlyOkButton(activity, Tools.TYPE_ERROR, "删除失败", null);
                    }

                    @Override
                    public void onTimeout() {
                        waitDialog.dismiss();
                        Tools.showDialogOnlyOkButton(activity, Tools.TYPE_TIP, "连接超时，请稍后重试", null);
                    }

                    @Override
                    public void noNet() {
                        waitDialog.dismiss();
                        Tools.showDialogOnlyOkButton(activity, Tools.TYPE_TIP, "网络连接不畅", null);
                    }
                });
            }
        });
    }

    private void requestSignInActivities() {
        if (!refresh.isRefreshing()) {
            refresh.setRefreshing(true);
        }
        addSignIn.setEnabled(false);
        delSignIn.setEnabled(false);
        Server.getSignInActivities(activity, new Server.EndOfRequest() {
            @Override
            public void onSuccess(String result) {
                endRefresh();
                switch (result) {
                    case "AES_KEY_ERROR":
                        Server.startInitConnectionThread();
                    case "ERROR":
                        onException();
                        break;
                    case "HAS_NOT_ACTIVITY":
                        if (parent.indexOfChild(tipBlank) < 0) {
                            parent.addView(tipBlank);
                        }
                        break;
                    default:
                        signInActivities = Tools.getListFromJson(Server.aesDecryptData(result), SignInActivity.class);
                        if (signInActivities != null) {
                            parent.removeView(tipBlank);
                            Collections.sort(signInActivities, new Comparator<SignInActivity>() {
                                @Override
                                public int compare(SignInActivity o1, SignInActivity o2) {
                                    return Integer.compare(o2.getActivityStatus(), o1.getActivityStatus());
                                }
                            });
                            signInListAdapter = new SignInListAdapter(activity, signInActivities, false);
                            signInList.setAdapter(signInListAdapter);
                        } else {
                            if (parent.indexOfChild(tipBlank) < 0) {
                                parent.addView(tipBlank);
                            }
                        }
                }
            }

            @Override
            public void onException() {
                endRefresh();
                Toast.makeText(activity, "获取签到活动失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onTimeout() {
                noNet();
            }

            @Override
            public void noNet() {
                endRefresh();
                Toast.makeText(activity, "网络连接不畅", Toast.LENGTH_SHORT).show();
            }

            private void endRefresh() {
                refresh.setRefreshing(false);
                addSignIn.setEnabled(true);
                delSignIn.setEnabled(true);
            }
        });
    }

    private void addSignInActivity(String activityTitle, String continueTime, String signInTime) {
        Server.addSignInActivity(activityTitle, signInTime, continueTime, activity, new Server.EndOfRequest() {
            @Override
            public void onSuccess(String result) {
                switch (result) {
                    case "ADD_SUCCESS":
                        Toast.makeText(activity, "已添加", Toast.LENGTH_SHORT).show();
                        inputSignInTitle.setText("");
                        inputContinueTime.setText("");
                        addSignInDialog.dismiss();
                        requestSignInActivities();
                        break;
                    case "ACTIVITY_ALREADY_EXIST":
                        Toast.makeText(activity, "签到活动已存在", Toast.LENGTH_SHORT).show();
                        break;
                    case "AES_KEY_ERROR":
                        Server.startInitConnectionThread();
                    case "ADD_FAIL":
                    default:
                        Toast.makeText(activity, "添加失败，请重试", Toast.LENGTH_SHORT).show();
                        break;
                }
            }

            @Override
            public void onException() {
                Toast.makeText(activity, "添加失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onTimeout() {
                Toast.makeText(activity, "连接超时", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void noNet() {
                Toast.makeText(activity, "没有网络连接", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static class SignInListAdapter extends BaseAdapter {
        private final Context context;
        private final List<SignInActivity> signInActivities;
        private final boolean showCheckbox;
        private final ForegroundColorSpan green;
        private final ForegroundColorSpan gray;
        private final ForegroundColorSpan orange;
        private final List<SignInActivity> needDelete = new LinkedList<>();
        private int initLocate = -1;// 当显示复选框时，用于定位长按的条目

        private SignInListAdapter(Context context, List<SignInActivity> signInActivities, boolean showCheckbox) {
            this.context = context;
            this.showCheckbox = showCheckbox;
            if (signInActivities == null) {
                this.signInActivities = new ArrayList<>();
            } else {
                this.signInActivities = signInActivities;
            }
            green = new ForegroundColorSpan(Color.parseColor("#24BB23"));
            gray = new ForegroundColorSpan(Color.parseColor("#ABACAC"));
            orange = new ForegroundColorSpan(ContextCompat.getColor(context, R.color.themeColor));
        }

        @Override
        public int getCount() {
            return signInActivities.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @SuppressLint("SetTextI18n")
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final SignInActivity signInActivity = signInActivities.get(position);
            ViewHolder viewHolder;
            if (convertView == null) {
                viewHolder = new ViewHolder();
                convertView = View.inflate(context, R.layout.item_list_sign_in_activity, null);
                viewHolder.title = convertView.findViewById(R.id.title);
                viewHolder.startTime = convertView.findViewById(R.id.startTime);
                viewHolder.status = convertView.findViewById(R.id.status);
                viewHolder.checkBox = convertView.findViewById(R.id.checkbox);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            if (showCheckbox) {
                viewHolder.checkBox.setVisibility(View.VISIBLE);
                viewHolder.checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            if (!needDelete.contains(signInActivity)) {
                                needDelete.add(signInActivity);
                            }
                        } else {
                            needDelete.remove(signInActivity);
                        }
                    }
                });
                viewHolder.checkBox.setChecked(initLocate == position);
            } else {
                viewHolder.checkBox.setVisibility(View.GONE);
            }
            viewHolder.title.setText(signInActivity.getTitle());
            viewHolder.startTime.setText(signInActivity.getSignInTime());
            SpannableString text;
            switch (signInActivity.getActivityStatus()) {
                case SignInActivity.IS_RUNNING:
                    text = new SpannableString("正在进行");
                    text.setSpan(green, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    viewHolder.status.setText(text);
                    break;
                case SignInActivity.COMING_SOON:
                    text = new SpannableString("即将开始");
                    text.setSpan(orange, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    viewHolder.status.setText(text);
                    break;
                case SignInActivity.ALREADY_END:
                    text = new SpannableString("已结束");
                    text.setSpan(gray, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    viewHolder.status.setText(text);
                    break;
                case SignInActivity.NO_STATUS:
                default:
                    viewHolder.status.setText("");
                    break;
            }
            return convertView;
        }

        private static class ViewHolder {
            AppCompatCheckBox checkBox;
            TextView title;
            TextView startTime;
            TextView status;
        }
    }

    private void checkNeedUpload() {
        File[] allFiles = Tools.getFilesFromDirectory(FixedValue.signInDataDirectory, Tools.getExternalCachePath(activity));
        if (allFiles != null) {
            for (File file : allFiles) {
                myLog.v("file ==> " + file.getAbsolutePath());
            }
            myLog.v("开始上传");
            new UploadSignInDataThread(activity, Arrays.asList(allFiles), 0).start();
        } else {
            myLog.v("文件为空");
        }
    }

    private static class UploadSignInDataThread extends Thread {
        private final MyLog myLog = new MyLog("SignInUploadTAG");
        private final AppCompatActivity activity;
        private final List<File> cacheFiles;
        private final UploadSignInDataThread thisThread;
        private final int index;

        private UploadSignInDataThread(AppCompatActivity activity, List<File> cacheFiles, int index) {
            this.activity = activity;
            this.cacheFiles = cacheFiles;
            thisThread = this;
            this.index = index;
        }

        @Override
        public void run() {
            if (cacheFiles.size() > index) {
                try {
                    File cache = cacheFiles.get(index);
                    final String title = new String(Base64.decode(cache.getName(), Base64.NO_WRAP), StandardCharsets.UTF_8);
                    final List<SignInInfo> signInInfoList = Tools.readListFromFile(cache, SignInInfo.class);
                    if (signInInfoList != null) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Tools.showDialogWithTwoButtons(activity, "上传“" + title + "”的签到数据？", Tools.DEFAULT_IS_OK, new Tools.ControlDialog() {
                                    @Override
                                    public void onDialog(AlertDialog dialog) {
                                        dialog.setCancelable(false);
                                        dialog.setCanceledOnTouchOutside(false);
                                    }

                                    @Override
                                    public void onOkButton(AlertDialog dialog, View dialogView) {
                                        dialog.dismiss();
                                        int size = signInInfoList.size();
                                        String[] multipleSignInNumber = new String[size];
                                        Integer[] multipleSignInStatus = new Integer[size];
                                        for (int i = 0; i < size; i++) {
                                            multipleSignInNumber[i] = signInInfoList.get(i).getNumber();
                                            multipleSignInStatus[i] = signInInfoList.get(i).getSignInStatus();
                                        }
                                        new UploadCoreThread(activity, title, multipleSignInNumber, multipleSignInStatus, thisThread).start();
                                    }

                                    @Override
                                    public void onCancelButton(AlertDialog dialog, View dialogView) {
                                        dialog.dismiss();
                                        uploadNext();
                                    }
                                });
                            }
                        });
                    } else {
                        uploadNext();
                    }
                } catch (Exception e) {
                    uploadNext();
                }
            }
        }

        private void uploadNext() {
            if (cacheFiles.size() > index) {
                if (!cacheFiles.get(index).delete()) {
                    myLog.w("缓存文件删除失败：" + cacheFiles.get(0).getAbsolutePath());
                }
                new UploadSignInDataThread(activity, cacheFiles, index + 1).start();
            }
        }

        private static class UploadCoreThread extends Thread {
            private final AppCompatActivity activity;
            private final String activityTitle;
            private final String[] multipleSignInNumber;
            private final Integer[] multipleSignInStatus;
            private final UploadSignInDataThread parentThread;

            private UploadCoreThread(AppCompatActivity activity, String activityTitle, String[] multipleSignInNumber, Integer[] multipleSignInStatus, UploadSignInDataThread parentThread) {
                this.activity = activity;
                this.activityTitle = activityTitle;
                this.multipleSignInNumber = multipleSignInNumber;
                this.multipleSignInStatus = multipleSignInStatus;
                this.parentThread = parentThread;
            }

            @Override
            public void run() {
                Server.setMultipleSignInStatus(activityTitle, multipleSignInNumber, multipleSignInStatus, activity, new Server.EndOfRequest() {
                    @Override
                    public void onSuccess(String result) {
                        switch (result) {
                            case "OK":
                                Toast.makeText(activity, "已上传", Toast.LENGTH_SHORT).show();
                                parentThread.uploadNext();
                                break;
                            case "ACTIVITY_NOT_EXIST":
                                Toast.makeText(activity, "签到活动不存在，可能已被删除", Toast.LENGTH_SHORT).show();
                                parentThread.uploadNext();
                                break;
                            case "AES_KEY_ERROR":
                                Server.startInitConnectionThread();
                                onFail("上传失败");
                                break;
                            case "ERROR":
                            default:
                                onException();
                                break;
                        }
                    }

                    @Override
                    public void onException() {
                        onFail("发生未知错误");
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
                        Tools.showDialogWithTwoButtons(activity, message + "，是否重新上传？", Tools.DEFAULT_IS_OK, new Tools.ControlDialog() {
                            @Override
                            public void onDialog(AlertDialog dialog) {
                                dialog.setCancelable(false);
                                dialog.setCanceledOnTouchOutside(false);
                            }

                            @Override
                            public void onOkButton(AlertDialog dialog, View dialogView) {
                                dialog.dismiss();
                                new UploadCoreThread(activity, activityTitle, multipleSignInNumber, multipleSignInStatus, parentThread).start();
                            }

                            @Override
                            public void onCancelButton(AlertDialog dialog, View dialogView) {
                                parentThread.uploadNext();
                            }
                        });
                    }
                });
            }
        }
    }
}