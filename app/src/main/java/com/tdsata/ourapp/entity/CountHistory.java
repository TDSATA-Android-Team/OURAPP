package com.tdsata.ourapp.entity;

import com.tdsata.ourapp.util.Tools;

/**
 * 积分修改记录的数据属性.
 */
public class CountHistory {
    private String editorName = "";// 修改人姓名
    private String changeName = "";// 被修改人姓名
    // 数据属性
    private String editor_number;// 修改人学号
    private String change_number;// 被修改人学号
    private int change_value;// 积分变动的数值
    private String description;// 积分变动的说明

    public String getEditorNumber() {
        return editor_number;
    }

    public String getChangeNumber() {
        return change_number;
    }

    public String getChangeValueString() {
        return change_value >= 0 ? "+" + change_value : String.valueOf(change_value);
    }

    public String getDescription() {
        if (description.equals("no-description")) {
            return "无";
        }
        return description;
    }

    public String getEditorName() {
        if ("".equals(editorName)) {
            for (Member member : Tools.administrators) {
                if (member.getNumber().equals(editor_number)) {
                    editorName = member.getName();
                    break;
                }
            }
        }
        return editorName;
    }

    public String getChangeName() {
        if ("".equals(changeName)) {
            for (Member member : Tools.memberList) {
                if (member.getNumber().equals(change_number)) {
                    changeName = member.getName();
                    break;
                }
            }
        }
        return changeName;
    }
}
