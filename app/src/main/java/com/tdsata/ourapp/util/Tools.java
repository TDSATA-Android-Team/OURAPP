package com.tdsata.ourapp.util;

import android.app.ActivityOptions;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.github.aelstad.keccakj.fips202.SHA3_512;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tdsata.ourapp.R;
import com.tdsata.ourapp.drawable.Loading;
import com.tdsata.ourapp.entity.Member;
import com.tdsata.ourapp.entity.SignInActivity;
import com.tdsata.ourapp.entity.SignInInfo;
import com.tdsata.ourapp.receiver.AlarmReceiver;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static android.content.Context.ALARM_SERVICE;

/**
 * 提供各式工具方法与工具类的使用.
 * 详细介绍可查看对应的Doc文档.
 */
public class Tools {

    /**
     * 登陆者所在部门的所有成员.
     * 按姓名排序.
     */
    public static List<Member> memberList = null;

    /**
     * 按积分降序排列的登陆者所在部门的所有成员.
     */
    public static List<Member> memberCount = null;

    /**
     * 部门部长副部长.
     */
    public static List<Member> administrators = new ArrayList<>();

    /**
     * 登录者.
     */
    public static My my = null;

    /**
     * 执行一些大量且短暂的任务，如从文件解析Bitmap.
     */
    public static ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newCachedThreadPool();

    // 类私有成员量
    private static final MyLog myLog = new MyLog("ToolsTAG");
    private static final Gson gson = new Gson();
    private static final Random random = new Random();
    private static boolean noInitTime = true;// 标记是否已开启获取时间线程，避免重复开启
    private static boolean isNetTime = false;// 标记获得的时间是否为网络时间
    private static long markTime;// 记录当获取到网络时间时，系统已开机时间
    private static long netTime;// 记录获取到的网络时间
    private static Timer carouselTimer = null;// 轮播计时器

    /**
     * 应用启动后应当先做的初始化活动.
     */
    public static void init() {
        initNetTime();
        Server.generateAESKey();// 在UI线程进行以确保AES密钥不为空
    }

    /**
     * 获取随机的请求码用于需要返回结果的启动Activity.
     *
     * @return 随机的一个整型数
     */
    public static int getRequestCode() {
        return random.nextInt(32768) + 128;
    }

