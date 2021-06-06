package com.tdsata.ourapp.entity;

import com.tdsata.ourapp.util.Tools;

import java.util.Objects;

/**
 * 公告的数据属性.
 */
public class Announcement {
    private Member administrator = null;
    // 数据属性
    private String id;
    private String number;
    private String message;

    public Member getAdministrator() {
        if (administrator == null) {
            for (Member member : Tools.administrators) {
                if (member.getNumber().equals(number)) {
                    administrator = member;
                    break;
                }
            }
        }
        return administrator;
    }

    public String getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "Announcement{" +
                "id='" + id + '\'' +
                ", number='" + number + '\'' +
                ", message='" + message + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Announcement that = (Announcement) o;
        return that.id.equals(id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(administrator, id, number, message);
    }
}
