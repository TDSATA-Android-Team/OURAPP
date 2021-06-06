package com.tdsata.ourapp.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.tdsata.ourapp.R;
import com.tdsata.ourapp.entity.SignInActivity;
import com.tdsata.ourapp.entity.SignInInfo;
import com.tdsata.ourapp.util.FixedValue;
import com.tdsata.ourapp.util.Server;
import com.tdsata.ourapp.util.Tools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 作为部长，查看签到进度的页面.
 */
public class ActivitySignInProgress extends AppCompatActivity {
    //private final MyLog myLog = new MyLog("SignInProgressTAG");
    private final AppCompatActivity activity = this;
    private SignInActivity currentSignIn = null;
    private Tools.OnTimeChangeObserver timeChangeObserver = null;
    private List<SignInInfo> signInInfoList = null;

    private ViewGroup parent;
    private View tipBlank;
    private Toolbar toolbar;
    private TextView activityTitle;
    private TextView endTime;
    private SwipeRefreshLayout refresh;
    private ListView signInStatusList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in_progress);
        Tools.setBlackWordOnStatus(activity);

        initView();
        parent.addView(tipBlank);
        currentSignIn = (SignInActivity) getIntent().getSerializableExtra(FixedValue.currentSignIn);
        if (currentSignIn == null) {
            Toast.makeText(activity, "未知错误", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            activityTitle.setText(currentSignIn.getTitle());
            myListener();
        }
    }

    private void initView() {
        parent = findViewById(R.id.parent);
        tipBlank = View.inflate(activity, R.layout.layout_tip_blank, null);
        tipBlank.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        toolbar = findViewById(R.id.toolbar);
        activityTitle = findViewById(R.id.activityTitle);
        endTime = findViewById(R.id.endTime);
        refresh = findViewById(R.id.refresh);
        signInStatusList = findViewById(R.id.signInStatusList);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (timeChangeObserver == null) {
            showSignInActivityInfo();
            if (!currentSignIn.isEnd()) {
                timeChangeObserver = Tools.addTimeChangeObserver(new Tools.OnTimeChangeObserver() {
                    @Override
                    public void onMinuteChange() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showSignInActivityInfo();
                            }
                        });
                    }
                });
            }
        }
        requestSignInStatusInfo();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (timeChangeObserver != null) {
            Tools.removeTimeChangeObserver(timeChangeObserver);
            timeChangeObserver = null;
        }
    }

    private void myListener() {
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (!refresh.isRefreshing() && currentSignIn != null) {
                    if (item.getItemId() == R.id.scan) {
                        Intent toScan = new Intent(activity, ActivitySignInScan.class);
                        toScan.putExtra(FixedValue.currentSignIn, currentSignIn);
                        startActivity(toScan);
                        return true;
                    } else if (item.getItemId() == R.id.export) {
                        if (signInInfoList != null) {
                            Tools.showDialogWithTwoButtons(activity, "导出签到数据？", Tools.DEFAULT_IS_OK, new Tools.ControlDialog() {
                                @Override
                                public void onDialog(AlertDialog dialog) {
                                    dialog.setCanceledOnTouchOutside(false);
                                }

                                @Override
                                public void onOkButton(AlertDialog dialog, View dialogView) {
                                    dialog.dismiss();
                                    File file = Tools.exportSignInData(activity, currentSignIn.getTitle(), signInInfoList);
                                    if (file != null) {
                                        Toast.makeText(activity, "已导出至" + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(activity, "导出失败", Toast.LENGTH_SHORT).show();
                                    }
                                }

                                @Override
                                public void onCancelButton(AlertDialog dialog, View dialogView) {
                                    dialog.dismiss();
                                }
                            });
                        } else {
                            Toast.makeText(activity, "无可导出数据", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                return false;
            }
        });

        refresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                requestSignInStatusInfo();
            }
        });

        signInStatusList.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                View topView = signInStatusList.getChildAt(0);
                int scrollY = topView == null ? 0 : signInStatusList.getFirstVisiblePosition() * topView.getHeight() - topView.getTop();
                refresh.setEnabled(scrollY == 0);
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void showSignInActivityInfo() {
        if (currentSignIn.isStart()) {
            int endMinute = currentSignIn.getEndTime();
            if (endMinute > 0) {
                endTime.setText("距离签到结束还有" + endMinute + "分钟");
            } else if (endMinute == 0) {
                endTime.setText("距离签到结束在1分钟以内");
            } else {
                endTime.setText("签到已结束");
            }
        } else {
            endTime.setText("请等待签到开始");
            parent.removeView(tipBlank);
        }
    }

    private void requestSignInStatusInfo() {
        if (!refresh.isRefreshing()) {
            refresh.setRefreshing(true);
        }
        Server.getSignInStatusList(currentSignIn.getTitle(), activity, new Server.EndOfRequest() {
            @Override
            public void onSuccess(String result) {
                refresh.setRefreshing(false);
                switch (result) {
                    case "ERROR":
                        onException();
                        break;
                    case "ACTIVITY_NOT_EXIST":
                        Toast.makeText(activity, "签到活动不存在，可能已被删除", Toast.LENGTH_SHORT).show();
                        signInInfoList = null;
                        signInStatusList.setAdapter(null);
                        if (parent.indexOfChild(tipBlank) < 0) {
                            parent.addView(tipBlank);
                        }
                        break;
                    case "AES_KEY_ERROR":
                        Server.startInitConnectionThread();
                        onException();
                        break;
                    default:
                        signInInfoList = Tools.getListFromJson(Server.aesDecryptData(result), SignInInfo.class);
                        if (signInInfoList != null) {
                            parent.removeView(tipBlank);
                            signInStatusList.setAdapter(new SignInStatusListAdapter(activity, signInInfoList));
                        } else {
                            onException();
                        }
                        break;
                }
            }

            @Override
            public void onException() {
                refresh.setRefreshing(false);
                Toast.makeText(activity, "获取签到状态信息失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onTimeout() {
                refresh.setRefreshing(false);
                Toast.makeText(activity, "连接超时", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void noNet() {
                refresh.setRefreshing(false);
                Toast.makeText(activity, "请检查网络连接", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static class SignInStatusListAdapter extends BaseAdapter {
        private final Context context;
        private final List<SignInInfo> signInInfoList;
        private final ForegroundColorSpan green;
        private final ForegroundColorSpan red;
        private final ForegroundColorSpan orange;

        private SignInStatusListAdapter(Context context, List<SignInInfo> signInInfoList) {
            this.context = context;
            if (signInInfoList != null) {
                this.signInInfoList = signInInfoList;
            } else {
                this.signInInfoList = new ArrayList<>();
            }
            green = new ForegroundColorSpan(Color.parseColor("#24BB23"));
            red = new ForegroundColorSpan(Color.parseColor("#F02409"));
            orange = new ForegroundColorSpan(ContextCompat.getColor(context, R.color.themeColor));
        }

        @Override
        public int getCount() {
            return signInInfoList.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = View.inflate(context, R.layout.item_list_sign_in_progress, null);
                viewHolder = new ViewHolder();
                viewHolder.name = convertView.findViewById(R.id.name);
                viewHolder.number = convertView.findViewById(R.id.number);
                viewHolder.signInStatus = convertView.findViewById(R.id.signInStatus);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            SignInInfo signInInfo = signInInfoList.get(position);
            viewHolder.name.setText(signInInfo.getName());
            viewHolder.number.setText(signInInfo.getNumber());
            SpannableString text = null;
            switch (signInInfo.getSignInStatus()) {
                case 0:
                    text = new SpannableString("未签到");
                    text.setSpan(orange, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case 1:
                    text = new SpannableString("已签到");
                    text.setSpan(green, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case 2:
                    text = new SpannableString("已迟到");
                    text.setSpan(red, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
            }
            if (text != null) {
                viewHolder.signInStatus.setText(text);
            }
            return convertView;
        }

        private static class ViewHolder {
            private TextView name;
            private TextView number;
            private TextView signInStatus;
        }
    }
}