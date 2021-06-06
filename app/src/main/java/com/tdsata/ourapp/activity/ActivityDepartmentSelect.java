package com.tdsata.ourapp.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.tdsata.ourapp.R;
import com.tdsata.ourapp.util.FixedValue;
import com.tdsata.ourapp.util.Tools;

/**
 * 部门选择页面.
 */
public class ActivityDepartmentSelect extends AppCompatActivity {
    //private final MyLog myLog = new MyLog("DepartmentSelectTAG");
    private final AppCompatActivity activity = this;

    private View[][] select;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_department_select);

        initView();
        myListener();
    }

    private void initView() {
        // 软研部  网络部  电子部
        // 办公室  科宣部  商务部
        select = new View[][] {
                {findViewById(R.id.software), findViewById(R.id.network), findViewById(R.id.electron)},
                {findViewById(R.id.office), findViewById(R.id.publicity), findViewById(R.id.business)}
        };
    }

    private void myListener() {
        View.OnClickListener selectListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // switch语句中case后须跟常量
                // Gradle Plugin 5.0（Android Studio 4.1）后，“R.id.*”为变量（不知道为什么）
                int id = v.getId();
                if (id == R.id.software) {
                    skipLogin(Tools.DepartmentEnum.SOFTWARE);
                } else if (id == R.id.network) {
                    skipLogin(Tools.DepartmentEnum.NETWORK);
                } else if (id == R.id.electron) {
                    skipLogin(Tools.DepartmentEnum.ELECTRON);
                } else if (id == R.id.office) {
                    skipLogin(Tools.DepartmentEnum.OFFICE);
                } else if (id == R.id.publicity) {
                    skipLogin(Tools.DepartmentEnum.PUBLICITY);
                } else if (id == R.id.business) {
                    skipLogin(Tools.DepartmentEnum.BUSINESS);
                }
            }
        };
        for (View[] views : select) {
            for (View view : views) {
                view.setOnClickListener(selectListener);
            }
        }
    }

    private void skipLogin(Tools.DepartmentEnum department) {
        Intent startLogin = new Intent(activity, ActivityLogin.class);
        startLogin.putExtra(FixedValue.myDepartment, department);
        startActivity(startLogin);
    }
}