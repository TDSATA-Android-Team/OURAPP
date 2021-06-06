package com.tdsata.ourapp.activity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.tdsata.ourapp.R;
import com.tdsata.ourapp.entity.CountHistory;
import com.tdsata.ourapp.entity.Member;
import com.tdsata.ourapp.util.FixedValue;
import com.tdsata.ourapp.util.Server;
import com.tdsata.ourapp.util.Tools;
import com.tdsata.ourapp.view.HeadPortraitView;

import java.util.Collections;
import java.util.List;

public class ActivityDepartmentInfo extends AppCompatActivity {
    private final AppCompatActivity activity = this;
    private boolean hasInfo = false;

    private Toolbar toolbar;
    private MenuItem countHistory;
    private LinearLayout administrators;
    private View alterInfo;
    private TextView departmentInfo;
    private View changeMemberInfo;
    private ListView memberList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_department_info);

        initView();
        myListener();
        departmentInfo.setText("部门简介暂无");
        Server.getDepartmentInfo(activity, new Server.EndOfRequest() {
            @Override
            public void onSuccess(String result) {
                switch (result) {
                    case "AES_KEY_ERROR":
                        Server.startInitConnectionThread();
                    case "ERROR":
                        onFail();
                        break;
                    default:
                        result = Server.aesDecryptData(result);
                        if (result == null) {
                            onFail();
                        } else if (!"no-info".equals(result)) {
                            departmentInfo.setText(result);
                            hasInfo = true;
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
                hasInfo = false;
                Toast.makeText(activity, "拉取部门简介失败", Toast.LENGTH_SHORT).show();
            }
        });
        if (Tools.my.hasPermission()) {
            countHistory.setVisible(true);
            alterInfo.setVisibility(View.VISIBLE);
            changeMemberInfo.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        administrators.removeAllViews();
        for (int i = 0; i < Tools.administrators.size(); i++) {
            Member administrator = Tools.administrators.get(i);
            View view = View.inflate(activity, R.layout.item_administrator, null);
            administrator.settingHeadPhoto(activity, (HeadPortraitView) view.findViewById(R.id.headPhoto));
            ((TextView) view.findViewById(R.id.name)).setText(administrator.getName());
            ((TextView) view.findViewById(R.id.identity)).setText(administrator.getIdentity());
            administrators.addView(view);
        }
        memberList.setAdapter(new MemberListAdapter(activity));
    }

    private void initView() {
        toolbar = findViewById(R.id.toolbar);
        countHistory = toolbar.getMenu().findItem(R.id.countHistory);
        administrators = findViewById(R.id.administrators);
        alterInfo = findViewById(R.id.alterInfo);
        departmentInfo = findViewById(R.id.departmentInfo);
        changeMemberInfo = findViewById(R.id.changeMemberInfo);
        memberList = findViewById(R.id.memberList);
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
                if (item.getItemId() == R.id.countHistory) {
                    final AlertDialog waitDialog = Tools.showPleaseWait(activity);
                    Server.getChangeCountHistory(activity, new Server.EndOfRequest() {
                        @Override
                        public void onSuccess(String result) {
                            switch (result) {
                                case "NO_HISTORY":
                                    waitDialog.dismiss();
                                    Toast.makeText(activity, "无积分修改记录", Toast.LENGTH_SHORT).show();
                                    break;
                                case "AES_KEY_ERROR":
                                    Server.startInitConnectionThread();
                                case "ERROR":
                                    onException();
                                    break;
                                default:
                                    List<CountHistory> countHistories = Tools.getListFromJson(Server.aesDecryptData(result), CountHistory.class);
                                    if (countHistories != null) {
                                        waitDialog.dismiss();
                                        View historyView = View.inflate(activity, R.layout.dialog_count_history, null);
                                        final AlertDialog historyDialog = new AlertDialog.Builder(activity, R.style.AlertDialogCornerRadius)
                                                .setView(historyView)
                                                .create();
                                        historyDialog.show();
                                        historyDialog.setCanceledOnTouchOutside(false);
                                        Collections.reverse(countHistories);
                                        ((ListView) historyView.findViewById(R.id.countHistoryList)).setAdapter(new CountHistoryListAdapter(countHistories));
                                    } else {
                                        onException();
                                    }
                                    break;
                            }
                        }

                        @Override
                        public void onException() {
                            onFail("获取积分修改记录失败");
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
                    return true;
                }
                return false;
            }

            class CountHistoryListAdapter extends BaseAdapter {
                private final List<CountHistory> countHistories;

                private CountHistoryListAdapter(List<CountHistory> countHistories) {
                    this.countHistories = countHistories;
                }

                @Override
                public int getCount() {
                    return countHistories.size();
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
                    ViewHolder viewHolder;
                    if (convertView == null) {
                        convertView = View.inflate(activity, R.layout.item_list_count_history, null);
                        viewHolder = new ViewHolder();
                        viewHolder.editorMember = convertView.findViewById(R.id.editorMember);
                        viewHolder.changeMember = convertView.findViewById(R.id.changeMember);
                        viewHolder.changeValue = convertView.findViewById(R.id.changeValue);
                        viewHolder.description = convertView.findViewById(R.id.description);
                        convertView.setTag(viewHolder);
                    } else {
                        viewHolder = (ViewHolder) convertView.getTag();
                    }
                    CountHistory countHistory = countHistories.get(position);
                    viewHolder.editorMember.setText(countHistory.getEditorNumber() + " " + countHistory.getEditorName());
                    viewHolder.changeMember.setText(countHistory.getChangeNumber() + " " + countHistory.getChangeName());
                    viewHolder.changeValue.setText(countHistory.getChangeValueString());
                    viewHolder.description.setText(countHistory.getDescription());
                    return convertView;
                }

                class ViewHolder {
                    private TextView editorMember;
                    private TextView changeMember;
                    private TextView changeValue;
                    private TextView description;
                }
            }
        });

        alterInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                View view = View.inflate(activity, R.layout.dialog_release_announcement, null);
                final AlertDialog dialog = new AlertDialog.Builder(activity, R.style.AlertDialogCornerRadius)
                        .setView(view)
                        .create();
                dialog.show();
                dialog.setCanceledOnTouchOutside(false);
                final EditText inputMessage = view.findViewById(R.id.inputMessage);
                final Button add = view.findViewById(R.id.add);
                inputMessage.setHint("请输入部门简介");
                if (hasInfo) {
                    inputMessage.setText(String.valueOf(departmentInfo.getText()));
                }
                add.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String info = String.valueOf(inputMessage.getText());
                        if (TextUtils.isEmpty(info)) {
                            Toast.makeText(activity, "输入不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        dialog.setCancelable(false);
                        add.setEnabled(false);
                        Server.addDepartmentInfo(info, activity, new Server.EndOfRequest() {
                            @Override
                            public void onSuccess(String result) {
                                switch (result) {
                                    case "OK":
                                        dialog.dismiss();
                                        departmentInfo.setText(info);
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
                                onFail("更新部门简介失败");
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
                                dialog.setCancelable(true);
                                add.setEnabled(true);
                                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }
        });

        changeMemberInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(activity, ActivityChangeMemberInfo.class));
            }
        });

        memberList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(activity, ActivityPersonalInformation.class);
                intent.putExtra(FixedValue.currentMember, Tools.memberList.get(position));
                startActivity(intent);
            }
        });
    }

    private static class MemberListAdapter extends BaseAdapter {
        private final Context context;

        private MemberListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getCount() {
            return Tools.memberList.size();
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
                convertView = View.inflate(context, R.layout.item_list_member, null);
                viewHolder = new ViewHolder();
                viewHolder.serialNumber = convertView.findViewById(R.id.serialNumber);
                viewHolder.number = convertView.findViewById(R.id.number);
                viewHolder.name = convertView.findViewById(R.id.name);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            Member member = Tools.memberList.get(position);
            viewHolder.serialNumber.setText(String.valueOf(position + 1));
            viewHolder.number.setText(member.getNumber());
            viewHolder.name.setText(member.getName());
            return convertView;
        }

        private static class ViewHolder {
            private TextView serialNumber;
            private TextView number;
            private TextView name;
        }
    }
}