    /**
     * 设置后台轮询警报.
     * @param context 上下文
     */
    public static void startPollingAlarm(Context context) {
        int requestCode = 21204;
        long delay = 5 * 60000L;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        Intent receiver = new Intent(context, AlarmReceiver.class);
        receiver.setAction(FixedValue.MY_ACTION_NAME);
        PendingIntent startReceiver = PendingIntent.getBroadcast(context, requestCode, receiver, PendingIntent.FLAG_CANCEL_CURRENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delay, startReceiver);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delay, startReceiver);
        }
    }

    //=====================日期和时间=======================
    private static final List<OnTimeChangeObserver> timeChangeObservers = new LinkedList<>();

    /**
     * 通用日期时间格式.
     */
    public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

    /**
     * 初始化网络时间.
     * 在第一次调用后，每隔3s自动获取一次，直至获取成功.
     * 第二次及之后调用无效.
     */
    public static void initNetTime() {
        if (noInitTime) {
            noInitTime = false;
            final Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        URL url = new URL("https://m.baidu.com");
                        URLConnection uc = url.openConnection();
                        uc.connect();
                        uc.setReadTimeout(1000);
                        uc.setConnectTimeout(1000);
                        netTime = uc.getDate();
                        markTime = SystemClock.elapsedRealtime();
                        isNetTime = true;
                        myLog.v("取得网络时间");
                        startTimer();
                        timer.cancel();
                    } catch (Exception e) {
                        myLog.v("正在获取网络时间...");
                    }
                }
            }, 0, 3000);
        }
    }

    /**
     * 获取当前时间.
     * 使用前请先调用initNetTime().
     * 精确时间的计算：首先获取一次网络时间，记录获得的网络时间和获得时的系统
     * 开机时间。之后当获取现在时间时，即可用请求获取现在时间时的系统开机时间
     * 减去获取网络时间时的系统开机时间，再加上记录的网络时间，所得即为精准的
     * 时间。
     *
     * @return 若isNetTime为真，则为精确的当前时间；否则，为系统当前时间。
     * @see #initNetTime()
     */
    public static long getNowTime() {
        if (isNetTime) {
            return (SystemClock.elapsedRealtime() - markTime + netTime);
        } else {
            return System.currentTimeMillis();
        }
    }

    private static void startTimer() {
        long delay = 60000L - getNowTime() % 60000L;
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                for (OnTimeChangeObserver observer : timeChangeObservers) {
                    observer.onMinuteChange();
                }
            }
        }, delay, 60000L);
    }

    /**
     * 时间改变的观察者.
     */
    public interface OnTimeChangeObserver {
        /**
         * 当分钟位改变时调用.
         */
        void onMinuteChange();
    }

    /**
     * 添加监听时间改变的观察者.
     *
     * @param observer 待添加的观察者
     * @return 添加的观察者
     */
    public static OnTimeChangeObserver addTimeChangeObserver(OnTimeChangeObserver observer) {
        if (!timeChangeObservers.contains(observer)) {
            timeChangeObservers.add(observer);
        }
        return observer;
    }

    /**
     * 移除监听时间改变的观察者.
     *
     * @param observer 待移除的观察者
     */
    public static void removeTimeChangeObserver(OnTimeChangeObserver observer) {
        timeChangeObservers.remove(observer);
    }

    //====================标题栏状态栏======================
    /**
     * 隐藏标题栏.
     *
     * @param activity 需要隐藏标题栏的Activity
     */
    public static void hideActionBar(AppCompatActivity activity) {
        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null)
            actionBar.hide();
    }

    /**
     * 设置状态栏字体为黑色.
     *
     * @param activity 需要设置的Activity
     */
    public static void setBlackWordOnStatus(AppCompatActivity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    /**
     * 移除状态栏设置透明后部分机型会有的阴影层.
     * 注意：调用该方法需在setContentView之前
     *
     * @param activity 欲设置的Activity
     */
    public static void removeStatusShadow(AppCompatActivity activity) {
        try {
            Class<?> decorViewClass = activity.getWindow().getDecorView().getClass();
            Field field = decorViewClass.getDeclaredField("mSemiTransparentStatusBarColor");
            field.setAccessible(true);
            field.setInt(activity.getWindow().getDecorView(), Color.TRANSPARENT);
        } catch (Exception e) {
            //ignore
        }
    }

    //======================弹窗提示========================
    /**
     * 提示类型弹窗.
     */
    public static final int TYPE_TIP = 0;

    /**
     * 警告类型弹窗.
     */
    public static final int TYPE_WARNING = 1;

    /**
     * 错误类型弹窗.
     */
    public static final int TYPE_ERROR = 2;

    /**
     * 没有默认按钮.
     */
    public static final int NO_DEFAULT = 0;

    /**
     * 默认按钮是“确定”.
     */
    public static final int DEFAULT_IS_OK = 1;

    /**
     * 默认按钮是“取消”.
     */
    public static final int DEFAULT_IS_CANCEL = 2;

    /**
     * 控制弹窗响应的接口.
     */
    public interface ControlDialog {
        /**
         * 此方法用来设置弹窗的一些属性.
         *
         * @param dialog 对弹窗的引用
         */
        void onDialog(AlertDialog dialog);

        /**
         * 点击弹窗上确定按钮时执行的操作.
         *
         * @param dialog 对弹窗的引用
         * @param dialogView 弹窗所使用的视图
         */
        void onOkButton(AlertDialog dialog, View dialogView);

        /**
         * 点击弹窗上取消按钮时执行的操作.
         *
         * @param dialog 对弹窗的引用
         * @param dialogView 弹窗所使用的视图
         */
        void onCancelButton(AlertDialog dialog, View dialogView);
    }

    /**
     * 展示一个弹窗只带有一个确定按钮.
     * 在该方法中未使用ControlDialog的onCancelButton方法.
     *
     * @param context 展示弹窗的上下文
     * @param type 弹窗的类型
     * @param message 弹窗展示的信息
     * @param controlDialog 对弹窗的操作控制，若为null，则默认点击确定按钮取消弹窗
     *
     * @see #TYPE_TIP
     * @see #TYPE_WARNING
     * @see #TYPE_ERROR
     */
    public static void showDialogOnlyOkButton(Context context, int type, String message, final ControlDialog controlDialog) {
        final View dialogView = View.inflate(context, R.layout.dialog_only_one_button, null);
        final AlertDialog dialog = new AlertDialog.Builder(context, R.style.AlertDialogCornerRadius)
                .setView(dialogView)
                .create();
        dialog.show();
        if (controlDialog != null) {
            controlDialog.onDialog(dialog);
        }
        int iconId;
        String title;
        switch (type) {
            case TYPE_WARNING:
                iconId = R.drawable.dialog_ic_warning;
                title = "警告";
                break;
            case TYPE_ERROR:
                iconId = R.drawable.dialog_ic_error;
                title = "错误";
                break;
            default:
                myLog.w("弹窗类型错误，为：" + type);
            case TYPE_TIP:
                iconId = R.drawable.dialog_ic_tip;
                title = "提示";
                break;
        }
        ((ImageView) dialogView.findViewById(R.id.typeIcon)).setImageResource(iconId);
        ((TextView) dialogView.findViewById(R.id.typeText)).setText(title);
        ((TextView) dialogView.findViewById(R.id.message)).setText(message);
        dialogView.findViewById(R.id.dialogOk).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (controlDialog == null) {
                    dialog.cancel();
                } else {
                    controlDialog.onOkButton(dialog, dialogView);
                }
            }
        });
    }

    /**
     * 展示一个弹窗带有确定和取消按钮.
     *
     * @param context 展示弹窗的上下文
     * @param message 弹窗展示的信息
     * @param controlDialog 对弹窗的操作控制
     */
    public static void showDialogWithTwoButtons(Context context, String message, int defaultButton, final ControlDialog controlDialog) {
        final View dialogView = View.inflate(context, R.layout.dialog_with_two_buttons, null);
        final AlertDialog dialog = new AlertDialog.Builder(context, R.style.AlertDialogCornerRadius)
                .setView(dialogView)
                .create();
        dialog.show();
        controlDialog.onDialog(dialog);
        ((TextView) dialogView.findViewById(R.id.message)).setText(message);
        Button dialogOk = dialogView.findViewById(R.id.dialogOk);
        Button dialogCancel = dialogView.findViewById(R.id.dialogCancel);
        switch (defaultButton) {
            case DEFAULT_IS_OK:
                dialogOk.setBackgroundResource(R.drawable.selector_default_button);
                dialogOk.setTextColor(Color.WHITE);
                dialogCancel.setBackgroundResource(R.drawable.selector_another_button);
                break;
            case DEFAULT_IS_CANCEL:
                dialogOk.setBackgroundResource(R.drawable.selector_another_button);
                dialogCancel.setBackgroundResource(R.drawable.selector_default_button);
                dialogCancel.setTextColor(Color.WHITE);
                break;
            case NO_DEFAULT:
                dialogOk.setBackgroundResource(R.drawable.selector_another_button);
                dialogCancel.setBackgroundResource(R.drawable.selector_another_button);
                break;
        }
        dialogOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                controlDialog.onOkButton(dialog, dialogView);
            }
        });
        dialogCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                controlDialog.onCancelButton(dialog, dialogView);
            }
        });
    }

    /**
     * 展示一个请稍后的提示弹窗.
     *
     * @param context 展示弹窗的上下文
     * @return 返回对弹窗的引用
     */
    public static AlertDialog showPleaseWait(Context context) {
        View waitDialog = View.inflate(context, R.layout.dialog_wait_loading, null);
        Loading loading = new Loading();
        ((ImageView) waitDialog.findViewById(R.id.loading)).setImageDrawable(loading);
        loading.start();
        AlertDialog wait = new AlertDialog.Builder(context, R.style.AlertDialogCornerRadius)
                .setView(waitDialog)
                .create();
        wait.show();
        wait.setCancelable(false);
        wait.setCanceledOnTouchOutside(false);
        return wait;
    }

    //========================文件==========================
    /**
     * 简单配置的存储.
     * 存储路径为 /data/user/0/包名/shared_prefs/ 下
     *
     * @param context 需要存储配置的Activity上下文
     * @param fileName 配置的文件名
     * @param keyAndValue 以key，value，key，value，...这种方式的存储值序列。
     *                    若为值数目奇数个，将舍掉最后一个
     */
    public static void saveSharedPreferences(Context context, String fileName, String... keyAndValue) {
        if (keyAndValue.length % 2 == 1) {
            keyAndValue = Arrays.copyOf(keyAndValue, keyAndValue.length - 1);
        }
        SharedPreferences read = context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = read.edit();
        for (int i = 0; i < keyAndValue.length; i += 2) {
            editor.putString(keyAndValue[i], keyAndValue[i + 1]);
        }
        editor.apply();
    }

    /**
     * 移除指定配置文件中指定的配置.
     *
     * @param context 需要移除配置的Activity上下文
     * @param fileName 配置的文件名
     * @param keys 需移除的配置的键
     */
    public static void removeSharedPreferencesCfg(Context context, String fileName, String... keys) {
        SharedPreferences read = context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = read.edit();
        for (String key : keys ) {
            editor.remove(key);
        }
        editor.apply();
    }

    /**
     * 对较大的数据进行文件存储.
     *
     * @param file 用来存储数据的文件，原文件内容将被覆盖。
     *             若文件不存在，将会创建。
     * @param data 将被存储的数据
     * @return 若文件存储成功，则返回存储数据的文件引用；否则返回null。
     */
    public static File saveFile(File file, String data, boolean append) {
        if (file == null || data == null) {
            myLog.w("保存文件失败：参数含空");
            return null;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs())
                return null;
        }
        try {
            if (file.exists() || file.createNewFile()) {
                try (FileOutputStream fos = new FileOutputStream(file, append)) {
                    fos.write(data.getBytes());
                    return file;
                }
            } else {
                return null;
            }
        } catch (IOException e) {
            myLog.w("保存文件发生异常：" + e.getMessage());
            return null;
        }
    }

    /**
     * 从文件中读取数据.
     *
     * @param file 指定的要读取数据的文件。
     * @return 若读取成功，则返回读取的结果；否则返回null。
     */
    public static String readFile(File file) {
        if (file == null) {
            myLog.w("读取文件失败：参数为空");
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            int len;
            byte[] cache = new byte[1024];
            StringBuilder stringBuilder = new StringBuilder();
            while ((len = fis.read(cache)) != -1) {
                stringBuilder.append(new String(cache, 0, len));
            }
            return String.valueOf(stringBuilder);
        } catch (IOException e) {
            myLog.w("读取文件发生异常", e);
            return null;
        }
    }

    /**
     * 将对象保存到文件.
     *
     * @param file 用来存储数据的文件，原文件内容将被覆盖。
     *             若文件不存在，将会创建。
     * @param object 保存的对象
     * @param <T> 任意对象类型
     * @return 若文件存储成功，则返回存储数据的文件引用；否则返回null。
     */
    public static <T> File saveObjectAtFile(File file, T object) {
        return saveFile(file, gson.toJson(object), false);
    }

    /**
     * 从文件读取对象.
     *
     * @param file 指定的要读取对象的文件
     * @param tClass 读取的对象类型
     * @param <T> 任意对象类型
     * @return 若读取成功，则返回读取的对象；否则返回null。
     */
    public static <T> T readObjectFromFile(File file, Class<T> tClass) {
        String readData = readFile(file);
        if (readData != null)
            return gson.fromJson(readData, tClass);
        else
            return null;
    }

    /**
     * 从文件中读取List对象.
     *
     * @param file 指定的要读取对象的文件
     * @param eClass 读取的List集合元素类型
     * @param <E> 任意对象类型
     * @return 若读取成功，则返回读取的List集合；否则返回null
     */
    public static <E> List<E> readListFromFile(File file, Class<E> eClass) {
        String data = readFile(file);
        if (data == null) {
            return null;
        }
        return getListFromJson(data, eClass);
    }

    /**
     * 通过指定的各个属性生成cache目录下的文件引用.
     *
     * @param directory 存储的文件目录
     * @param filename 存储的文件名
     * @param atExternal true表示保存在外部存储设备；false表示保存在内部存储
     * @return 返回生成的文件引用
     */
    public static File generateFileAtCache(Context context, String directory, String filename, boolean atExternal) {
        String path;
        if (atExternal)
            path = getExternalCachePath(context);
        else
            path = getInternalCachePath(context);
        if (!TextUtils.isEmpty(directory)) {
            if (directory.lastIndexOf('/') == directory.length() - 1)// 若末尾有'/'，则去除
                directory = directory.substring(0, directory.length() - 1);
            if (directory.indexOf('/') == 0)
                path += directory;
            else
                path += ("/" + directory);
        }
        return new File(path, filename);
    }

    /**
     * 通过指定的各个属性生成files目录下的文件引用.
     *
     * @param directory 存储的文件目录
     * @param filename 存储的文件名
     * @param atExternal true表示保存在外部存储设备；false表示保存在内部存储
     * @return 返回生成的文件引用
     */
    public static File generateFileAtFiles(Context context, String directory, String filename, boolean atExternal) {
        String path;
        if (atExternal)
            path = getExternalFilesPath(context);
        else
            path = getInternalFilesPath(context);
        if (!TextUtils.isEmpty(directory)) {
            if (directory.lastIndexOf('/') == directory.length() - 1)// 若末尾有'/'，则去除
                directory = directory.substring(0, directory.length() - 1);
            if (directory.indexOf('/') == 0)
                path += directory;
            else
                path += ("/" + directory);
        }
        return new File(path, filename);
    }

    //// 内部存储(internal)，路径一般为：
    ////         /data/user/0/包名/...
    /**
     * 获得内部存储中的cache目录路径.
     *
     * @return 返回内部存储中的cache目录路径
     */
    public static String getInternalCachePath(Context context) {
        return context.getCacheDir().getAbsolutePath();
    }

    /**
     * 获得内部存储中的files目录路径.
     *
     * @return 返回内部存储中的files目录路径.
     */
    public static String getInternalFilesPath(Context context) {
        return context.getFilesDir().getAbsolutePath();
    }

    //// 外部存储(external)，路径一般为：
    ////         /storage/emulated/0/Android/data/包名/...
    /**
     * 获得外部存储中的cache目录路径.
     *
     * @return 返回外部存储中的cache目录路径；
     *         若获取为空，则返回内部存储中的cache目录路径
     */
    public static String getExternalCachePath(Context context) {
        File cache = context.getExternalCacheDir();
        if (cache != null)
            return context.getExternalCacheDir().getAbsolutePath();
        else
            return getInternalCachePath(context);
    }

    /**
     * 获得外部存储中的files目录路径.
     *
     * @return 返回外部存储中的files目录路径；
     *         若获取为空，则返回外部存储中的files目录路径
     */
    public static String getExternalFilesPath(Context context) {
        File file = context.getExternalFilesDir("");
        if (file != null)
            return context.getExternalFilesDir("").getAbsolutePath();
        else
            return getInternalFilesPath(context);
    }

    /**
     * 从指定的目录获取File数组.
     *
     * @param directory 指定的目录
     * @param parentPath 指定的根目录
     * @return 若指定的确为目录则返回目录下所有文件的File数组，否则返回null
     * @see #getInternalCachePath(Context)
     * @see #getInternalFilesPath(Context)
     * @see #getExternalCachePath(Context)
     * @see #getExternalFilesPath(Context)
     */
    public static File[] getFilesFromDirectory(String directory, String parentPath) {
        if (!TextUtils.isEmpty(directory)) {
            if (directory.lastIndexOf('/') == directory.length() - 1)// 若末尾有'/'，则去除
                directory = directory.substring(0, directory.length() - 1);
            if (directory.indexOf('/') == 0)
                parentPath += directory;
            else
                parentPath += ("/" + directory);
        }
        File file = new File(parentPath);
        if (file.exists() && file.isDirectory()) {
            return file.listFiles();
        } else {
            return null;
        }
    }

    /**
     * 从指定的目录下获取所有文件的文件名.
     *
     * @param directory 指定的目录
     * @param parentPath 指定的根目录
     * @return 若指定的确为目录则返回目录下所有文件的文件名，否则返回null
     * @see #getInternalCachePath(Context)
     * @see #getInternalFilesPath(Context)
     * @see #getExternalCachePath(Context)
     * @see #getExternalFilesPath(Context)
     */
    public static String[] getFilenamesFromDirectory(String directory, String parentPath) {
        if (!TextUtils.isEmpty(directory)) {
            if (directory.lastIndexOf('/') == directory.length() - 1)// 若末尾有'/'，则去除
                directory = directory.substring(0, directory.length() - 1);
            if (directory.indexOf('/') == 0)
                parentPath += directory;
            else
                parentPath += ("/" + directory);
        }
        File file = new File(parentPath);
        if (file.exists() && file.isDirectory()) {
            return file.list();
        } else {
            return null;
        }
    }

    /**
     * 删除指定的文件.
     * 若为目录，则目录下的文件也将被删除.
     *
     * @param file 待删除的文件
     */
    public static void deleteFile(File file) {
        try {
            if (file != null && file.exists()) {
                if (file.isDirectory()) {
                    File[] files = file.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            deleteFile(f);
                        }
                    }
                }
                if (!file.delete()) {
                    myLog.v("删除文件失败: " + file.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    //========================图片=========================
    /**
     * 获取合适大小的Bitmap图片数据.
     *
     * @param srcBitmap 源Bitmap图片
     * @return 若获取图片的byte数组成功且成功压缩则返回大小在500KB（含）以内的图片的byte数组，否则返回null
     */
    public static byte[] getRightDataFromBitmap(Bitmap srcBitmap) {
        int maxBytes = 500 * 1024;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            int quality = 100;
            while (true) {
                if (srcBitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)) {
                    byte[] result = bos.toByteArray();
                    if (result.length <= maxBytes) {
                        return result;
                    } else {
                        if (quality == 100) {
                            quality = (int) (100d * maxBytes / result.length);
                            bos.reset();
                            myLog.v("第二次压缩");
                        } else {
                            return null;
                        }
                    }
                } else {
                    return null;
                }
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 将头像存储至文件.
     *
     * @param context 上下文
     * @param picData 储存头像数据的字节数组
     * @param member 头像对应的成员
     */
    public static void savePhotoFromBytes(Context context, byte[] picData, Member member) {
        if (member.useDefaultPhoto()) {
            return;
        }
        String directory = getExternalCachePath(context) + FixedValue.photoDirectory;
        File dir = new File(directory);
        if (dir.exists() || dir.mkdirs()) {
            File pic = new File(directory, member.getPhotoName());
            try (FileOutputStream fos = new FileOutputStream(pic)) {
                fos.write(picData);
            } catch (IOException e) {
                // ignore
            }
        }
    }

    //========================其它=========================
    /**
     * 进行sha3_256加密.
     *
     * @param data 待加密数据
     * @return 加密结果
     */
    public static String getKeccak512Encrypt(String data) {
        if (data == null) {
            myLog.w("Keccak512加密的数据为空");
            return null;
        }
        SHA3_512 keccak512 = new SHA3_512();
        return toHexString(keccak512.digest(data.getBytes()));
    }

    /**
     * 获取通过MD5加密后的结果.
     *
     * @param data 原始数据
     * @return 加密结果
     */
    public static String getMD5(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return toHexString(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // ignore
            return data;
        }
    }

    private static String toHexString(byte[] data) {
        StringBuilder strHexString = new StringBuilder();
        for (byte aByte : data) {
            String hex = Integer.toHexString(0xff & aByte);
            if (hex.length() == 1) {
                strHexString.append('0');
            }
            strHexString.append(hex);
        }
        return strHexString.toString();
    }

    /**
     * 将Json字符串转换为List集合.
     *
     * @param json Json字符串
     * @param eClass 集合的元素类型
     * @return 转换后的List集合
     */
    public static <E> List<E> getListFromJson(String json, Class<E> eClass) {
        try {
            List<E> list = new LinkedList<>();
            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                list.add(gson.fromJson(String.valueOf(jsonArray.getJSONObject(i)), eClass));
            }
            return list;
        } catch (Exception e) {
            myLog.e("解析Json为List结构发生异常", e);
            return null;
        }
    }

    /**
     * 从Json字符串中解析出Java对象.
     *
     * @param json Json字符串
     * @param tClass 对象的类型
     * @return 解析出的Java对象
     */
    public static <T> T getObjectFromJson(String json, Class<T> tClass) {
        return gson.fromJson(json, tClass);
    }

    /**
     * 从Json字符串解析出Map对象.
     *
     * @param mapJson Json字符串
     * @param <K> Map的键的类型
     * @param <V> Map的值的类型
     * @return 返回Map对象，若发生异常则返回null
     */
    public static <K, V> Map<K, V> getMapFromJson(String mapJson) {
        try {
            return gson.fromJson(mapJson, new TypeToken<Map<K, V>>() {}.getType());
        } catch (Exception e) {
            myLog.e("解析Json为Map结构发生异常", e);
            return null;
        }
    }

    /**
     * 保存签到数据缓存.
     *
     * @param context 上下文
     * @param signInActivity 签到活动
     * @param signInData 签到活动的签到数据
     */
    public static File saveSignInData(Context context, SignInActivity signInActivity, List<SignInInfo> signInData) {
        File file = generateFileAtCache(context, FixedValue.signInDataDirectory,
                Base64.encodeToString(signInActivity.getTitle().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP), true);
        return saveObjectAtFile(file, signInData);
    }

    /**
     * 将签到数据导出至文件.
     *
     * @param context 上下文
     * @param activityTitle 签到活动标题
     * @param signInInfoList 签到数据
     * @return 所存储文件的引用
     */
    public static File exportSignInData(Context context, String activityTitle, List<SignInInfo> signInInfoList) {
        File file = generateFileAtFiles(context, FixedValue.exportSignInDirectory, getNowTime() + ".txt", true);
        StringBuilder data = new StringBuilder("签到活动标题：");
        data.append(activityTitle);
        data.append("（若导入到Excel请删除该行）\n学号\t姓名\t签到状态\n");
        for (SignInInfo signInInfo : signInInfoList) {
            data.append(signInInfo.getNumber());
            data.append("\t");
            data.append(signInInfo.getName());
            data.append("\t");
            switch (signInInfo.getSignInStatus()) {
                case 0:
                    data.append("未签到");
                    break;
                case 1:
                    data.append("已签到");
                    break;
                case 2:
                    data.append("已迟到");
                    break;
            }
            data.append("\n");
        }
        return saveFile(file, String.valueOf(data), false);
    }

    /**
     * 获取对象的Json字符串.
     *
     * @param object 转换的对象
     * @return 转换结果
     */
    public static <T> String getJson(T object) {
        return gson.toJson(object);
    }

    /**
     * 判断设置的密码是否符合要求.
     *       1.长度在 6 - 16 位
     *       2.只能由数字、大小写字母、特殊字符（仅短横和下划线）四种类型组成
     *       3.至少包含两种类型
     *
     * @param password 需要检查规范的密码字符串
     * @param context 上下文
     * @return true表示符合规范；false表示不符合规范
     */
    public static boolean availablePassword(String password, Context context) {
        int len = password.length();
        if (len < 6 || len > 16) {
            Toast.makeText(context, "密码长度不在限定范围", Toast.LENGTH_SHORT).show();
            return false;
        }
        boolean[] typeNo = new boolean[4];// 数字，大写字母，小写字母，特殊符号
        Arrays.fill(typeNo, true);// true表示没有对应的类型
        int typeCount = 0;
        for (int i = 0; i < len; i++) {
            char ch = password.charAt(i);
            if (ch >= '0' && ch <= '9') {
                if (typeNo[0]) {
                    typeNo[0] = false;
                    typeCount++;
                }
                continue;
            } else if (ch >= 'A' && ch <= 'Z') {
                if (typeNo[1]) {
                    typeNo[1] = false;
                    typeCount++;
                }
                continue;
            } else if (ch >= 'a' && ch <= 'z') {
                if (typeNo[2]) {
                    typeNo[2] = false;
                    typeCount++;
                }
                continue;
            } else if (ch == '-' || ch == '_') {
                if (typeNo[3]) {
                    typeNo[3] = false;
                    typeCount++;
                }
                continue;
            }
            Toast.makeText(context, "密码只能由数字、大小写字母、短横和下划线组成", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (typeCount < 2) {
            Toast.makeText(context, "密码至少由两种类型组成", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * 启动首页轮播任务.
     *
     * @param task 轮播任务
     */
    public static void startCarouselTimer(TimerTask task) {
        if (carouselTimer == null) {
            carouselTimer = new Timer();
            carouselTimer.schedule(task, FixedValue.carouselIntervals, FixedValue.carouselIntervals);
        }
    }

    /**
     * 当memberList刷新时调用(非登录时).
     *
     * @param membersJson 包含memberList的json字符串
     */
    public static boolean refreshMemberList(Context context, String membersJson) {
        if (my == null) {
            return false;
        }
        List<Member> tempMembers = getListFromJson(membersJson, Member.class);
        if (tempMembers == null) {
            return false;
        } else {
            memberList = tempMembers;
            sortMemberList();
            setAdministrators();
            boolean result = setMy(context, my.getDepartment(), my.getAccount());
            new Server.SetEnableThread(null).start();
            return result;
        }
    }

    /**
     * 为成员列表排序.
     *
     * 将memberList按身份降序排序，身份相同则按姓名字典升序排序
     * 将memberCount按积分降序排序，若积分相同则按姓名字典升序排序.
     */
    public static void sortMemberList() {
        if (memberList != null) {
            Collections.sort(memberList, new Comparator<Member>() {
                @Override
                public int compare(Member o1, Member o2) {
                    if (o1.getFlag().contains("3")) {// o1是部长
                        return -1;
                    } else if (o2.getFlag().contains("3")) {// o2是部长
                        return 1;
                    } else {
                        boolean o1Flag2 = o1.getFlag().contains("2");
                        boolean o2Flag2 = o2.getFlag().contains("2");
                        if (o1Flag2 && o2Flag2) {// 都是副部长
                            return o1.getName().compareTo(o2.getName());
                        } else if (o1Flag2) {// o1是副部长
                            return -1;
                        } else if (o2Flag2) {// o2是副部长
                            return 1;
                        } else {
                            boolean o1Flag1 = o1.getFlag().contains("1");
                            boolean o2Flag1 = o2.getFlag().contains("1");
                            if (o1Flag1 && o2Flag1) {// 都是组长
                                return o1.getName().compareTo(o2.getName());
                            } else if (o1Flag1) {// o1是组长
                                return -1;
                            } else if (o2Flag1) {// o2是组长
                                return 1;
                            } else {// 都是部员
                                return o1.getName().compareTo(o2.getName());
                            }
                        }
                    }
                }
            });
            memberCount = new LinkedList<>(memberList);
            Collections.sort(memberCount, new Comparator<Member>() {
                @Override
                public int compare(Member o1, Member o2) {
                    int compare = Integer.compare(o2.getCount(), o1.getCount());
                    if (compare == 0) {
                        compare = o1.getName().compareTo(o2.getName());
                    }
                    return compare;
                }
            });
        }
    }

    /**
     * 为登录者设置数据.
     *
     * @param department 登录者部门
     * @param account 登录者账号
     * @return 设置成功则返回true
     */
    public static boolean setMy(Context context, DepartmentEnum department, String account) {
        for (Member member : memberList) {
            if (member.getNumber().equals(account)) {
                my = new My(member, department);
                Tools.saveSharedPreferences(context, FixedValue.LoginCfg, FixedValue.headPhoto, Tools.my.getPhotoName());
                return true;
            }
        }
        return false;
    }

    /**
     * 添加部门部长副部长信息.
     */
    public static void setAdministrators() {
        administrators.clear();
        for (Member member : memberList) {
            if (member.isAdministrator()) {
                administrators.add(member);
            }
        }
    }

    /**
     * 优化页面跳转动画(?).
     * 是否保留待定.
     */
    @Deprecated
    public static Bundle getAnimationBundle(AppCompatActivity activity) {
        return ActivityOptions.makeSceneTransitionAnimation(activity).toBundle();
    }

    //======================内部类=========================
    /**
     * 枚举六个部门，每个部门都有其全称与简称.
     */
    public enum DepartmentEnum implements Serializable {
        SOFTWARE("软研部", "软件研发部", R.drawable.ic_software_true),
        NETWORK("网络部", "网络部", R.drawable.ic_network_true),
        ELECTRON("电子部", "电子部", R.drawable.ic_electron_true),
        OFFICE("办公室", "办公中心", R.drawable.ic_office_true),
        PUBLICITY("科宣部", "科宣部", R.drawable.ic_publicity_true),
        BUSINESS("商务部", "商务部", R.drawable.ic_business_true);

        private static final long serialVersionUID = 202011052018L;
        private final String simpleName;
        private final String wholeName;
        private final int iconId;

        DepartmentEnum(String simpleName, String wholeName, int iconId) {
            this.simpleName = simpleName;
            this.wholeName = wholeName;
            this.iconId = iconId;
        }

        public String getSimpleName() {
            return simpleName;
        }

        public String getWholeName() {
            return wholeName;
        }

        public int getIconId() {
            return iconId;
        }
    }

    /**
     * 登陆者信息.
     */
    public static class My {
        private final Member memberInfo;// 作为部门成员的信息
        private final DepartmentEnum department;// 部门
        private final boolean permission;
        public boolean infoEnable = false;
        public boolean mailEnable = false;
        public String mail = "";

        private My(Member memberInfo, DepartmentEnum department) {
            this.memberInfo = memberInfo;
            this.department = department;
            permission = memberInfo.getFlag().contains("2"/*副部长*/) || memberInfo.getFlag().contains("3"/*部长*/);
        }

        /**
         * 通过flag简单判断是否具有部长副部长权限.
         *
         * @return 有权限则返回true，否则返回false
         */
        public boolean hasPermission() {
            return permission;
        }

        public DepartmentEnum getDepartment() {
            return department;
        }

        public String getName() {
            return memberInfo.getName();
        }

        public String getAccount() {
            return memberInfo.getNumber();
        }

        public String getSubject() {
            return memberInfo.getSubject();
        }

        public String getPhone() {
            return memberInfo.getPhone();
        }

        public String getTeacher() {
            return memberInfo.getTeacher();
        }

        public String getQQ() {
            return memberInfo.getQQ();
        }

        public String getSex() {
            return memberInfo.getSex();
        }

        public int getCount() {
            return memberInfo.getCount();
        }

        public String[] getFlag() {
            return memberInfo.getFlagArray();
        }

        public String getPhotoName() {
            return memberInfo.getPhotoName();
        }

        public Member getMemberInfo() {
            return memberInfo;
        }

        /**
         * 获取身份信息.
         * 多个身份以"/"连接.
         *
         * @return 身份信息
         */
        public String getIdentity() {
            return memberInfo.getIdentity();
        }

        /**
         * 为ImageView及其子类的对象设置头像.
         *
         * @param context 上下文
         * @param imageView 被设置的ImageView对象
         */
        public void settingHeadPhoto(Context context, ImageView imageView) {
            memberInfo.settingHeadPhoto(context, imageView);
        }

        @Override
        public String toString() {
            return "My {" +
                    "部门:" + department.simpleName +
                    ", 账号(学号):" + memberInfo.getNumber() +
                    ", 姓名:" + memberInfo.getName() +
                    ", 性别:" + memberInfo.getSex() +
                    ", 专业:" + memberInfo.getSubject() +
                    ", 辅导员:" + memberInfo.getTeacher() +
                    ", QQ:" + memberInfo.getQQ() +
                    ", 电话:" + memberInfo.getPhone() +
                    ", 部门积分:" + memberInfo.getCount() +
                    ", 身份标识:" + Arrays.toString(memberInfo.getFlagArray()) +
                    '}';
        }
    }
}
