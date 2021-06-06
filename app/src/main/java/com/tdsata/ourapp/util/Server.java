package com.tdsata.ourapp.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkRequest;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.tdsata.ourapp.entity.Member;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.tdsata.ourapp.util.FixedValue.address;
import static com.tdsata.ourapp.util.FixedValue.rsaUpdateTime;

/**
 * 与服务器进行交互.
 * 在该类中，已将各与服务器交互的部分进行了包装，并提供了接口以供调用.
 * 详细介绍可查看对应方法的Doc文档.
 *
 * @author MingZ266
 * @version 2
 */
public class Server {
    private static final MyLog myLog = new MyLog("LocalTAG");
    private static final Gson gson = new Gson();
    private static final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.SECONDS)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build();// 部分通用网络请求超时设置

    //*****************初始化连接******************
    private static ConnectivityManager connectivityManager = null;// 用于监听网络状态
    private static InitConnectionThread initConnectionThread = null;// 当前正在运行的初始化连接线程

    /**
     * 初始化与服务器的连接.
     * 获取服务器当前的RSA公钥，若未能成功获取，将重复执行获取任务直至成功.
     */
    public static void initConnection(AppCompatActivity activity, InitConnectionInterface initConnectionInterface) {
        connectivityManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        initConnectionThread = new InitConnectionThread(initConnectionInterface);
        initConnectionThread.start();
    }

    /**
     * 将初始化连接中的接口方法体全部置空.
     * 避免在启动页已跳转后因接口方法调用发生跳转或产生异常.
     */
    public static void setNullInterfaceOnInitConnection() {
        if (initConnectionThread != null) {
            initConnectionThread.setNullInitConnectionInterface();
        }
    }

    /**
     * 检查初始化连接是否成功.
     *
     * @return 初始化连接成功则返回true，否则返回false
     */
    public static boolean initConnectionSuccess() {
        return publicKey != null;
    }

    /**
     * 用于当服务器端RSA公钥失去同步(发生AES_KEY_ERROR)时进行同步.
     */
    public static void startInitConnectionThread() {
        if (connectivityManager != null && initConnectionThread == null) {
            new InitConnectionThread(null).start();
        }
    }

    //******************服务请求*******************
    /**
     * 向服务器发送POST网络请求.
     *
     * @param TAG 网络请求目的的区分标识
     * @param theAPI 请求的API名称
     * @param endOfRequestOnUiThread 请求的回调
     * @param department 基础信息参数 --> 部门，若为空则忽略
     * @param account 基础信息参数 --> 账号，若为空则忽略
     * @param otherKeyAndValue 其它信息参数，以key, value, key, value, ...格式传入；为空或个数不为偶数将被忽略
     */
    private static void sendPostRequest(final String TAG, String theAPI, final EndOfRequestOnUiThread endOfRequestOnUiThread, Tools.DepartmentEnum department, String account, String... otherKeyAndValue) {
        boolean allNotNull = true;
        String departmentJson = null;
        if (department != null) {
            departmentJson = aesEncryptData(gson.toJson(department));
            allNotNull = departmentJson != null;
        }
        if (account != null) {
            account = aesEncryptData(account);
            allNotNull = allNotNull && account != null;
        }
        if (otherKeyAndValue != null && otherKeyAndValue.length % 2 == 0 && allNotNull) {
            for (int i = 1; i < otherKeyAndValue.length; i += 2) {
                otherKeyAndValue[i] = aesEncryptData(otherKeyAndValue[i]);
                if (otherKeyAndValue[i] == null) {
                    allNotNull = false;
                    break;
                }
            }
        }
        if (allNotNull) {
            FormBody.Builder requestBuilder = new FormBody.Builder()
                    .add("aesKeyStr", String.valueOf(encryptAESKey))
                    .add("verifyCiphertext", verifyCiphertext);
            if (departmentJson != null) {
                requestBuilder.add("departmentJson", departmentJson);
            }
            if (account != null) {
                requestBuilder.add("account", account);
            }
            if (otherKeyAndValue != null) {
                for (int i = 0; i < otherKeyAndValue.length; i += 2) {
                    requestBuilder.add(otherKeyAndValue[i], otherKeyAndValue[i + 1]);
                }
            }
            Request request = new Request.Builder()
                    .url(address + theAPI)
                    .post(requestBuilder.build())
                    .build();
            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    call.cancel();
                    if (e instanceof SocketTimeoutException) {
                        endOfRequestOnUiThread.onTimeout();
                    } else {
                        endOfRequestOnUiThread.noNet(e);
                    }
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    ResponseBody body = response.body();
                    if (body != null) {
                        endOfRequestOnUiThread.onSuccess(body.string());
                    } else {
                        endOfRequestOnUiThread.onException(TAG + " ==> 请求返回结果为空");
                    }
                }
            });
        } else {
            endOfRequestOnUiThread.onException(TAG + " ==> 加密数据后为空");
        }
    }

    /**
     * 用户登录.
     *
     * @param department 部门
     * @param account 账号
     * @param password 已经使用Keccak512加密后的密码
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void login(Tools.DepartmentEnum department, String account, String password, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("登录", "login", new EndOfRequestOnUiThread(activity, endOfRequest), department, account, "password", password);
    }

    /**
     * 刷新memberList.
     *
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void refreshMembers(AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("刷新memberList", "refreshMembers", new EndOfRequestOnUiThread(activity, endOfRequest), Tools.my.getDepartment(), null);
    }

    /**
     * 请求发送验证码邮件.
     *
     * @param department 部门
     * @param account 账号
     * @param email 接收邮件的电子邮箱
     * @param activity 调用该方法的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void requestMailCode(Tools.DepartmentEnum department, String account, String email, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("请求发送邮件验证码", "getMailCode", new EndOfRequestOnUiThread(activity, endOfRequest), department, account, "useEmail", email);
    }

    /**
     * 请求验证邮件验证码.
     *
     * @param department 部门
     * @param account 账号
     * @param inputMailCode 输入的邮件验证码
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void verifyMailCode(Tools.DepartmentEnum department, String account, String inputMailCode, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("验证邮件验证码", "verifyMailCode", new EndOfRequestOnUiThread(activity, endOfRequest), department, account, "inputMailCode", inputMailCode);
    }

    /**
     * 修改账号对应的密码.
     * 该方法仅在通过邮件验证码后有效.
     *
     * @param department 部门
     * @param account 账号
     * @param newPassword 已经通过Keccak512加密后的新密码
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void alterPasswordUseMail(Tools.DepartmentEnum department, String account, String newPassword, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("通过邮件验证码修改密码", "alterPasswordUseMail", new EndOfRequestOnUiThread(activity, endOfRequest), department, account, "newPassword", newPassword);
    }

    /**
     * 添加签到活动.
     * 服务器端API会核实登陆者部门部长副部长身份.
     *
     * @param activityTitle 签到活动的标题，30字（含）以内
     * @param signInTime 签到开始时间（yyyy-MM-dd HH:mm）
     * @param continueTime 签到持续时间（单位：分钟）
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void addSignInActivity(String activityTitle, String signInTime, String continueTime, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("添加签到活动", "addSignInActivity", new EndOfRequestOnUiThread(activity, endOfRequest), Tools.my.getDepartment(), Tools.my.getAccount(),
                "activityTitle", activityTitle, "signInTime", signInTime, "continueTime", continueTime);
    }

    /**
     * 删除多个签到活动.
     * 服务器端API会核实登陆者部门部长副部长身份.
     *
     * @param activityTitles 签到活动标题数组
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void delSignInActivities(String[] activityTitles, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("删除多个签到活动", "delSignInActivities", new EndOfRequestOnUiThread(activity, endOfRequest),
                Tools.my.getDepartment(), Tools.my.getAccount(), "activityTitlesJson", gson.toJson(activityTitles));
    }

    /**
     * 设置多个部员的签到状态.
     * 服务器端API会核实登陆者部门部长副部长身份.
     *
     * @param activityTitle 签到活动标题
     * @param multipleSignInNumber 多个签到部员的学号
     * @param multipleSignInStatus 多个签到部员的签到状态，与学号一一对应
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void setMultipleSignInStatus(String activityTitle, String[] multipleSignInNumber, Integer[] multipleSignInStatus, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("设置多个部员的签到状态", "setMultipleSignInStatus", new EndOfRequestOnUiThread(activity, endOfRequest), Tools.my.getDepartment(), Tools.my.getAccount(),
                "activityTitle", activityTitle, "multipleSignInNumberJson", gson.toJson(multipleSignInNumber), "multipleSignInStatusJson", gson.toJson(multipleSignInStatus));
    }

    /**
     * 获得指定签到活动部员签到状态列表.
     *
     * @param activityTitle 指定的签到活动标题
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void getSignInStatusList(String activityTitle, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("获得签到活动部员签到状态列表", "getSignInStatusList", new EndOfRequestOnUiThread(activity, endOfRequest), Tools.my.getDepartment(), null,
                "activityTitle", activityTitle);
    }

    /**
     * 获取在指定的签到活动中的签到状态.
     *
     * @param activityTitle 活动标题
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void getSignInStatus(String activityTitle, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("获取在指定的签到活动中的签到状态", "getSignInStatus", new EndOfRequestOnUiThread(activity, endOfRequest),
                Tools.my.getDepartment(), Tools.my.getAccount(), "activityTitle", activityTitle);
    }

    /**
     * 获得签到活动列表.
     *
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void getSignInActivities(AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("获得签到活动列表", "getSignInActivities", new EndOfRequestOnUiThread(activity, endOfRequest),
                Tools.my.getDepartment(), null);
    }

    /**
     * 通过常规方式修改密码.
     *
     * @param oldPassword 已经通过Keccak512加密后的旧密码
     * @param newPassword 已经通过Keccak512加密后的新密码
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void alterPassword(String oldPassword, String newPassword, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("修改密码", "alterPassword", new EndOfRequestOnUiThread(activity, endOfRequest), Tools.my.getDepartment(),
                Tools.my.getAccount(), "oldPassword", oldPassword, "newPassword", newPassword);
    }

    /**
     * 上传头像.
     *
     * @param photoData 储存头像数据的字节数组
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void uploadHeadPhoto(byte[] photoData, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("上传头像", "uploadHeadPhoto", new EndOfRequestOnUiThread(activity, endOfRequest), Tools.my.getDepartment(),
                Tools.my.getAccount(), "photoText", new String(Base64.encode(photoData, Base64.NO_WRAP), StandardCharsets.UTF_8));
    }

    /**
     * 获取多个成员的头像.
     *
     * @param number 需要获取头像的学号组成的数组
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void getHeadPhotos(String[] number, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("获取多个成员的头像", "getHeadPhotos", new EndOfRequestOnUiThread(activity, endOfRequest),
                Tools.my.getDepartment(), null, "numbersJson", gson.toJson(number));

    }

    /**
     * 添加公告.
     * 服务器端API会核实登陆者部门部长副部长身份.
     *
     * @param message 公告内容（限200字）
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void addAnnouncement(String message, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("添加公告", "addAnnouncement", new EndOfRequestOnUiThread(activity, endOfRequest),
                Tools.my.getDepartment(), Tools.my.getAccount(), "message", message);
    }

    /**
     * 删除公告.
     * 服务器端API会核实登陆者部门部长副部长身份.
     *
     * @param id 公告id
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void delAnnouncement(String id, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("删除公告", "delAnnouncement", new EndOfRequestOnUiThread(activity, endOfRequest),
                Tools.my.getDepartment(), Tools.my.getAccount(), "id", id);
    }

    /**
     * 获取公告列表.
     *
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void getAnnouncementList(AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("获取公告列表", "getAnnouncementList", new EndOfRequestOnUiThread(activity, endOfRequest),
                Tools.my.getDepartment(), null);
    }

    /**
     * 修改积分.
     * 服务器端API会核实登陆者部门部长副部长身份.
     *
     * @param toNumber 被修改者学号
     * @param changeValue 修改的值（有符号整型）
     * @param description 积分变动的描述
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void changeCount(String toNumber, int changeValue, String description, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("修改积分", "changeCount", new EndOfRequestOnUiThread(activity, endOfRequest), Tools.my.getDepartment(), Tools.my.getAccount(),
                "toNumber", toNumber, "changeValue", String.valueOf(changeValue), "description", description);
    }

    /**
     * 获取积分修改记录.
     *
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void getChangeCountHistory(AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("获取积分修改记录", "getChangeCountHistory", new EndOfRequestOnUiThread(activity, endOfRequest),
                Tools.my.getDepartment(), Tools.my.getAccount());
    }

    /**
     * 修改部门介绍.
     * 服务器端API会核实登陆者部门部长副部长身份.
     *
     * @param info 新的部门介绍（限500字）
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void addDepartmentInfo(String info, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("修改部门介绍", "addDepartmentInfo", new EndOfRequestOnUiThread(activity, endOfRequest),
                Tools.my.getDepartment(), Tools.my.getAccount(), "info", info);
    }

    /**
     * 获取部门介绍.
     *
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void getDepartmentInfo(AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("获取部门介绍", "getDepartmentInfo", new EndOfRequestOnUiThread(activity, endOfRequest), Tools.my.getDepartment(), null);
    }

    /**
     * 上传更新个人信息.
     *
     * @param subject 专业
     * @param sex 性别
     * @param phone 电话
     * @param qq QQ
     * @param teacher 辅导员
     * @param email 电子邮件（可为空字符串）
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void uploadPersonalInfo(String subject, String sex, String phone, String qq, String teacher,
                                          String email, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("上传个人信息", "uploadPersonalInfo", new EndOfRequestOnUiThread(activity, endOfRequest), Tools.my.getDepartment(),
                Tools.my.getAccount(), "subject", subject, "sex", sex, "phone", phone, "qq", qq, "teacher", teacher, "email", email);
    }

    /**
     * 发送验证电子邮件的邮件.
     *
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void sendVerifyEmailMail(AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("发送验证电子邮件的邮件", "sendVerifyEmailMail", new EndOfRequestOnUiThread(activity, endOfRequest),
                Tools.my.getDepartment(), Tools.my.getAccount());
    }

    /**
     * 添加部门成员.
     * 服务器端API会核实登陆者部门部长副部长身份.
     *
     * @param addNumber 待添加的成员学号
     * @param addName 待添加的成员姓名
     * @param addFlags 待添加的成员身份标识数组
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void addMember(String addNumber, String addName, String[] addFlags, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("添加部门成员", "addMember", new EndOfRequestOnUiThread(activity, endOfRequest), Tools.my.getDepartment(), Tools.my.getAccount(),
                "number", addNumber, "name", addName, "flagsJson", gson.toJson(addFlags));
    }

    /**
     * 删除部门成员.
     * 服务器端API会核实登陆者部门部长副部长身份.
     *
     * @param delNumber 待删除的成员的学号
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void delMember(String delNumber, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("移除部门成员", "delMember", new EndOfRequestOnUiThread(activity, endOfRequest),
                Tools.my.getDepartment(), Tools.my.getAccount(), "number", delNumber);
    }

    /**
     * 更新部门成员信息.
     * 服务器端API会核实登陆者部门部长副部长身份.
     *
     * @param oldNumber 需要更新的成员的旧学号
     * @param newNumber 更新后的成员的新学号
     * @param newName 更新后的成员的新姓名
     * @param newFlags 更新后的成员的新身份标识数组
     * @param activity 调用该方法所在的上下文引用
     * @param endOfRequest 运行在UI线程
     */
    public static void updateMember(String oldNumber, String newNumber, String newName, String[] newFlags, AppCompatActivity activity, EndOfRequest endOfRequest) {
        sendPostRequest("更新部门成员信息", "updateMember", new EndOfRequestOnUiThread(activity, endOfRequest), Tools.my.getDepartment(), Tools.my.getAccount(),
                "oldNumber", oldNumber, "number", newNumber, "name", newName, "flagsJson", gson.toJson(newFlags));
    }

    //******************加密传输*******************
    private static String verifyCiphertext = null;// 使用AES密钥加密后的“TD-SATA”校验密文
    private static volatile String encryptAESKey = null;// 使用服务器端RSA公钥加密的AES密钥
    //====================AES=====================
    private static Key aesKey = null;// AES密钥
    private static byte[] aesKeyBytes = null;// AES密钥字节数组

    /**
     * 生成AES密钥.
     */
    public static void generateAESKey() {
        try {
            KeyGenerator aes = KeyGenerator.getInstance("AES");
            aes.init(256);
            aesKeyBytes = aes.generateKey().getEncoded();
            aesKey = new SecretKeySpec(aesKeyBytes, "AES");
            verifyCiphertext = aesEncryptData("TD-SATA");
        } catch (Exception e) {
            myLog.e("生成AES密钥失败", e);
        }
    }

    /**
     * 使用AES密钥加密数据.
     *
     * @param data 待加密的数据
     * @return 加密后的密文，使用Base64编码
     */
    @SuppressLint("GetInstance")
    private static String aesEncryptData(String data) {
        try {
            if (aesKey == null)
                throw new NullPointerException("AES密钥为空");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
            return new String(Base64.encode(cipher.doFinal(data.getBytes()), Base64.NO_WRAP), StandardCharsets.UTF_8);
        } catch (Exception e) {
            myLog.e( "AES加密数据失败", e);
            return null;
        }
    }

    /**
     * 使用AES密钥解密数据.
     *
     * @param ciphertext 待解密的使用Base64编码的密文
     * @return 解密后的数据
     */
    @SuppressLint("GetInstance")
    public static String aesDecryptData(String ciphertext) {
        try {
            if (aesKey == null)
                throw new NullPointerException("AES密钥为空");
            if (ciphertext == null)
                throw new NullPointerException("密文为空");
            byte[] decodeCiphertextBytes = Base64.decode(ciphertext.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, aesKey);
            return new String(cipher.doFinal(decodeCiphertextBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            myLog.e("AES解密数据失败", e);
            return null;
        }
    }

    //====================RSA=====================
    private static volatile boolean isSynchronize = false;// 表示RSA公钥的更新时间间隔与服务器同步
    private static PublicKey publicKey = null;// RSA公钥

    /**
     * 解码RSA公钥字符串以构造RSA公钥.
     */
    private static void revertRSAPublicKey(String rsaPublicKeyStr) {
        try {
            byte[] rsaPublicKeyBytes = Base64.decode(rsaPublicKeyStr.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(rsaPublicKeyBytes));
            encryptAESKey = getEncryptAESKey();
        } catch (Exception e) {
            myLog.e("还原RSA公钥失败", e);
        }
    }

    /**
     * 使用RSA公钥加密AES密钥.
     *
     * @return 以Base64编码的加密后的AES密钥字符串
     */
    private static String getEncryptAESKey() {
        try {
            if (publicKey == null) {
                throw new NullPointerException("RSA公钥为空");
            }
            if (aesKeyBytes == null) {
                throw new NullPointerException("AES密钥为空");
            }
            int rsaKeySize = 1024;// RSA密钥长度
            int maxLength = rsaKeySize / 8 - 11;
            int mod = aesKeyBytes.length % maxLength;
            int groupNum = aesKeyBytes.length / maxLength;
            if (mod != 0)
                groupNum++;
            byte[][] dataSrc = new byte[groupNum][0];
            for (int i = 0, start = 0; i < groupNum; i++, start += maxLength) {
                if (i != groupNum - 1 || mod == 0) {
                    dataSrc[i] = Arrays.copyOfRange(aesKeyBytes, start, start + maxLength);
                } else {
                    dataSrc[i] = Arrays.copyOfRange(aesKeyBytes, start, start + mod);
                }
            }
            byte[][] cache = new byte[dataSrc.length][0];
            byte[] result = new byte[0];
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            for (int i = 0, start = 0; i < dataSrc.length; i++) {
                cache[i] = cipher.doFinal(dataSrc[i]);
                result = Arrays.copyOf(result, result.length + cache[i].length);
                System.arraycopy(cache[i], 0, result, start, cache[i].length);
                start = cache[i].length;
            }
            return new String(Base64.encode(result, Base64.NO_WRAP), StandardCharsets.UTF_8);
        } catch (Exception e) {
            myLog.e("使用RSA公钥加密AES密钥失败", e);
            return null;
        }
    }

    //*******************内部类********************
    /**
     * 初始化与服务器端的连接的线程.
     */
    private static class InitConnectionThread extends Thread {
        private static volatile boolean connectException = false;// 发生ConnectException（多因网络未连接）
        private static boolean firstRun = true;// 第一次运行
        private static boolean unregisterRSA = true;// 没有注册过RSA更新的网络状态监听
        private volatile InitConnectionInterface initConnectionInterface;
        private final InitNetworkCallback initNetworkCallback;// 网络状态监听回调

        InitConnectionThread(InitConnectionInterface initConnectionInterface) {
            if (initConnectionInterface == null) {
                setNullInitConnectionInterface();
            } else {
                this.initConnectionInterface = initConnectionInterface;
            }
            initNetworkCallback = new InitNetworkCallback();
        }

        public void setNullInitConnectionInterface() {
            // 方法体全部为空
            this.initConnectionInterface = new InitConnectionInterface() {
                @Override
                public void onSuccess() {}

                @Override
                public void onFail() {}

                @Override
                public void onTimeout() {}

                @Override
                public void onException() {}
            };
        }

        @Override
        public void run() {
            // 注册网络状态监听
            connectivityManager.registerNetworkCallback(new NetworkRequest.Builder().build(), initNetworkCallback);
            Request request = new Request.Builder()
                    .url(address + "initConnection")
                    .build();
            OkHttpClient okHttpClient;
            if (firstRun) {
                firstRun = false;
                okHttpClient = new OkHttpClient.Builder()
                        .connectTimeout(3, TimeUnit.SECONDS)
                        .readTimeout(2, TimeUnit.SECONDS)
                        .build();
            } else {
                okHttpClient = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build();
            }
            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    call.cancel();
                    myLog.d("初始化连接请求失败", e);
                    if (e instanceof ConnectException) {// 网络未连接是触发之一
                        myLog.v("初始化连接连接异常", e);
                        connectException = true;
                        initConnectionInterface.onFail();
                    } else if (e instanceof SocketTimeoutException) {// 超时
                        myLog.d("因超时启用新初始化线程");
                        startNewInitThread();
                        initConnectionInterface.onTimeout();
                    } else {
                        myLog.e("初始化连接异常", e);
                        initConnectionInterface.onException();
                        try {
                            sleep(5000);// 避免短时间大量开启新线程
                        } catch (InterruptedException interruptedException) {
                            // ignore
                        }
                        startNewInitThread();
                    }
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    // 注销网络状态监听
                    connectivityManager.unregisterNetworkCallback(initNetworkCallback);
                    ResponseBody body = response.body();
                    if (body != null) {
                        revertRSAPublicKey(body.string());
                        isSynchronize = false;
                        if (unregisterRSA) {
                            unregisterRSA = false;
                            // 注册RSA实时更新网络状态监听，不会主动注销
                            connectivityManager.registerNetworkCallback(new NetworkRequest.Builder().build(), new UpdateRSAPublicKeyThread.RSANetworkCallback());
                        }
                        new UpdateRSAPublicKeyThread().start();
                        initConnectionThread = null;
                        initConnectionInterface.onSuccess();
                    } else {
                        myLog.w("初始化连接返回结果为空");
                        initConnectionInterface.onException();
                        try {
                            sleep(5000);// 避免短时间大量开启新线程
                        } catch (InterruptedException interruptedException) {
                            // ignore
                        }
                        initConnectionThread = new InitConnectionThread(initConnectionInterface);
                        initConnectionThread.start();
                    }
                }
            });
        }

        public void startNewInitThread() {
            // 注销网络状态监听
            connectivityManager.unregisterNetworkCallback(initNetworkCallback);
            initConnectionThread = new InitConnectionThread(initConnectionInterface);
            initConnectionThread.start();
        }

        private class InitNetworkCallback extends NetworkCallback {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                if (connectException) {
                    connectException = false;
                    myLog.d("因网络连接异常启用新初始化线程");
                    startNewInitThread();
                }
            }
        }
    }

    /**
     * 更新获取的RSA公钥的线程.
     */
    private static class UpdateRSAPublicKeyThread extends Thread {
        private static volatile boolean connectException = false;// 发生ConnectException（多因网络未连接）
        private static volatile long lastGetTime = 0L;// 上一次同步获取服务器端RSA公钥的时间

        @Override
        public void run() {
            Request request = new Request.Builder()
                    .url(address + "updateRSAPublicKey")
                    .build();
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(0, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.SECONDS)
                    .build();// 不会超时
            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    if (e instanceof SocketException) {// 网络连接可能断开
                        myLog.v("RSA实时更新连接异常", e);
                        connectException = true;
                    } else {
                        if (connectException) {
                            connectException = false;
                        }
                        myLog.e("RSA实时更新异常", e);
                        try {
                            sleep(10000);
                        } catch (InterruptedException interruptedException) {
                            // ignore
                        }
                        new UpdateRSAPublicKeyThread().start();
                    }
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    if (connectException) {
                        connectException = false;
                    }
                    ResponseBody body = response.body();
                    if (body != null) {
                        revertRSAPublicKey(body.string());
                        lastGetTime = System.currentTimeMillis();
                        isSynchronize = true;
                    } else {
                        myLog.w("实时更新RSA公钥返回结果为空");
                        try {
                            sleep(10000);
                        } catch (InterruptedException interruptedException) {
                            // ignore
                        }
                    }
                    new UpdateRSAPublicKeyThread().start();
                }
            });
        }

        public static class RSANetworkCallback extends NetworkCallback {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                if (connectException) {
                    if (isSynchronize && System.currentTimeMillis() - lastGetTime <= rsaUpdateTime - 10000) {
                        myLog.d("触发RSA更新线程重启");
                        new UpdateRSAPublicKeyThread().start();
                    } else {
                        myLog.d("启用初始化线程同步RSA更新");
                        InitConnectionThread.connectException = false;
                        InitConnectionThread.firstRun = false;
                        initConnectionThread = new InitConnectionThread(null);
                        initConnectionThread.start();
                    }
                }
            }
        }
    }

    /**
     * 对EndOfRequest接口的包装.
     * 在该类调用接口的同名方法将在UI线程中执行.
     */
    private static class EndOfRequestOnUiThread {
        private final AppCompatActivity activity;
        private final EndOfRequest endOfRequest;

        EndOfRequestOnUiThread(AppCompatActivity activity, EndOfRequest endOfRequest) {
            this.activity = activity;
            this.endOfRequest = endOfRequest;
        }

        /**
         * 当网络请求成功获得返回结果时调用.
         *
         * @param result 请求结果
         */
        void onSuccess(final String result) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    endOfRequest.onSuccess(result);
                }
            });
        }

        /**
         * 当网络请求发生异常时调用.
         * 该方法会将异常信息打印在日志中.
         *
         * @param errorInfo 对异常状况的描述信息
         */
        void onException(String errorInfo) {
            myLog.w(errorInfo);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    endOfRequest.onException();
                }
            });
        }

        /**
         * 当网络请求连接超时时调用.
         */
        void onTimeout() {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    endOfRequest.onTimeout();
                }
            });
        }

        /**
         * 当网络连接不可用时调用.
         */
        void noNet(IOException e) {
            myLog.d("连接异常", e);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    endOfRequest.noNet();
                }
            });
        }
    }

    /**
     * 刷新本地缓存的头像的线程.
     * 必须于登录成功后运行.
     */
    public static class RefreshLocalPhotoThread extends Thread {
        private final RefreshLocalPhotoInterface theInterface;
        private volatile static boolean isNotRunning = true;
        private final AppCompatActivity activity;
        private Map<String, Member> memberMap;// 需要下载或更新本地缓存头像的成员（键 - 学号；值 - 对应的Member对象）
        private Map<String, String> oldCaches;// 需要更新的缓存头像的文件名（键 - 学号；值 - 对应的缓存头像文件名）

        public RefreshLocalPhotoThread(AppCompatActivity activity, RefreshLocalPhotoInterface theInterface) {
            this.activity = activity;
            this.theInterface = theInterface;
            decodeLocalFile();
        }

        /**
         * 解析本地缓存头像以判断需要下载或更新的头像.
         */
        private void decodeLocalFile() {
            memberMap = new HashMap<>();
            oldCaches = new HashMap<>();
            Map<String, String> exists = new HashMap<>();// 本地应有的缓存头像（键 - 学号；值 - 缓存头像文件名应为的前8位）
            for (Member member : Tools.memberList) {
                if (!member.useDefaultPhoto()) {// 获取不使用默认头像的成员
                    try {
                        exists.put(member.getNumber(), Tools.getMD5(member.getNumber()).substring(0, 8));
                        memberMap.put(member.getNumber(), member);
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
            if (exists.isEmpty()) {// 均使用默认头像，不需下载（memberMap亦为空）
                return;
            }
            // 有成员在服务器上传了头像
            Set<String> keys = exists.keySet();// 不使用默认头像者的学号
            String[] filenames = Tools.getFilenamesFromDirectory(FixedValue.photoDirectory, Tools.getExternalCachePath(activity));
            if (filenames == null) {// 本地无缓存头像，下载全部不使用默认头像的成员的头像
                return;
            }
            // 本地有部分或全部的头像缓存
            // 定位文件
            // 缓存的头像文件名与服务器的头像文件名保持一致，
            // 长为16，前8位由学号生成，后8位由上传时间生成，不含格式后缀
            for (String filename : filenames) {
                if (filename.length() != 16 || filename.contains(".")) {
                    continue;
                }
                for (String key : keys) {
                    if (filename.startsWith(exists.get(key))) {// 在本地有缓存的头像的学号，检查是否需要更新
                        try {
                            if (filename.equals(Objects.requireNonNull(memberMap.get(key)).getPhotoName())) {// 不需要更新缓存的头像
                                memberMap.remove(key);
                            } else {// 需要更新
                                oldCaches.put(key, filename);
                            }
                        } catch (NullPointerException e) {
                            // ignore
                        }
                        keys.remove(key);
                        break;
                    }
                }
                if (keys.isEmpty()) {
                    break;
                }
            }
        }

        @Override
        public void run() {
            if (isNotRunning) {
                isNotRunning = false;
                if (!memberMap.isEmpty()) {
                    String[] numbers = memberMap.keySet().toArray(new String[0]);
                    getHeadPhotos(numbers, activity, new EndOfRequest() {
                        @Override
                        public void onSuccess(String result) {
                            switch (result) {
                                case "AES_KEY_ERROR":
                                    Server.startInitConnectionThread();
                                case "ERROR":
                                    onException();
                                    break;
                                default:
                                    if (!activity.isDestroyed()) {
                                        // 获得的头像（键 - 学号；值 - 将头像Base64编码后生成的UTF-8文本）
                                        Map<String, String> map = Tools.getMapFromJson(aesDecryptData(result));
                                        if (map != null) {
                                            Set<String> numbers = map.keySet();
                                            for (String number : numbers) {
                                                try {
                                                    // 缓存至本地
                                                    Tools.savePhotoFromBytes(activity,
                                                            Base64.decode(map.get(number).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP),
                                                            memberMap.get(number));
                                                    // 删除旧缓存
                                                    if (!oldCaches.isEmpty()) {
                                                        String filename;
                                                        if ((filename = oldCaches.get(number)) != null) {
                                                            Tools.deleteFile(Tools.generateFileAtCache(activity, FixedValue.photoDirectory, filename, true));
                                                        }
                                                    }
                                                } catch (Exception e) {
                                                    // ignore
                                                }
                                            }
                                        }
                                        if (theInterface != null) {
                                            activity.runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    theInterface.onComplete();
                                                }
                                            });
                                        }
                                    }
                                    isNotRunning = true;
                                    break;
                            }
                        }

                        @Override
                        public void onException() {
                            isNotRunning = true;
                        }

                        @Override
                        public void onTimeout() {
                            isNotRunning = true;
                        }

                        @Override
                        public void noNet() {
                            isNotRunning = true;
                        }
                    });
                } else {
                    if (theInterface != null) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                theInterface.onComplete();
                            }
                        });
                    }
                }
            }
        }
    }

    /**
     * 设置信息完善标识的线程.
     */
    public static class SetEnableThread extends Thread {
        private final SetEnableInterface setEnableInterface;

        public SetEnableThread(SetEnableInterface setEnableInterface) {
            this.setEnableInterface = setEnableInterface;
        }

        @Override
        public void run() {
            String departmentJson = aesEncryptData(gson.toJson(Tools.my.getDepartment()));
            String account = aesEncryptData(Tools.my.getAccount());
            if (departmentJson != null && account != null) {
                RequestBody requestBody = new FormBody.Builder()
                        .add("aesKeyStr", String.valueOf(encryptAESKey))
                        .add("verifyCiphertext", verifyCiphertext)
                        .add("departmentJson", departmentJson)
                        .add("account", account)
                        .build();
                Request request = new Request.Builder()
                        .url(address + "getEnable")
                        .post(requestBody)
                        .build();
                okHttpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NotNull Call call, @NotNull IOException e) {}

                    @Override
                    public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                        ResponseBody body = response.body();
                        if (body != null) {
                            String result = body.string();
                            if (!(result.equals("AES_KEY_ERROR") || result.equals("ERROR"))) {
                                Map<String, String> map = Tools.getMapFromJson(aesDecryptData(result));
                                if (map != null) {
                                    if ("1".equals(String.valueOf(map.get("info_enable")))) {
                                        Tools.my.infoEnable = true;
                                    }
                                    if ("1".equals(String.valueOf(map.get("mail_enable")))) {
                                        Tools.my.mailEnable = true;
                                    }
                                    if ((Tools.my.mail = String.valueOf(map.get("mail"))).equals("no-mail")) {
                                        Tools.my.mail = "";
                                    }
                                }
                            }
                        }
                        if (setEnableInterface != null) {
                            setEnableInterface.onComplete();
                        }
                    }
                });
            }
        }
    }

    //********************接口*********************
    /**
     * 初始化与服务器连接的专用接口.
     */
    public interface InitConnectionInterface {
        /**
         * 当初始化连接成功时调用.
         */
        void onSuccess();

        /**
         * 当初始化连接失败时调用.
         * 在启动页中已确保只会调用一次.
         */
        void onFail();

        /**
         * 当初始化连接超时时调用.
         * 在启动页中已确保只会调用一次.
         */
        void onTimeout();

        /**
         * 当初始化连接发生可处理异常之外的异常时调用.
         * 在启动页中已确保只会调用一次.
         * 可处理的异常：ConnectException、SocketTimeoutException.
         */
        void onException();
    }

    /**
     * 刷新本地缓存头像的专用接口.
     */
    public interface RefreshLocalPhotoInterface {

        /**
         * 本地缓存刷新完成后调用.
         * 运行在UI线程.
         */
        void onComplete();
    }

    /**
     * 设置完善信息标识的专用接口.
     */
    public interface SetEnableInterface {

        /**
         * 当完成请求时调用.
         */
        void onComplete();
    }

    /**
     * 进行部分网络请求时的通用请求结束回调接口.
     */
    public interface EndOfRequest {
        /**
         * 当网络请求成功获得返回结果时调用.
         *
         * @param result 请求结果
         */
        void onSuccess(String result);

        /**
         * 当网络请求过程中发生异常时调用.
         */
        void onException();

        /**
         * 当网络请求连接超时时调用.
         */
        void onTimeout();

        /**
         * 当网络连接不可用时调用.
         */
        void noNet();
    }
}
