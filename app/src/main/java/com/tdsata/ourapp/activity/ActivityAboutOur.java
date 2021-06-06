package com.tdsata.ourapp.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Bundle;
import android.view.View;

import com.tdsata.ourapp.R;
import com.tdsata.ourapp.util.Tools;

public class ActivityAboutOur extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_our);

        Tools.setBlackWordOnStatus(this);
        ((Toolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
    }
}