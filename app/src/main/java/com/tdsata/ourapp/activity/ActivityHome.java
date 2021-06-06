package com.tdsata.ourapp.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.tdsata.ourapp.R;
import com.tdsata.ourapp.entity.Announcement;
import com.tdsata.ourapp.entity.Member;
import com.tdsata.ourapp.util.FixedValue;
import com.tdsata.ourapp.util.MyLog;
import com.tdsata.ourapp.util.Server;
import com.tdsata.ourapp.util.Tools;
import com.tdsata.ourapp.view.HeadPortraitView;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.TimerTask;

/**
 * 应用主页.
 */
public class ActivityHome extends AppCompatActivity {
    //private final MyLog myLog = new MyLog("HomeTAG");
    private final AppCompatActivity activity = this;
    private HomeFragment homeFragment;
    private LeaderboardFragment leaderboardFragment;
    private PersonalCenterFragment personalCenterFragment;
    private static boolean isRefreshing;// 标记首页和排行榜的刷新控件是否正在刷新

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        new Server.SetEnableThread(new Server.SetEnableInterface() {
            @Override
            public void onComplete() {
                if (!Tools.my.infoEnable) {
                    startActivity(new Intent(activity, ActivityUploadPersonalInfo.class));
                }
            }
        }).start();
        new Server.RefreshLocalPhotoThread(activity, null).start();
        if (getIntent().getBooleanExtra("defaultPassword", false)) {
            Tools.showDialogWithTwoButtons(activity, "当前正在使用初始密码登录，请前往修改", Tools.DEFAULT_IS_OK, new Tools.ControlDialog() {
                @Override
                public void onDialog(AlertDialog dialog) {
                    dialog.setCancelable(false);
                    dialog.setCanceledOnTouchOutside(false);
                }

                @Override
                public void onOkButton(AlertDialog dialog, View dialogView) {
                    dialog.dismiss();
                    startActivity(new Intent(activity, ActivityAlterPassword.class));
                }

                @Override
                public void onCancelButton(AlertDialog dialog, View dialogView) {
                    dialog.dismiss();
                    Toast.makeText(activity, "为保障账号安全，请尽快前往修改密码", Toast.LENGTH_SHORT).show();
                }
            });
        }
        isRefreshing = false;
        Tools.startPollingAlarm(activity);
        homeFragment = new HomeFragment();
        leaderboardFragment = new LeaderboardFragment();
        personalCenterFragment = new PersonalCenterFragment();
        getSupportFragmentManager().beginTransaction().add(R.id.baseLayout, homeFragment).commit();
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.home);
        bottomNavigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if (isRefreshing) {
                    return false;
                }
                FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
                int id = item.getItemId();
                if (id == R.id.home) {
                    fragmentTransaction.replace(R.id.baseLayout, homeFragment).commit();
                } else if (id == R.id.leaderboard) {
                    if (Tools.memberCount.size() >= 3) {
                        fragmentTransaction.replace(R.id.baseLayout, leaderboardFragment).commit();
                    } else {
                        Toast.makeText(activity, "部员人数过少，功能不可用", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                } else if (id == R.id.personalCenter) {
                    fragmentTransaction.replace(R.id.baseLayout, personalCenterFragment).commit();
                } else {
                    return false;
                }
                return true;
            }
        });
        tipBackgroundRunningPermission();
    }

    private void tipBackgroundRunningPermission() {
        final SharedPreferences read = getSharedPreferences(FixedValue.HomeCfg, MODE_PRIVATE);
        boolean notRemind = read.getBoolean("notRemind", false);
        if (notRemind) {
            return;
        }
        View dialogView = View.inflate(activity, R.layout.dialog_apply_background_process, null);
        final AlertDialog dialog = new AlertDialog.Builder(activity, R.style.AlertDialogCornerRadius)
                .setView(dialogView)
                .create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        final AppCompatCheckBox notRemindCheckbox = dialogView.findViewById(R.id.notRemind);
        dialogView.findViewById(R.id.toSetting).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        dialogView.findViewById(R.id.ignore).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                boolean isNotRemind = notRemindCheckbox.isChecked();
                read.edit().putBoolean("notRemind", isNotRemind).apply();
                if (isNotRemind) {
                    Tools.showDialogOnlyOkButton(activity, Tools.TYPE_TIP, "若需实时推送公告消息，请前往应用自启动设置允许后台运行。\n若已设置请忽略。", null);
                }
            }
        });
    }

    public static class HomeFragment extends Fragment {
        private final MyLog myLog = new MyLog("HomeFragmentTAG");
        private Context context;
        private float oneDp;
        private int lastCarouselPic = 0;
        private List<Announcement> announcements = null;
        private File announcementCacheFile = null;

        private View addAnnouncement;
        private SwipeRefreshLayout refresh;
        private View search;
        private View signIn;
        private ViewPager carouselViewPager;
        private LinearLayout carouselPoints;
        private View announcementTip;
        private LinearLayout announcementLayout;
        private LinearLayout homeLeaderboard;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_home, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            context = getContext();
            oneDp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.0f, getResources().getDisplayMetrics());
            if (announcementCacheFile == null) {
                announcementCacheFile = Tools.generateFileAtCache(context, null, FixedValue.PullServiceCfg, false);
                try {
                    if (!announcementCacheFile.exists()) {
                        if (!announcementCacheFile.createNewFile()) {
                            myLog.w("公告缓存文件创建失败");
                        }
                    }
                } catch (Exception e) {
                    announcementCacheFile = null;
                }
            }
            initView(view);
            myListener();
            if (Tools.my.hasPermission()) {
                addAnnouncement.setVisibility(View.VISIBLE);
            }
            refresh.setColorSchemeResources(R.color.themeColor);
            Bitmap[] pictures = new Bitmap[] {
                    BitmapFactory.decodeResource(getResources(), R.drawable.pic_carousel_one),
                    BitmapFactory.decodeResource(getResources(), R.drawable.pic_carousel_two),
                    BitmapFactory.decodeResource(getResources(), R.drawable.pic_carousel_three)
            };
            setCarousel(pictures);
            announcementTip.setVisibility(View.VISIBLE);
            announcementLayout.setVisibility(View.GONE);
            setAnnouncements();
        }

        @Override
        public void onResume() {
            super.onResume();
            refreshHomeLeaderboard();
        }

        private void initView(View view) {
            addAnnouncement = view.findViewById(R.id.addAnnouncement);
            refresh = view.findViewById(R.id.refresh);
            search = view.findViewById(R.id.search);
            signIn = view.findViewById(R.id.signIn);
            carouselViewPager = view.findViewById(R.id.carouselViewPager);
            carouselPoints = view.findViewById(R.id.carouselPoints);
            announcementTip = view.findViewById(R.id.announcementTip);
            announcementLayout = view.findViewById(R.id.announcementLayout);
            homeLeaderboard = view.findViewById(R.id.homeLeaderboard);
        }

        private void myListener() {
            addAnnouncement.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    View addView = View.inflate(context, R.layout.dialog_release_announcement, null);
                    final AlertDialog addDialog = new AlertDialog.Builder(context, R.style.AlertDialogCornerRadius)
                            .setView(addView)
                            .create();
                    addDialog.show();
                    addDialog.setCanceledOnTouchOutside(false);
                    final EditText inputMessage = addView.findViewById(R.id.inputMessage);
                    final Button add = addView.findViewById(R.id.add);
                    add.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String message = String.valueOf(inputMessage.getText());
                            if (TextUtils.isEmpty(message)) {
                                Toast.makeText(context, "输入不能为空", Toast.LENGTH_SHORT).show();
                            } else if (message.length() > 200) {
                                Toast.makeText(context, "公告内容限200字以内", Toast.LENGTH_SHORT).show();
                            } else {
                                add.setEnabled(false);
                                addDialog.setCancelable(false);
                                Server.addAnnouncement(message, (AppCompatActivity) context, new Server.EndOfRequest() {
                                    @Override
                                    public void onSuccess(String result) {
                                        switch (result) {
                                            case "OK":
                                                addDialog.dismiss();
                                                Toast.makeText(context, "已发布", Toast.LENGTH_SHORT).show();
                                                setAnnouncements();
                                                break;
                                            case "ANNOUNCEMENT_ALREADY_EXISTS":
                                                add.setEnabled(true);
                                                addDialog.setCancelable(true);
                                                Toast.makeText(context, "已存在相同内容的公告", Toast.LENGTH_SHORT).show();
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
                                        add.setEnabled(true);
                                        addDialog.setCancelable(true);
                                        Toast.makeText(context, "请求异常，请重试", Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onTimeout() {
                                        noNet();
                                    }

                                    @Override
                                    public void noNet() {
                                        add.setEnabled(true);
                                        addDialog.setCancelable(true);
                                        Toast.makeText(context, "网络连接不畅", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    });
                }
            });

            refresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    isRefreshing = true;
                    setAnnouncements();
                    Server.refreshMembers((AppCompatActivity) context, new Server.EndOfRequest() {
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
                                    if (Tools.refreshMemberList(context, Server.aesDecryptData(result))) {
                                        new Server.RefreshLocalPhotoThread((AppCompatActivity) context, new Server.RefreshLocalPhotoInterface() {
                                            @Override
                                            public void onComplete() {
                                                refreshHomeLeaderboard();
                                            }
                                        }).start();
                                        isRefreshing = false;
                                    } else {
                                        onException();
                                    }
                                    break;
                            }
                        }

                        @Override
                        public void onException() {
                            refresh.setRefreshing(false);
                            isRefreshing = false;
                            Toast.makeText(context, "刷新失败", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onTimeout() {
                            noNet();
                        }

                        @Override
                        public void noNet() {
                            refresh.setRefreshing(false);
                            isRefreshing = false;
                            Toast.makeText(context, "网络连接不畅", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

            search.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(context, ActivitySearch.class));
                }
            });

            signIn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(context, ActivitySignIn.class));
                }
            });
        }

        private void refreshHomeLeaderboard() {
            List<Member> members;
            if (Tools.memberCount.size() >= 10) {
                members = Tools.memberCount.subList(0, 10);
            } else {
                members = Tools.memberCount;
            }
            homeLeaderboard.removeAllViews();
            for (int i = 0; i < members.size(); i++) {
                Member member = members.get(i);
                View item = View.inflate(context, R.layout.item_home_leaderboard, null);
                ((TextView) item.findViewById(R.id.ranking)).setText(String.valueOf(i + 1));
                member.settingHeadPhoto(context, (HeadPortraitView) item.findViewById(R.id.headPhoto));
                ((TextView) item.findViewById(R.id.name)).setText(member.getName());
                ((TextView) item.findViewById(R.id.count)).setText(String.valueOf(member.getCount()));
                homeLeaderboard.addView(item);
            }
        }

        private void setCarousel(Bitmap[] pictures) {
            carouselPoints.removeAllViews();
            final int count = pictures.length;
            for (int i = 0; i < count; i++) {
                View point = new View(context);
                point.setBackgroundResource(R.drawable.selector_point);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((int) (7 * oneDp), (int) (7 * oneDp));
                if (i == 0) {
                    point.setEnabled(false);
                } else {
                    params.leftMargin = (int) (4 * oneDp);
                }
                point.setLayoutParams(params);
                carouselPoints.addView(point);
            }
            carouselViewPager.setAdapter(new CarouselViewPagerAdapter(context, pictures));
            Tools.startCarouselTimer(new TimerTask() {
                @Override
                public void run() {
                    carouselViewPager.post(new Runnable() {
                        @Override
                        public void run() {
                            carouselViewPager.setCurrentItem((lastCarouselPic + 1) % count);
                        }
                    });
                }
            });
            carouselViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

                @Override
                public void onPageSelected(int position) {
                    carouselPoints.getChildAt(position).setEnabled(false);
                    carouselPoints.getChildAt(lastCarouselPic).setEnabled(true);
                    lastCarouselPic = position;
                }

                @Override
                public void onPageScrollStateChanged(int state) {}
            });
        }

        private void setAnnouncements() {
            Server.getAnnouncementList((AppCompatActivity) context, new Server.EndOfRequest() {
                @Override
                public void onSuccess(String result) {
                    switch (result) {
                        case "AES_KEY_ERROR":
                            Server.startInitConnectionThread();
                        case "ERROR":
                            onException();
                            break;
                        case "NO_ANNOUNCEMENT":
                            announcementTip.setVisibility(View.VISIBLE);
                            announcementLayout.setVisibility(View.GONE);
                            break;
                        default:
                            List<Announcement> tempAnnouncements = Tools.getListFromJson(Server.aesDecryptData(result), Announcement.class);
                            if (tempAnnouncements != null) {
                                announcementTip.setVisibility(View.GONE);
                                announcementLayout.setVisibility(View.VISIBLE);
                                announcements = tempAnnouncements;
                                if (announcementCacheFile != null) {
                                    Tools.saveObjectAtFile(announcementCacheFile, announcements);
                                }
                                Collections.reverse(announcements);
                                settingAnnouncements();
                            } else {
                                onException();
                            }
                    }
                }

                @Override
                public void onException() {
                    Toast.makeText(context, "拉取公告失败", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onTimeout() {}

                @Override
                public void noNet() {}
            });
        }

        private void settingAnnouncements() {
            announcementLayout.removeAllViews();
            for (int i = 0; i < announcements.size(); i++) {
                final Announcement announcement = announcements.get(i);
                Member member = announcement.getAdministrator();
                View item = View.inflate(context, R.layout.item_announcement, null);
                member.settingHeadPhoto(context, (HeadPortraitView) item.findViewById(R.id.headPhoto));
                ((TextView) item.findViewById(R.id.name)).setText(member.getName());
                ((TextView) item.findViewById(R.id.message)).setText(announcement.getMessage());
                item.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if (!Tools.my.hasPermission() || announcements == null || announcements.size() == 0) {
                            return false;
                        }
                        Tools.showDialogWithTwoButtons(context, "删除该公告？", Tools.DEFAULT_IS_CANCEL, new Tools.ControlDialog() {
                            @Override
                            public void onDialog(AlertDialog dialog) {
                                dialog.setCanceledOnTouchOutside(false);
                            }

                            @Override
                            public void onOkButton(AlertDialog dialog, View dialogView) {
                                dialog.dismiss();
                                final AlertDialog waitDialog = Tools.showPleaseWait(context);
                                Server.delAnnouncement(announcement.getId(), (AppCompatActivity) context, new Server.EndOfRequest() {
                                    @Override
                                    public void onSuccess(String result) {
                                        waitDialog.dismiss();
                                        switch (result) {
                                            case "OK":
                                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show();
                                                announcements.remove(announcement);
                                                if (announcements.isEmpty()) {
                                                    announcementTip.setVisibility(View.VISIBLE);
                                                    announcementLayout.setVisibility(View.GONE);
                                                } else {
                                                    settingAnnouncements();
                                                }
                                                break;
                                            case "ANNOUNCEMENT_NOT_EXISTS":
                                                Toast.makeText(context, "不存在该公告，可能已被删除", Toast.LENGTH_SHORT).show();
                                                break;
                                            case "AES_KEY_ERROR":
                                                Server.startInitConnectionThread();
                                            case "ERROR":
                                            default:
                                                onException();
                                        }
                                    }

                                    @Override
                                    public void onException() {
                                        if (waitDialog.isShowing()) {
                                            waitDialog.dismiss();
                                        }
                                        Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onTimeout() {
                                        noNet();
                                    }

                                    @Override
                                    public void noNet() {
                                        waitDialog.dismiss();
                                        Toast.makeText(context, "请检查网络连接", Toast.LENGTH_SHORT).show();
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
                announcementLayout.addView(item);
            }
        }

        private static class CarouselViewPagerAdapter extends PagerAdapter {
            private final Context context;
            private final Bitmap[] pictures;

            private CarouselViewPagerAdapter(Context context, Bitmap[] pictures) {
                this.context = context;
                this.pictures = pictures;
            }

            @Override
            public int getCount() {
                return pictures.length;
            }

            @Override
            public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                return view == object;
            }

            @NonNull
            @Override
            public Object instantiateItem(@NonNull ViewGroup container, int position) {
                View picture = View.inflate(context, R.layout.item_pager_carousel_picture, null);
                ((ImageView) picture.findViewById(R.id.picture)).setImageBitmap(pictures[position]);
                container.addView(picture);
                return picture;
            }

            @Override
            public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
                container.removeView((View) object);
            }
        }
    }

    public static class LeaderboardFragment extends Fragment {
        private Context context;

        private SwipeRefreshLayout refresh;
        private View first;
        private HeadPortraitView firstHeadPhoto;
        private TextView firstName;
        private TextView firstCount;
        private View second;
        private HeadPortraitView secondHeadPhoto;
        private TextView secondName;
        private TextView secondCount;
        private View third;
        private HeadPortraitView thirdHeadPhoto;
        private TextView thirdName;
        private TextView thirdCount;
        private ListView leaderboardList;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_leaderboard, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            context = getContext();
            initView(view);
            myListener();
            refresh.setColorSchemeResources(R.color.themeColor);
        }

        @Override
        public void onResume() {
            super.onResume();
            refreshLeaderboard();
        }

        private void initView(View view) {
            refresh = view.findViewById(R.id.refresh);
            first = view.findViewById(R.id.first);
            firstHeadPhoto = view.findViewById(R.id.firstHeadPhoto);
            firstName = view.findViewById(R.id.firstName);
            firstCount = view.findViewById(R.id.firstCount);
            second = view.findViewById(R.id.second);
            secondHeadPhoto = view.findViewById(R.id.secondHeadPhoto);
            secondName = view.findViewById(R.id.secondName);
            secondCount = view.findViewById(R.id.secondCount);
            third = view.findViewById(R.id.third);
            thirdHeadPhoto = view.findViewById(R.id.thirdHeadPhoto);
            thirdName = view.findViewById(R.id.thirdName);
            thirdCount = view.findViewById(R.id.thirdCount);
            leaderboardList = view.findViewById(R.id.leaderboardList);
        }

        private void myListener() {
            refresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    isRefreshing = true;
                    Server.refreshMembers((AppCompatActivity) context, new Server.EndOfRequest() {
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
                                    if (Tools.refreshMemberList(context, Server.aesDecryptData(result))) {
                                        new Server.RefreshLocalPhotoThread((AppCompatActivity) context, new Server.RefreshLocalPhotoInterface() {
                                            @Override
                                            public void onComplete() {
                                                refreshLeaderboard();
                                            }
                                        }).start();
                                        isRefreshing = false;
                                    } else {
                                        onException();
                                    }
                                    break;
                            }
                        }

                        @Override
                        public void onException() {
                            refresh.setRefreshing(false);
                            isRefreshing = false;
                            Toast.makeText(context, "刷新失败", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onTimeout() {
                            noNet();
                        }

                        @Override
                        public void noNet() {
                            refresh.setRefreshing(false);
                            isRefreshing = false;
                            Toast.makeText(context, "网络连接不畅", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

            first.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toInfo(Tools.memberCount.get(0));
                }
            });

            second.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toInfo(Tools.memberCount.get(1));
                }
            });

            third.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toInfo(Tools.memberCount.get(2));
                }
            });

            leaderboardList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    toInfo(Tools.memberCount.get(position + 3));
                }
            });

            leaderboardList.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
                @Override
                public void onScrollChanged() {
                    View topView = leaderboardList.getChildAt(0);
                    int scrollY = topView == null ? 0 : leaderboardList.getFirstVisiblePosition() * topView.getHeight() - topView.getTop();
                    refresh.setEnabled(scrollY == 0);
                }
            });
        }

        private void toInfo(Member currentMember) {
            Intent toInfo = new Intent(context, ActivityPersonalInformation.class);
            toInfo.putExtra(FixedValue.currentMember, currentMember);
            startActivity(toInfo);
        }

        private void refreshLeaderboard() {
            Member first = Tools.memberCount.get(0);
            first.settingHeadPhoto(context, firstHeadPhoto);
            firstName.setText(first.getName());
            firstCount.setText(String.valueOf(first.getCount()));
            Member second = Tools.memberCount.get(1);
            second.settingHeadPhoto(context, secondHeadPhoto);
            secondName.setText(second.getName());
            secondCount.setText(String.valueOf(second.getCount()));
            Member third = Tools.memberCount.get(2);
            third.settingHeadPhoto(context, thirdHeadPhoto);
            thirdName.setText(third.getName());
            thirdCount.setText(String.valueOf(third.getCount()));
            leaderboardList.setAdapter(new LeaderboardListAdapter(context, Tools.memberCount.subList(3, Tools.memberCount.size())));
        }

        private static class LeaderboardListAdapter extends BaseAdapter {
            private final Context context;
            private final List<Member> members;

            private LeaderboardListAdapter(Context context, List<Member> members) {
                this.context = context;
                this.members = members;
            }

            @Override
            public int getCount() {
                return members.size();
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
                    convertView = View.inflate(context, R.layout.item_list_leaderboard, null);
                    viewHolder = new ViewHolder();
                    viewHolder.ranking = convertView.findViewById(R.id.ranking);
                    viewHolder.headPhoto = convertView.findViewById(R.id.headPhoto);
                    viewHolder.name = convertView.findViewById(R.id.name);
                    viewHolder.count = convertView.findViewById(R.id.count);
                    convertView.setTag(viewHolder);
                } else {
                    viewHolder = (ViewHolder) convertView.getTag();
                }
                Member member = members.get(position);
                viewHolder.ranking.setText(String.valueOf(position + 4));
                member.settingHeadPhoto(context, viewHolder.headPhoto);
                viewHolder.name.setText(member.getName());
                viewHolder.count.setText(String.valueOf(member.getCount()));
                return convertView;
            }

            private static class ViewHolder {
                private TextView ranking;
                private HeadPortraitView headPhoto;
                private TextView name;
                private TextView count;
            }
        }
    }

    public static class PersonalCenterFragment extends Fragment {
        private Context context;
        private final int permissionsRequestCode = Tools.getRequestCode();
        private final int takePhotoRequestCode = Tools.getRequestCode();
        private final int fromPhotoAlbumRequestCode = Tools.getRequestCode();
        private final int cropPhotoRequestCode = Tools.getRequestCode();

        private HeadPortraitView headPhoto;// 个人头像
        private TextView name;// 个人姓名
        private TextView number;// 个人学号
        private View toMyInfo;// 个人姓名学号区域Layout
        private TextView count;// 个人积分
        private TextView department;// 个人部门
        private TextView flag;// 个人身份
        private View alterPassword;// 修改密码
        private View departmentMessage;// 部门信息
        private View exitLogin;// 退出登录
        private View aboutOur;// 关于我们

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_personal_center, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            context = getContext();
            initView(view);
            myListener();
            Tools.my.settingHeadPhoto(context, headPhoto);
            name.setText(Tools.my.getName());
            number.setText(Tools.my.getAccount());
            count.setText(String.valueOf(Tools.my.getCount()));
            department.setText(Tools.my.getDepartment().getWholeName());
            flag.setText(Tools.my.getIdentity());
        }

        private void initView(View view) {
            headPhoto = view.findViewById(R.id.headPhoto);
            name = view.findViewById(R.id.name);
            number = view.findViewById(R.id.number);
            toMyInfo = view.findViewById(R.id.toMyInfo);
            count = view.findViewById(R.id.count);
            department = view.findViewById(R.id.department);
            flag = view.findViewById(R.id.flag);
            alterPassword = view.findViewById(R.id.alterPassword);
            departmentMessage = view.findViewById(R.id.departmentMessage);
            exitLogin = view.findViewById(R.id.exitLogin);
            aboutOur = view.findViewById(R.id.aboutOur);
        }

        private void myListener() {
            headPhoto.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    View selectView = View.inflate(context, R.layout.dialog_select_photo_source, null);
                    final AlertDialog selectDialog = new AlertDialog.Builder(context, R.style.AlertDialogCornerRadius)
                            .setView(selectView)
                            .create();
                    selectDialog.show();
                    selectView.findViewById(R.id.takePhoto).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            selectDialog.dismiss();
                            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.CAMERA}, permissionsRequestCode);
                            } else {
                                openCamera();
                            }
                        }
                    });
                    selectView.findViewById(R.id.fromPhotoAlbum).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            selectDialog.dismiss();
                            Intent toSystemPicAlbum = new Intent(Intent.ACTION_PICK);
                            toSystemPicAlbum.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
                            startActivityForResult(toSystemPicAlbum, fromPhotoAlbumRequestCode);
                        }
                    });
                }
            });

            toMyInfo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    skip(ActivityPersonalInformation.class, new DoIntent() {
                        @Override
                        public void doIntent(Intent intent) {
                            intent.putExtra(FixedValue.currentMember, Tools.my.getMemberInfo());
                        }
                    });
                }
            });

            alterPassword.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    skip(ActivityAlterPassword.class, null);
                }
            });

            departmentMessage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    skip(ActivityDepartmentInfo.class, null);
                }
            });

            exitLogin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Tools.showDialogWithTwoButtons(context, "退出当前登录账号？", Tools.DEFAULT_IS_CANCEL, new Tools.ControlDialog() {
                        @Override
                        public void onDialog(AlertDialog dialog) {
                            dialog.setCanceledOnTouchOutside(false);
                        }

                        @Override
                        public void onOkButton(AlertDialog dialog, View dialogView) {
                            dialog.dismiss();
                            Tools.saveSharedPreferences(context, FixedValue.LoginCfg, FixedValue.autoLogin, "false");
                            Intent intent = new Intent(context, ActivityStartPicture.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }

                        @Override
                        public void onCancelButton(AlertDialog dialog, View dialogView) {
                            dialog.dismiss();
                        }
                    });
                }
            });

            aboutOur.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    skip(ActivityAboutOur.class, null);
                }
            });
        }

        private void openCamera() {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(intent, takePhotoRequestCode);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (data != null) {
                if (requestCode == fromPhotoAlbumRequestCode) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        Intent toCrop = new Intent(context, ActivityCropHeadPhoto.class);
                        toCrop.putExtra(FixedValue.cropUri, uri);
                        startActivityForResult(toCrop, cropPhotoRequestCode);
                    }
                } else if (requestCode == takePhotoRequestCode) {
                    Bundle cameraPhoto = data.getExtras();
                    try {
                        if (cameraPhoto != null) {
                            Bitmap photo = (Bitmap) cameraPhoto.get("data");
                            Intent toCrop = new Intent(context, ActivityCropHeadPhoto.class);
                            toCrop.putExtra(FixedValue.cropBitmap, photo);
                            startActivityForResult(toCrop, cropPhotoRequestCode);
                        } else {
                            Toast.makeText(context, "照片解析失败", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(context, "照片解析失败", Toast.LENGTH_SHORT).show();
                    }
                } else if (requestCode == cropPhotoRequestCode) {
                    if (resultCode == RESULT_OK) {
                        final String photoName = data.getStringExtra(FixedValue.cropResult);
                        if (photoName != null) {
                            Tools.threadPool.execute(new Runnable() {
                                @Override
                                public void run() {
                                    File photo = Tools.generateFileAtCache(context, FixedValue.photoDirectory, photoName, true);
                                    final Bitmap pic = BitmapFactory.decodeFile(photo.getAbsolutePath());
                                    if (pic != null) {
                                        headPhoto.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                headPhoto.setImageBitmap(pic);
                                            }
                                        });
                                    }
                                }
                            });
                        }
                    }
                }
            }
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode == permissionsRequestCode) {
                boolean noAllPermissions = false;
                for (int requestResult : grantResults) {
                    if (requestResult != PackageManager.PERMISSION_GRANTED) {
                        noAllPermissions = true;
                        break;
                    }
                }
                if (noAllPermissions) {
                    Toast.makeText(context, "权限不足", Toast.LENGTH_SHORT).show();
                } else {
                    openCamera();
                }
            }
        }

        /**
         * 跳转到目标Activity.
         *
         * @param goalActivity 跳转目标
         * @param doIntent 为跳转意图设置附加信息
         */
        private void skip(Class<? extends AppCompatActivity> goalActivity, DoIntent doIntent) {
            Intent intent = new Intent(context, goalActivity);
            if (doIntent != null) {
                doIntent.doIntent(intent);
            }
            startActivity(intent);
        }

        private interface DoIntent {
            void doIntent(Intent intent);
        }
    }
}