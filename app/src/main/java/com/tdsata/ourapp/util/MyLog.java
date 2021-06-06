package com.tdsata.ourapp.util;

import android.util.Log;

import java.io.Serializable;

/**
 * 对类{@link Log}进行包装.<br>
 * 更改{@link #DEBUG}的值可决定是否输出低于信息级别的日志.
 */
public class MyLog implements Serializable {
    private static final long serialVersionUID = 202101311607L;
    private final String tag;

    /**
     * 控制是否输出低于信息级别的日志（true表示输出）.
     */
    private static final boolean DEBUG = false;

    public MyLog(String tag) {
        this.tag = tag;
    }

    public void v(String msg) {
        v(tag, msg);
    }

    public static void v(String tag, String msg) {
        if (DEBUG) {
            Log.v(tag, msg);
        }
    }

    public void v(String errorInfo, Throwable e) {
        v(tag, errorInfo, e);
    }

    public static void v(String tag, String errorInfo, Throwable e) {
        if (DEBUG) {
            Log.v(tag, errorInfo + " (" + e.getClass().getSimpleName() + "): " + e.getMessage());
        }
    }

    public void d(String msg) {
        d(tag, msg);
    }

    public static void d(String tag, String msg) {
        if (DEBUG) {
            Log.d(tag, msg);
        }
    }

    public void d(String errorInfo, Throwable e) {
        d(tag, errorInfo, e);
    }

    public static void d(String tag, String errorInfo, Throwable e) {
        if (DEBUG) {
            Log.d(tag, errorInfo + " (" + e.getClass().getSimpleName() + "): " + e.getMessage());
        }
    }

    public void i(String msg) {
        i(tag, msg);
    }

    public static void i(String tag, String msg) {
        Log.i(tag, msg);
    }

    public void i(String errorInfo, Throwable e) {
        i(tag, errorInfo, e);
    }

    public static void i(String tag, String errorInfo, Throwable e) {
        Log.i(tag, errorInfo + " (" + e.getClass().getSimpleName() + "): " + e.getMessage());
    }

    public void w(String msg) {
        w(tag, msg);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, msg);
    }

    public void w(String errorInfo, Throwable e) {
        w(tag, errorInfo, e);
    }

    public static void w(String tag, String errorInfo, Throwable e) {
        Log.w(tag, errorInfo + " (" + e.getClass().getSimpleName() + "): " + e.getMessage());
    }

    public void e(String msg) {
        e(tag, msg);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
    }

    public void e(String errorInfo, Throwable e) {
        e(tag, errorInfo, e);
    }

    public static void e(String tag, String errorInfo, Throwable e) {
        Log.e(tag, errorInfo + " (" + e.getClass().getSimpleName() + "): " + e.getMessage());
    }
}
