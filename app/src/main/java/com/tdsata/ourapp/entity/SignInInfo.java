package com.tdsata.ourapp.entity;

/**
 * 签到状态信息的数据属性.
 */
public class SignInInfo {
    private String number;
    private String name;
    private int signInStatus;

    public SignInInfo(String number, String name, int signInStatus) {
        this.number = number;
        this.name = name;
        this.signInStatus = signInStatus;
    }

    public String getNumber() {
        return number;
    }

    public String getName() {
        return name;
    }

    public int getSignInStatus() {
        return signInStatus;
    }
}
