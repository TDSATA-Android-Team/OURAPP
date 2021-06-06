package com.tdsata.ourapp.entity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import com.tdsata.ourapp.R;
import com.tdsata.ourapp.util.FixedValue;
import com.tdsata.ourapp.util.Tools;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;

/**
 * 部门成员的数据属性.
 */
public class Member implements Serializable {
    private static final long serialVersionUID = 202102100951L;
    // 数据属性
    private String name;   // 姓名
    private String number; // 学号
    private String subject;// 专业
    private String phone;  // 联系电话
    private String teacher;// 辅导员
    private String qq;     // QQ
    private String sex;    // 性别
    private int count;     // 积分
    private String flag;   // 身份标识
    private String photoName;// 图片的文件名，用于本地缓存及校验更新

    public String getName() {
        return name;
    }

    public String getNumber() {
        return number;
    }

    public String getSubject() {
        return subject;
    }

    public String getPhone() {
        return phone;
    }

    public String getTeacher() {
        return teacher;
    }

    public String getQQ() {
        return qq;
    }

    public String getSex() {
        return sex;
    }

    public int getCount() {
        return count;
    }

    public String getFlag() {
        return flag;
    }

    public String[] getFlagArray() {
        String[] result = flag.split(",");
        for (int i = 0; i < result.length; i++) {
            result[i] = result[i].trim();
        }
        return result;
    }

    /**
     * 获取身份信息.
     * 多个身份以"/"连接.
     *
     * @return 身份信息
     */
    public String getIdentity() {
        String[] flags = getFlagArray();
        StringBuilder identity = new StringBuilder();
        // 0-部员，1-小组组长，2-副部长，3-部长
        for (int i = 0; i < flags.length; i++) {
            switch (flags[i]) {
                case "0":
                    identity.append("部员");
                    break;
                case "1":
                    identity.append("组长");
                    break;
                case "2":
                    identity.append("副部长");
                    break;
                case "3":
                    identity.append("部长");
                    break;
            }
            if (i < flags.length - 1) {
                identity.append("/");
            }
        }
        return identity.toString();
    }

    public String getPhotoName() {
        return photoName;
    }

    public void setPhotoName(String photoName) {
        this.photoName = photoName;
    }

    /**
     * 判断是否使用默认的头像.
     *
     * @return 若使用默认的头像则返回true，否则返回false
     */
    public boolean useDefaultPhoto() {
        return "default_photo".equals(photoName);
    }

    /**
     * 为ImageView及其子类的对象设置头像.
     *
     * @param context 上下文
     * @param imageView 被设置的ImageView对象
     */
    public void settingHeadPhoto(final Context context, final ImageView imageView) {
        imageView.setImageResource(R.drawable.pic_default_head_photo);
        if (!useDefaultPhoto()) {
            Tools.threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    File photo = Tools.generateFileAtCache(context, FixedValue.photoDirectory, photoName, true);
                    final Bitmap pic = BitmapFactory.decodeFile(photo.getAbsolutePath());
                    imageView.post(new Runnable() {
                        @Override
                        public void run() {
                            if (pic != null) {
                                imageView.setImageBitmap(pic);
                            } else {
                                imageView.setImageResource(R.drawable.pic_default_head_photo);
                            }
                        }
                    });
                }
            });
        }
    }

    /**
     * 判断是否是部门部长或副部长.
     *
     * @return 若是则返回true，否则返回false
     */
    public boolean isAdministrator() {
        return flag.contains("2") || flag.contains("3");
    }

    @Override
    public String toString() {
        return "Member {" +
                "学号:" + number +
                ", 姓名:" + name +
                ", 性别:" + sex +
                ", 专业:" + subject +
                ", 辅导员:" + teacher +
                ", QQ:" + qq +
                ", 电话:" + phone +
                ", 部门积分:" + count +
                ", 身份标识:" + Arrays.toString(getFlagArray()) +
                ", 头像文件名:" + photoName +
                '}';
    }
}
