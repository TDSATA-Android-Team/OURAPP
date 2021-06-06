package com.tdsata.ourapp.activity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.Toolbar;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.tdsata.ourapp.R;
import com.tdsata.ourapp.entity.Member;
import com.tdsata.ourapp.util.Server;
import com.tdsata.ourapp.util.Tools;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class ActivityChangeMemberInfo extends AppCompatActivity {
    private final AppCompatActivity activity = this;
    private AlertDialog memberInfoDialog;
    private String oldNumber = null;// 在更新部员信息时所使用的部员的旧学号
    private boolean addMode = true;// true表示弹窗的目的是添加成员，false表示是修改成员信息
    private final List<String> flagList = new LinkedList<>();

    private Toolbar toolbar;
    private SwipeRefreshLayout refresh;
    private ListView memberInfoList;
    // 弹窗
    private EditText memberNumber;
    private EditText memberName;
    private AppCompatCheckBox memberFlag3;// 部长
    private AppCompatCheckBox memberFlag2;// 副部长
    private AppCompatCheckBox memberFlag1;// 组长
    private AppCompatCheckBox memberFlag0;// 部员
    private Button ok;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_member_info);

        Tools.setBlackWordOnStatus(activity);
        initView();
        initDialog();
        myListener();
        memberInfoList.setAdapter(new MemberInfoListAdapter(activity));
    }

    private void initView() {
        toolbar = findViewById(R.id.toolbar);
        refresh = findViewById(R.id.refresh);
        memberInfoList = findViewById(R.id.memberInfoList);
    }

    private void initDialog() {
        View memberInfoView = View.inflate(activity, R.layout.dialog_member_info, null);
        memberInfoDialog = new AlertDialog.Builder(activity, R.style.AlertDialogCornerRadius)
                .setView(memberInfoView)
                .create();
        memberInfoDialog.setCanceledOnTouchOutside(false);
        memberNumber = memberInfoView.findViewById(R.id.memberNumber);
        memberName = memberInfoView.findViewById(R.id.memberName);
        memberFlag3 = memberInfoView.findViewById(R.id.memberFlag3);
        memberFlag2 = memberInfoView.findViewById(R.id.memberFlag2);
        memberFlag1 = memberInfoView.findViewById(R.id.memberFlag1);
        memberFlag0 = memberInfoView.findViewById(R.id.memberFlag0);
        ok = memberInfoView.findViewById(R.id.ok);
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
                if (item.getItemId() == R.id.addMember) {
                    addMode = true;
                    memberInfoDialog.show();
                    memberNumber.setText("");
                    memberName.setText("");
                    memberFlag3.setChecked(false);
                    memberFlag2.setChecked(false);
                    memberFlag1.setChecked(false);
                    memberFlag0.setChecked(false);
                    flagList.clear();
                    return true;
                }
                return false;
            }
        });

        refresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshMemberList();
            }
        });

        memberInfoList.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                View topView = memberInfoList.getChildAt(0);
                int scrollY = topView == null ? 0 : memberInfoList.getFirstVisiblePosition() * topView.getHeight() - topView.getTop();
                refresh.setEnabled(scrollY == 0);
            }
        });

        memberInfoList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                addMode = false;
                memberInfoDialog.show();
                memberFlag3.setChecked(false);
                memberFlag2.setChecked(false);
                memberFlag1.setChecked(false);
                memberFlag0.setChecked(false);
                Member member = Tools.memberList.get(position);
                oldNumber = member.getNumber();
                memberNumber.setText(oldNumber);
                memberName.setText(member.getName());
                flagList.clear();
                String[] flags = member.getFlagArray();
                for (String flag : flags) {
                    switch (flag) {
                        case "0":// 部员
                            memberFlag0.setChecked(true);
                            break;
                        case "1":// 组长
                            memberFlag1.setChecked(true);
                            break;
                        case "2":// 副部长
                            memberFlag2.setChecked(true);
                            break;
                        case "3":// 部长
                            memberFlag3.setChecked(true);
                            break;
                    }
                }
            }
        });

        memberInfoList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final Member member = Tools.memberList.get(position);
                Tools.showDialogWithTwoButtons(activity, "确定要移除成员<" + member.getNumber() + " " + member.getName() + ">吗？",
                        Tools.DEFAULT_IS_CANCEL, new Tools.ControlDialog() {
                            @Override
                            public void onDialog(AlertDialog dialog) {}

                            @Override
                            public void onOkButton(AlertDialog dialog, View dialogView) {
                                dialog.dismiss();
                                final AlertDialog waitDialog = Tools.showPleaseWait(activity);
                                Server.delMember(member.getNumber(), activity, new Server.EndOfRequest() {
                                    @Override
                                    public void onSuccess(String result) {
                                        switch (result) {
                                            case "OK":
                                                closeDialog("成员已移除");
                                                refreshMemberList();
                                                checkExit(member.getNumber());
                                                break;
                                            case "MEMBER_NOT_EXISTS":
                                                closeDialog("成员不存在，可能已被移除");
                                                refreshMemberList();
                                                checkExit(member.getNumber());
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
                                        closeDialog("成员移除失败，请重试");
                                    }

                                    @Override
                                    public void onTimeout() {
                                        closeDialog("网络连接超时");
                                    }

                                    @Override
                                    public void noNet() {
                                        closeDialog("网络连接不畅");
                                    }

                                    private void closeDialog(String message) {
                                        waitDialog.dismiss();
                                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            @Override
                            public void onCancelButton(AlertDialog dialog, View dialogView) {
                                dialog.dismiss();
                            }
                        });
                return true;
            }
        });

        memberFlag3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    flagList.add("3");
                } else {
                    flagList.remove("3");
                }
            }
        });

        memberFlag2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    flagList.add("2");
                } else {
                    flagList.remove("2");
                }
            }
        });

        memberFlag1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    flagList.add("1");
                } else {
                    flagList.remove("1");
                }
            }
        });

        memberFlag0.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    flagList.add("0");
                } else {
                    flagList.remove("0");
                }
            }
        });

        ok.setOnClickListener(new View.OnClickListener() {
            private Boolean isAdministrator = false;

            @Override
            public void onClick(View v) {
                final String number = String.valueOf(memberNumber.getText());
                final String name = String.valueOf(memberName.getText());
                if (TextUtils.isEmpty(number) || TextUtils.isEmpty(name)) {
                    Toast.makeText(activity, "输入不能为空", Toast.LENGTH_SHORT).show();
                } else if (flagList.size() == 0) {
                    Toast.makeText(activity, "请选择成员身份", Toast.LENGTH_SHORT).show();
                } else {
                    if (isAdministrator = (memberFlag3.isChecked() || memberFlag2.isChecked())) {
                        Tools.showDialogWithTwoButtons(activity, "部长/副部长拥有对公告、签到活动、部员变动等的高级权限，确定要继续吗？", Tools.DEFAULT_IS_CANCEL, new Tools.ControlDialog() {
                            @Override
                            public void onDialog(AlertDialog dialog) {
                                dialog.setCanceledOnTouchOutside(false);
                            }

                            @Override
                            public void onOkButton(AlertDialog dialog, View dialogView) {
                                dialog.dismiss();
                                core(number, name);
                            }

                            @Override
                            public void onCancelButton(AlertDialog dialog, View dialogView) {
                                dialog.dismiss();
                            }
                        });
                    } else {
                        core(number, name);
                    }
                }
            }

            private void core(String number, String name) {
                Collections.sort(flagList, new Comparator<String>() {
                    @Override
                    public int compare(String o1, String o2) {
                        return o2.compareTo(o1);
                    }
                });
                String[] flags = flagList.toArray(new String[0]);
                if (addMode) {
                    ok.setEnabled(false);
                    memberInfoDialog.setCancelable(false);
                    Server.addMember(number, name, flags, activity, new Server.EndOfRequest() {
                        @Override
                        public void onSuccess(String result) {
                            switch (result) {
                                case "OK":
                                    memberInfoDialog.dismiss();
                                    ok.setEnabled(true);
                                    memberInfoDialog.setCancelable(true);
                                    String password = isAdministrator ? "TD_SATA" : "TD-SATA";
                                    Tools.showDialogOnlyOkButton(activity, Tools.TYPE_TIP, "成员已添加，初始密码为“" + password + "”，请及时提醒其前往修改", null);
                                    refreshMemberList();
                                    break;
                                case "MEMBER_ALREADY_EXISTS":
                                    end("成员已存在");
                                    memberInfoDialog.dismiss();
                                    refreshMemberList();
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
                            end("添加成员失败，请重试");
                        }

                        @Override
                        public void onTimeout() {
                            end("连接超时");
                        }

                        @Override
                        public void noNet() {
                            end("网络连接不畅");
                        }

                        private void end(String message) {
                            ok.setEnabled(true);
                            memberInfoDialog.setCancelable(true);
                            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    if (TextUtils.isEmpty(oldNumber)) {
                        Toast.makeText(activity, "更新部员信息异常", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ok.setEnabled(false);
                    memberInfoDialog.setCancelable(false);
                    Server.updateMember(oldNumber, number, name, flags, activity, new Server.EndOfRequest() {
                        @Override
                        public void onSuccess(String result) {
                            switch (result) {
                                case "OK":
                                    end("成员信息已更新");
                                    memberInfoDialog.dismiss();
                                    refreshMemberList();
                                    checkExit(oldNumber);
                                    break;
                                case "MEMBER_NOT_EXISTS":
                                    end("成员不存在，可能已被删除");
                                    memberInfoDialog.dismiss();
                                    refreshMemberList();
                                    checkExit(oldNumber);
                                    break;
                                case "MEMBER_ALREADY_EXISTS_IF_UPDATE":
                                    end("更新失败，更新后的成员信息与已有成员冲突");
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
                            end("更新失败，请重试");
                        }

                        @Override
                        public void onTimeout() {
                            end("连接超时");
                        }

                        @Override
                        public void noNet() {
                            end("网络连接不畅");
                        }

                        private void end(String message) {
                            ok.setEnabled(true);
                            memberInfoDialog.setCancelable(true);
                            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void refreshMemberList() {
        if (!refresh.isRefreshing()) {
            refresh.setRefreshing(true);
        }
        Server.refreshMembers(activity, new Server.EndOfRequest() {
            @Override
            public void onSuccess(String result) {
                refresh.setRefreshing(false);
                switch (result) {
                    case "AES_KEY_ERROR":
                        Server.startInitConnectionThread();
                    case "ERROR":
                        onException();
                        break;
                    default:
                        if (Tools.refreshMemberList(activity, Server.aesDecryptData(result))) {
                            memberInfoList.setAdapter(new MemberInfoListAdapter(activity));
                        } else {
                            Toast.makeText(activity, "刷新失败", Toast.LENGTH_SHORT).show();
                        }
                        break;
                }
            }

            @Override
            public void onException() {
                refresh.setRefreshing(false);
                Toast.makeText(activity, "刷新失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onTimeout() {
                refresh.setRefreshing(false);
                Toast.makeText(activity, "连接超时", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void noNet() {
                refresh.setRefreshing(false);
                Toast.makeText(activity, "网络连接不畅", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkExit(String changeNumber) {
        if (changeNumber.equals(Tools.my.getAccount())) {
            Tools.showDialogOnlyOkButton(activity, Tools.TYPE_TIP, "本人信息已更新，请重新登录", new Tools.ControlDialog() {
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
    }

    private static class MemberInfoListAdapter extends BaseAdapter {
        private final Context context;

        private MemberInfoListAdapter(Context context) {
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
                convertView = View.inflate(context, R.layout.item_list_member_info, null);
                viewHolder = new ViewHolder();
                viewHolder.ranking = convertView.findViewById(R.id.ranking);
                viewHolder.number = convertView.findViewById(R.id.number);
                viewHolder.name = convertView.findViewById(R.id.name);
                viewHolder.flag = convertView.findViewById(R.id.flag);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            Member member = Tools.memberList.get(position);
            viewHolder.ranking.setText(String.valueOf(position + 1));
            viewHolder.number.setText(member.getNumber());
            viewHolder.name.setText(member.getName());
            viewHolder.flag.setText(member.getIdentity());
            return convertView;
        }

        private static class ViewHolder {
            private TextView ranking;
            private TextView number;
            private TextView name;
            private TextView flag;
        }
    }
}