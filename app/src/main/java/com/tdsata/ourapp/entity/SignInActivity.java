package com.tdsata.ourapp.entity;

import com.tdsata.ourapp.util.Tools;

import java.io.Serializable;
import java.util.Objects;

/**
 * 签到活动的数据属性.
 */
public class SignInActivity implements Serializable {
    private String title;// 活动标题
    private String signInTime;// 活动开始时间
    private int continueTime;// 签到有效限时（单位：分钟）

    private static final long serialVersionUID = 202101310040L;
    private long startTime = 0L;
    private long endTime = 0L;
    //private final MyLog myLog = new MyLog("SignInClassTAG");

    // 优先级 IS_RUNNING > COMING_SONG > ALREADY_END > NO_STATUS
    /**
     * 签到活动正在进行.
     * 数值大小代表优先级.
     */
    public static final int IS_RUNNING = 3;

    /**
     * 签到活动距离开始时间在12小时以内.
     * 数值大小代表优先级.
     */
    public static final int COMING_SOON = 2;

    /**
     * 签到活动已经结束.
     * 数值大小代表优先级.
     */
    public static final int ALREADY_END = 1;

    /**
     * 签到活动无符合状态.
     * 数值大小代表优先级.
     */
    public static final int NO_STATUS = 0;

    public String getTitle() {
        return title;
    }

    public String getSignInTime() {
        return signInTime;
    }

    public int getContinueTime() {
        return continueTime;
    }

    /**
     * 获取距离结束时间所剩时长（单位：分钟）.
     *
     * @return 若尚未结束，则返回不大于距离结束所剩时长的非负数，否则返回-1
     */
    public int getEndTime() {
        if (endTime != 0L) {
            long nowTime = Tools.getNowTime();
            if (nowTime <= endTime) {
                return (int) ((endTime - nowTime) / 60000L);
            } else {
                return -1;
            }
        } else {
            return -1;
        }
    }

    /**
     * 判断活动是否已开始.
     *
     * @return 若活动开始时间已过则返回true
     */
    public boolean isStart() {
        return startTime <= Tools.getNowTime();
    }

    /**
     * 判断活动是否已结束.
     *
     * @return 若已结束则返回true
     */
    public boolean isEnd() {
        return endTime < Tools.getNowTime();
    }

    /**
     * 活动活动当前状态.
     *
     * @return 标识活动状态的常量整型值
     * @see #IS_RUNNING
     * @see #COMING_SOON
     * @see #ALREADY_END
     * @see #NO_STATUS
     */
    public int getActivityStatus() {
        try {
            if (startTime == 0L || endTime == 0L) {
                startTime = Objects.requireNonNull(Tools.dateFormat.parse(signInTime)).getTime();
                endTime = startTime + continueTime * 60000L;
            }
            long nowTime = Tools.getNowTime();
            if (nowTime < startTime) {
                if (nowTime >= startTime - 12 * 60 * 60000L) {
                    return COMING_SOON;
                } else {
                    return NO_STATUS;
                }
            } else if (nowTime <= endTime) {
                return IS_RUNNING;
            } else {
                return ALREADY_END;
            }
        } catch (Exception e) {
            return NO_STATUS;
        }
    }

    @Override
    public String toString() {
        return "SignInActivity{" +
                "title='" + title + '\'' +
                '}';
    }
}
