package com.tdsata.ourapp.service;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.tdsata.ourapp.R;
import com.tdsata.ourapp.activity.ActivityStartPicture;
import com.tdsata.ourapp.entity.Announcement;
import com.tdsata.ourapp.util.FixedValue;
import com.tdsata.ourapp.util.Tools;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

public class PullMessageService extends Service {
    private final Service service = this;
    private File logFile;
    private File cacheFile;
    private String departmentJson;
    private List<Announcement> cacheAnnouncements;
    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .callTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Tools.startPollingAlarm(service);
        logFile = Tools.generateFileAtCache(service, null, "ServiceLog.log", true);
        try {
            if (logFile.exists() || logFile.createNewFile()) {
                log("\n" + Tools.dateFormat.format(System.currentTimeMillis()) + ": 服务启动");
            } else {
                Log.e("com.tdsata.ourapp", "日志构造失败");
            }
        } catch (Exception e) {
            Log.e("com.tdsata.ourapp", "日志构造失败", e);
            logFile = null;
        }
        if (readCache()) {
            log("读取配置 ==> 部门: " + departmentJson + "  公告缓存: " + cacheAnnouncements);
            generateAESKey();
            log("生成AES密钥");
            Request request = new Request.Builder()
                    .url(FixedValue.address + "initConnection")
                    .build();
            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    log("获取RSA公钥失败", e);
                    service.stopSelf();
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    ResponseBody body = response.body();
                    if (body != null) {
                        revertRSAPublicKey(body.string());
                        log("获取RSA公钥");
                        departmentJson = aesEncryptData(departmentJson);
                        RequestBody requestBody = new FormBody.Builder()
                                .add("aesKeyStr", String.valueOf(encryptAESKey))
                                .add("verifyCiphertext", verifyCiphertext)
                                .add("departmentJson", departmentJson)
                                .build();
                        Request request = new Request.Builder()
                                .url(FixedValue.address + "getAnnouncementList")
                                .post(requestBody)
                                .build();
                        okHttpClient.newCall(request).enqueue(new Callback() {
                            @Override
                            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                                log("获取公告列表失败", e);
                                service.stopSelf();
                            }

                            @Override
                            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                                ResponseBody body = response.body();
                                if (body != null) {
                                    String result = body.string();
                                    switch (result) {
                                        case "NO_ANNOUNCEMENT":
                                        case "ERROR":
                                        case "AES_KEY_ERROR":
                                            log("获取公告数据失败：" + result);
                                            break;
                                        default:
                                            List<Announcement> announcements = Tools.getListFromJson(aesDecryptData(result), Announcement.class);
                                            if (announcements != null) {
                                                log("获取公告：" + announcements);
                                                Tools.saveObjectAtFile(cacheFile, announcements);
                                                StringBuilder message = new StringBuilder();
                                                int num = 1;
                                                for (Announcement announcement : announcements) {
                                                    if (!cacheAnnouncements.contains(announcement)) {
                                                        message.append(num++).append(". ").append(announcement.getMessage()).append("\n");
                                                    }
                                                }
                                                if (message.length() > 0) {
                                                    showNotify(message.toString());
                                                }
                                            }
                                            break;
                                    }
                                }
                                service.stopSelf();
                            }
                        });
                    } else {
                        service.stopSelf();
                    }
                }
            });
        }
        return START_NOT_STICKY;
    }

    private boolean readCache() {
        try {
            SharedPreferences read = getSharedPreferences(FixedValue.LoginCfg, MODE_PRIVATE);
            Tools.DepartmentEnum department = Tools.DepartmentEnum.valueOf(read.getString(FixedValue.myDepartment, null));
            departmentJson = Tools.getJson(department);
            cacheFile = Tools.generateFileAtCache(service, null, FixedValue.PullServiceCfg, false);
            if (cacheFile.exists() || cacheFile.createNewFile()) {
                cacheAnnouncements = Tools.readListFromFile(cacheFile, Announcement.class);
                if (cacheAnnouncements == null) {
                    cacheAnnouncements = new ArrayList<>();
                }
                return departmentJson != null;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 展示通知.
     *
     * @param message 通知信息
     */
    private void showNotify(String message) {
        Intent start = new Intent(service, ActivityStartPicture.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(service, 0, start, PendingIntent.FLAG_ONE_SHOT);
        int notifyId = Tools.getRequestCode();
        String channelId = "20105";
        // Android 8.0 及以上需添加通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(channelId, "推送服务", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("为您实时推送公告讯息");
            manager.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(service, channelId)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("新公告发布")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        // 发出通知
        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(service);
        managerCompat.notify(notifyId, builder.build());
    }

    private void log(String message) {
        if (logFile != null) {
            Tools.saveFile(logFile, message + "\n", true);
        }
    }

    private void log(String message, Exception e) {
        log(message + "(" + e.getClass().getSimpleName() + "): " + e.getMessage());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    //====================AES=====================
    private Key aesKey = null;// AES密钥
    private byte[] aesKeyBytes = null;// AES密钥字节数组
    private String verifyCiphertext = null;

    /**
     * 生成AES密钥.
     */
    public void generateAESKey() {
        try {
            KeyGenerator aes = KeyGenerator.getInstance("AES");
            aes.init(256);
            aesKeyBytes = aes.generateKey().getEncoded();
            aesKey = new SecretKeySpec(aesKeyBytes, "AES");
            verifyCiphertext = aesEncryptData("TD-SATA");
        } catch (Exception e) {
            log("生成AES密钥失败", e);
            service.stopSelf();
        }
    }

    /**
     * 使用AES密钥加密数据.
     *
     * @param data 待加密的数据
     * @return 加密后的密文，使用Base64编码
     */
    @SuppressLint("GetInstance")
    private String aesEncryptData(String data) {
        try {
            if (aesKey == null)
                throw new NullPointerException("AES密钥为空");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
            return new String(Base64.encode(cipher.doFinal(data.getBytes()), Base64.NO_WRAP), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log("加密数据失败", e);
            service.stopSelf();
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
    public String aesDecryptData(String ciphertext) {
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
            log("解密数据失败", e);
            return null;
        }
    }

    //====================RSA=====================
    private PublicKey publicKey = null;// RSA公钥
    private String encryptAESKey = null;// 使用RSA公钥加密的AES密钥

    /**
     * 解码RSA公钥字符串以构造RSA公钥.
     */
    private void revertRSAPublicKey(String rsaPublicKeyStr) {
        try {
            byte[] rsaPublicKeyBytes = Base64.decode(rsaPublicKeyStr.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(rsaPublicKeyBytes));
            encryptAESKey = getEncryptAESKey();
        } catch (Exception e) {
            log("构造RSA公钥失败", e);
            service.stopSelf();
        }
    }

    /**
     * 使用RSA公钥加密AES密钥.
     *
     * @return 以Base64编码的加密后的AES密钥字符串
     */
    private String getEncryptAESKey() {
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
            log("加密AES密钥失败", e);
            return null;
        }
    }
}