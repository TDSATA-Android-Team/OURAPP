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
                log("\n" + Tools.dateFormat.format(System.currentTimeMillis()) + ": ????????????");
            } else {
                Log.e("com.tdsata.ourapp", "??????????????????");
            }
        } catch (Exception e) {
            Log.e("com.tdsata.ourapp", "??????????????????", e);
            logFile = null;
        }
        if (readCache()) {
            log("???????????? ==> ??????: " + departmentJson + "  ????????????: " + cacheAnnouncements);
            generateAESKey();
            log("??????AES??????");
            Request request = new Request.Builder()
                    .url(FixedValue.address + "initConnection")
                    .build();
            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    log("??????RSA????????????", e);
                    service.stopSelf();
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    ResponseBody body = response.body();
                    if (body != null) {
                        revertRSAPublicKey(body.string());
                        log("??????RSA??????");
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
                                log("????????????????????????", e);
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
                                            log("???????????????????????????" + result);
                                            break;
                                        default:
                                            List<Announcement> announcements = Tools.getListFromJson(aesDecryptData(result), Announcement.class);
                                            if (announcements != null) {
                                                log("???????????????" + announcements);
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
     * ????????????.
     *
     * @param message ????????????
     */
    private void showNotify(String message) {
        Intent start = new Intent(service, ActivityStartPicture.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(service, 0, start, PendingIntent.FLAG_ONE_SHOT);
        int notifyId = Tools.getRequestCode();
        String channelId = "20105";
        // Android 8.0 ??????????????????????????????
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(channelId, "????????????", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("??????????????????????????????");
            manager.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(service, channelId)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("???????????????")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        // ????????????
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
    private Key aesKey = null;// AES??????
    private byte[] aesKeyBytes = null;// AES??????????????????
    private String verifyCiphertext = null;

    /**
     * ??????AES??????.
     */
    public void generateAESKey() {
        try {
            KeyGenerator aes = KeyGenerator.getInstance("AES");
            aes.init(256);
            aesKeyBytes = aes.generateKey().getEncoded();
            aesKey = new SecretKeySpec(aesKeyBytes, "AES");
            verifyCiphertext = aesEncryptData("TD-SATA");
        } catch (Exception e) {
            log("??????AES????????????", e);
            service.stopSelf();
        }
    }

    /**
     * ??????AES??????????????????.
     *
     * @param data ??????????????????
     * @return ???????????????????????????Base64??????
     */
    @SuppressLint("GetInstance")
    private String aesEncryptData(String data) {
        try {
            if (aesKey == null)
                throw new NullPointerException("AES????????????");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
            return new String(Base64.encode(cipher.doFinal(data.getBytes()), Base64.NO_WRAP), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log("??????????????????", e);
            service.stopSelf();
            return null;
        }
    }

    /**
     * ??????AES??????????????????.
     *
     * @param ciphertext ??????????????????Base64???????????????
     * @return ??????????????????
     */
    @SuppressLint("GetInstance")
    public String aesDecryptData(String ciphertext) {
        try {
            if (aesKey == null)
                throw new NullPointerException("AES????????????");
            if (ciphertext == null)
                throw new NullPointerException("????????????");
            byte[] decodeCiphertextBytes = Base64.decode(ciphertext.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, aesKey);
            return new String(cipher.doFinal(decodeCiphertextBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log("??????????????????", e);
            return null;
        }
    }

    //====================RSA=====================
    private PublicKey publicKey = null;// RSA??????
    private String encryptAESKey = null;// ??????RSA???????????????AES??????

    /**
     * ??????RSA????????????????????????RSA??????.
     */
    private void revertRSAPublicKey(String rsaPublicKeyStr) {
        try {
            byte[] rsaPublicKeyBytes = Base64.decode(rsaPublicKeyStr.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(rsaPublicKeyBytes));
            encryptAESKey = getEncryptAESKey();
        } catch (Exception e) {
            log("??????RSA????????????", e);
            service.stopSelf();
        }
    }

    /**
     * ??????RSA????????????AES??????.
     *
     * @return ???Base64?????????????????????AES???????????????
     */
    private String getEncryptAESKey() {
        try {
            if (publicKey == null) {
                throw new NullPointerException("RSA????????????");
            }
            if (aesKeyBytes == null) {
                throw new NullPointerException("AES????????????");
            }
            int rsaKeySize = 1024;// RSA????????????
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
            log("??????AES????????????", e);
            return null;
        }
    }
}